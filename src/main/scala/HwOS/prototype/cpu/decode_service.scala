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
  private def withReservedWrite(
      tx: HardwareThread,
      writePort: AgeOrderedScoreboardRegfileProcess#OrderedRegWritePort,
      targetRd: UInt,
      entryLabel: String,
  )(body: => UInt): UInt = {
    tx.Step(entryLabel) {
      SysCall.Inline(writePort.Reserve(targetRd))
    }
    body
  }

  def AddiPath(
      serverId: Int,
      rd: UInt,
      rs1: UInt,
      imm: UInt,
  ): HwInline[UInt] = HwInline.thread(s"AddiPath_$serverId") { tx =>
    val regFile = tx.importService[AgeOrderedScoreboardRegfileProcess]("RegFile")
    val arith = tx.importService[ArithmeticServiceProcess]("Arithmetic")
    val writePort = SysCall.Inline(regFile.RequestWritePort(serverId))
    val decodedSrc = RegInit(0.U(32.W))
    val pathResult = RegInit(0.U(32.W))

    withReservedWrite(tx, writePort, rd, s"ArithReserve_$serverId") {
      tx.Step(s"ArithRead_$serverId") {
        decodedSrc := SysCall.Inline(regFile.Read(rs1))
      }
      tx.Step(s"ArithExec_$serverId") {
        pathResult := SysCall.Call(arith.RequestExecute(serverId, decodedSrc, imm), s"ArithDone_$serverId")
      }
      tx.Step(s"ArithDone_$serverId") {}
      pathResult
    }
  }

  def LoadPath(
      serverId: Int,
      rd: UInt,
      imm: UInt,
  ): HwInline[UInt] = HwInline.thread(s"LoadPath_$serverId") { tx =>
    val load = tx.importService[LoadServiceProcess]("Load")
    val regFile = tx.importService[AgeOrderedScoreboardRegfileProcess]("RegFile")
    val writePort = SysCall.Inline(regFile.RequestWritePort(serverId))
    val pathResult = RegInit(0.U(32.W))

    tx.Step(s"LoadReserve_$serverId") {
      SysCall.Inline(writePort.Reserve(rd))
    }
    tx.Step(s"LoadExec_$serverId") {
      pathResult := SysCall.Call(load.RequestLoad(serverId, imm), s"LoadDone_$serverId")
    }
    tx.Step(s"LoadDone_$serverId") {}
    pathResult
  }

  def LoadAddPath(
      serverId: Int,
      rd: UInt,
      rs1: UInt,
      imm: UInt,
  ): HwInline[UInt] = HwInline.thread(s"LoadAddPath_$serverId") { tx =>
    val regFile = tx.importService[AgeOrderedScoreboardRegfileProcess]("RegFile")
    val load = tx.importService[LoadServiceProcess]("Load")
    val arith = tx.importService[ArithmeticServiceProcess]("Arithmetic")
    val writePort = SysCall.Inline(regFile.RequestWritePort(serverId))
    val loadedValue = RegInit(0.U(32.W))
    val decodedSrc = RegInit(0.U(32.W))
    val pathResult = RegInit(0.U(32.W))

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
      pathResult := SysCall.Call(arith.RequestExecute(serverId, loadedValue, decodedSrc), s"LoadAddDone_$serverId")
    }
    tx.Step(s"LoadAddDone_$serverId") {}
    pathResult
  }

}

final class ServerDecodeProcess(
    val maxClients: Int,
    val maxServers: Int,
    val initData: Seq[Int],
    localName: String,
)(implicit kernel: Kernel)
    extends HwProcess(localName) {
  val regFile = spawn(new AgeOrderedScoreboardRegfileProcess(8, 32, maxServers max 1, maxServers max 1, zeroReg = true, "RegFile"))
  private val arith = spawn(new ArithmeticServiceProcess(maxServers max 1, 1, "Arithmetic"))
  private val load = spawn(new LoadServiceProcess(maxServers max 1, 1, initData, "Load"))

  private case class ClientReq(pending: Bool, inFlight: Bool, completed: Bool, instBits: UInt)
  private case class ServerSlot(
      thread: HardwareThread,
      instArg: UInt,
      ownerValid: Bool,
      ownerClient: UInt,
  )

  private val clientReqs = Array.tabulate(maxClients max 1) { _ =>
    ClientReq(
      pending = RegInit(false.B),
      inFlight = RegInit(false.B),
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
        val threadExitLabel = s"ThreadExit_$serverId"

        val addiPath = DecodePathSegments.AddiPath(
          serverId,
          rd,
          rs1,
          imm,
        )

        val loadPath = DecodePathSegments.LoadPath(
          serverId,
          rd,
          imm,
        )

        val loadAddPath = DecodePathSegments.LoadAddPath(
          serverId,
          rd,
          rs1,
          imm,
        )

        StructuredControl
          .If(slot.thread, "DecodeIsAddi", opcode === ISA.OP_ADDI) {
            val addiResult = SysCall.Call(addiPath, s"RouteWritebackAddi_$serverId")
            slot.thread.Step(s"RouteWritebackAddi_$serverId") {
              val writePort = SysCall.Inline(regFile.RequestWritePort(serverId))
              SysCall.Inline(writePort.WritebackAndClear(rd, addiResult))
              slot.thread.jump(threadExitLabel)
            }
          }
          .ElseIf(opcode === ISA.OP_LOAD) {
            val loadResult = SysCall.Call(loadPath, s"RouteWritebackLoad_$serverId")
            slot.thread.Step(s"RouteWritebackLoad_$serverId") {
              val writePort = SysCall.Inline(regFile.RequestWritePort(serverId))
              SysCall.Inline(writePort.WritebackAndClear(rd, loadResult))
              slot.thread.jump(threadExitLabel)
            }
          }
          .ElseIf(opcode === ISA.OP_LOADADD) {
            val loadAddResult = SysCall.Call(loadAddPath, s"RouteWritebackLoadAdd_$serverId")
            slot.thread.Step(s"RouteWritebackLoadAdd_$serverId") {
              val writePort = SysCall.Inline(regFile.RequestWritePort(serverId))
              SysCall.Inline(writePort.WritebackAndClear(rd, loadAddResult))
              slot.thread.jump(threadExitLabel)
            }
          }
          .Else {
            slot.thread.Step(s"UnsupportedOpcode_$serverId") {
              slot.thread.jump(threadExitLabel)
            }
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
    t.waitCondition(!req.inFlight)
    when(!req.inFlight) {
      req.instBits := instBits
      req.completed := false.B
      req.pending := true.B
      req.inFlight := true.B
    }
    t.waitCondition(req.completed)
    when(req.completed) {
      req.completed := false.B
      req.inFlight := false.B
      SysCall.Return()
    }
    ()
  }

  def ActiveServerCount(): HwInline[UInt] = HwInline.stateless(s"${name}_ActiveServerCount") { _ =>
    PopCount(servers.map(_.thread.active)) + SysCall.Inline(arith.ActiveServerCount()) + SysCall.Inline(load.ActiveServerCount())
  }
}
