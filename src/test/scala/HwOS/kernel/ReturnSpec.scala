package HwOS.kernel

import HwOS.kernel.HwOSLanguage._
import HwOS.kernel.function.HwInline
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.SysCall
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class ReturnProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  val worker = createThread("Worker")
  val out = this.own(RegInit(0.U(8.W)))

  private def inner: HwInline[Unit] = HwInline.thread("Inner") { t =>
    t.Step("InnerWrite") {
      out <== 7.U
    }
    SysCall.Call(SysCall.Return())
    t.Step("InnerDead") {
      out <== 99.U
    }
    ()
  }

  private def innerMost: HwInline[Unit] = HwInline.thread("InnerMost") { t =>
    t.Step("InnerMostWrite") {
      out <== 10.U
    }
    SysCall.Call(SysCall.Return())
    t.Step("InnerMostDead") {
      out <== 77.U
    }
    ()
  }

  private def middle: HwInline[Unit] = HwInline.thread("Middle") { t =>
    t.Step("MiddleWrite") {
      out <== 5.U
    }
    SysCall.Call(innerMost)
    t.Step("MiddleDead") {
      out <== 66.U
    }
    ()
  }

  private def outer: HwInline[Unit] = HwInline.thread("Outer") { t =>
    t.Step("OuterInit") {
      out <== 1.U
    }
    SysCall.Call(inner, "OuterResume")
    t.Step("OuterResume") {
      out <== out + 1.U
    }
    ()
  }

  private def outerNested: HwInline[Unit] = HwInline.thread("OuterNested") { t =>
    t.Step("OuterNestedInit") {
      out <== 2.U
    }
    SysCall.Call(middle, "OuterNestedResume")
    t.Step("OuterNestedResume") {
      out <== out + 1.U
    }
    ()
  }

  override def entry(): Unit = {
    this.grant(out, worker)

    worker.entry {
      SysCall.Call(outer)
      SysCall.Call(outerNested)
      SysCall.Call(SysCall.Return())
    }
  }
}

class ReturnModule extends Module {
  val io = IO(new Bundle {
    val out = Output(UInt(8.W))
    val done = Output(Bool())
  })

  io.out := DontCare
  io.done := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    this.own(io.out)
    this.own(io.done)

    val proc = spawn(new ReturnProcess("ReturnProc"))
    val daemon = createLogic("Daemon")

    override def entry(): Unit = {
      this.grant(io.out, daemon)
      this.grant(io.done, daemon)
      this.grantLifecycle(proc.worker, daemon)

      daemon.run {
        when(!proc.worker.active && !proc.worker.done) {
          SysCall.Call(SysCall.start(proc.worker))
        }
        io.out <== proc.out
        io.done <== proc.worker.done
      }
    }
  }

  Init.build()
}

class ReturnSpec extends AnyFlatSpec {
  "SysCall.Return" should "jump to the explicit caller continuation when Call binds a return target" in {
    simulate(new ReturnModule) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      var cycles = 0
      while (c.io.done.peek().litValue == 0 && cycles < 40) {
        c.clock.step()
        cycles += 1
      }

      c.io.done.expect(true.B)
      c.io.out.expect(11.U)
    }
  }
}
