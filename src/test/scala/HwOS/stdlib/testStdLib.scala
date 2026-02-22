package HwOS.stdlib

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel._
import HwOS.kernel.HwOSLanguage._

class SyncIntegrationModule extends Module {
  val io = IO(new Bundle {
    val start      = Input(Bool())
    val finalCount = Output(UInt(32.W))
    val allDone    = Output(Bool())
  })

  val kernel = new Kernel()

  class SyncProcess(k: Kernel) extends HwProcess("SyncProc", debugEnable = true, parent = None)(k) {
    val main    = createThread("Main")
    val worker1 = createThread("Worker1")
    val worker2 = createThread("Worker2")

    // --- 声明并发原语 (实例化硬件对象) ---
    // myLock 供 worker1(ID 0) 和 worker2(ID 1) 使用
    val myLock = new sync.Mutex(maxClients = 2)
    // myWG 供 main(ID 0), worker1(ID 1), worker2(ID 2) 使用
    val myWG   = new sync.WaitGroup(maxClients = 3)

    // 业务数据，利用我们框架的 <== 保护
    val sharedCounter = RegInit(0.U(32.W))
    this.own(sharedCounter)
    this.grant(sharedCounter, worker1)
    this.grant(sharedCounter, worker2)

    worker1.grantLifecycle(main)
    worker2.grantLifecycle(main)

    override def entry(): Unit = {
      
      main.entry {
        main.Step("Init") {
          SysCall.Call(myWG.Add(0, 2.U)) // Main 使用 ID 0 进行 Add
          SysCall.start(worker1)
          SysCall.start(worker2)
        }
        main.Step("WaitWorkers") {
          SysCall.Call(myWG.Wait()) // 阻塞等
        }
        main.Step("Finish") {
          main.exit()
        }
      }

      // Worker 闭包逻辑，绑定特定的硬件 ID
      def workerLogic(t: HardwareThread, lockId: Int, wgId: Int): Unit = {
        t.Step("AcquireLock") {
          SysCall.Call(myLock.Lock(lockId))
        }
        t.Step("CriticalSection") {
          sharedCounter <== sharedCounter + 10.U
        }
        t.Step("ReleaseAndDone") {
          SysCall.Call(myLock.Unlock(lockId))
          SysCall.Call(myWG.Done(wgId))
          t.exit()
        }
      }

      // 分配 ID: worker1 占 lock 0 / wg 1
      worker1.entry { workerLogic(worker1, lockId = 0, wgId = 1) }
      // 分配 ID: worker2 占 lock 1 / wg 2
      worker2.entry { workerLogic(worker2, lockId = 1, wgId = 2) }
    }
  }

  val proc = new SyncProcess(kernel)
  proc.build()

  when(io.start) { SysCall.start(proc.main) }

  io.finalCount := proc.sharedCounter
  io.allDone    := proc.main.done
}

class StdlibSpec extends AnyFlatSpec {
  "HwOS stdlib.sync" should "correctly orchestrate WaitGroup and Mutex across multiple threads" in {
    simulate(new SyncIntegrationModule) { c =>
      println("\n=== Stdlib Sync Integration Test ===")
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      c.io.start.poke(true.B)
      c.clock.step()
      c.io.start.poke(false.B)

      var cycles = 0
      while (c.io.allDone.peek().litValue == 0 && cycles < 30) {
        c.clock.step()
        cycles += 1
      }

      println(s"Test finished in $cycles cycles.")
      val count = c.io.finalCount.peek().litValue
      println(s"Final Counter Value: $count (Expected: 20)")

      c.io.allDone.expect(true.B)
      c.io.finalCount.expect(20.U) 
      println("=== Test Passed: Mutex and WaitGroup work perfectly ===\n")
    }
  }
}