package HwOS.kernel

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel._ 

// ==========================================
// 1. 搭建测试用的硬件外壳 (Test Harness)
// ==========================================
class ThreadTestModule extends Module {
  val io = IO(new Bundle {
    val startTrigger = Input(Bool())
    val flagA        = Input(Bool()) // 用于 waitCondition
    val flagB        = Input(Bool()) // 用于 waitAndAct
    
    val counterValue = Output(UInt(32.W))
    val pcValue      = Output(UInt(32.W)) // 暴露 PC 方便调试
    val isRunning    = Output(Bool())
    val isDone       = Output(Bool())
  })

  val kernel = new Kernel()

  class TestProcess(k: Kernel) extends HwProcess("TestProc", debugEnable = false, parent = None)(k) {
    val counter = RegInit(0.U(32.W))
    val worker = createThread("WorkerThread")

    // 这里调用 start，利用 Last Connect 语义，没问题
    when(io.startTrigger) {
        worker.start()
        printf("[Hardware] Trigger hit! Calling start().\n")
    }

    override def entry(): Unit = {
      
      worker.entry {
        // [Index 0] 初始化
        worker.Step("INIT") {
          counter := 0.U
          printf(p"[Step 0] INIT: Counter reset to 0\n")
        }

        // [Index 1] 测试 waitCondition
        // 只有当 io.flagA 为 true 时，PC 才会往下走，否则卡在 1
        worker.Step("WAIT_A") {
          worker.waitCondition(io.flagA)
          printf(p"[Step 1] WAIT_A: Waiting for flagA... (FlagA=${io.flagA})\n")
        }

        // [Index 2] 测试 waitAndAct + 循环体主体
        // 只有当 io.flagB 为 true 时，执行 block 并往下走，否则卡在 2 且不执行 block
        worker.Step("ADD_ACT") {
          worker.waitAndAct(io.flagB) {
             counter := counter + 1.U
             printf(p"[Step 2] ADD_ACT: Executing Logic! Counter becomes ${counter + 1.U}\n")
          }
        }

        // [Index 3] 测试内部 PC 跳转 (实现 Loop)
        // 如果 counter < 3，跳转回 Index 2 (ADD_ACT)
        worker.Step("JUMP_CHECK") {
          when (counter < 3.U) {
             worker.pc := 2.U // Jump back to Step 2
             printf(p"[Step 3] JUMP: Counter < 3, jumping back to 2!\n")
          } .otherwise {
             printf(p"[Step 3] PASS: Counter >= 3, continuing...\n")
          }
        }

        // [Index 4] 退出
        worker.Step("EXIT") {
          worker.exit()
          printf(p"[Step 4] EXIT: Bye!\n")
        }
      }

      // 暴露内部状态给 TestBench
      io.pcValue := worker.pc
    }
  }

  val proc = new TestProcess(kernel)
  proc.build()

  io.counterValue := proc.counter
  io.isRunning    := proc.worker.isRunning
  io.isDone       := proc.worker.done
}

// ==========================================
// 2. 编写测试用例
// ==========================================
class HardwareThreadTest extends AnyFlatSpec {
  "HardwareThread" should "handle waitCondition, waitAndAct and PC jumps correctly" in {
    simulate(new ThreadTestModule) { c =>
      
      println("\n=== Complex Test Start ===")
      
      // 初始化信号
      c.io.startTrigger.poke(false.B)
      c.io.flagA.poke(false.B)
      c.io.flagB.poke(false.B)
      c.clock.step(5) 

      // --- 1. 启动线程 ---
      println("[Test] Starting Thread...")
      c.io.startTrigger.poke(true.B)
      c.clock.step()
      c.io.startTrigger.poke(false.B)
      c.io.isRunning.expect(true.B)

      // --- 2. 执行 INIT (Step 0) -> 自动跳到 Step 1 ---
      c.io.pcValue.expect(0.U)
      c.clock.step()
      // 此时 INIT 执行完，counter=0，PC 应该变为 1
      c.io.counterValue.expect(0.U)
      c.io.pcValue.expect(1.U)
      println("[Test] INIT done. Now at Step 1 (WAIT_A).")

      // --- 3. 测试 waitCondition (Step 1) ---
      // flagA 是 false，应该卡住
      println("[Test] Testing waitCondition (Holding)...")
      c.clock.step()
      c.io.pcValue.expect(1.U) // 还在 1
      c.clock.step()
      c.io.pcValue.expect(1.U) // 还在 1

      // 释放 flagA
      println("[Test] Releasing flagA...")
      c.io.flagA.poke(true.B)
      c.clock.step()
      // 这一拍 waitCondition 满足，PC 应该 +1 变成 2
      c.io.pcValue.expect(2.U)
      c.io.flagA.poke(false.B) // 关掉，不影响后续
      println("[Test] Wait passed. Now at Step 2 (ADD_ACT).")

      // --- 4. 测试 waitAndAct + Jump 循环 (Step 2 & 3) ---
      // 目标：循环 3 次 (0 -> 1 -> 2 -> 3)
      
      // Loop 1: Counter 0 -> 1
      // flagB 目前是 false，应该卡在 Step 2，且 counter 不变
      c.clock.step() 
      c.io.pcValue.expect(2.U)
      c.io.counterValue.expect(0.U) 

      // 开启 flagB，执行加法
      println("[Test] Loop 1: Activating flagB")
      c.io.flagB.poke(true.B)
      c.clock.step()
      // Step 2 执行完毕: Counter 变 1, PC 变 3
      c.io.counterValue.expect(1.U)
      c.io.pcValue.expect(3.U) 
      
      // Step 3 (Check): 1 < 3, 应该跳回 2
      c.clock.step()
      c.io.pcValue.expect(2.U) // Jumped back!
      println("[Test] Loop 1: Jumped back to 2.")

      // Loop 2: Counter 1 -> 2
      // Step 2 (Again)
      c.clock.step() // flagB 依然是 true (因为我们没关，模拟连续信号)
      c.io.counterValue.expect(2.U)
      c.io.pcValue.expect(3.U)

      // Step 3 (Again): 2 < 3, 跳回 2
      c.clock.step()
      c.io.pcValue.expect(2.U)
      println("[Test] Loop 2: Jumped back to 2.")

      // Loop 3: Counter 2 -> 3
      // Step 2 (Again)
      c.clock.step()
      c.io.counterValue.expect(3.U)
      c.io.pcValue.expect(3.U)

      // Step 3 (Final): 3 < 3 is False. 应该继续到 4
      c.clock.step()
      c.io.pcValue.expect(4.U)
      println("[Test] Loop 3: Condition Met! Moved to 4.")

      // --- 5. Exit ---
      // 此时在 Step 4
      c.io.isDone.expect(true.B) // 组合逻辑立刻看到 done
      c.clock.step()
      c.io.isRunning.expect(false.B)
      c.io.isDone.expect(false.B)

      println("=== Complex Test Passed ===\n")
    }
  }
}