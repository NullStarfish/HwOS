package HwOS.kernel

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel.HwOSLanguage._

class ExplicitStartWorkerProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  val counter = (RegInit(0.U(8.W)))
  val worker = createThread("InlineWorker")

  override def entry(): Unit = {
    worker.entry {
      worker.Step("Count0") {
        counter  :=  counter + 1.U
      }
      worker.Step("Count1") {
        counter  :=  counter + 1.U
      }
      worker.Step("Count2") {
        counter  :=  counter + 1.U
      }
      worker.Step("Finish") {
      }
      SysCall.Return()
    }
  }
}

class ExplicitStartRuntimeModule extends Module {
  val io = IO(new Bundle {
    val counter = Output(UInt(8.W))
    val done = Output(Bool())
  })
  io.counter := DontCare
  io.done := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    (io.counter)
    (io.done)
    val proc = spawn(new ExplicitStartWorkerProcess("InlineProc"))
    val daemon = createLogic("Daemon")

    override def entry(): Unit = {
      daemon.run {
        when(!proc.worker.active && !proc.worker.done) {
          SysCall.Inline(SysCall.start(proc.worker))
        }
        io.counter  :=  proc.counter
        io.done  :=  proc.worker.done
      }
    }
  }

  Init.build()
}

class ExplicitStartRuntimeSpec extends AnyFlatSpec {
  "Unified thread runtime" should "run to completion after an explicit start" in {
    simulate(new ExplicitStartRuntimeModule) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      var cycles = 0
      while (c.io.done.peek().litValue == 0 && cycles < 10) {
        c.clock.step()
        cycles += 1
      }

      c.io.done.expect(true.B)
      c.io.counter.expect(3.U)
    }
  }
}
