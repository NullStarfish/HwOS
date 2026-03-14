package HwOS.prototype.cpu

import HwOS.prototype.cpu.ModuleWrapperExample._
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class ModuleWrapperExampleSpec extends AnyFlatSpec {
  "Arithmetic wrapper link top" should "connect caller and wrapped callee through a start/busy/done ABI" in {
    simulate(new ArithmeticWrapperLinkTop) { c =>
      c.reset.poke(true.B)
      c.io.kick.poke(false.B)
      c.io.lhs.poke(0.U)
      c.io.rhs.poke(0.U)
      c.clock.step()

      c.reset.poke(false.B)
      c.io.kick.poke(true.B)
      c.io.lhs.poke(13.U)
      c.io.rhs.poke(29.U)

      c.clock.step(8)

      c.io.done.expect(true.B)
      c.io.result.expect(42.U)

      c.io.kick.poke(false.B)
      c.clock.step()
      c.io.done.expect(false.B)
    }
  }
}
