package HwOS.kernel

import chisel3._
import chisel3.util._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel._ 

// 顶层测试模块
class ForkAndB2BModule extends Module {
  val io = IO(new Bundle {
    val start       = Input(Bool())
    val forkResult  = Output(UInt(32.W))
    val b2bCount    = Output(UInt(32.W))
    val workerActive= Output(Bool()) // 用于观察背靠背时的 active 状态
    val allDone     = Output(Bool())
  })

  val kernel = new Kernel()

  // 定义一个包含测试逻辑的 Process
  class TestProcess(k: Kernel) extends HwProcess("TestProc", debugEnable = true, parent = None)(k) {
    
    // ==========================================
    // 场景 1: Fork 测试用的寄存器
    // ==========================================
    val sharedReg = RegInit(0.U(32.W))
    
    // ==========================================
    // 场景 2: Back-to-Back 测试用的 Worker
    // ==========================================
    val b2bCounter = RegInit(0.U(32.W))
    val worker = createThread("Worker")
    
    // Worker 逻辑：耗时 1 个周期，然后退出
    worker.entry {
      worker.Step("DoWork") {
        // 模拟工作
        worker.exit()
      }
    }

    // ==========================================
    // 主线程逻辑
    // ==========================================
    val main = createThread("Main")
    
    override def entry(): Unit = {
      main.entry {
        // --- 阶段 1: 测试 Fork ---
        // 我们在这里 fork 一个子线程，让它把 sharedReg + 10
        // 注意：fork 返回的是 child 的句柄
        val child = main.fork("Adder") { t => 
          t.Step("Add10") {
            sharedReg := sharedReg + 10.U
            t.exit()
          }
        }
        
        // 主线程阻塞等待子线程完成
        // 使用你代码中的 waitCondition 和 done (doneWire || doneReg)
        main.Step("WaitChild") {
          child.start()
          main.waitCondition(child.done)
        }

        main.Step("CheckFork") {
          // 此时 child 应该已经结束，sharedReg 应该是 10
          // 如果逻辑正确，代码会继续执行
        }

        // --- 阶段 2: 测试 Back-to-Back Restart ---
        // 我们将连续启动 worker 3 次
        // 第一次启动
        main.Step("StartWorker_1") {
          worker.start()
        }

        // 这里的逻辑是：等待 Worker 完成，一旦完成，
        // 在【同一周期】再次拉高 worker.start()，实现无缝重启
        main.Step("B2B_Loop") {
          // 只要 worker 完成了 (doneWire 或者是 doneReg)，我们就重启它
          // 这里的 b2bCounter 记录重启了多少次
          
          when (worker.done) {
             // 只有当次数小于 3 时才重启
             when (b2bCounter < 3.U) {
               worker.start() // <--- 关键：在 done 的当拍 start
               b2bCounter := b2bCounter + 1.U
             }
          }

          // 等待重启计数达到 3
          main.waitCondition(b2bCounter === 3.U && worker.done)
        }
        
        main.Step("Finish") {
          main.exit()
        }
      }
    }
    
    // 绑定 start 信号
    when(io.start) { main.start() }
  }

  val proc = new TestProcess(kernel)
  proc.build()

  io.forkResult   := proc.sharedReg
  io.b2bCount     := proc.b2bCounter
  io.workerActive := proc.worker.active
  io.allDone      := proc.main.done
}

// ScalaTest 测试用例
class ForkAndB2BTest extends AnyFlatSpec {
  "HwOS" should "support Fork/Join and Back-to-Back restarts" in {
    simulate(new ForkAndB2BModule) { c =>
      println("\n=== Starting Fork & B2B Test ===")
      
      // 初始化
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)
      c.io.start.poke(true.B)
      c.clock.step()
      c.io.start.poke(false.B) // Start 只是一个脉冲

      // -------------------------------------------------------
      // 1. Fork 阶段
      // -------------------------------------------------------
      // Main Start -> Fork Child -> Child Run -> Child Done -> Main Wakeup
      // 这大约需要 3-4 个周期
      var cycles = 0
      while (c.io.forkResult.peek().litValue != 10 && cycles < 10) {
        c.clock.step()
        cycles += 1
      }
      println(s"[Fork Test] Cycles: $cycles, Result: ${c.io.forkResult.peek().litValue}")
      c.io.forkResult.expect(10.U) // 验证 Fork 的子线程是否正确写入了数据

      // -------------------------------------------------------
      // 2. Back-to-Back 阶段
      // -------------------------------------------------------
      // 此时 Main 应该进入了 B2B_Loop
      // 我们需要观察 workerActive 信号。
      // 如果背靠背启动成功，workerActive 应该在该阶段一直保持为 True (1)，中间没有变成 0
      
      println("[B2B Test] Monitoring Worker Active State...")
      val activeTrace = scala.collection.mutable.ArrayBuffer[Boolean]()
      
      // 继续运行直到测试结束
      while (c.io.allDone.peek().litValue == 0 && cycles < 20) {
        // 记录 Worker 的 active 状态
        // 注意：在 B2B 重启的那一拍，active 应该保持为 1
        activeTrace += (c.io.workerActive.peek().litValue == 1)
        c.clock.step()
        cycles += 1
      }
      
      println(s"[B2B Test] Final Count: ${c.io.b2bCount.peek().litValue}")
      c.io.b2bCount.expect(3.U)
      
      // 关键验证：
      // 在 Worker 运行期间（B2B 循环期间），Active 信号应该始终为高，不应出现低电平。
      // 因为 doneWire 和 startWire 在同一拍有效，activeReg 在 entry 逻辑中会被保持为 true。
      // c.io.workerActive.expect(true.B) // 理想情况下一直为真
      
      println(s"Worker Active Trace: ${activeTrace.mkString(", ")}")
      
      // 简单的启发式检查：如果全是 true，说明没有气泡
      // (忽略初始启动前的 false)
      val runningPhase = activeTrace.dropWhile(_ == false)
      assert(runningPhase.forall(_ == true), "Worker Active signal dropped to 0 during Back-to-Back! Pipeline bubble detected.")

      println("=== Test Passed: Fork Logic & Back-to-Back Restart OK ===")
    }
  }
}