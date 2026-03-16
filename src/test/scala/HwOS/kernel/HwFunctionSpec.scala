package HwOS.kernel

import HwOS.kernel.HwOSLanguage._
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class HwFunctionProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  val worker = createThread("Worker")
  val out = this.own(RegInit(0.U(8.W)))

  private val addOne = HwFunction.thread("AddOne") { t =>
    t.Step("Bump") {
      out <== out + 1.U
    }
    SysCall.Call(SysCall.Return())
    t.Step("Dead") {
      out <== 99.U
    }
    ()
  }

  override def entry(): Unit = {
    this.grant(out, worker)

    worker.entry {
      worker.Step("Init") {
        out <== 0.U
      }

      SysCall.Call(addOne.Invoke("AfterCall"))

      worker.Step("AfterCall") {
        out <== out + 10.U
      }

      SysCall.Call(SysCall.Return())
    }
  }
}

class HwFunctionModule extends Module {
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

    val proc = spawn(new HwFunctionProcess("FnProc"))
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

class HwFunctionSpec extends AnyFlatSpec {
  "HwFunction MVP" should "wrap a callable thread-level body distinct from HwInline" in {
    simulate(new HwFunctionModule) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      var cycles = 0
      while (c.io.done.peek().litValue == 0 && cycles < 30) {
        c.clock.step()
        cycles += 1
      }

      c.io.done.expect(true.B)
      c.io.out.expect(11.U)
    }
  }
}
