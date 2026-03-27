package HwOS.kernel

import HwOS.kernel.HwOSLanguage._
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class LifecycleLeaseProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  val worker = createThread("Worker")
  val controller = createThread("Controller")
  val hits = (RegInit(0.U(8.W)))

  override def entry(): Unit = {
    worker.entry {
      worker.Step("Hit") {
        hits  :=  hits + 1.U
      }
      worker.Step("Park") {
        worker.waitCondition(false.B)
      }
      worker.Step("Never") {
      }
      SysCall.Return()
    }

    controller.entry {
      controller.Step("Start1") {
        SysCall.Inline(SysCall.start(worker))
      }
      controller.Step("Wait1") {}
      controller.Step("Kill1") {
        SysCall.Inline(SysCall.kill(worker))
      }
      controller.Step("Gap1") {}
      controller.Step("Start2") {
        SysCall.Inline(SysCall.start(worker))
      }
      controller.Step("Wait2") {}
      controller.Step("Kill2") {
        SysCall.Inline(SysCall.kill(worker))
      }
      controller.Step("Gap2") {}
      controller.Step("Finish") {
      }
      SysCall.Return()
    }
  }
}

class LifecycleLeaseModule extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    val hits = Output(UInt(8.W))
    val done = Output(Bool())
    val workerActive = Output(Bool())
  })

  io.hits := DontCare
  io.done := DontCare
  io.workerActive := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    val proc = spawn(new LifecycleLeaseProcess("Lifecycle"))
    val daemon = createLogic("Daemon")

    override def entry(): Unit = {
      daemon.run {
        when(io.start) {
          SysCall.Inline(SysCall.start(proc.controller))
        }
        io.hits  :=  proc.hits
        io.done  :=  proc.controller.done
        io.workerActive  :=  proc.worker.active
      }
    }
  }

  Init.build()
}

class LifecycleLeaseSpec extends AnyFlatSpec {
  "Unified thread runtime lifecycle" should "support kill, reset, and restart" in {
    simulate(new LifecycleLeaseModule) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      c.io.start.poke(true.B)
      c.clock.step()
      c.io.start.poke(false.B)

      var cycles = 0
      while (c.io.done.peek().litValue == 0 && cycles < 20) {
        c.clock.step()
        cycles += 1
      }
      c.clock.step(3)

      c.io.done.expect(true.B)
      assert(c.io.hits.peek().litValue >= 2, "worker was not restarted after kill/reset")
    }
  }
}
