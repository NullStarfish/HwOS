package HwOS.kernel

import HwOS.kernel.HwOSLanguage._
import HwOS.kernel.context.HwLease
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class HwFunctionKillProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  val worker = createThread("Worker")
  val controller = createThread("Controller")
  val out = this.own(RegInit(0.U(8.W)))
  val reclaimCount = this.own(RegInit(0.U(8.W)))
  val releaseFlag = this.own(RegInit(false.B))

  private var activationHeldOpt: Option[Bool] = None

  private val blocker = HwFunction.thread("Blocker") { t =>
    this.grant(out, t)
    this.grant(reclaimCount, t)
    this.grant(releaseFlag, t)

    val held = t.own(RegInit(false.B))
    val localTmp = t.own(RegInit(0.U(8.W)))
    activationHeldOpt = Some(held)

    val holdLease = new HwLease {
      override def isActive: Bool = held

      override private[kernel] def forceReclaim(agent: HwOS.kernel.thread.HardwareAgent): Unit = {
        t.grant(held, agent)
        HwFunctionKillProcess.this.grant(reclaimCount, agent)
        held <==! false.B
        reclaimCount <==! reclaimCount + 1.U
      }
    }

    t.Step("Acquire") {
      when(!held) {
        held <== true.B
      }
      t.ctx.registerLease(holdLease)
    }

    t.Step("WaitRelease") {
      t.waitCondition(releaseFlag)
      when(releaseFlag) {
        localTmp <== out + 1.U
        held <== false.B
      }
    }

    t.Step("Commit") {
      out <== localTmp
    }

    SysCall.Call(SysCall.Return())
    ()
  }

  def functionActivationThread: HardwareThread =
    blocker.debugActivationThread.getOrElse(throw new Exception("[HwOS Test] Function activation thread was not initialized."))

  def activationHeld: Bool =
    activationHeldOpt.getOrElse(throw new Exception("[HwOS Test] Activation-held signal was not initialized."))

  override def entry(): Unit = {
    this.grant(out, worker)

    worker.entry {
      worker.Step("Init") {
        out <== 0.U
      }

      SysCall.Call(blocker.Invoke("AfterCall"))

      worker.Step("AfterCall") {
        out <== out + 10.U
      }

      SysCall.Call(SysCall.Return())
    }

    this.grant(releaseFlag, controller)
    this.grantLifecycle(worker, controller)

    controller.entry {
      controller.Step("Start1") {
        releaseFlag <== false.B
        SysCall.Call(SysCall.start(worker))
      }
      controller.Step("Gap1") {}
      controller.Step("Gap2") {}
      controller.Step("Kill1") {
        SysCall.Call(SysCall.kill(worker))
      }
      controller.Step("ObserveKill1") {}
      controller.Step("ObserveKill2") {}
      controller.Step("ReleaseAndRestart") {
        releaseFlag <== true.B
        SysCall.Call(SysCall.start(worker))
      }
      controller.Step("WaitDone") {
        controller.waitCondition(worker.done)
        when(worker.done) {
          controller.hijack(controller.Next)
        }
      }
      controller.Step("Finish") {}
      SysCall.Call(SysCall.Return())
    }
  }
}

class HwFunctionKillModule extends Module {
  val io = IO(new Bundle {
    val done = Output(Bool())
    val out = Output(UInt(8.W))
    val reclaimCount = Output(UInt(8.W))
    val workerActive = Output(Bool())
    val activationActive = Output(Bool())
    val activationHeld = Output(Bool())
  })

  io.done := DontCare
  io.out := DontCare
  io.reclaimCount := DontCare
  io.workerActive := DontCare
  io.activationActive := DontCare
  io.activationHeld := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    this.own(io.done)
    this.own(io.out)
    this.own(io.reclaimCount)
    this.own(io.workerActive)
    this.own(io.activationActive)
    this.own(io.activationHeld)

    val proc = spawn(new HwFunctionKillProcess("FnKillProc"))
    val daemon = createLogic("Daemon")

    override def entry(): Unit = {
      val activation = proc.functionActivationThread

      this.grant(io.done, daemon, GrantAbi.LevelDrivenWire)
      this.grant(io.out, daemon, GrantAbi.LevelDrivenWire)
      this.grant(io.reclaimCount, daemon, GrantAbi.LevelDrivenWire)
      this.grant(io.workerActive, daemon, GrantAbi.LevelDrivenWire)
      this.grant(io.activationActive, daemon, GrantAbi.LevelDrivenWire)
      this.grant(io.activationHeld, daemon, GrantAbi.LevelDrivenWire)
      this.grantLifecycle(proc.controller, daemon)

      daemon.run {
        when(!proc.controller.active && !proc.controller.done) {
          SysCall.Call(SysCall.start(proc.controller))
        }
        io.done <== proc.controller.done
        io.out <== proc.out
        io.reclaimCount <== proc.reclaimCount
        io.workerActive <== proc.worker.active
        io.activationActive <== activation.active
        io.activationHeld <== proc.activationHeld
      }
    }
  }

  Init.build()
}

class HwFunctionKillSpec extends AnyFlatSpec {
  "HwFunction kill propagation" should "kill the activation, reclaim its leases, and allow a clean restart" in {
    simulate(new HwFunctionKillModule) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      var finished = false
      var guard = 0
      while (!finished && guard < 60) {
        c.clock.step()
        finished =
          c.io.done.peek().litValue == 1 &&
            c.io.out.peek().litValue == 11 &&
            c.io.reclaimCount.peek().litValue >= 1 &&
            c.io.workerActive.peek().litValue == 0 &&
            c.io.activationActive.peek().litValue == 0 &&
            c.io.activationHeld.peek().litValue == 0
        guard += 1
      }

      assert(finished, "kill propagation did not settle to a fully reclaimed and restarted function call")
      c.io.workerActive.expect(false.B)
      c.io.activationActive.expect(false.B)
      c.io.activationHeld.expect(false.B)
      c.io.done.expect(true.B)
      assert(c.io.reclaimCount.peek().litValue >= 1, "activation reclaim count never incremented")
      c.io.out.expect(11.U)
    }
  }
}
