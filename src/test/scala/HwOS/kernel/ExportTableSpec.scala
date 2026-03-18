package HwOS.kernel

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel.HwOSLanguage._
import HwOS.kernel.memory.ExportCapability
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.Kernel

class ExportTableModule extends Module {
  val io = IO(new Bundle {
    val observed = Output(UInt(8.W))
  })
  io.observed := 0.U

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    private val exportedValue = RegInit(21.U(8.W))
    private val consumer = createLogic("Consumer")

    override def entry(): Unit = {
      export("table.counter", exportedValue, ExportCapability.ReadWrite)
      consumer.run {
        val handle = consumer.declare[UInt]("table.counter", ExportCapability.Read)
        io.observed := handle.read
      }
    }
  }

  Init.build()
}

class ExportTableSpec extends AnyFlatSpec {
  "Kernel exported memory tables" should "record exports and dependencies in a module-safe build" in {
    simulate(new ExportTableModule) { c =>
      c.clock.step()
      c.io.observed.expect(21.U)

      val exports = c.kernel.addressSpace.exportedMemoryEntries
      val dependencies = c.kernel.addressSpace.dependencyEntries
      val rendered = c.kernel.addressSpace.renderAddressTables()

      assert(exports.exists(_.symbolName == "table.counter"))
      assert(exports.exists(_.ownerName == "Init"))
      assert(exports.exists(_.capability.render == "read+write"))

      assert(dependencies.exists(_.symbolName == "table.counter"))
      assert(dependencies.exists(_.requesterName == "Init/Consumer_logic"))
      assert(dependencies.exists(_.resolvedOwnerName == "Init"))
      assert(dependencies.exists(_.requestedCapability.render == "read"))

      assert(rendered.contains("Exported Memory Table"))
      assert(rendered.contains("Dependency Table"))
      assert(rendered.contains("table.counter"))
      assert(rendered.contains("Init/Consumer_logic"))
    }
  }
}
