package HwOS.prototype.cpu

import HwOS.kernel.function.HwInline
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{Kernel, SysCall}
import HwOS.kernel.thread.HardwareThread
import HwOS.lib.regfile.RegfileLib._
import chisel3._
import chisel3.util._

final class ServerDecodeProcess(
    val maxClients: Int,
    val maxServers: Int,
    backend: BackendProcess,
    localName: String,
)(implicit kernel: Kernel)
    extends HwProcess(localName) {
  val regFile = backend.regFile
  private val addiPath = backend.addiPath
  private val loadPath = backend.loadPath
  private val loadAddPath = backend.loadAddPath

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
        slot.thread.Step(s"DecodeDispatch_$serverId") {
          when(opcode === ISA.OP_ADDI) {
            val writePort = SysCall.Inline(regFile.RequestWritePort(0))
            SysCall.Inline(writePort.Reserve(rd))
            SysCall.Inline(addiPath.RequestAddi(serverId, rd, rs1, imm))
          }.elsewhen(opcode === ISA.OP_LOAD) {
            val writePort = SysCall.Inline(regFile.RequestWritePort(1))
            SysCall.Inline(writePort.Reserve(rd))
            SysCall.Inline(loadPath.RequestLoadPath(serverId, rd, imm))
          }.elsewhen(opcode === ISA.OP_LOADADD) {
            val writePort = SysCall.Inline(regFile.RequestWritePort(2))
            SysCall.Inline(writePort.Reserve(rd))
            SysCall.Inline(loadAddPath.RequestLoadAdd(serverId, rd, rs1, imm))
          }
        }
        SysCall.Return()
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
    PopCount(servers.map(_.thread.active)) + SysCall.Inline(backend.ActiveThreadCount())
  }
}
