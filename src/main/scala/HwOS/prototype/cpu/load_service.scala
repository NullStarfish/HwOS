package HwOS.prototype.cpu

import HwOS.kernel.function.HwInline
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{Kernel, SysCall}
import chisel3._
import chisel3.util._

final class LoadServiceProcess(val maxClients: Int, val ports: Int, val initData: Seq[Int], localName: String)(implicit kernel: Kernel)
    extends HwProcess(localName) {
  private val memDepth = 16
  private val mem = RegInit(VecInit((0 until memDepth).map(i => initData.lift(i).getOrElse(0).U(32.W))))

  private case class LoadReq(pending: Bool, completed: Bool, addr: UInt, result: UInt)
  private case class ServerSlot(
      thread: HwOS.kernel.thread.HardwareThread,
      addr: UInt,
      result: UInt,
      ownerValid: Bool,
      ownerClient: UInt,
  )

  private val requests = Array.tabulate(maxClients max 1) { _ =>
    LoadReq(
      pending = RegInit(false.B),
      completed = RegInit(false.B),
      addr = RegInit(0.U(32.W)),
      result = RegInit(0.U(32.W)),
    )
  }

  private val servers = Array.tabulate(ports max 1) { i =>
    val thread = createThread(s"Server$i")
    ServerSlot(
      thread = thread,
      addr = RegInit(0.U(32.W)),
      result = RegInit(0.U(32.W)),
      ownerValid = RegInit(false.B),
      ownerClient = RegInit(0.U(log2Ceil((maxClients max 1) max 2).W)),
    )
  }

  override def entry(): Unit = {
    val dispatcher = createLogic("Dispatcher")

    for ((slot, serverId) <- servers.zipWithIndex) {
      val delay = RegInit(0.U(2.W))
      slot.thread.entry {
        slot.thread.Step(s"Prepare_$serverId") {
          delay := 0.U
        }
        slot.thread.Step(s"Delay_$serverId") {
          when(delay >= 2.U) {
            slot.thread.jump(slot.thread.stepRef(s"Read_$serverId"))
          }.otherwise {
            delay := delay + 1.U
          }
        }
        slot.thread.Step(s"Read_$serverId") {
          slot.result := mem(slot.addr(log2Ceil(memDepth) - 1, 0))
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
                slot.addr := req.addr
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

  def RequestLoad(clientId: Int, addr: UInt): HwInline[UInt] = HwInline.atomic(s"${name}_RequestLoad_$clientId") { t =>
    val req = requests(clientId)
    t.waitCondition(!req.pending)
    when(!req.pending) {
      req.addr := addr
      req.completed := false.B
      req.pending := true.B
    }
    t.waitCondition(req.completed)
    when(req.completed) {
      req.completed := false.B
      SysCall.Return()
    }
    req.result
  }

  private[cpu] def ResultRef(clientId: Int): UInt = requests(clientId).result

  def ActiveServerCount(): HwInline[UInt] = HwInline.stateless(s"${name}_ActiveServerCount") { _ =>
    PopCount(servers.map(_.thread.active))
  }
}
