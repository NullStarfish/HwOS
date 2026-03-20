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
      serverId: Int,
      rd: UInt,
      rs1: UInt,
      imm: UInt,
      decodedSrc: UInt,
      result: UInt,
  ): HwInline[Unit] = HwInline.thread(s"AddiPath_$serverId") { tx =>
    def withReservedWrite(writePort: ScoreboardRegfileProcess#RegWritePort, targetRd: UInt, entryLabel: String)(
        body: => Unit
    ): Unit = {
      tx.Step(entryLabel) {
        SysCall.Inline(writePort.Reserve(targetRd))
      }
      body
    }

    val regFile = tx.importService[ScoreboardRegfileProcess]("RegFile")
    val arith = tx.importService[ArithmeticServiceProcess]("Arithmetic")
    val writePort = SysCall.Inline(regFile.RequestWritePort(serverId))

    withReservedWrite(writePort, rd, s"ArithReserve_$serverId") {
      tx.Step(s"ArithRead_$serverId") {
        decodedSrc := SysCall.Inline(regFile.Read(rs1))
      }
      tx.Step(s"ArithExec_$serverId") {
        result := SysCall.Call(arith.RequestExecute(serverId, decodedSrc, imm), s"AddiReturn_$serverId")
      }

      tx.Step(s"AddiReturn_$serverId") {
      }
      SysCall.Return()
      ()
    }
    ()
  }

  def LoadPath(
      serverId: Int,
      rd: UInt,
      imm: UInt,
      result: UInt,
  ): HwInline[Unit] = HwInline.thread(s"LoadPath_$serverId") { tx =>
    val load = tx.importService[LoadServiceProcess]("Load")
    val regFile = tx.importService[ScoreboardRegfileProcess]("RegFile")
    val writePort = SysCall.Inline(regFile.RequestWritePort(serverId))

    tx.Step(s"LoadReserve_$serverId") {
      SysCall.Inline(writePort.Reserve(rd))
    }
    tx.Step(s"LoadExec_$serverId") {
      result := SysCall.Call(load.RequestLoad(serverId, imm), s"LoadReturn_$serverId")
    }
    tx.Step(s"LoadReturn_$serverId") {
    }
    SysCall.Return()
    ()
  }

  def LoadAddPath(
      serverId: Int,
      rd: UInt,
      rs1: UInt,
      imm: UInt,
      decodedSrc: UInt,
      loadedValue: UInt,
      result: UInt,
  ): HwInline[Unit] = HwInline.thread(s"LoadAddPath_$serverId") { tx =>


    val regFile = tx.importService[ScoreboardRegfileProcess]("RegFile")
    val load = tx.importService[LoadServiceProcess]("Load")
    val arith = tx.importService[ArithmeticServiceProcess]("Arithmetic")
    val writePort = SysCall.Inline(regFile.RequestWritePort(serverId))

    tx.Step(s"LoadAddReserve_$serverId") {
      SysCall.Inline(writePort.Reserve(rd))
    }
    tx.Step(s"LoadAddLoadExec_$serverId") {
      loadedValue := SysCall.Call(load.RequestLoad(serverId, imm), s"LoadAddArithRead_$serverId")
    }
    tx.Step(s"LoadAddArithRead_$serverId") {
      decodedSrc := SysCall.Inline(regFile.Read(rs1))
    }
    tx.Step(s"LoadAddArithExec_$serverId") {
      result := SysCall.Call(arith.RequestExecute(serverId, loadedValue, decodedSrc), s"LoadAddReturn_$serverId")
    }
    tx.Step(s"LoadAddReturn_$serverId") {
    }
    SysCall.Return()
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
        val routeWritebackLabel = s"RouteWriteback_$serverId"
        val threadExitLabel = s"ThreadExit_$serverId"

        val addiPath = DecodePathSegments.AddiPath(
          serverId,
          rd,
          rs1,
          imm,
          slot.decodedSrc,
          slot.result,
        )

        val loadPath = DecodePathSegments.LoadPath(
          serverId,
          rd,
          imm,
          slot.result,
        )

        val loadAddPath = DecodePathSegments.LoadAddPath(
          serverId,
          rd,
          rs1,
          imm,
          slot.decodedSrc,
          slot.loadedValue,
          slot.result,
        )

        StructuredControl
          .If(slot.thread, "DecodeIsAddi", opcode === ISA.OP_ADDI) {
            SysCall.Call(addiPath, routeWritebackLabel)
          }
          .ElseIf(opcode === ISA.OP_LOAD) {
            SysCall.Call(loadPath, routeWritebackLabel)
          }
          .ElseIf(opcode === ISA.OP_LOADADD) {
            SysCall.Call(loadAddPath, routeWritebackLabel)
          }
          .Else {
            slot.thread.Step(s"UnsupportedOpcode_$serverId") {
              slot.thread.jump(threadExitLabel)
            }
          }

        slot.thread.Step(routeWritebackLabel) {
          val writePort = SysCall.Inline(regFile.RequestWritePort(serverId))
          SysCall.Inline(writePort.WritebackAndClear(rd, slot.result))
        }
        slot.thread.Step(threadExitLabel) {
          SysCall.Return()
        }
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
                SysCall.Inline(SysCall.start(slot.thread))
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
      SysCall.Return()
    }
    ()
  }

  def ActiveServerCount(): HwInline[UInt] = HwInline.stateless(s"${name}_ActiveServerCount") { _ =>
    PopCount(servers.map(_.thread.active)) + SysCall.Inline(arith.ActiveServerCount()) + SysCall.Inline(load.ActiveServerCount())
  }
}
