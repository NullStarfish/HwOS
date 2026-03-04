package HwOS.prototype.cpu

import HwOS.prototype.cpu.InjectedFreeFlow._
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class ServerInjectedFreeFlowSpec extends AnyFlatSpec {
  private val program = Seq(
    ISA.Instr(op = 0, rd = 1, rs1 = 0, imm = 9),
    ISA.Instr(op = 1, rd = 2, rs1 = 0, imm = 4),
  )

  private val loadAddProgram = Seq(
    ISA.Instr(op = 0, rd = 1, rs1 = 0, imm = 9),
    ISA.Instr(op = 2, rd = 2, rs1 = 1, imm = 4),
  )

  "Server-based injected free-flow prototype" should "run a minimal decode-server MVP" in {
    simulate(new ServerInjectedCpuModule(program, initData = Seq(0, 0, 0, 0, 42), decodeServers = 2)) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      c.clock.step(20)

      c.io.x1.expect(9.U)
      c.io.x2.expect(42.U)
    }
  }

  it should "run a LOADADD program through decode servers" in {
    simulate(new ServerInjectedCpuModule(loadAddProgram, initData = Seq(0, 0, 0, 0, 42), decodeServers = 2)) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      c.clock.step(28)

      c.io.x1.expect(9.U)
      c.io.x2.expect(51.U)
    }
  }
}
