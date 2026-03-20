package HwOS.lib.regfile

import chisel3._
import chisel3.util._
import HwOS.kernel.function.HwInline
import HwOS.kernel.lang.HwOSLanguage._
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{Kernel, SysCall}
import HwOS.stdlib.sync._

object RegfileLib {

  // ==========================================
  // Layer 1: 基础寄存器堆 (纯物理数据通路)
  // 不提供端口分配，仅保留最薄的读写接口
  // ==========================================
  class BaseRegfileProcess(val depth: Int, val width: Int, val zeroReg: Boolean, localName: String)(implicit kernel: Kernel)
      extends HwProcess(localName) {

    private val regs = ((RegInit(VecInit(Seq.fill(depth)(0.U(width.W))))))
    
    override def entry(): Unit = {}

    def Read(addr: UInt): HwInline[UInt] = HwInline.stateless("Base_Read") { _ =>
      if (zeroReg) Mux(addr === 0.U, 0.U, regs(addr)) else regs(addr)
    }

    def Write(addr: UInt, data: UInt): HwInline[Unit] = HwInline.stateless("Base_Write") { _ =>
      if (zeroReg) {
        when(addr =/= 0.U) {
          regs.at(addr)  :=  data
        }
      } else {
        regs.at(addr)  :=  data
      }
      ()
    }
  }

  // ==========================================
  // Layer 2: 信号量寄存器堆
  // 在基础寄存器堆之上挂写槽仲裁，不引入冲突协议
  // ==========================================
  class SemaphoreRegfileProcess(
      val depth: Int,
      val width: Int,
      val maxClients: Int,
      val maxWriteSlots: Int,
      val zeroReg: Boolean,
      localName: String,
  )(implicit kernel: Kernel)
      extends HwProcess(localName) {

    private val baseReg = spawn(new BaseRegfileProcess(depth, width, zeroReg, "Base"))
    private val writeSlots = spawn(new SemaphoreProcess(maxClients, initialCount = maxWriteSlots, "WriteSlots"))

    override def entry(): Unit = {}

    def Read(addr: UInt): HwInline[UInt] = baseReg.Read(addr)

    def Write(clientId: Int, addr: UInt, data: UInt): HwInline[Unit] = HwInline.atomic(s"SemaWrite_$clientId") { _ =>
      val writePort = SysCall.Inline(RequestWritePort(clientId))
      SysCall.Inline(writePort.Acquire())
      SysCall.Inline(writePort.Write(addr, data))
      SysCall.Inline(writePort.Release())
      ()
    }

    class RegWritePort(val clientId: Int) {
      def Acquire(): HwInline[Unit] = HwInline.atomic(s"AcquireWriteSlot_$clientId") { t =>
        val slotLease = SysCall.Inline(writeSlots.RequestLease(clientId))
        SysCall.Inline(slotLease.Acquire())
      }

      def Write(addr: UInt, data: UInt): HwInline[Unit] = HwInline.stateless(s"Write_$clientId") { _ =>
        val slotLease = SysCall.Inline(writeSlots.RequestLease(clientId))
        chisel3.assert(slotLease.isActive, s"RegWritePort[$clientId].Write requires an acquired write-slot lease")
        SysCall.Inline(baseReg.Write(addr, data))
        ()
      }

      def Release(): HwInline[Unit] = HwInline.stateless(s"ReleaseWriteSlot_$clientId") { _ =>
        val slotLease = SysCall.Inline(writeSlots.RequestLease(clientId))
        SysCall.Inline(slotLease.Release())
      }
    }

    private val writePorts = Array.tabulate(maxClients)(i => new RegWritePort(i))

    def RequestWritePort(clientId: Int): HwInline[RegWritePort] = HwInline.bindings(s"ReqSemaRegPort_$clientId") { _ =>
      writePorts(clientId)
    }
  }

