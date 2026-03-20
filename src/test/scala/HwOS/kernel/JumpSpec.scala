package HwOS.kernel

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel.HwOSLanguage._

class JumpProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  val worker = createThread("Worker")
  val out = (RegInit(0.U(8.W)))

  override def entry(): Unit = {
    worker.entry {
      worker.Step("Dispatch") {
        worker.jump(worker.stepRef("Target"))
      }
      worker.Step("Skipped") {
        out  :=  1.U
      }
      worker.Step("Target") {
        out  :=  2.U
      }
      worker.Step("Finish") {
      }
      SysCall.Return()
    }
  }
}

class JumpModule extends Module {
  val io = IO(new Bundle {
    val out = Output(UInt(8.W))
    val done = Output(Bool())
  })

  io.out := DontCare
  io.done := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    (io.out)
    (io.done)

    val proc = spawn(new JumpProcess("JumpProc"))
    val daemon = createLogic("Daemon")

    override def entry(): Unit = {
      daemon.run {
        when(!proc.worker.active && !proc.worker.done) {
          SysCall.Call(SysCall.start(proc.worker))
        }
        io.out  :=  proc.out
        io.done  :=  proc.worker.done
      }
    }
  }

  Init.build()
}

class JumpSpec extends AnyFlatSpec {
  "jump(stepRef)" should "branch to the named step and skip intermediate steps" in {
    simulate(new JumpModule) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      var cycles = 0
      while (c.io.done.peek().litValue == 0 && cycles < 10) {
        c.clock.step()
        cycles += 1
      }

      c.io.done.expect(true.B)
      c.io.out.expect(2.U)
    }
  }
}
