package HwOS.kernel.examples.symbolic

import HwOS.kernel.HwOSLanguage._
import HwOS.kernel.memory.ExportCapability
import HwOS.kernel.system.SysCall
import HwOS.kernel.thread.HardwareThread
import chisel3._

/**
 * A thread unit that only depends on exported symbols. It never reaches into a
 * provider process directly.
 */
object CounterWorkerThreadUnit extends SymbolicThreadUnit {
  val CounterSymbol: String = "demo.counter"
  val LimitSymbol: String = "demo.limit"

  override def define(worker: HardwareThread): Unit = {
    worker.entry {
      val counter = worker.declare[UInt](CounterSymbol, ExportCapability.ReadWrite)
      val limit = worker.declare[UInt](LimitSymbol, ExportCapability.Read)

      worker.Step("CheckLimit") {
        when(counter.read < limit.read) {
          counter.write := (counter.read + 1.U).asUInt
          worker.jump(worker.stepRef("CheckLimit"))
        }.otherwise {
          worker.jump(worker.stepRef("Finish"))
        }
      }

      worker.Step("Finish") {}
      SysCall.Call(SysCall.Return())
    }
  }
}
