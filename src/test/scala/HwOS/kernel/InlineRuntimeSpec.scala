package HwOS.kernel

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel.HwOSLanguage._

class InlineWorkerProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  val counter = this.own(RegInit(0.U(8.W)))
  val worker = createThread("InlineWorker", ThreadPolicy.InlinePreferred)

  override def entry(): Unit = {
    this.grant(counter, worker)
    worker.entry {
      worker.Step("Count0") {
        counter <== counter + 1.U
      }
      worker.Step("Count1") {
        counter <== counter + 1.U
      }
      worker.Step("Count2") {
        counter <== counter + 1.U
      }
      worker.Step("Finish") {
        worker.exit()
      }
    }
  }
}

class InlineRuntimeModule extends Module {
  val io = IO(new Bundle {
    val counter = Output(UInt(8.W))
    val done = Output(Bool())
  })
  io.counter := DontCare
  io.done := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    this.own(io.counter)
    this.own(io.done)
    val proc = spawn(new InlineWorkerProcess("InlineProc"))
    val daemon = createLogic("Daemon")

    override def entry(): Unit = {
      this.grant(io.counter, daemon)
      this.grant(io.done, daemon)
      daemon.run {
        io.counter <== proc.counter
        io.done <== proc.worker.done
      }
    }
  }

  Init.build()
}

class InlineRuntimeSpec extends AnyFlatSpec {
  "InlinePreferred thread" should "run to completion without explicit start/kill lifecycle" in {
    simulate(new InlineRuntimeModule) { c =>
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
