package HwOS.stdlib

import chisel3._
import chisel3.util._
import HwOS.kernel.context.{AtomicCtx, ContextScope, LogicCtx}
import HwOS.kernel.context.HwLease
import HwOS.kernel.function.HwFunction
import HwOS.kernel.lang.HwOSLanguage._
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{Kernel, SysCall}
import HwOS.kernel.thread.{HardwareAgent, HardwareThread}

object sync {

  // ==========================================
  // 1. 硬件互斥锁 (Mutex Process)
  // 内建基于优先级编码器的分布式仲裁器
  // ==========================================
  class MutexProcess(val maxClients: Int, localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
    private val gate = spawn(new SemaphoreProcess(maxClients, initialCount = 1, "Gate"))

    override def entry(): Unit = {}

    // ==========================================
    // 发行内核级租约 (Mutex Lease)
    // ==========================================
    class MutexLease(val id: Int) extends HwLease {
      private val gateLease = gate.directLease(id)
      override def isActive: Bool = gateLease.isActive

      def Lock(): HwFunction[Unit] = HwFunction.atomic(s"Lock_$id") { t =>
        val gateLease = SysCall.Call(gate.RequestLease(id))
        SysCall.Call(gateLease.Acquire())
      }

      def Unlock(): HwFunction[Unit] = HwFunction.stateless(s"Unlock_$id") { agent =>
        val gateLease = SysCall.Call(gate.RequestLease(id))
        when(gateLease.isActive) {
          SysCall.Call(gateLease.Release())
        }
      }

      override def forceReclaim(agent: HardwareAgent): Unit = {
        gateLease.forceReclaim(agent)
      }
    }

    private val leases = Array.tabulate(maxClients)(i => new MutexLease(i))

    def RequestLease(id: Int): HwFunction[MutexLease] = HwFunction.bindings(s"ReqMutexLease_$id") { _ =>
      leases(id) // 无状态返回预分配的句柄
    }

    def Lock(id: Int): HwFunction[Unit] = HwFunction.atomic(s"MutexLock_$id") { _ =>
      val lease = SysCall.Call(RequestLease(id))
      SysCall.Call(lease.Lock())
    }

    def Unlock(id: Int): HwFunction[Unit] = HwFunction.stateless(s"MutexUnlock_$id") { _ =>
      val lease = SysCall.Call(RequestLease(id))
      SysCall.Call(lease.Unlock())
    }

    def WithLock(id: Int)(body: HardwareThread => Unit): HwFunction[Unit] = HwFunction.thread(s"WithLock_$id") { t =>
      t.Step(s"AcquireLock_$id") {
        SysCall.Call(Lock(id))
      }
      body(t)
      t.Step(s"ReleaseLock_$id") {
        SysCall.Call(Unlock(id))
      }
      ()
    }

  }

