package HwOS.kernel

import HwOS.kernel.HwOSLanguage._
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class HwFunctionProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  val worker = createThread("Worker")
  val out = (RegInit(0.U(8.W)))
  val callCount = (RegInit(0.U(8.W)))

  private val addOne = HwFunction.thread("AddOne") { t =>
    val localTmp = (RegInit(0.U(8.W)))

    t.Step("LoadTmp") {
      localTmp  :=  out + 1.U
    }
    t.Step("Commit") {
      out  :=  localTmp
      callCount  :=  callCount + 1.U
    }
    SysCall.Return()
    ()
  }

  def functionActivationThread: Option[HardwareThread] = addOne.debugActivationThread

  override def entry(): Unit = {
    worker.entry {
      worker.Step("Init") {
        out  :=  0.U
      }

      SysCall.Inline(addOne.Invoke("AfterCall"))

      worker.Step("AfterCall") {
        out  :=  out + 10.U
      }

      SysCall.Inline(addOne.Invoke("AfterSecondCall"))

      worker.Step("AfterSecondCall") {
        out  :=  out + 20.U
      }

      SysCall.Return()
    }
  }
}

class HwFunctionModule extends Module {
  val io = IO(new Bundle {
    val out = Output(UInt(8.W))
    val callCount = Output(UInt(8.W))
    val done = Output(Bool())
    val functionCodeRegistered = Output(Bool())
    val activationOwnedStateCount = Output(UInt(8.W))
  })

  io.out := DontCare
  io.callCount := DontCare
  io.done := DontCare
  io.functionCodeRegistered := DontCare
  io.activationOwnedStateCount := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    val proc = spawn(new HwFunctionProcess("FnProc"))
    val daemon = createLogic("Daemon")

    override def entry(): Unit = {
      daemon.run {
        val activation = proc.functionActivationThread.getOrElse(
          throw new Exception("[HwOS Test] Function activation thread was not initialized."),
        )
        when(!proc.worker.active && !proc.worker.done) {
          SysCall.Inline(SysCall.start(proc.worker))
        }
        io.out  :=  proc.out
        io.callCount  :=  proc.callCount
        io.done  :=  proc.worker.done
        io.functionCodeRegistered  :=  kernel.addressSpace.codeTableEntries.exists(_.segment.ownerName == activation.name).B
        io.activationOwnedStateCount  :=  kernel.addressSpace.stateTableEntries.count(_.ownerName == activation.name).U
      }
    }
  }

  Init.build()
}

class HwFunctionSpec extends AnyFlatSpec {
  "HwFunction v1" should "run as a real blocking activation with its own code segment and tracked runtime state" in {
    simulate(new HwFunctionModule) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      var cycles = 0
      while (c.io.done.peek().litValue == 0 && cycles < 30) {
        c.clock.step()
        cycles += 1
      }

      c.io.done.expect(true.B)
      c.io.out.expect(32.U)
      c.io.callCount.expect(2.U)
      c.io.functionCodeRegistered.expect(true.B)
      assert(
        c.io.activationOwnedStateCount.peek().litValue >= 2,
        "function activation should at least contribute runtime cursor/state entries",
      )
    }
  }
}
