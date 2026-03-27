package HwOS.stdlib

import HwOS.kernel._
import HwOS.kernel.HwOSLanguage._
import HwOS.stdlib.sync._
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class ScoreboardResetHookProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  val victim = createThread("Victim")
  val observer = createThread("Observer")
  val main = createThread("Main")
  val scoreboard = spawn(new ScoreboardProcess(resourceCount = 8, maxConcurrentPorts = 2, zeroAlwaysFree = false, "Scoreboard"))
  val observerSuccess = RegInit(false.B)

  override def entry(): Unit = {
    victim.entry {
      val lease = SysCall.Inline(scoreboard.RequestLease(0))
      victim.registerReset {
        lease.forceReclaim()
      }
      victim.Step("Reserve") {
        SysCall.Inline(lease.Reserve(3.U))
      }
      victim.Step("Hold") {
        victim.waitCondition(false.B)
      }
      SysCall.Return()
    }

    observer.entry {
      val lease = SysCall.Inline(scoreboard.RequestLease(1))
      observer.Step("ReserveSameAddr") {
        SysCall.Inline(lease.Reserve(3.U))
      }
      observer.Step("Release") {
        observerSuccess := true.B
        SysCall.Inline(lease.Release())
      }
      SysCall.Return()
    }

    main.entry {
      main.Step("StartVictim") {
        SysCall.Inline(SysCall.start(victim))
      }
      main.Step("Wait") {}
      main.Step("KillVictim") {
        SysCall.Inline(SysCall.kill(victim))
      }
      main.Step("StartObserver") {
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

class ScoreboardResetHookModule extends Module {
  val io = IO(new Bundle {
    val success = Output(Bool())
    val done = Output(Bool())
  })
  io.success := DontCare
  io.done := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    val proc = spawn(new ScoreboardResetHookProcess("Proc"))
    val daemon = createLogic("Daemon")

    override def entry(): Unit = {
      daemon.run {
        when(!proc.main.active && !proc.main.done) {
          SysCall.Inline(SysCall.start(proc.main))
        }
        io.success := proc.observerSuccess
        io.done := proc.main.done
      }
    }
  }

  Init.build()
}

class OrderedWindowResetHookProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  val victim = createThread("Victim")
  val observer = createThread("Observer")
  val main = createThread("Main")
  val window = spawn(new OrderedWindowProcess(maxClients = 2, maxInFlight = 4, "Window"))
  val observerCommitted = RegInit(false.B)

  override def entry(): Unit = {
    victim.entry {
      val lease = SysCall.Inline(window.RequestLease(0))
      victim.registerReset {
        lease.forceReclaim()
      }
      victim.Step("Reserve") {
        SysCall.Inline(lease.Reserve())
      }
      victim.Step("Hold") {
        victim.waitCondition(false.B)
      }
      SysCall.Return()
    }

    observer.entry {
      val lease = SysCall.Inline(window.RequestLease(1))
      observer.Step("Reserve") {
        SysCall.Inline(lease.Reserve())
      }
      observer.Step("Commit") {
        SysCall.Inline(lease.Commit())
      }
      observer.Step("WaitCommitted") {
        observer.waitCondition(SysCall.Inline(lease.Committed()))
      }
      observer.Step("Mark") {
        observerCommitted := true.B
      }
      SysCall.Return()
    }

    main.entry {
      main.Step("StartVictim") {
        SysCall.Inline(SysCall.start(victim))
      }
      main.Step("StartObserver") {
        SysCall.Inline(SysCall.start(observer))
      }
      main.Step("Wait") {}
      main.Step("KillVictim") {
        SysCall.Inline(SysCall.kill(victim))
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

class OrderedWindowResetHookModule extends Module {
  val io = IO(new Bundle {
    val committed = Output(Bool())
    val done = Output(Bool())
  })
  io.committed := DontCare
  io.done := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    val proc = spawn(new OrderedWindowResetHookProcess("Proc"))
    val daemon = createLogic("Daemon")

    override def entry(): Unit = {
      daemon.run {
        when(!proc.main.active && !proc.main.done) {
          SysCall.Inline(SysCall.start(proc.main))
        }
        io.committed := proc.observerCommitted
        io.done := proc.main.done
      }
    }
  }

  Init.build()
}

class LeaseResetHookSpec extends AnyFlatSpec {
  "ScoreboardLease.forceReclaim" should "clear a reservation when wired into thread reset hooks" in {
    simulate(new ScoreboardResetHookModule) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      var cycles = 0
      while (c.io.done.peek().litValue == 0 && cycles < 30) {
        c.clock.step()
        cycles += 1
      }

      c.io.done.expect(true.B)
      c.io.success.expect(true.B)
    }
  }

  "WindowLease.forceReclaim" should "advance ordered commit when wired into thread reset hooks" in {
    simulate(new OrderedWindowResetHookModule) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      var cycles = 0
      while (c.io.done.peek().litValue == 0 && cycles < 40) {
        c.clock.step()
        cycles += 1
      }

      c.io.done.expect(true.B)
      c.io.committed.expect(true.B)
    }
  }
}