  // ==========================================
  // 2. 硬件信号量 (Semaphore Process)
  // 支持多 Client 并发 Release，但单拍只允许 1 个 Client Acquire 成功
  // ==========================================
  class SemaphoreProcess(val maxClients: Int, val initialCount: Int,  localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
    
    private val count = this.own(RegInit(initialCount.U(32.W)))
    
    private val acquires = WireInit(VecInit(Seq.fill(maxClients)(false.B)))
    private val releases = WireInit(VecInit(Seq.fill(maxClients)(false.B)))

    for (i <- 0 until maxClients) {
      this.own(acquires(i))
      this.own(releases(i))
    }

    private val anyAcquire = acquires.reduce(_ || _)
    private val winnerAcqIdx = PriorityEncoder(acquires)
    
    // 允许多个 Client 在同一拍释放资源，使用 PopCount 累加
    private val totalRelease = PopCount(releases)
    
    // 结算下一拍的 Count
    override def entry(): Unit = {
      val main = createLogic("Main")
      this.grant(count, main)
      main.run{
        when (anyAcquire && (count > 0.U)) {
          count <== count + totalRelease - 1.U
        } .otherwise {
          count <== count + totalRelease
        }
      }
    }

    // --- 高阶 HwFunction 接口 ---
    class SemaphoreLease(val id: Int) extends HwLease {
      val isHeld = RegInit(false.B)
      SemaphoreProcess.this.own(isHeld)
      override def isActive: Bool = isHeld

      def Acquire(): HwFunction[Unit] = HwFunction.atomic(s"Acquire_$id") { t =>
        SemaphoreProcess.this.grant(acquires(id), t)
        acquires(id) <== true.B
        val canAcquire = isHeld || ((count > 0.U) && (winnerAcqIdx === id.U))
        t.waitCondition(canAcquire)

        when(!isHeld && canAcquire) {
          SemaphoreProcess.this.grant(isHeld, t)
          isHeld <== true.B
        }
        t.ctx.registerLease(this)
      }

      def Release(): HwFunction[Unit] = HwFunction.stateless(s"Release_$id") { agent =>
        SemaphoreProcess.this.grant(releases(id), agent)
        SemaphoreProcess.this.grant(isHeld, agent)
        when(isHeld) {
          releases(id) <== true.B
          isHeld <== false.B
        }
      }

      override def forceReclaim(agent: HardwareAgent): Unit = {
        SemaphoreProcess.this.grant(releases(id), agent)
        SemaphoreProcess.this.grant(isHeld, agent)
        releases(id) <==! true.B
        isHeld <==! false.B
      }
    }

    private val leases = Array.tabulate(maxClients)(i => new SemaphoreLease(i))
    private[sync] def directLease(id: Int): SemaphoreLease = leases(id)

    // 显式接口
    def RequestLease(id: Int): HwFunction[SemaphoreLease] = HwFunction.bindings(s"ReqSemLease_$id") { _ =>
      leases(id)
    }

    def Available(): HwFunction[Bool] = HwFunction.stateless("SemAvailable") { _ =>
      count > 0.U
    }

    def Acquire(id: Int): HwFunction[Unit] = HwFunction.atomic(s"SemAcquire_$id") { _ =>
      val lease = SysCall.Call(RequestLease(id))
      SysCall.Call(lease.Acquire())
    }

    def Release(id: Int): HwFunction[Unit] = HwFunction.stateless(s"SemRelease_$id") { _ =>
      val lease = SysCall.Call(RequestLease(id))
      SysCall.Call(lease.Release())
    }

    def WithPermit(id: Int)(body: HardwareThread => Unit): HwFunction[Unit] = HwFunction.thread(s"WithPermit_$id") { t =>
      t.Step(s"AcquirePermit_$id") {
        SysCall.Call(Acquire(id))
      }
      body(t)
      t.Step(s"ReleasePermit_$id") {
        SysCall.Call(Release(id))
      }
      ()
    }
  }

  // ==========================================
  // 3. 硬件等待组 (WaitGroup Process)
  // 内建并发加法树，完美解决多 Worker 同拍 Done 的数据践踏问题
  // ==========================================
  class WaitGroupProcess(val maxClients: Int, localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
    
    private val count = this.own(RegInit(0.U(32.W)))
    
    private val adds  = WireInit(VecInit(Seq.fill(maxClients)(0.U(32.W))))
    private val dones = WireInit(VecInit(Seq.fill(maxClients)(false.B)))

    for (i <- 0 until maxClients) {
      this.own(adds(i))
      this.own(dones(i))
    }

    // 组合逻辑加法树与 PopCount
    private val totalAdd  = adds.reduce(_ + _)
    private val totalDone = PopCount(dones)
    
    // 窥探下一拍的值，保证当拍归零时能立刻解锁主线程
    private val nextCount = count + totalAdd - totalDone

    override def entry(): Unit = {
      val main = createLogic("Main") 
      this.grant(count, main)
      main.run {
        count <== nextCount
      }
    }


    // --- 高阶 HwFunction 接口 ---
    def Add(id: Int, delta: UInt): HwFunction[Unit] = HwFunction.stateless(s"WG_Add_$id") { agent =>
      this.grant(adds(id), agent)
      adds(id) <== delta
    }

    def Done(id: Int): HwFunction[Unit] = HwFunction.stateless(s"WG_Done_$id") { agent =>
      this.grant(dones(id), agent)
      dones(id) <== true.B
    }

    def Wait(): HwFunction[Unit] = HwFunction.atomic("WG_Wait") { t =>
      // Wait 操作仅仅是纯组合逻辑读取 nextCount，不修改数据通路，因此不需要 grant 和 <==
      t.waitCondition(nextCount === 0.U)
      when(nextCount === 0.U) {
        t.Next.hijack()
      }
      ()
    }
  }

