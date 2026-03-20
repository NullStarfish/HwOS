package HwOS.kernel

import HwOS.kernel.function.HwFunction
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class HwFunctionStructureProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  val worker = createThread("Worker")
  val out = RegInit(0.U(8.W))

  val fn = HwFunction.thread("StructFn") { t =>
    t.Step("Write") {
      out := out + 1.U
    }
    SysCall.Return()
    ()
  }

  override def entry(): Unit = {
    worker.entry {
      SysCall.Inline(fn.Invoke("AfterCall"))
      worker.Step("AfterCall") {
        out := out + 10.U
      }
      SysCall.Return()
    }
  }
}

class HwFunctionStructureModule extends Module {
  val io = IO(new Bundle {
    val done = Output(Bool())
    val hasRuntimeHost = Output(Bool())
    val hasActivation = Output(Bool())
  })

  implicit val kernel: Kernel = new Kernel()

  io.done := false.B
  io.hasRuntimeHost := false.B
  io.hasActivation := false.B

  object Init extends HwProcess("Init") {
    val proc = spawn(new HwFunctionStructureProcess("FnProc"))
    val daemon = createLogic("Daemon")

    override def entry(): Unit = {
      daemon.run {
        when(!proc.worker.active && !proc.worker.done) {
          SysCall.Inline(SysCall.start(proc.worker))
        }
        io.done := proc.worker.done
        io.hasRuntimeHost := proc.fn.debugRuntimeHost.nonEmpty.B
        io.hasActivation := proc.fn.debugActivationThread.nonEmpty.B
      }
    }
  }

  Init.build()
}

class HwFunctionStructureSpec extends AnyFlatSpec {
  "HwFunction runtime structure" should "centralize activation and binding state inside a single runtime host" in {
    simulate(new HwFunctionStructureModule) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      var cycles = 0
      while (c.io.done.peek().litValue == 0 && cycles < 30) {
        c.clock.step()
        cycles += 1
      }

      c.io.done.expect(true.B)
      c.io.hasRuntimeHost.expect(true.B)
      c.io.hasActivation.expect(true.B)
    }
  }
}
