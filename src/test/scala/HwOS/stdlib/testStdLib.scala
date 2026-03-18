package HwOS.stdlib

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel._
import HwOS.kernel.HwOSLanguage._
import HwOS.stdlib.sync.MutexProcess

class SyncProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  val main    = createThread("Main")
  val worker1 = createThread("Worker1")
  val worker2 = createThread("Worker2")

  // --- 声明物理资源与授权 ---
  val sharedCounter = RegInit(0.U(32.W))
  this.own(sharedCounter)
  this.grant(sharedCounter, worker1)
  this.grant(sharedCounter, worker2)



  // ===================================================================
  // 核心亮点：孵化 Stdlib 并发原语 (作为子进程)
  // 注意：spawn 内部已经自动将 mutex 和 wg 的访问权 grant 给 SyncProcess 了！
  // 构造函数完整传递 n, d, p, kr 以维护进程树
  // ===================================================================
  val mutex = spawn( new sync.MutexProcess(maxClients = 2, "Mutex") )
  val wg    = spawn( new sync.WaitGroupProcess(maxClients = 3, "WG") )

  override def entry(): Unit = {
    
    

    // Worker 模板逻辑：抢锁 -> 执行关键区 -> 释放并汇报 -> 退出
    def workerLogic(t: HardwareThread, lockId: Int, wgId: Int): Unit = {
      t.Step("AcquireLock") {
        SysCall.Call(mutex.Lock(lockId))
      }
      t.Step("CriticalSection") {
        sharedCounter  :=  sharedCounter + 10.U
      }
      t.Step("ReleaseAndDone") {
        SysCall.Call(mutex.Unlock(lockId))
        SysCall.Call(wg.Done(wgId))
      }
      SysCall.Call(SysCall.Return())
    }

    // 分配 ID 并注入逻辑
    worker1.entry { workerLogic(worker1, lockId = 0, wgId = 1) }
    worker2.entry { workerLogic(worker2, lockId = 1, wgId = 2) }
    worker1.grantLifecycle(worker1, main)
    worker2.grantLifecycle(worker2, main)

    main.entry {
      main.Step("Init") {
        // Main 使用 WG 的 ID 0
        SysCall.Call(wg.Add(0, 2.U)) 
        SysCall.Call(SysCall.start(worker1))
        SysCall.Call(SysCall.start(worker2))
      }
      main.Step("WaitWorkers") {
        SysCall.Call(wg.Wait())
      }
      main.Step("Finish") {
      }
      SysCall.Call(SysCall.Return())
    }
  }
}


// ---------------------------------------------------------
// 硬件顶层模块
// ---------------------------------------------------------
class SyncIntegrationModule extends Module {
  val io = IO(new Bundle {
    val start      = Input(Bool())
    val finalCount = Output(UInt(32.W))
    val allDone    = Output(Bool())
  })

  io.finalCount := DontCare; io.allDone := DontCare

  implicit val kernel: Kernel = new Kernel()


  object Init extends HwProcess("Init") {
    this.own(io.finalCount); this.own(io.allDone)


    val sync = spawn(new SyncProcess("Sync"))

    val daemon = createLogic("daemon")
    grant(io.finalCount, daemon, GrantAbi.LevelDrivenWire); grant(io.allDone, daemon, GrantAbi.LevelDrivenWire)
    grantLifecycle(sync.main, daemon)
    override def entry(): Unit = {
      daemon.run {
        when(io.start) {SysCall.Call(SysCall.start(sync.main))}
        io.finalCount  :=  sync.sharedCounter
        io.allDone  :=  sync.main.done
      }
    }
  }

  Init.build()

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
