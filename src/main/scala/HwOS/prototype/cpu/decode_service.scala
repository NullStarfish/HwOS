package HwOS.prototype.cpu

import HwOS.kernel.control.StructuredControl
import HwOS.kernel.function.HwInline
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{Kernel, SysCall}
import HwOS.kernel.thread.HardwareThread
import HwOS.lib.regfile.RegfileLib._
import chisel3._
import chisel3.util._

private object DecodePathSegments {
  def AddiPath(
      serviceName: String,
      serverId: Int,
      thread: HardwareThread,
      rd: UInt,
      rs1: UInt,
      imm: UInt,
      decodedSrc: UInt,
      result: UInt,
      regFile: ScoreboardRegfileProcess,
      arith: ArithmeticServiceProcess,
      withReservedWrite: (String, HardwareThread => Unit) => Unit,
      routeWritebackLabel: String,
  ): HwInline[Unit] = HwInline.thread(s"${serviceName}_AddiPath_$serverId") { _ =>
    withReservedWrite(s"ArithReserve_$serverId", { rx =>
      rx.Step(s"ArithRead_$serverId") {
        decodedSrc := SysCall.Call(regFile.Read(rs1))
      }
      rx.Step(s"ArithExec_$serverId") {
        result := SysCall.Call(arith.RequestExecute(serverId, decodedSrc, imm))
      }
      rx.Step(s"ArithAfterExec_$serverId") {
        rx.jump(routeWritebackLabel)
      }
    })
    ()
  }

  def LoadPath(
      serviceName: String,
      serverId: Int,
      imm: UInt,
      result: UInt,
      load: LoadServiceProcess,
      withReservedWrite: (String, HardwareThread => Unit) => Unit,
      routeWritebackLabel: String,
  ): HwInline[Unit] = HwInline.thread(s"${serviceName}_LoadPath_$serverId") { _ =>
    withReservedWrite(s"LoadReserve_$serverId", { rx =>
      rx.Step(s"LoadExec_$serverId") {
        result := SysCall.Call(load.RequestLoad(serverId, imm))
      }
      rx.Step(s"LoadAfterExec_$serverId") {
        rx.jump(routeWritebackLabel)
      }
    })
    ()
  }

  def LoadAddPath(
      serviceName: String,
      serverId: Int,
      rs1: UInt,
      imm: UInt,
      decodedSrc: UInt,
      loadedValue: UInt,
      result: UInt,
      regFile: ScoreboardRegfileProcess,
      load: LoadServiceProcess,
      arith: ArithmeticServiceProcess,
      withReservedWrite: (String, HardwareThread => Unit) => Unit,
      routeWritebackLabel: String,
  ): HwInline[Unit] = HwInline.thread(s"${serviceName}_LoadAddPath_$serverId") { _ =>
    withReservedWrite(s"LoadAddReserve_$serverId", { rx =>
      rx.Step(s"LoadAddLoadExec_$serverId") {
        loadedValue := SysCall.Call(load.RequestLoad(serverId, imm))
      }
      rx.Step(s"LoadAddArithRead_$serverId") {
        decodedSrc := SysCall.Call(regFile.Read(rs1))
      }
      rx.Step(s"LoadAddArithExec_$serverId") {
        result := SysCall.Call(arith.RequestExecute(serverId, loadedValue, decodedSrc))
      }
      rx.Step(s"LoadAddAfterExec_$serverId") {
        rx.jump(routeWritebackLabel)
      }
    })
    ()
  }

  def InvalidPath(serviceName: String, serverId: Int, threadExitLabel: String): HwInline[Unit] =
    HwInline.thread(s"${serviceName}_InvalidPath_$serverId") { tx =>
      tx.Step(s"UnsupportedOpcode_$serverId") {
        tx.jump(threadExitLabel)
      }
      ()
    }
}

