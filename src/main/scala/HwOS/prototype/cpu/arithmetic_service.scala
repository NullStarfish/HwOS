package HwOS.prototype.cpu

import HwOS.kernel.function.HwInline
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{Kernel, SysCall}
import chisel3._
import chisel3.util._

final class ArithmeticServiceProcess(val maxClients: Int, val ports: Int, localName: String)(implicit kernel: Kernel)
    extends HwProcess(localName) {
  private case class ExecuteReq(pending: Bool, completed: Bool, lhs: UInt, rhs: UInt, result: UInt)
  private case class ServerSlot(
      thread: HwOS.kernel.thread.HardwareThread,
      lhs: UInt,
      rhs: UInt,
      result: UInt,
      ownerValid: Bool,
      ownerClient: UInt,
  )

  private val requests = Array.tabulate(maxClients max 1) { _ =>
    ExecuteReq(
      pending = RegInit(false.B),
      completed = RegInit(false.B),
      lhs = RegInit(0.U(32.W)),
      rhs = RegInit(0.U(32.W)),
      result = RegInit(0.U(32.W)),
    )
  }

  private val servers = Array.tabulate(ports max 1) { i =>
    val thread = createThread(s"Server$i")
    ServerSlot(
      thread = thread,
      lhs = RegInit(0.U(32.W)),
      rhs = RegInit(0.U(32.W)),
      result = RegInit(0.U(32.W)),
      ownerValid = RegInit(false.B),
      ownerClient = RegInit(0.U(log2Ceil((maxClients max 1) max 2).W)),
    )
  }

  override def entry(): Unit = {
    val dispatcher = createLogic("Dispatcher")

    for ((slot, serverId) <- servers.zipWithIndex) {
      slot.thread.entry {
        slot.thread.Step(s"Execute_$serverId") {
          slot.result := slot.lhs + slot.rhs
        }
        slot.thread.Step(s"Finish_$serverId") {}
        SysCall.Return()
      }
    }

    dispatcher.run {
      val pendingOH = PriorityEncoderOH(VecInit(requests.toIndexedSeq.map(_.pending)).asUInt)
      val freeOH = PriorityEncoderOH(VecInit(servers.toIndexedSeq.map(s => !s.ownerValid && !s.thread.active)).asUInt)

      when(pendingOH.orR && freeOH.orR) {
        for ((req, clientIdx) <- requests.zipWithIndex) {
          when(pendingOH(clientIdx)) {
            for ((slot, freeIdx) <- servers.zipWithIndex) {
              when(freeOH(freeIdx)) {
                slot.lhs := req.lhs
                slot.rhs := req.rhs
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
          for ((req, clientIdx) <- requests.zipWithIndex) {
            when(owner === clientIdx.U) {
              req.result := slot.result
              req.completed := true.B
            }
          }
        }
      }
    }
  }

  def RequestExecute(clientId: Int, lhs: UInt, rhs: UInt): HwInline[UInt] = HwInline.atomic(s"${name}_RequestExecute_$clientId") { t =>
    val req = requests(clientId)
    t.waitCondition(!req.pending)
    when(!req.pending) {
      req.lhs := lhs
      req.rhs := rhs
      req.completed := false.B
      req.pending := true.B
    }
    t.waitCondition(req.completed)
    when(req.completed) {
      req.completed := false.B
      t.hijack(t.Next)
    }
    req.result
  }

  def ActiveServerCount(): HwInline[UInt] = HwInline.stateless(s"${name}_ActiveServerCount") { _ =>
    PopCount(servers.map(_.thread.active))
  }
}