  // ==========================================
  // Layer 3: 记分板寄存器堆
  // 组装 SemaphoreRegfile 和 Stdlib.Scoreboard
  // ==========================================
  class ScoreboardRegfileProcess(val depth: Int, val width: Int, val maxWriters: Int, val zeroReg: Boolean, localName: String)(implicit kernel: Kernel)
      extends HwProcess(localName) {

    val semaReg = spawn(new SemaphoreRegfileProcess(depth, width, maxWriters, maxWriters, zeroReg, "Sema"))
    val scoreboard = spawn(new ScoreboardProcess(resourceCount = depth, maxConcurrentPorts = maxWriters, zeroAlwaysFree = zeroReg, "Control"))

    override def entry(): Unit = {}

    def ReadCommitted(addr: UInt): HwInline[UInt] = semaReg.Read(addr)
    def Read(addr: UInt): HwInline[UInt] = GuardedRead(addr)

    def GuardedRead(addr: UInt): HwInline[UInt] = HwInline.atomic("GuardedRead") { t =>
      val ready = SysCall.Inline(scoreboard.Guard(addr))

      val rdata = (WireInit(0.U(width.W)))
      when(ready) {
        rdata  :=  SysCall.Inline(semaReg.Read(addr))
      }
      rdata
    }

    class RegWritePort(val portIdx: Int) {
      def Reserve(addr: UInt): HwInline[Unit] = HwInline.atomic(s"Reserve_$portIdx") { t =>
        val writePort = SysCall.Inline(semaReg.RequestWritePort(portIdx))
        val sbLease = SysCall.Inline(scoreboard.RequestLease(portIdx))
        SysCall.Inline(writePort.Acquire())
        SysCall.Inline(sbLease.Reserve(addr))
      }

      def WritebackAndClear(addr: UInt, data: UInt): HwInline[Unit] = HwInline.stateless(s"WB_$portIdx") { _ =>
        val writePort = SysCall.Inline(semaReg.RequestWritePort(portIdx))
        val sbLease = SysCall.Inline(scoreboard.RequestLease(portIdx))
        SysCall.Inline(writePort.Write(addr, data))
        SysCall.Inline(sbLease.Release())
        SysCall.Inline(writePort.Release())
      }
    }

    def Write(portIdx: Int, addr: UInt, data: UInt): HwInline[Unit] = HwInline.atomic(s"RegWrite_$portIdx") { _ =>
      val writePort = SysCall.Inline(RequestWritePort(portIdx))
      SysCall.Inline(writePort.Reserve(addr))
      SysCall.Inline(writePort.WritebackAndClear(addr, data))
      ()
    }

    private val writePorts = Array.tabulate(maxWriters)(i => new RegWritePort(i))

    def RequestWritePort(portIdx: Int): HwInline[RegWritePort] = HwInline.bindings(s"ReqRegPort_$portIdx") { _ =>
      writePorts(portIdx)
    }
  }

