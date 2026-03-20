package HwOS.kernel

import HwOS.kernel.HwOSLanguage._
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class HwFunctionKillProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  val worker = createThread("Worker")
  val controller = createThread("Controller")
  private val reaper = createReaperManagedLogic("Reaper")
  val out = (RegInit(0.U(8.W)))
  val reclaimCount = (RegInit(0.U(8.W)))
  val releaseFlag = (RegInit(false.B))

  private var activationHeldOpt: Option[Bool] = None

  private val blocker = HwFunction.thread("Blocker") { t =>
    val held = (RegInit(false.B))
    val localTmp = (RegInit(0.U(8.W)))
    activationHeldOpt = Some(held)

    def holdLeaseActive: Bool = held

    def forceHoldReclaim(agent: HwOS.kernel.thread.HardwareAgent): Unit = {
        HwOS.kernel.system.OSReaper.forceAssign(held, false.B)
        HwOS.kernel.system.OSReaper.forceAssign(reclaimCount, reclaimCount + 1.U)
    }

    t.Step("Acquire") {
      when(!held) {
        held  :=  true.B
      }
      reaper.registerReclaimEntry(t, holdLeaseActive) { agent =>
        forceHoldReclaim(agent)
      }
    }

    t.Step("WaitRelease") {
      t.waitCondition(releaseFlag)
      when(releaseFlag) {
        localTmp  :=  out + 1.U
        held  :=  false.B
      }
    }

    t.Step("Commit") {
      out  :=  localTmp
    }

    SysCall.Return()
    ()
  }

  def functionActivationThread: HardwareThread =
    blocker.debugActivationThread.getOrElse(throw new Exception("[HwOS Test] Function activation thread was not initialized."))

  def activationHeld: Bool =
    activationHeldOpt.getOrElse(throw new Exception("[HwOS Test] Activation-held signal was not initialized."))

  override def entry(): Unit = {
    worker.entry {
      worker.Step("Init") {
        out  :=  0.U
      }

      SysCall.Call(blocker.Invoke("AfterCall"))

      worker.Step("AfterCall") {
        out  :=  out + 10.U
      }

      SysCall.Return()
    }

    controller.entry {
      controller.Step("Start1") {
        releaseFlag  :=  false.B
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
        releaseFlag  :=  true.B
        SysCall.Call(SysCall.start(worker))
      }
      controller.Step("WaitDone") {
        controller.waitCondition(worker.done)
        when(worker.done) {
          controller.hijack(controller.Next)
        }
      }
      controller.Step("Finish") {}
      SysCall.Return()
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
    (io.done)
    (io.out)
    (io.reclaimCount)
    (io.workerActive)
    (io.activationActive)
    (io.activationHeld)

    val proc = spawn(new HwFunctionKillProcess("FnKillProc"))
    val daemon = createLogic("Daemon")

    override def entry(): Unit = {
      val activation = proc.functionActivationThread

      daemon.run {
        when(!proc.controller.active && !proc.controller.done) {
          SysCall.Call(SysCall.start(proc.controller))
        }
        io.done  :=  proc.controller.done
        io.out  :=  proc.out
        io.reclaimCount  :=  proc.reclaimCount
        io.workerActive  :=  proc.worker.active
        io.activationActive  :=  activation.active
        io.activationHeld  :=  proc.activationHeld
      }
    }
  }

  Init.build()
}

class HwFunctionKillSpec extends AnyFlatSpec {
  "HwFunction kill propagation" should "reset the caller thread without invoking OSReaper reclaim, and still allow a clean restart" in {
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
            c.io.workerActive.peek().litValue == 0 &&
            c.io.activationActive.peek().litValue == 0 &&
            c.io.activationHeld.peek().litValue == 0
        guard += 1
      }

      val finalDone = c.io.done.peek().litValue
      val finalOut = c.io.out.peek().litValue
      val finalReclaim = c.io.reclaimCount.peek().litValue
      val finalWorkerActive = c.io.workerActive.peek().litValue
      val finalActivationActive = c.io.activationActive.peek().litValue
      val finalActivationHeld = c.io.activationHeld.peek().litValue

      assert(
        finished,
        s"kill propagation did not settle: done=$finalDone out=$finalOut reclaim=$finalReclaim workerActive=$finalWorkerActive activationActive=$finalActivationActive activationHeld=$finalActivationHeld",
      )
      c.io.workerActive.expect(false.B)
      c.io.activationActive.expect(false.B)
      c.io.activationHeld.expect(false.B)
      c.io.done.expect(true.B)
      c.io.reclaimCount.expect(0.U)
      c.io.out.expect(11.U)
    }
  }
}
