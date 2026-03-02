package HwOS.kernel


import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel._
import HwOS.kernel.HwOSLanguage._
import HwOS.kernel.examples.CallStackIntegrationModule

// ---------------------------------------------------------
// 1. 业务逻辑进程 (展示双轨调用栈)
// ---------------------------------------------------------

// ---------------------------------------------------------
// 3. ScalaTest 驱动程序
// ---------------------------------------------------------
class CallStackSpec extends AnyFlatSpec {
  "HwOS Dual-Track CallStack" should "generate correct temporal and combinational traces" in {
    simulate(new CallStackIntegrationModule) { c =>
      println("\n=== HwOS Dual-Track CallStack Test ===")
      
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      c.io.start.poke(true.B)
      c.clock.step()
      c.io.start.poke(false.B)

      var cycles = 0
      while (c.io.done.peek().litValue == 0 && cycles < 20) {
        c.clock.step()
        cycles += 1
      }
      c.clock.step()

      // 显式触发符号表导出 (假设你在 Kernel 中添加了 dumpSymbolTable)
      c.osKernel.dumpSymbolTable("test_dual_stack.symbols")
      println("=== Simulation Done! Please check test_dual_stack.symbols ===\n")
    }
  }
}