  class AgeOrderedScoreboardRegfileProcess(
      val depth: Int,
      val width: Int,
      val maxWriters: Int,
      val maxInFlight: Int,
      val zeroReg: Boolean,
      localName: String,
  )(implicit kernel: Kernel)
      extends HwProcess(localName) {

    private val addrWidth = log2Ceil(depth max 2)
    private case class PendingPort(busy: Bool, addr: UInt, data: UInt, ready: Bool)

    val semaReg = spawn(new SemaphoreRegfileProcess(depth, width, maxWriters, maxWriters, zeroReg, "Sema"))
    val scoreboard = spawn(new ScoreboardProcess(depth, maxWriters, zeroAlwaysFree = zeroReg, "Control"))
    val orderWindow = spawn(new OrderedWindowProcess(maxWriters, maxInFlight, "OrderWindow"))

    private val pendingPorts = Array.tabulate(maxWriters) { _ =>
      PendingPort(
        (RegInit(false.B)),
        (RegInit(0.U(addrWidth.W))),
        (RegInit(0.U(width.W))),
        (RegInit(false.B)),
      )
    }
    private val publishDone = Array.tabulate(maxWriters)(_ => (RegInit(false.B)))

    private def matchingPorts(addr: UInt, requireReady: Bool): Vec[Bool] =
      VecInit(pendingPorts.toIndexedSeq.map(p => p.busy && p.addr === addr && (!requireReady || p.ready)))

    private def BeginPendingWrite(portIdx: Int, addr: UInt): HwInline[Unit] = HwInline.atomic(s"BeginPendingWrite_$portIdx") { t =>
      val pending = pendingPorts(portIdx)
      pending.busy  :=  true.B
      pending.addr  :=  addr
      pending.ready  :=  false.B
      publishDone(portIdx)  :=  false.B
      ()
    }

    private def FinishPendingWrite(portIdx: Int, data: UInt): HwInline[Unit] = HwInline.atomic(s"FinishPendingWrite_$portIdx") { t =>
      val pending = pendingPorts(portIdx)
      pending.data  :=  data
      pending.ready  :=  true.B
      ()
    }

    override def entry(): Unit = {
      val daemon = createLogic("OrderedWriteDaemon")
      for (pending <- pendingPorts) {
        }
      for (done <- publishDone) {
        }

      daemon.run {
        for ((pending, i) <- pendingPorts.zipWithIndex) {
          val windowLease = SysCall.Inline(orderWindow.RequestLease(i))
          when(pending.busy && pending.ready && SysCall.Inline(windowLease.Committed())) {
            val writePort = SysCall.Inline(semaReg.RequestWritePort(i))
            SysCall.Inline(writePort.Write(pending.addr, pending.data))
            val sbLease = SysCall.Inline(scoreboard.RequestLease(i))
            SysCall.Inline(sbLease.Release())
            SysCall.Inline(writePort.Release())
            SysCall.Inline(windowLease.ForceCommit())
            pending.busy  :=  false.B
            pending.ready  :=  false.B
            publishDone(i)  :=  true.B
          }
        }
      }
    }

    def ReadCommitted(addr: UInt): HwInline[UInt] = semaReg.Read(addr)
    def Read(addr: UInt): HwInline[UInt] = GuardedRead(addr)

    def GuardedRead(addr: UInt): HwInline[UInt] = HwInline.atomic("OrderedGuardedRead") { t =>
      val matchingReady = matchingPorts(addr, requireReady = true.B)
      val matchingBusy = matchingPorts(addr, requireReady = false.B)
      val canRead = (if (zeroReg) addr === 0.U else false.B) || !matchingBusy.asUInt.orR || matchingReady.asUInt.orR
      t.waitCondition(canRead)

      val rdata = (WireInit(0.U(width.W)))
      when(if (zeroReg) addr === 0.U else false.B) {
        rdata  :=  0.U
      }.elsewhen(matchingReady.asUInt.orR) {
        rdata  :=  Mux1H(matchingReady, VecInit(pendingPorts.toIndexedSeq.map(_.data)))
      }.otherwise {
        rdata  :=  SysCall.Inline(semaReg.Read(addr))
      }
      rdata
    }

    class OrderedRegWritePort(val portIdx: Int) {
      def Reserve(addr: UInt): HwInline[Unit] = HwInline.atomic(s"Reserve_$portIdx") { t =>
        val writePort = SysCall.Inline(semaReg.RequestWritePort(portIdx))
        val sbLease = SysCall.Inline(scoreboard.RequestLease(portIdx))
        val windowLease = SysCall.Inline(orderWindow.RequestLease(portIdx))
        SysCall.Inline(writePort.Acquire())
        SysCall.Inline(sbLease.Reserve(addr))
        SysCall.Inline(windowLease.Reserve())
        SysCall.Inline(BeginPendingWrite(portIdx, addr))
      }

      def WritebackAndClear(addr: UInt, data: UInt): HwInline[Unit] = HwInline.atomic(s"WB_$portIdx") { t =>
        val windowLease = SysCall.Inline(orderWindow.RequestLease(portIdx))
        SysCall.Inline(FinishPendingWrite(portIdx, data))
        SysCall.Inline(windowLease.Commit())
        t.waitCondition(publishDone(portIdx))
        ()
      }
    }

    def Write(portIdx: Int, addr: UInt, data: UInt): HwInline[Unit] = HwInline.atomic(s"OrderedRegWrite_$portIdx") { _ =>
      val writePort = SysCall.Inline(RequestWritePort(portIdx))
      SysCall.Inline(writePort.Reserve(addr))
      SysCall.Inline(writePort.WritebackAndClear(addr, data))
      ()
    }

    private val writePorts = Array.tabulate(maxWriters)(i => new OrderedRegWritePort(i))

    def RequestWritePort(portIdx: Int): HwInline[OrderedRegWritePort] = HwInline.bindings(s"ReqOrderedRegPort_$portIdx") { _ =>
      writePorts(portIdx)
    }
  }
}
