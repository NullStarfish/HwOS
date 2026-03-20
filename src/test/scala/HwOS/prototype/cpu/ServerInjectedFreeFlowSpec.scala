package HwOS.prototype.cpu

import HwOS.prototype.cpu.ServerInjectedFreeFlow._
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

  it should "overlap decode and backend service threads" in {
    simulate(new ServerInjectedCpuModule(program, initData = Seq(0, 0, 0, 0, 42), decodeServers = 2)) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      c.clock.step(2)
      assert(c.io.activeThreads.peek().litValue >= 1, "expected the server-based pipeline to keep work in flight")

      c.clock.step(18)
      c.io.x1.expect(9.U)
      c.io.x2.expect(42.U)
    }
  }

  it should "run a LOADADD program through decode servers" in {
    simulate(new ServerInjectedCpuModule(loadAddProgram, initData = Seq(0, 0, 0, 0, 42), decodeServers = 2)) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      c.clock.step(45)

      c.io.x1.expect(9.U)
      c.io.x2.expect(51.U)
    }
  }

  it should "issue multiple instruction threads with fetch workers" in {
    val multiIssueProgram = Seq(
      ISA.Instr(op = 1, rd = 1, rs1 = 0, imm = 1),
      ISA.Instr(op = 1, rd = 2, rs1 = 0, imm = 2),
      ISA.Instr(op = 1, rd = 3, rs1 = 0, imm = 3),
      ISA.Instr(op = 1, rd = 4, rs1 = 0, imm = 4),
    )

    simulate(new ServerInjectedCpuModule(multiIssueProgram, initData = Seq(0, 11, 22, 33, 44), decodeServers = 2)) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      c.clock.step(7)
      assert(c.io.activeThreads.peek().litValue >= 3, "expected multiple request/server threads in flight with dual fetch workers")
    }
  }

  it should "launch two instructions together from fetch" in {
    val dualIssueProgram = Seq(
      ISA.Instr(op = 1, rd = 1, rs1 = 0, imm = 1),
      ISA.Instr(op = 1, rd = 2, rs1 = 0, imm = 2),
    )

    simulate(new ServerInjectedCpuModule(dualIssueProgram, initData = Seq(0, 11, 22), decodeServers = 2)) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      c.clock.step(2)
      assert(c.io.activeThreads.peek().litValue >= 2, "expected dual issue from fetch to make two threads active quickly")
    }
  }
}
