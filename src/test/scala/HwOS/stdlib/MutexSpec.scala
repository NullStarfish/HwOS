package HwOS.stdlib

import HwOS.kernel._
import HwOS.kernel.HwOSLanguage._
import HwOS.stdlib.sync._
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class MutexProcessDemo(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  val holder = createThread("Holder")
  val waiter = createThread("Waiter")
  val main = createThread("Main")
  val mutex = spawn(new MutexProcess(maxClients = 2, "Lock"))

  val holderEntered = RegInit(false.B)
  val waiterEntered = RegInit(false.B)

  override def entry(): Unit = {
    holder.entry {
      val lease = SysCall.Inline(mutex.RequestLease(0))
      holder.Step("Acquire") {
        SysCall.Inline(lease.Acquire())
      }
      holder.Step("Mark") {
        holderEntered := true.B
      }
      holder.Step("Hold1") {}
      holder.Step("Hold2") {}
      holder.Step("Release") {
        SysCall.Inline(lease.Release())
      }
      SysCall.Return()
    }

    waiter.entry {
      val lease = SysCall.Inline(mutex.RequestLease(1))
      waiter.Step("Acquire") {
        SysCall.Inline(lease.Acquire())
      }
      waiter.Step("Mark") {
        waiterEntered := true.B
      }
      waiter.Step("Release") {
        SysCall.Inline(lease.Release())
      }
      SysCall.Return()
    }

    main.entry {
      main.Step("StartHolder") {
        SysCall.Inline(SysCall.start(holder))
      }
      main.Step("StartWaiter") {
        SysCall.Inline(SysCall.start(waiter))
      }
      main.Step("WaitWaiter") {
        main.waitCondition(waiter.done)
        when(waiter.done) { main.hijack(main.Next) }
      }
      main.Step("Finish") {}
      SysCall.Return()
    }
  }
}

class MutexModule extends Module {
  val io = IO(new Bundle {
    val holderEntered = Output(Bool())
    val waiterEntered = Output(Bool())
    val done = Output(Bool())
  })

  io.holderEntered := DontCare
  io.waiterEntered := DontCare
  io.done := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    val proc = spawn(new MutexProcessDemo("Proc"))
    val daemon = createLogic("Daemon")

    override def entry(): Unit = {
      daemon.run {
        when(!proc.main.active && !proc.main.done) {
          SysCall.Inline(SysCall.start(proc.main))
        }
        io.holderEntered := proc.holderEntered
        io.waiterEntered := proc.waiterEntered
        io.done := proc.main.done
      }
    }
  }

  Init.build()
}

class MutexSpec extends AnyFlatSpec {
  "A mutex" should "allow the second thread to enter only after the first releases" in {
    simulate(new MutexModule) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      var preCycles = 0
      while (c.io.holderEntered.peek().litValue == 0 && preCycles < 20) {
        c.clock.step()
        preCycles += 1
      }

      c.io.holderEntered.expect(true.B)
      c.io.waiterEntered.expect(false.B)

      var cycles = 0
      while (c.io.done.peek().litValue == 0 && cycles < 30) {
        c.clock.step()
        cycles += 1
      }

      c.io.done.expect(true.B)
      c.io.holderEntered.expect(true.B)
      c.io.waiterEntered.expect(true.B)
    }
  }
}