final class ServerDecodeProcess(
    val maxClients: Int,
    val maxServers: Int,
    val initData: Seq[Int],
    localName: String,
)(implicit kernel: Kernel)
    extends HwProcess(localName) {
  val regFile = spawn(new ScoreboardRegfileProcess(8, 32, maxServers max 1, zeroReg = true, "RegFile"))
  private val arith = spawn(new ArithmeticServiceProcess(maxServers max 1, 1, "Arithmetic"))
  private val load = spawn(new LoadServiceProcess(maxServers max 1, 1, initData, "Load"))

  private case class ClientReq(pending: Bool, completed: Bool, instBits: UInt)
  private case class ServerSlot(
      thread: HardwareThread,
      instArg: UInt,
      ownerValid: Bool,
      ownerClient: UInt,
      decodedSrc: UInt,
      loadedValue: UInt,
      result: UInt,
  )

  private val clientReqs = Array.tabulate(maxClients max 1) { _ =>
    ClientReq(
      pending = RegInit(false.B),
      completed = RegInit(false.B),
      instBits = RegInit(0.U(ISA.instWidth.W)),
    )
  }

  private val servers = Array.tabulate(maxServers max 1) { i =>
    val thread = createThread(s"Server$i")
    ServerSlot(
      thread = thread,
      instArg = RegInit(0.U(ISA.instWidth.W)),
      ownerValid = RegInit(false.B),
      ownerClient = RegInit(0.U(log2Ceil((maxClients max 1) max 2).W)),
      decodedSrc = RegInit(0.U(32.W)),
      loadedValue = RegInit(0.U(32.W)),
      result = RegInit(0.U(32.W)),
    )
  }

  override def entry(): Unit = {
    val dispatcher = createLogic("Dispatcher")

    for ((slot, serverId) <- servers.zipWithIndex) {
      val opcode = ISA.opcode(slot.instArg)
      val rd = ISA.rd(slot.instArg)
      val rs1 = ISA.rs1(slot.instArg)
      val imm = ISA.imm(slot.instArg)

      slot.thread.entry {
        val writePort = SysCall.Call(regFile.RequestWritePort(serverId))

        def withReservedWrite(entryLabel: String)(body: HardwareThread => Unit): Unit = {
          slot.thread.Step(entryLabel) {
            SysCall.Call(writePort.Reserve(rd))
          }
          body(slot.thread)
        }

        val reserveWrite: (String, HardwareThread => Unit) => Unit = (entryLabel, body) =>
          withReservedWrite(entryLabel)(body)

        val routeWritebackLabel = s"RouteWriteback_$serverId"
        val threadExitLabel = s"ThreadExit_$serverId"

        val addiPath = DecodePathSegments.AddiPath(
          name,
          serverId,
          slot.thread,
          rd,
          rs1,
          imm,
          slot.decodedSrc,
          slot.result,
          regFile,
          arith,
          reserveWrite,
          routeWritebackLabel,
        )

        val loadPath = DecodePathSegments.LoadPath(
          name,
          serverId,
          imm,
          slot.result,
          load,
          reserveWrite,
          routeWritebackLabel,
        )

        val loadAddPath = DecodePathSegments.LoadAddPath(
          name,
          serverId,
          rs1,
          imm,
          slot.decodedSrc,
          slot.loadedValue,
          slot.result,
          regFile,
          load,
          arith,
          reserveWrite,
          routeWritebackLabel,
        )

        val invalidPath = DecodePathSegments.InvalidPath(name, serverId, threadExitLabel)

        StructuredControl
          .If(slot.thread, "DecodeIsAddi", opcode === ISA.OP_ADDI)(addiPath)
          .ElseIf(opcode === ISA.OP_LOAD)(loadPath)
          .ElseIf(opcode === ISA.OP_LOADADD)(loadAddPath)
          .Else(invalidPath)

        slot.thread.Step(routeWritebackLabel) {
          SysCall.Call(writePort.WritebackAndClear(rd, slot.result))
        }

        slot.thread.Step(threadExitLabel) {}
        SysCall.Call(SysCall.Return())
      }
    }

    dispatcher.run {
      val pendingOH = PriorityEncoderOH(VecInit(clientReqs.toIndexedSeq.map(_.pending)).asUInt)
      val freeOH = PriorityEncoderOH(VecInit(servers.toIndexedSeq.map(s => !s.ownerValid && !s.thread.active)).asUInt)

      when(pendingOH.orR && freeOH.orR) {
        for ((req, clientIdx) <- clientReqs.zipWithIndex) {
          when(pendingOH(clientIdx)) {
            for ((slot, freeIdx) <- servers.zipWithIndex) {
              when(freeOH(freeIdx)) {
                slot.instArg := req.instBits
                slot.ownerValid := true.B
                slot.ownerClient := clientIdx.U
                req.pending := false.B
                SysCall.Call(SysCall.start(slot.thread))
              }
            }
          }
        }
      }

      for (slot <- servers) {
        when(slot.ownerValid && slot.thread.done) {
          val owner = slot.ownerClient
          slot.ownerValid := false.B
          for ((req, clientIdx) <- clientReqs.zipWithIndex) {
            when(owner === clientIdx.U) {
              req.completed := true.B
            }
          }
        }
      }
    }
  }

  def RequestDecode(clientId: Int, instBits: UInt): HwInline[Unit] = HwInline.atomic(s"${name}_RequestDecode_$clientId") { t =>
    val req = clientReqs(clientId)
    t.waitCondition(!req.pending)
    when(!req.pending) {
      req.instBits := instBits
      req.completed := false.B
      req.pending := true.B
    }
    t.waitCondition(req.completed)
    when(req.completed) {
      req.completed := false.B
      t.hijack(t.Next)
    }
    ()
  }

  def ActiveServerCount(): HwInline[UInt] = HwInline.stateless(s"${name}_ActiveServerCount") { _ =>
    PopCount(servers.map(_.thread.active)) + SysCall.Call(arith.ActiveServerCount()) + SysCall.Call(load.ActiveServerCount())
  }
}