  // ==========================================
  // 4. 多路复用 (Select)
  // 作为纯无状态的 Utility 函数存在，不占有任何物理寄存器资源
  // ==========================================
  def Select(readySignals: Seq[Bool]): HwFunction[UInt] = HwFunction("Select") { agent =>
    val selectedIdx = WireInit(0.U(log2Ceil(readySignals.length max 2).W))

    ContextScope.current match {
      case AtomicCtx(t) =>
        val anyReady = readySignals.reduce(_ || _)
        t.waitCondition(anyReady)
        when(anyReady) {
          selectedIdx := PriorityEncoder(readySignals)
          t.Next.hijack()
        }
      case LogicCtx(l) =>
        selectedIdx := PriorityEncoder(readySignals)
      case _ => throw new Exception(s"[stdlib.sync] Select 上下文错误。调用者: ${agent.name}")
    }
    selectedIdx // 返回硬件线缆引用
  }

  class OrderedWindowProcess(val maxClients: Int, val maxInFlight: Int, localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
    private val tokenWidth = log2Ceil((maxInFlight + 1) max 2)
    private case class WindowReq(reserve: Bool, commit: Bool, forceCommit: Bool, reclaim: Bool)
    private case class WindowEntry(active: Bool, commitPending: Bool, token: UInt)

    private val nextIssue = this.own(RegInit(0.U(tokenWidth.W)))
    private val nextCommit = this.own(RegInit(0.U(tokenWidth.W)))
    private val inFlight = this.own(RegInit(0.U(tokenWidth.W)))
    private val reqs = Array.tabulate(maxClients) { _ =>
      WindowReq(
        this.own(WireInit(false.B)),
        this.own(WireInit(false.B)),
        this.own(WireInit(false.B)),
        this.own(WireInit(false.B)),
      )
    }

    private val entries = Array.tabulate(maxClients) { _ =>
      WindowEntry(
        this.own(RegInit(false.B)),
        this.own(RegInit(false.B)),
        this.own(RegInit(0.U(tokenWidth.W))),
      )
    }
    private def reserveRequests: UInt =
      PriorityEncoderOH(VecInit((0 until maxClients).map(i => reqs(i).reserve && !entries(i).active)).asUInt)
    private def forceCommitRequests: UInt =
      PriorityEncoderOH(VecInit((0 until maxClients).map(i => reqs(i).forceCommit && entries(i).active && entries(i).commitPending && entries(i).token === nextCommit)).asUInt)

    override def entry(): Unit = {
      val daemon = createLogic("OrderDaemon")
      this.grant(nextIssue, daemon)
      this.grant(nextCommit, daemon)
      this.grant(inFlight, daemon)
      entries.foreach { entry =>
        this.grant(entry.active, daemon)
        this.grant(entry.commitPending, daemon)
        this.grant(entry.token, daemon)
      }

      daemon.run {
        val reserveGrantOH = Mux(inFlight < maxInFlight.U, reserveRequests, 0.U(maxClients.W))
        val reserveFire = reserveGrantOH.orR
        val forceCommitOH = forceCommitRequests
        val commitFire = forceCommitOH.orR
        val reclaimOH = VecInit((0 until maxClients).map(i => reqs(i).reclaim && entries(i).active)).asUInt
        val reclaimCount = PopCount(reclaimOH)

        for ((entry, i) <- entries.zipWithIndex) {
          when(reserveGrantOH(i)) {
            entry.active <== true.B
            entry.commitPending <== false.B
            entry.token <== nextIssue
          }
          when(reqs(i).commit && entry.active) {
            entry.commitPending <== true.B
          }
          when(forceCommitOH(i) || reclaimOH(i)) {
            entry.active <== false.B
            entry.commitPending <== false.B
          }
        }

        when(reserveFire) {
          nextIssue <== nextIssue + 1.U
        }
        when(commitFire || reclaimOH.asUInt.orR) {
          when(commitFire) {
            nextCommit <== nextCommit + 1.U
          }.otherwise {
            val reclaimHead = VecInit((0 until maxClients).map(i => reclaimOH(i) && entries(i).token === nextCommit)).asUInt.orR
            when(reclaimHead) {
              nextCommit <== nextCommit + 1.U
            }
          }
          inFlight <== inFlight + Mux(reserveFire, 1.U, 0.U) - Mux(commitFire, 1.U, 0.U) - reclaimCount
        }.elsewhen(reserveFire) {
          inFlight <== inFlight + 1.U
        }
      }
    }

    class WindowLease(val id: Int) extends HwLease {
      override def isActive: Bool = entries(id).active

      def Reserve(): HwFunction[Unit] = HwFunction.atomic(s"Reserve_$id") { t =>
        OrderedWindowProcess.this.grant(reqs(id).reserve, t)
        reqs(id).reserve <== true.B
        val granted = entries(id).active
        t.waitCondition(granted)
        t.ctx.registerLease(this)
      }

      def Commit(): HwFunction[Unit] = HwFunction.stateless(s"Commit_$id") { agent =>
        OrderedWindowProcess.this.grant(reqs(id).commit, agent)
        reqs(id).commit <== true.B
      }

      def Committed(): HwFunction[Bool] = HwFunction.stateless(s"Committed_$id") { _ =>
        val entry = entries(id)
        entry.active && entry.commitPending && entry.token === nextCommit
      }

      def ForceCommit(): HwFunction[Unit] = HwFunction.stateless(s"ForceCommit_$id") { agent =>
        OrderedWindowProcess.this.grant(reqs(id).forceCommit, agent)
        reqs(id).forceCommit <== true.B
      }

      override def forceReclaim(agent: HardwareAgent): Unit = {
        OrderedWindowProcess.this.grant(reqs(id).reclaim, agent)
        reqs(id).reclaim <==! true.B
      }
    }

    private val leases = Array.tabulate(maxClients)(i => new WindowLease(i))

    def RequestLease(id: Int): HwFunction[WindowLease] = HwFunction.bindings(s"ReqWindowLease_$id") { _ =>
      leases(id)
    }
  }




