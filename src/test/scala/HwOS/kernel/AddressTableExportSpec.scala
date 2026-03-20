package HwOS.kernel

import HwOS.kernel.HwOSLanguage._
import HwOS.kernel.examples.symbolic.CounterWorkerThreadUnit
import HwOS.kernel.memory.ExportCapability
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{AddressKind, Kernel, SysCall}
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import java.nio.file.{Files, Path, Paths}
import org.scalatest.flatspec.AnyFlatSpec

class AddressTableExportSpec extends AnyFlatSpec {
  private def deleteIfExists(path: Path): Unit = {
    if (Files.exists(path)) {
      Files.delete(path)
    }
  }

  "KernelAddressSpace" should "render empty tables and export JSON/text snapshots" in {
    val kernel = new Kernel()
    val rendered = kernel.addressSpace.renderAddressTables()

    assert(rendered.contains("State Table"))
    assert(rendered.contains("Code Table"))
    assert(rendered.contains("Binding Table"))
    assert(rendered.contains("Exported Memory Table"))
    assert(rendered.contains("Dependency Table"))
    assert(rendered.contains("0 entries"))

    val tempDir = Files.createTempDirectory("hwos_address_tables_")
    kernel.addressSpace.exportAddressTables(tempDir.toString)

    val jsonPath = tempDir.resolve("address_tables.json")
    val textPath = tempDir.resolve("address_tables.txt")
    assert(Files.exists(jsonPath))
    assert(Files.exists(textPath))

    val json = Files.readString(jsonPath)
    val text = Files.readString(textPath)
    assert(json.contains("\"state_table\""))
    assert(json.contains("\"code_table\""))
    assert(json.contains("\"binding_table\""))
    assert(json.contains("\"exported_memory_table\""))
    assert(json.contains("\"dependency_table\""))
    assert(text.contains("State Table"))
    assert(text.contains("Code Table"))
    assert(text.contains("Binding Table"))
    assert(text.contains("Exported Memory Table"))
    assert(text.contains("Dependency Table"))
    assert(text.contains("code_start"))
    assert(text.contains("code_entry"))
  }

  it should "auto-export populated tables after boot" in {
    val generatedDir = Paths.get("generated")
    val jsonPath = generatedDir.resolve("address_tables.json")
    val textPath = generatedDir.resolve("address_tables.txt")
    deleteIfExists(jsonPath)
    deleteIfExists(textPath)

    class AddressTableExportModule extends Module {
      implicit val kernel: Kernel = new Kernel()

      object Init extends HwProcess("Init") {
        val worker = createThread("Worker")
        val shared = (RegInit(0.U(8.W)))
        private val observer = createLogic("Observer")

        override def entry(): Unit = {
          export("exported.shared", shared, ExportCapability.Read)
          worker.entry {
            worker.Step("Init") {}
            SysCall.Return()
          }
          observer.run {
            observer.declare[UInt]("exported.shared", ExportCapability.Read)
          }
        }
      }

      Init.build()
    }

    simulate(new AddressTableExportModule) { _ =>
      assert(Files.exists(jsonPath), "boot should auto-export address_tables.json")
      assert(Files.exists(textPath), "boot should auto-export address_tables.txt")

      val json = Files.readString(jsonPath)
      val text = Files.readString(textPath)

      assert(json.contains("\"state_table\""))
      assert(json.contains("\"code_table\""))
      assert(json.contains("\"binding_table\""))
      assert(json.contains("\"exported_memory_table\""))
      assert(json.contains("\"dependency_table\""))
      assert(json.contains("Init/Worker_thread"))
      assert(json.contains("Init/Worker_thread_segment"))
      assert(json.contains("exported.shared"))
      assert(json.contains("\"code_start\":0"))
      assert(json.contains("\"code_entry\":0"))
      assert(json.contains("\"cursor_start_address\":"))
      assert(json.contains("\"runtime_state_start_address\":"))
      assert(json.contains("\"code_space\":\"code\""))
      assert(json.contains("\"cursor_space\":\"state\""))
      assert(json.contains("\"runtime_state_space\":\"state\""))
      assert(json.contains("\"space\":\"state\""))
      assert(json.contains("\"space\":\"code\""))

      assert(text.contains("State Table"))
      assert(text.contains("Code Table"))
      assert(text.contains("Binding Table"))
      assert(text.contains("Exported Memory Table"))
      assert(text.contains("Dependency Table"))
      assert(text.contains("Init/Worker_thread"))
      assert(text.contains("Init/Worker_thread_segment"))
      assert(text.contains("exported.shared"))
      assert(text.contains("code_start"))
      assert(text.contains("code_entry"))
    }
  }

  it should "allocate state and code addresses independently" in {
    val kernel = new Kernel()

    val state0 = kernel.addressSpace.reserveAddressObject(
      kind = AddressKind.State,
      ownerName = "TestOwner",
      objectName = "state0",
      span = 8,
    )
    val state1 = kernel.addressSpace.reserveAddressObject(
      kind = AddressKind.State,
      ownerName = "TestOwner",
      objectName = "state1",
      span = 2,
    )
    val code0 = kernel.addressSpace.reserveAddressObject(
      kind = AddressKind.Code,
      ownerName = "TestOwner",
      objectName = "code0",
      span = 3,
    )
    val code1 = kernel.addressSpace.reserveAddressObject(
      kind = AddressKind.Code,
      ownerName = "TestOwner",
      objectName = "code1",
      span = 4,
    )

    assert(state0.startAddress == 0)
    assert(state1.startAddress == 8)
    assert(code0.startAddress == 0)
    assert(code1.startAddress == 3)
    assert(state1.endAddressExclusive == 10)
    assert(code1.endAddressExclusive == 7)
  }

  it should "record symbolic thread-unit exports and dependencies by global symbol name" in {
    class SymbolicAddressExportModule extends Module {
      implicit val kernel: Kernel = new Kernel()

      object Init extends HwProcess("Init") {
        private val provider = spawn(new HwOS.kernel.examples.symbolic.CounterProviderProcess("CounterProvider"))

        override def entry(): Unit = {}
      }

      Init.build()
    }

    simulate(new SymbolicAddressExportModule) { c =>
      val exports = c.kernel.addressSpace.exportedMemoryEntries
      val dependencies = c.kernel.addressSpace.dependencyEntries
      val rendered = c.kernel.addressSpace.renderAddressTables()

      assert(exports.exists(_.symbolName == CounterWorkerThreadUnit.CounterSymbol))
      assert(exports.exists(_.symbolName == CounterWorkerThreadUnit.LimitSymbol))
      assert(dependencies.exists(_.symbolName == CounterWorkerThreadUnit.CounterSymbol))
      assert(dependencies.exists(_.symbolName == CounterWorkerThreadUnit.LimitSymbol))
      assert(dependencies.exists(_.requesterName == "Init/CounterProvider/Worker_thread"))
      assert(rendered.contains(CounterWorkerThreadUnit.CounterSymbol))
      assert(rendered.contains(CounterWorkerThreadUnit.LimitSymbol))
      assert(rendered.contains("Init/CounterProvider/Worker_thread"))
    }
  }
}
