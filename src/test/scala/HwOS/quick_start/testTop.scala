package HwOS.quick_start

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

// --- 你的 TopModule 定义保持不变 ---
// class TopModule extends Module { ... }

// --- 编写 ScalaTest 驱动程序 ---
class TopModuleSpec extends AnyFlatSpec {
  "HwOS TopModule" should "start the counter thread and wait until it reaches 10" in {
    // 实例化 EphemeralSimulator 进行轻量级极速仿真
    simulate(new TopModule) { c =>
      println("\n=== HwOS Counter QuickStart Test ===")
      
      // 1. 初始化与复位 (Reset)
      c.reset.poke(true.B)
      c.clock.step(1)
      c.reset.poke(false.B)

      // 2. 发送单拍启动脉冲 (Start Pulse)
      c.io.start.poke(true.B)
      c.clock.step(1)
      c.io.start.poke(false.B)

      println("[Test] 启动脉冲已发送，等待 HwOS 线程执行...")

      // 3. 轮询等待 done 信号拉高 (带超时保护防止死循环)
      var cycles = 0
      val maxTimeout = 50 // 设定最大超时拍数
      
      while (c.io.done.peek().litValue == 0 && cycles < maxTimeout) {
        c.clock.step(1)
        cycles += 1
      }

      // 打印执行所花费的周期数
      println(s"[Test] 线程执行完毕，耗时: $cycles 拍.")

      // 4. 断言验证 (Assertions)
      // 验证是否在超时前正常退出
      assert(cycles < maxTimeout, "Simulation timed out! Thread did not finish.")
      
      // 验证最终状态是否正确
      c.io.done.expect(true.B)
      c.io.result.expect(10.U) // 我们在 Step("CountUp") 中设定了计数到 10 退出
      
      val finalResult = c.io.result.peek().litValue
      println(s"[Test] 最终结果验证成功: io_result = $finalResult")
      println("=== Test Passed! ===\n")
    }
  }
}