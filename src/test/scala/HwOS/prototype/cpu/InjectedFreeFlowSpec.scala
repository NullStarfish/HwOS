package HwOS.prototype.cpu

import HwOS.prototype.cpu.InjectedFreeFlow._
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class InjectedFreeFlowSpec extends AnyFlatSpec {
  private val program = Seq(
    ISA.Instr(op = 0, rd = 1, rs1 = 0, imm = 9),
    ISA.Instr(op = 1, rd = 2, rs1 = 0, imm = 4),
  )
  private val loadAddProgram = Seq(
    ISA.Instr(op = 0, rd = 1, rs1 = 0, imm = 9),
    ISA.Instr(op = 2, rd = 2, rs1 = 1, imm = 4),
  )
  private val multiIssueProgram = Seq(
    ISA.Instr(op = 1, rd = 1, rs1 = 0, imm = 1),
    ISA.Instr(op = 1, rd = 2, rs1 = 0, imm = 2),
    ISA.Instr(op = 1, rd = 3, rs1 = 0, imm = 3),
    ISA.Instr(op = 1, rd = 4, rs1 = 0, imm = 4),
  )
  private val dualIssueProgram = Seq(
    ISA.Instr(op = 1, rd = 1, rs1 = 0, imm = 1),
    ISA.Instr(op = 1, rd = 2, rs1 = 0, imm = 2),
  )

  "Injected free-flow prototype" should "run a minimal fetch-decode-service MVP" in {
    simulate(new InjectedCpuModule(program, initData = Seq(0, 0, 0, 0, 42))) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      c.clock.step(16)

      c.io.x1.expect(9.U)
      c.io.x2.expect(42.U)
    }
  }

  it should "overlap decode and selected service threads" in {
    simulate(new InjectedCpuModule(program, initData = Seq(0, 0, 0, 0, 42))) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      c.clock.step(5)
      assert(c.io.activeThreads.peek().litValue >= 2, "expected at least two instruction threads active concurrently")

      c.clock.step(11)
      c.io.x1.expect(9.U)
      c.io.x2.expect(42.U)
    }
  }

  it should "run a LOADADD control program through load then arithmetic" in {
    simulate(new InjectedCpuModule(loadAddProgram, initData = Seq(0, 0, 0, 0, 42))) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      c.clock.step(24)

      c.io.x1.expect(9.U)
      c.io.x2.expect(51.U)
    }
  }

  it should "issue multiple instruction threads with fetch workers" in {
    simulate(new InjectedCpuModule(multiIssueProgram, initData = Seq(0, 11, 22, 33, 44))) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      c.clock.step(7)
      assert(c.io.activeThreads.peek().litValue >= 3, "expected multiple instruction threads in flight with dual fetch workers")
    }
  }

  it should "launch two instructions together from fetch" in {
    simulate(new InjectedCpuModule(dualIssueProgram, initData = Seq(0, 11, 22))) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      c.clock.step(2)
      assert(c.io.activeThreads.peek().litValue >= 2, "expected dual issue from fetch to make two threads active quickly")
    }
  }
}
