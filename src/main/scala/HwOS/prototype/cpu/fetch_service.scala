package HwOS.prototype.cpu

import HwOS.kernel.function.HwInline
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{Kernel, SysCall}
import chisel3._
import chisel3.util._

final class ServerFetchProcess(
    program: Seq[ISA.Instr],
    decode: ServerDecodeProcess,
    localName: String,
)(implicit kernel: Kernel)
    extends HwProcess(localName) {
  private val issueWidth = 2
  private val launcher = createLogic("Launcher")

  private val fetchPtr = RegInit(0.U(log2Ceil(program.length + 1).W))
  private val programRom = VecInit(program.map(ISA.encode))

  val slots = program.indices.map { i =>
    val thread = createThread(s"Client${i}_req")
    val instArg = RegInit(0.U(ISA.instWidth.W))
    val occupied = RegInit(false.B)
    (new Slot(i, thread, instArg), occupied)
  }

  override def entry(): Unit = {
    require(issueWidth == 2, "Current MVP keeps only 1-bit intra-bundle order")

    for ((slot, _) <- slots) {
      slot.thread.entry {
        slot.thread.Step(s"SubmitDecode_${slot.slotId}") {
          SysCall.Call(decode.RequestDecode(slot.slotId, slot.instArg), s"Retire_${slot.slotId}")
        }
        slot.thread.Step(s"Retire_${slot.slotId}") {}
        SysCall.Return()
      }
    }

    launcher.run {
      for ((slot, occupied) <- slots) {
        when(occupied && slot.thread.done) {
          occupied := false.B
        }
      }

      val freeVec = VecInit(slots.map { case (slot, occupied) =>
        !slot.thread.active && !occupied
      })
      val launchPlan = Seq(
        freeVec,
        VecInit(slots.indices.map(idx => freeVec(idx) && !PriorityEncoderOH(freeVec.asUInt)(idx)))
      )
      val fires = launchPlan.zipWithIndex.map { case (candidates, laneIdx) =>
        val slotOH = PriorityEncoderOH(candidates.asUInt)
        val slotFire = (fetchPtr + laneIdx.U) < program.length.U && slotOH.orR
        val inst = programRom((fetchPtr + laneIdx.U)(log2Ceil(program.length max 2) - 1, 0))
        when(slotFire) {
          for (((slot, occupied), idx) <- slots.zipWithIndex) {
            when(slotOH(idx)) {
              slot.instArg := inst
              occupied := true.B
              SysCall.Inline(SysCall.start(slot.thread))
            }
          }
        }
        slotFire
      }

      val issueCount = PopCount(VecInit(fires))
      when(issueCount =/= 0.U) {
        fetchPtr := fetchPtr + issueCount
      }
    }
  }

  def ActiveThreadCount(): HwInline[UInt] = HwInline.stateless(s"${name}_ActiveThreadCount") { _ =>
    PopCount(slots.map(_._1.thread.active)) + SysCall.Inline(decode.ActiveServerCount())
  }
}
