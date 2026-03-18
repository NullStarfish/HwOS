package HwOS.kernel

import HwOS.kernel.HwOSLanguage._
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class LifecycleLeaseProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  val worker = createThread("Worker")
  val controller = createThread("Controller")
  val hits = this.own(RegInit(0.U(8.W)))

  override def entry(): Unit = {
    this.grant(hits, worker)
    worker.grantLifecycle(worker, controller)

    worker.entry {
      worker.Step("Hit") {
        hits  :=  hits + 1.U
      }
      worker.Step("Park") {
        worker.waitCondition(false.B)
      }
      worker.Step("Never") {
      }
      SysCall.Call(SysCall.Return())
    }

    controller.entry {
      controller.Step("Start1") {
        SysCall.Call(SysCall.start(worker))
      }
      controller.Step("Wait1") {}
      controller.Step("Kill1") {
        SysCall.Call(SysCall.kill(worker))
      }
      controller.Step("Gap1") {}
      controller.Step("Start2") {
        SysCall.Call(SysCall.start(worker))
      }
      controller.Step("Wait2") {}
      controller.Step("Kill2") {
        SysCall.Call(SysCall.kill(worker))
      }
      controller.Step("Gap2") {}
      controller.Step("Finish") {
      }
      SysCall.Call(SysCall.Return())
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
    this.own(io.hits)
    this.own(io.done)
    this.own(io.workerActive)

    val proc = spawn(new LifecycleLeaseProcess("Lifecycle"))
    val daemon = createLogic("Daemon")

    override def entry(): Unit = {
      this.grant(io.hits, daemon, GrantAbi.LevelDrivenWire)
      this.grant(io.done, daemon, GrantAbi.LevelDrivenWire)
      this.grant(io.workerActive, daemon, GrantAbi.LevelDrivenWire)
      this.grantLifecycle(proc.controller, daemon)

      daemon.run {
        when(io.start) {
          SysCall.Call(SysCall.start(proc.controller))
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
  "Unified thread runtime lifecycle" should "support kill, reclaim, and restart" in {
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
      assert(c.io.hits.peek().litValue >= 2, "worker was not restarted after kill/reclaim")
    }
  }
}
