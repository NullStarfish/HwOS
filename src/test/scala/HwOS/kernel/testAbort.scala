package HwOS.kernel

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel._
import HwOS.kernel.HwOSLanguage._ // 引入 <== 安全赋值操作符

class AbortSafetyTestModule extends Module {
  val io = IO(new Bundle {
    val start     = Input(Bool())
    val doAbort   = Input(Bool())
    val outSignal = Output(UInt(32.W))
    val isAlive   = Output(Bool())
  })

  val kernel = new Kernel()

  class TestProc(k: Kernel) extends HwProcess("Proc", debugEnable = true, parent = None)(k) {
    val manager = createThread("Manager")
    val worker  = createThread("Worker")

    // 使用 Wire 作为外部信号，以观测当拍的组合逻辑坍缩
    val extSignal = Wire(UInt(32.W))
    extSignal := 0.U // 默认挂起值为 0

    // 1. 数据所有权声明与授权
    manager.own(extSignal)
    manager.grant(extSignal, worker)
    
    // 2. 生命周期授权：允许 Manager 跨线程处决 Worker
    worker.grantLifecycle(manager)

    override def entry(): Unit = {
      manager.entry {
        manager.Step("StartWorker") {
           worker.start()
        }
        manager.Step("Monitor") {
           // 当外部输入 doAbort 拉高时，执行处决
           manager.waitAndAct(io.doAbort) {
              SysCall.kill(worker)
              manager.exit()
           }
        }
      }

      worker.entry {
        worker.Step("Working") {
           // 这里的 <== 会被展开为：
           // extSignal := Mux(worker.active && !worker.abortWire, 42.U, 0.U)
           extSignal <== 42.U
           
           // 故意卡死，模拟长周期的独占输出
           worker.waitCondition(false.B) 
        }
        worker.Step("exit") {
          worker.exit()
        }
      }
    }
  }
  
  val proc = new TestProc(kernel)
  proc.build()

  when(io.start) { proc.manager.start() }

  io.outSignal := proc.extSignal
  io.isAlive   := proc.worker.active
}

class AbortSafetySpec extends AnyFlatSpec {
  "HwOS <==" should "immediately pull down external signals in the same cycle upon abort" in {
    simulate(new AbortSafetyTestModule) { c =>
      println("\n=== Abort Safety & Signal Collapse Test ===")
      
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      // 1. 发送启动脉冲
      c.io.start.poke(true.B)
      c.clock.step()
      c.io.start.poke(false.B)

      // 2. 等待 Worker 进入 Working 状态 (大概需要 2 拍)
      c.clock.step(2)
      
      // 此时 Worker 应该活跃，且输出 42
      println(s"[Phase 1] Worker isAlive: ${c.io.isAlive.peek().litValue}")
      println(s"[Phase 1] ExtSignal Out:  ${c.io.outSignal.peek().litValue}")
      c.io.isAlive.expect(true.B)
      c.io.outSignal.expect(42.U)

      // 3. 触发 Abort (纯组合逻辑观察)
      println("\n[Phase 2] Asserting doAbort (Combinational Test)...")
      c.io.doAbort.poke(true.B)
      
      // 【核心断言】我们不跨越时钟边界 (不执行 c.clock.step())，直接 peek
      // 由于 Manager 监控到了 doAbort 并执行 SysCall.kill(worker)
      // worker.abortWire 会在当拍立即被拉高。
      // <== 操作符里的 Mux(t.active && !t.abortWire, ...) 会立刻将输出切断回 0。
      val signalDuringAbort = c.io.outSignal.peek().litValue
      println(s"[Phase 2] ExtSignal Output in the SAME CYCLE: $signalDuringAbort")
      
      c.io.outSignal.expect(0.U) // 必须立刻跌回 0，不能有哪怕一拍的毛刺

      // 4. 步进时钟，观察寄存器状态的结算
      c.clock.step()
      c.io.doAbort.poke(false.B)
      
      println(s"\n[Phase 3] Next Cycle isAlive: ${c.io.isAlive.peek().litValue}")
      println(s"[Phase 3] Next Cycle ExtSignal Out: ${c.io.outSignal.peek().litValue}")
      
      // Worker 应该已经彻底下线，信号继续保持为 0
      c.io.isAlive.expect(false.B)
      c.io.outSignal.expect(0.U)

      println("=== Test Passed: Strict Cycle-Level Abort Safety Verified ===\n")
    }
  }
}