package HwOS.kernel

import HwOS.kernel.HwOSLanguage._
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class UnifiedCodeTableProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  val worker = createThread("Worker")
  val out = (RegInit(0.U(8.W)))

  override def entry(): Unit = {
    worker.entry {
      worker.Step("Init") {
        out  :=  1.U
      }
      worker.Step("Dispatch") {
        worker.hijack(worker.Next)
      }
      worker.Step("Merged") {
        out  :=  7.U
        worker.jump(worker.stepRef("Target"))
      }
      worker.Step("Target") {
        out  :=  out + 3.U
      }
      worker.Step("Finish") {
      }
      SysCall.Call(SysCall.Return())
    }
  }
}

class UnifiedCodeTableModule extends Module {
  val io = IO(new Bundle {
    val out = Output(UInt(8.W))
    val done = Output(Bool())
  })

  io.out := DontCare
  io.done := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    (io.out)
    (io.done)

    val proc = spawn(new UnifiedCodeTableProcess("VirtualProc"))
    val daemon = createLogic("Daemon")

    override def entry(): Unit = {
      daemon.run {
        when(!proc.worker.active && !proc.worker.done) {
          SysCall.Call(SysCall.start(proc.worker))
        }
        io.out  :=  proc.out
        io.done  :=  proc.worker.done
      }
    }
  }

  Init.build()
}

class UnifiedCodeTableSpec extends AnyFlatSpec {
  "Unified thread code table" should "execute on kernel-allocated global code addresses" in {
    simulate(new UnifiedCodeTableModule) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      var cycles = 0
      while (c.io.done.peek().litValue == 0 && cycles < 12) {
        c.clock.step()
        cycles += 1
      }

      c.io.done.expect(true.B)
      c.io.out.expect(10.U)
    }
  }
}
