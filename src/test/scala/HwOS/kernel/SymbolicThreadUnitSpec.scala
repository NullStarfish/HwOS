package HwOS.kernel

import HwOS.kernel.examples.symbolic.CounterProviderDemoModule
import HwOS.kernel.examples.symbolic.CounterWorkerThreadUnit
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class SymbolicThreadUnitSpec extends AnyFlatSpec {
  "A symbolic thread unit" should "be installable from a separate file and run against exported resources" in {
    simulate(new CounterProviderDemoModule) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      var cycles = 0
      while (c.io.done.peek().litValue == 0 && cycles < 20) {
        c.clock.step()
        cycles += 1
      }

      c.io.done.expect(true.B)
      c.io.counter.expect(3.U)
      c.io.limit.expect(3.U)

      val exports = c.kernel.addressSpace.exportedMemoryEntries
      val dependencies = c.kernel.addressSpace.dependencyEntries

      assert(exports.exists(_.symbolName == CounterWorkerThreadUnit.CounterSymbol))
      assert(exports.exists(_.symbolName == CounterWorkerThreadUnit.LimitSymbol))
      assert(dependencies.exists(_.symbolName == CounterWorkerThreadUnit.CounterSymbol))
      assert(dependencies.exists(_.symbolName == CounterWorkerThreadUnit.LimitSymbol))
      assert(dependencies.exists(_.requesterName == "Init/CounterProvider/Worker_thread"))
    }
  }
}
