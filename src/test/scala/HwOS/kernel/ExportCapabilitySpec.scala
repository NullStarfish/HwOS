package HwOS.kernel

import chisel3._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel.memory.ExportCapability
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.Kernel

class ExportCapabilitySpec extends AnyFlatSpec {
  "export capability checks" should "reject declares that request permissions not granted by the exporter" in {
    implicit val kernel: Kernel = new Kernel()

    object Init extends HwProcess("Init") {
      private val source = RegInit(0.U(8.W))
      private val consumer = createLogic("Consumer")

      override def entry(): Unit = {
        export("readonly.counter", source, ExportCapability.Read)
        consumer.run {
          consumer.declare[UInt]("readonly.counter", ExportCapability.ReadWrite)
        }
      }
    }

    assertThrows[Exception] {
      Init.build()
    }
  }

  it should "reject writes on a read-only virtual handle" in {
    implicit val kernel: Kernel = new Kernel()

    object Init extends HwProcess("Init") {
      private val source = RegInit(0.U(8.W))
      private val consumer = createLogic("Consumer")

      override def entry(): Unit = {
        export("readonly.value", source, ExportCapability.Read)
        consumer.run {
          val handle = consumer.declare[UInt]("readonly.value", ExportCapability.Read)
          handle.write := 1.U
        }
      }
    }

    assertThrows[Exception] {
      Init.build()
    }
  }

  it should "reject incompatible handle types at declare time" in {
    implicit val kernel: Kernel = new Kernel()

    object Init extends HwProcess("Init") {
      private val source = RegInit(0.U(8.W))
      private val consumer = createLogic("Consumer")

      override def entry(): Unit = {
        export("typed.value", source, ExportCapability.Read)
        consumer.run {
          consumer.declare[Bool]("typed.value", ExportCapability.Read)
        }
      }
    }

    assertThrows[Exception] {
      Init.build()
    }
  }
}
