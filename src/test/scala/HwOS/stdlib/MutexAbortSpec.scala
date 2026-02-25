package HwOS.stdlib


import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel._
import HwOS.kernel.HwOSLanguage._
import HwOS.stdlib.sync._
class MutexAbortTestProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  
  val main     = createThread("Main")
  val victim   = createThread("Victim")
  val observer = createThread("Observer")

  // 实例化智能互斥锁
  val mutex = spawn(new MutexProcess(maxClients = 2, "Mutex"))

  // 观测寄存器，证明 Observer 最终拿到了锁
  val observerSuccess = this.own(RegInit(false.B))
  this.grant(observerSuccess, observer)

  override def entry(): Unit = {
    // ---------------------------------------------------------
    // 1. Victim 线程：抢到锁之后，进入死循环 (永远不调 Unlock)
    // ---------------------------------------------------------
    victim.entry {
      // 静态获取专属于自己的锁句柄 (能力/Capability)
      val myLock = SysCall.Call(mutex.RequestLease(0)) 
      
      victim.Step("AcquireLock") {
        SysCall.Call(myLock.Lock()) // 直接通过句柄上锁
      }
      victim.Step("InfiniteLoop") {
        victim.waitCondition(false.B) 
      }
      victim.Step("NeverReachesHere") {
        SysCall.Call(myLock.Unlock())
        victim.exit()
      }
    }

    // ---------------------------------------------------------
    // 2. Observer 线程：尝试抢锁。如果 OS 没回收，它会卡死在这里
    // ---------------------------------------------------------
    observer.entry {
      val myLock = SysCall.Call(mutex.RequestLease(1))

      observer.Step("TryAcquireLock") {
        SysCall.Call(myLock.Lock())
      }
      observer.Step("Success") {
        observerSuccess <== true.B 
        SysCall.Call(myLock.Unlock())
        observer.exit()
      }
    }


    victim.grantLifecycle(victim, main)
    observer.grantLifecycle(observer, main)

    // ---------------------------------------------------------
    // 3. Main 线程：上帝视角，负责启动和强杀
    // ---------------------------------------------------------
    main.entry {
      main.Step("StartVictim") {
        SysCall.start(victim)
      }
      main.Step("WaitAWhile1") {} // 等 Victim 拿稳锁
      main.Step("WaitAWhile2") {} 
      
      main.Step("KillAndRescue") {
        // 【触发神迹】：强杀 Victim！
        // 此时 HwOS 内核会切断 Victim 的 pc，并触发 forceReclaim 释放锁！
        SysCall.kill(victim) 
        SysCall.start(observer) // 立刻让二号机上场
      }
      
      main.Step("WaitObserver") {
        main.waitCondition(observer.done)
        when(observer.done) { main.Next.hijack() }
      }
      main.Step("Finish") {
        main.exit()
      }
    }
  }
}

// 顶层模块包装
class MutexAbortModule extends Module {
  val io = IO(new Bundle {
    val start   = Input(Bool())
    val success = Output(Bool())
    val done    = Output(Bool())
  })
  io.success := DontCare
  io.done := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    this.own(io.success); this.own(io.done)
    val testProc = spawn(new MutexAbortTestProcess("TestProc"))
    val daemon = createLogic("Daemon")
    
    override def entry(): Unit = {
      this.grant(io.success, daemon); this.grant(io.done, daemon)
      this.grantLifecycle(testProc.main, daemon)
      
      daemon.run {
        when(io.start) { SysCall.start(testProc.main) }
        io.success <== testProc.observerSuccess
        io.done    <== testProc.main.done
      }
    }
  }
  Init.build()
}

// ScalaTest 验证
class MutexAbortSpec extends AnyFlatSpec {
  "HwOS Mutex with HwLease" should "automatically release lock when holding thread is killed" in {
    simulate(new MutexAbortModule) { c =>
      println("\n=== HwOS Kernel Lease Reclaim Test ===")
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      c.io.start.poke(true.B)
      c.clock.step()
      c.io.start.poke(false.B)

      var cycles = 0
      while (c.io.done.peek().litValue == 0 && cycles < 30) {
        c.clock.step()
        cycles += 1
      }
      c.clock.step()
      cycles += 1
      
      println(s"Test finished in $cycles cycles.")
      
      // 核心断言：Observer 成功拿到了锁，证明 OS 兜底回收生效！
      val success = c.io.success.peek().litValue == 1
      assert(success, "Observer failed to acquire lock! Mutex was NOT reclaimed by OS.")
      
      println("=== Test Passed: Dying Breath Lease Reclaim works perfectly! ===\n")
    }
  }
}