  class BaseScoreboardProcess(val resourceCount: Int, val zeroAlwaysFree: Boolean = false, localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
    private val busyTable = this.exemptVectorAcl(this.own(RegInit(VecInit(Seq.fill(resourceCount)(false.B)))))
    
    override def entry(): Unit = {}

    def ReadBusy(addr: UInt): HwFunction[Bool] = HwFunction.stateless("BaseSB_ReadBusy") { _ =>
      if (zeroAlwaysFree) Mux(addr === 0.U, false.B, busyTable(addr)) else busyTable(addr)
    }

    def SetBusy(addr: UInt): HwFunction[Unit] = HwFunction.stateless("BaseSB_SetBusy") { agent =>
      if (zeroAlwaysFree) {
        when(addr =/= 0.U) {
          busyTable.at(addr) <== true.B
        }
      } else {
        busyTable.at(addr) <== true.B
      }
      ()
    }

    def ClearBusy(addr: UInt): HwFunction[Unit] = HwFunction.stateless("BaseSB_ClearBusy") { agent =>
      busyTable.at(addr) <== false.B
      ()
    }
  }

  class SemaphoreScoreboardProcess(
      val resourceCount: Int,
      val maxClients: Int,
      val maxUpdateSlots: Int,
      val zeroAlwaysFree: Boolean = false,
      localName: String,
  )(implicit kernel: Kernel)
      extends HwProcess(localName) {

    private val base = spawn(new BaseScoreboardProcess(resourceCount, zeroAlwaysFree, "Base"))
    private val updateSlots = spawn(new SemaphoreProcess(maxClients, initialCount = maxUpdateSlots, "UpdateSlots"))

    override def entry(): Unit = {}

    def ReadBusy(addr: UInt): HwFunction[Bool] = base.ReadBusy(addr)

    class BusyPort(val clientId: Int) {
      def Acquire(): HwFunction[Unit] = HwFunction.atomic(s"AcquireBusySlot_$clientId") { t =>
        val slotLease = SysCall.Call(updateSlots.RequestLease(clientId))
        SysCall.Call(slotLease.Acquire())
      }

      def SetBusy(addr: UInt): HwFunction[Unit] = HwFunction.stateless(s"SetBusy_$clientId") { _ =>
        SysCall.Call(base.SetBusy(addr))
      }

      def ClearBusy(addr: UInt): HwFunction[Unit] = HwFunction.stateless(s"ClearBusy_$clientId") { _ =>
        SysCall.Call(base.ClearBusy(addr))
      }

      def Release(): HwFunction[Unit] = HwFunction.stateless(s"ReleaseBusySlot_$clientId") { _ =>
        val slotLease = SysCall.Call(updateSlots.RequestLease(clientId))
        SysCall.Call(slotLease.Release())
      }
    }

    private val busyPorts = Array.tabulate(maxClients)(i => new BusyPort(i))

    def RequestBusyPort(clientId: Int): HwFunction[BusyPort] = HwFunction.bindings(s"ReqSemaSBPort_$clientId") { _ =>
      busyPorts(clientId)
    }
  }

