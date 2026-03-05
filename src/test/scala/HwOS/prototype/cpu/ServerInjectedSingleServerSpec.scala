package HwOS.prototype.cpu

import HwOS.prototype.cpu.InjectedFreeFlow._
import HwOS.prototype.cpu.ServerInjectedFreeFlow._
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class ServerInjectedSingleServerSpec extends AnyFlatSpec {
  private val program = Seq(
    ISA.Instr(op = 0, rd = 1, rs1 = 0, imm = 7),
    ISA.Instr(op = 1, rd = 2, rs1 = 0, imm = 4),
  )

  "Server-based injected cpu" should "work when decode server count changes" in {
    simulate(new ServerInjectedCpuModule(program, initData = Seq(0, 0, 0, 0, 42), decodeServers = 3)) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      c.clock.step(30)

      c.io.x1.expect(7.U)
      c.io.x2.expect(42.U)
    }
  }
}
