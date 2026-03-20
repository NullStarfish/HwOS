package HwOS.prototype.cpu

import HwOS.kernel.function.HwInline
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{Kernel, SysCall}
import chisel3._
import chisel3.util._

final class ServerFetchProcess(
    program: Seq[ISA.Instr],
    initData: Seq[Int],
    decodeServers: Int,
    localName: String,
)(implicit kernel: Kernel)
    extends HwProcess(localName) {
  private val issueWidth = 2
  val decode = spawn(new ServerDecodeProcess(program.length max 1, decodeServers, initData, "Decode"))
  private val launcher = createLogic("Launcher")

  private val fetchPtr = RegInit(0.U(log2Ceil(program.length + 1).W))
  private val programRom = VecInit(program.map(ISA.encode))

  val slots = program.indices.map { i =>
    val thread = createThread(s"Client${i}_req")
    val instArg = RegInit(0.U(ISA.instWidth.W))
    new Slot(i, thread, instArg)
  }

  override def entry(): Unit = {
    require(issueWidth == 2, "Current MVP keeps only 1-bit intra-bundle order")

    for (slot <- slots) {
      slot.thread.entry {
        slot.thread.Step(s"SubmitDecode_${slot.slotId}") {
          SysCall.Call(decode.RequestDecode(slot.slotId, slot.instArg))
        }
        slot.thread.Step(s"Retire_${slot.slotId}") {}
        SysCall.Return()
      }
    }

    launcher.run {
      val freeVec = VecInit(slots.map(slot => !slot.thread.active))
      val launchPlan = Seq(
        freeVec,
        VecInit(slots.indices.map(idx => freeVec(idx) && !PriorityEncoderOH(freeVec.asUInt)(idx)))
      )
      val fires = launchPlan.zipWithIndex.map { case (candidates, laneIdx) =>
        val slotOH = PriorityEncoderOH(candidates.asUInt)
        val slotFire = (fetchPtr + laneIdx.U) < program.length.U && slotOH.orR
        val inst = programRom((fetchPtr + laneIdx.U)(log2Ceil(program.length max 2) - 1, 0))
        when(slotFire) {
          for ((slot, idx) <- slots.zipWithIndex) {
            when(slotOH(idx)) {
              slot.instArg := inst
              SysCall.Call(SysCall.start(slot.thread))
            }
          }
        }
        Mux(slotFire, 1.U, 0.U)
      }

      val issueCount = fires.reduce(_ + _)
      when(issueCount =/= 0.U) {
        fetchPtr := fetchPtr + issueCount
      }
    }
  }

  def ActiveThreadCount(): HwInline[UInt] = HwInline.stateless(s"${name}_ActiveThreadCount") { _ =>
    PopCount(slots.map(_.thread.active)) + SysCall.Call(decode.ActiveServerCount())
  }
}
