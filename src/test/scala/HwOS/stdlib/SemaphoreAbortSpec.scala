package HwOS.stdlib

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel._
import HwOS.kernel.HwOSLanguage._
import HwOS.kernel.system.OSReaper
import HwOS.stdlib.sync._

class SemaphoreAbortTestProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {

  val main     = createThread("Main")
  val victim   = createThread("Victim")
  val observer = createThread("Observer")
  val contextGate = createReaperManagedLogic("ContextGate")

  val permit = spawn(new SemaphoreProcess(maxClients = 2, initialCount = 1, "Permit"))

  val observerSuccess = (RegInit(false.B))
  private var victimLeaseOpt: Option[permit.SemaphoreLease] = None

  override def entry(): Unit = {
    victim.entry {
      val lease = SysCall.Call(permit.RequestLease(0))
      victimLeaseOpt = Some(lease)
      victim.Step("AcquirePermit") {
        SysCall.Call(lease.Acquire())
      }
      victim.Step("InfiniteLoop") {
        victim.waitCondition(false.B)
      }
      victim.Step("NeverReachesHere") {
        SysCall.Call(lease.Release())
      }
      SysCall.Return()
    }

    contextGate.registerReclaimEntry(victim, victim.active) { agent =>
      victimLeaseOpt.foreach(_.forceReclaim(agent))
      OSReaper.reclaimThread(victim, Seq.empty, agent)
    }

    observer.entry {
      val lease = SysCall.Call(permit.RequestLease(1))
      observer.Step("TryAcquirePermit") {
        SysCall.Call(lease.Acquire())
      }
      observer.Step("Success") {
        observerSuccess  :=  true.B
        SysCall.Call(lease.Release())
      }
      SysCall.Return()
    }

    main.entry {
      main.Step("StartVictim") {
        SysCall.Call(SysCall.start(victim))
      }
      main.Step("WaitAWhile1") {}
      main.Step("WaitAWhile2") {}

      main.Step("KillAndRescue") {
        OSReaper.kill(contextGate, main)
        SysCall.Call(SysCall.start(observer))
      }

      main.Step("WaitObserver") {
        main.waitCondition(observer.done)
        when(observer.done) { main.hijack(main.Next) }
      }
      main.Step("Finish") {
      }
      SysCall.Return()
    }
  }
}

class SemaphoreAbortModule extends Module {
  val io = IO(new Bundle {
    val start   = Input(Bool())
    val success = Output(Bool())
    val done    = Output(Bool())
  })
  io.success := DontCare
  io.done := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    (io.success); (io.done)
    val testProc = spawn(new SemaphoreAbortTestProcess("TestProc"))
    val daemon = createLogic("Daemon")

    override def entry(): Unit = {
      daemon.run {
        when(io.start) { SysCall.Call(SysCall.start(testProc.main)) }
        io.success  :=  testProc.observerSuccess
        io.done     :=  testProc.main.done
      }
    }
  }
  Init.build()
}

class SemaphoreAbortSpec extends AnyFlatSpec {
  "A single-permit semaphore" should "automatically release its permit when the holding context is killed by OSReaper" in {
    simulate(new SemaphoreAbortModule) { c =>
      println("\n=== HwOS single-permit semaphore reclaim test ===")
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

      val success = c.io.success.peek().litValue == 1
      assert(success, "Observer failed to acquire permit! Single-permit semaphore was NOT reclaimed by OSReaper.")

      println("=== Test Passed: single-permit semaphore reclaim works perfectly! ===\n")
    }
  }
}
