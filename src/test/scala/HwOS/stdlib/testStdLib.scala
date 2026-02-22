package HwOS.stdlib

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel._
import HwOS.kernel.HwOSLanguage._

// ---------------------------------------------------------
// 硬件顶层模块
// ---------------------------------------------------------
class SyncIntegrationModule extends Module {
  val io = IO(new Bundle {
    val start      = Input(Bool())
    val finalCount = Output(UInt(32.W))
    val allDone    = Output(Bool())
  })

  val kernel = new Kernel()

  // 我们的主业务系统
  class SyncProcess(k: Kernel) extends HwProcess("SyncProc", debugEnable = true, parent = None)(k) {
    val main    = createThread("Main")
    val worker1 = createThread("Worker1")
    val worker2 = createThread("Worker2")

    // --- 声明物理资源与授权 ---
    val sharedCounter = RegInit(0.U(32.W))
    this.own(sharedCounter)
    this.grant(sharedCounter, worker1)
    this.grant(sharedCounter, worker2)

    worker1.grantLifecycle(main)
    worker2.grantLifecycle(main)

    // ===================================================================
    // 核心亮点：孵化 Stdlib 并发原语 (作为子进程)
    // 注意：spawn 内部已经自动将 mutex 和 wg 的访问权 grant 给 SyncProcess 了！
    // 构造函数完整传递 n, d, p, kr 以维护进程树
    // ===================================================================
    val mutex = spawn("MyMutex") { (n, d, p, kr) => new sync.MutexProcess(maxClients = 2, n, d, p, kr) }
    val wg    = spawn("MyWG")    { (n, d, p, kr) => new sync.WaitGroupProcess(maxClients = 3, n, d, p, kr) }

    override def entry(): Unit = {
      
      main.entry {
        main.Step("Init") {
          // Main 使用 WG 的 ID 0
          SysCall.Call(wg.Add(0, 2.U)) 
          SysCall.start(worker1)
          SysCall.start(worker2)
        }
        main.Step("WaitWorkers") {
          SysCall.Call(wg.Wait())
        }
        main.Step("Finish") {
          main.exit() // 必须显式退出
        }
      }

      // Worker 模板逻辑：抢锁 -> 执行关键区 -> 释放并汇报 -> 退出
      def workerLogic(t: HardwareThread, lockId: Int, wgId: Int): Unit = {
        t.Step("AcquireLock") {
          SysCall.Call(mutex.Lock(lockId))
        }
        t.Step("CriticalSection") {
          sharedCounter <== sharedCounter + 10.U
        }
        t.Step("ReleaseAndDone") {
          SysCall.Call(mutex.Unlock(lockId))
          SysCall.Call(wg.Done(wgId))
          t.exit() // 必须显式退出
        }
      }

      // 分配 ID 并注入逻辑
      worker1.entry { workerLogic(worker1, lockId = 0, wgId = 1) }
      worker2.entry { workerLogic(worker2, lockId = 1, wgId = 2) }
    }
  }

  val proc = new SyncProcess(kernel)
  proc.build()

  // 将顶层 start 信号连接到主线程的启动
  when(io.start) { SysCall.start(proc.main) }

  io.finalCount := proc.sharedCounter
  io.allDone    := proc.main.done
}

// ---------------------------------------------------------
// ScalaTest 驱动程序
// ---------------------------------------------------------
class StdlibSpec extends AnyFlatSpec {
  "HwOS stdlib.sync" should "correctly orchestrate WaitGroup and Mutex across multiple threads without SegFault" in {
    simulate(new SyncIntegrationModule) { c =>
      println("\n=== Stdlib Sync Integration Test ===")
      
      // 复位
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      // 发射启动脉冲
      c.io.start.poke(true.B)
      c.clock.step()
      c.io.start.poke(false.B)

      // 等待执行完成 (超时保险设为 40 拍)
      var cycles = 0
      while (c.io.allDone.peek().litValue == 0 && cycles < 40) {
        c.clock.step()
        cycles += 1
      }

      println(s"Test finished in $cycles cycles.")
      val count = c.io.finalCount.peek().litValue
      println(s"Final Counter Value: $count (Expected: 20)")

      // 验证断言
      c.io.allDone.expect(true.B)
      // worker1 加 10，worker2 加 10，总和必定是 20
      c.io.finalCount.expect(20.U) 

      println("=== Test Passed: Mutex and WaitGroup work perfectly as HwProcess ===\n")
    }
  }
}