  // ==========================================
  // 5. 协议记分板 (Protocol Wrapper)
  // 在 BaseScoreboard + SemaphoreScoreboard 之上实现 Guard / Reserve / Release 协议
  // ==========================================
  class ScoreboardProcess(val resourceCount: Int, val maxConcurrentPorts: Int, val zeroAlwaysFree: Boolean = false, localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
    private val semaScoreboard =
      spawn(new SemaphoreScoreboardProcess(resourceCount, maxConcurrentPorts, maxConcurrentPorts, zeroAlwaysFree, "Sema"))

    override def entry(): Unit = {}

    def Guard(addr: UInt): HwFunction[Bool] = HwFunction.atomic("Guard") { t =>
      val isBusy = SysCall.Call(semaScoreboard.ReadBusy(addr))
      t.waitCondition(!isBusy)
      when(!isBusy) { t.Next.hijack() }
      !isBusy
    }

    class ScoreboardLease(val portIdx: Int) extends HwLease {
      val isReserved = RegInit(false.B)
      val reservedAddr = RegInit(0.U(log2Ceil(resourceCount).W))

      ScoreboardProcess.this.own(isReserved)
      ScoreboardProcess.this.own(reservedAddr)
      override def isActive: Bool = isReserved

      def Reserve(addr: UInt): HwFunction[Unit] = HwFunction.atomic(s"Reserve_$portIdx") { t =>
        val busyPort = SysCall.Call(semaScoreboard.RequestBusyPort(portIdx))
        val isBusy = SysCall.Call(semaScoreboard.ReadBusy(addr))
        val alreadyReserved = isReserved && (reservedAddr === addr)
        val canReserve = alreadyReserved || !isBusy
        t.waitCondition(canReserve)

        when(!alreadyReserved && !isBusy) {
          ScoreboardProcess.this.grant(isReserved, t)
          ScoreboardProcess.this.grant(reservedAddr, t)
          SysCall.Call(busyPort.Acquire())
          SysCall.Call(busyPort.SetBusy(addr))
          isReserved <== true.B
          reservedAddr <== addr
        }
        t.ctx.registerLease(this)
      }

      def Release(): HwFunction[Unit] = HwFunction.stateless(s"Release_$portIdx") { agent =>
        val busyPort = SysCall.Call(semaScoreboard.RequestBusyPort(portIdx))
        ScoreboardProcess.this.grant(isReserved, agent)
        when(isReserved) {
          SysCall.Call(busyPort.ClearBusy(reservedAddr))
          SysCall.Call(busyPort.Release())
          isReserved <== false.B
        }
      }

      override def forceReclaim(agent: HardwareAgent): Unit = {
        val busyPort = SysCall.Call(semaScoreboard.RequestBusyPort(portIdx))
        ScoreboardProcess.this.grant(isReserved, agent)
        when(isReserved) {
          SysCall.Call(busyPort.ClearBusy(reservedAddr))
          SysCall.Call(busyPort.Release())
          isReserved <==! false.B
        }
      }
    }

    private val leases = Array.tabulate(maxConcurrentPorts)(i => new ScoreboardLease(i))

    def RequestLease(portIdx: Int): HwFunction[ScoreboardLease] = HwFunction.bindings(s"ReqSBLease_$portIdx") { _ =>
      leases(portIdx)
    }

    def WaitUntilFree(addr: UInt): HwFunction[Unit] = HwFunction.atomic("WaitUntilFree") { _ =>
      SysCall.Call(Guard(addr))
      ()
    }

    def Reserve(portIdx: Int, addr: UInt): HwFunction[Unit] = HwFunction.atomic(s"SBReserve_$portIdx") { _ =>
      val lease = SysCall.Call(RequestLease(portIdx))
      SysCall.Call(lease.Reserve(addr))
    }

    def Release(portIdx: Int): HwFunction[Unit] = HwFunction.stateless(s"SBRelease_$portIdx") { _ =>
      val lease = SysCall.Call(RequestLease(portIdx))
      SysCall.Call(lease.Release())
    }

    def WithReservation(portIdx: Int, addr: UInt)(body: HardwareThread => Unit): HwFunction[Unit] =
      HwFunction.thread(s"WithReservation_$portIdx") { t =>
        t.Step(s"ReserveBusy_$portIdx") {
          SysCall.Call(Reserve(portIdx, addr))
        }
        body(t)
        t.Step(s"ReleaseBusy_$portIdx") {
          SysCall.Call(Release(portIdx))
        }
        ()
      }
  }
}
