package HwOS.stdlib

import HwOS.kernel._
import HwOS.kernel.HwOSLanguage._
import HwOS.stdlib.sync._
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class SemaphoreResetHookProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  val main = createThread("Main")
  val victim = createThread("Victim")
  val observer = createThread("Observer")
  val permit = spawn(new SemaphoreProcess(maxClients = 2, initialCount = 1, "Permit"))

  val observerSuccess = RegInit(false.B)

  override def entry(): Unit = {
    victim.entry {
      val lease = SysCall.Inline(permit.RequestLease(0))
      victim.registerReset {
        lease.forceReclaim()
      }

      victim.Step("AcquirePermit") {
        SysCall.Inline(lease.Acquire())
      }
      victim.Step("InfiniteLoop") {
        victim.waitCondition(false.B)
      }
      victim.Step("NeverReachesHere") {
        SysCall.Inline(lease.Release())
      }
      SysCall.Return()
    }

    observer.entry {
      val lease = SysCall.Inline(permit.RequestLease(1))
      observer.Step("TryAcquirePermit") {
        SysCall.Inline(lease.Acquire())
      }
      observer.Step("Success") {
        observerSuccess := true.B
        SysCall.Inline(lease.Release())
      }
      SysCall.Return()
    }

    main.entry {
      main.Step("StartVictim") {
        SysCall.Inline(SysCall.start(victim))
      }
      main.Step("WaitAWhile1") {}
      main.Step("WaitAWhile2") {}
      main.Step("KillVictim") {
        SysCall.Inline(SysCall.kill(victim))
        SysCall.Inline(SysCall.start(observer))
      }
      main.Step("WaitObserver") {
        main.waitCondition(observer.done)
        when(observer.done) { main.hijack(main.Next) }
      }
      main.Step("Finish") {}
      SysCall.Return()
    }
  }
}

class SemaphoreResetHookModule extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    val success = Output(Bool())
    val done = Output(Bool())
  })
  io.success := DontCare
  io.done := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    val testProc = spawn(new SemaphoreResetHookProcess("TestProc"))
    val daemon = createLogic("Daemon")

    override def entry(): Unit = {
      daemon.run {
        when(io.start) { SysCall.Inline(SysCall.start(testProc.main)) }
        io.success := testProc.observerSuccess
        io.done := testProc.main.done
      }
    }
  }

  Init.build()
}

class SemaphoreResetHookSpec extends AnyFlatSpec {
  "A single-permit semaphore" should "release its permit when the holder thread registers forceReclaim on reset" in {
    simulate(new SemaphoreResetHookModule) { c =>
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

      c.io.done.expect(true.B)
      c.io.success.expect(true.B)
    }
  }
}
