package HwOS.kernel

import HwOS.kernel.examples.symbolic.{AltCounterProviderDemoModule, CounterProviderDemoModule}
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class SymbolicThreadReuseSpec extends AnyFlatSpec {
  "A symbolic thread unit" should "be reusable across providers that export the same symbols" in {
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
    }

    simulate(new AltCounterProviderDemoModule) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      var cycles = 0
      while (c.io.done.peek().litValue == 0 && cycles < 20) {
        c.clock.step()
        cycles += 1
      }

      c.io.done.expect(true.B)
      c.io.counter.expect(5.U)
      c.io.limit.expect(5.U)
    }
  }
}
