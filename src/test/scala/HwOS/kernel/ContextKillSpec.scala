package HwOS.kernel

import HwOS.kernel.HwOSLanguage._
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class ContextKillProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  val worker = createThread("Worker")
  val controller = createThread("Controller")
  val contextGate = createReaperManagedLogic("ContextGate")
  val hits = this.own(RegInit(0.U(8.W)))

  override def entry(): Unit = {
    this.grant(hits, worker)
    this.grantLifecycle(worker, controller)

    worker.entry {
      worker.Step("Tick") {
        hits  :=  hits + 1.U
      }
      worker.Step("Hold") {
        worker.waitCondition(false.B)
      }
      SysCall.Call(SysCall.Return())
    }

    contextGate.registerReclaimEntry(worker, worker.active) { agent =>
      HwOS.kernel.system.OSReaper.reclaimThread(worker, Seq.empty, agent)
    }

    controller.entry {
      controller.Step("Start") {
        SysCall.Call(SysCall.start(worker))
      }
      controller.Step("ContextKill") {
        SysCall.Call(SysCall.kill(contextGate: HwContextEntity))
      }
      controller.Step("Finish") {}
      SysCall.Call(SysCall.Return())
    }
  }
}

class ContextKillModule extends Module {
  val io = IO(new Bundle {
    val hits = Output(UInt(8.W))
    val workerActive = Output(Bool())
    val workerDone = Output(Bool())
  })

  io.hits := DontCare
  io.workerActive := DontCare
  io.workerDone := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    this.own(io.hits)
    this.own(io.workerActive)
    this.own(io.workerDone)

    val proc = spawn(new ContextKillProcess("CtxKill"))
    val daemon = createLogic("Daemon")

    override def entry(): Unit = {
      this.grant(io.hits, daemon, GrantAbi.LevelDrivenWire)
      this.grant(io.workerActive, daemon, GrantAbi.LevelDrivenWire)
      this.grant(io.workerDone, daemon, GrantAbi.LevelDrivenWire)
      this.grantLifecycle(proc.controller, daemon)

      daemon.run {
        when(!proc.controller.active && !proc.controller.done) {
          SysCall.Call(SysCall.start(proc.controller))
        }
        io.hits  :=  proc.hits
        io.workerActive  :=  proc.worker.active
        io.workerDone  :=  proc.worker.done
      }
    }
  }

  Init.build()
}

class ContextKillSpec extends AnyFlatSpec {
  "Context kill" should "cut the context and let the reaper reset a thread runtime lease by default" in {
    simulate(new ContextKillModule) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)
      c.clock.step(10)

      val hitsAfterKill = c.io.hits.peek().litValue
      c.clock.step(4)

      assert(hitsAfterKill >= 1, "worker never started before context kill")
      c.io.workerActive.expect(false.B)
      c.io.workerDone.expect(false.B)
      c.io.hits.expect(hitsAfterKill.U)
    }
  }
}
