package HwOS.kernel

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel.HwOSLanguage._
import HwOS.kernel.examples.ExportDeclareDemoModule
import HwOS.kernel.memory.ExportCapability
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{Kernel}

class ExportDeclareCombinationalModule extends Module {
  val io = IO(new Bundle {
    val observed = Output(UInt(8.W))
  })
  io.observed := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    private val exportedValue = RegInit(9.U(8.W))
    private val consumer = createLogic("Consumer")

    override def entry(): Unit = {
      export("sample.counter", exportedValue, ExportCapability.ReadWrite)
      consumer.run {
        val handle = consumer.declare[UInt]("sample.counter", ExportCapability.Read)
        io.observed  :=  handle.read
      }
    }
  }

  Init.build()
}

class ExportDeclareSpec extends AnyFlatSpec {
  "export/declare" should "allow one entity to export and another to read by symbol name" in {
    simulate(new ExportDeclareCombinationalModule) { c =>
      c.clock.step()
      c.io.observed.expect(9.U)
    }
  }

  it should "work inside the existing thread DSL through a virtual handle" in {
    simulate(new ExportDeclareDemoModule) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      var cycles = 0
      while (c.io.done.peek().litValue == 0 && cycles < 10) {
        c.clock.step()
        cycles += 1
      }

      c.io.done.expect(true.B)
      c.io.counter.expect(1.U)
    }
  }
}
