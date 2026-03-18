package HwOS.kernel

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel.HwOSLanguage._

class UnifiedThreadKernelProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  val value = this.own(RegInit(0.U(8.W)))
  val worker = createThread(name = "PersistentWorker")

  override def entry(): Unit = {
    this.grant(value, worker)

    worker.entry {
      worker.Step("Bump") {
        value  :=  7.U
      }
      worker.Step("Finish") {
      }
      SysCall.Call(SysCall.Return())
    }
  }
}

class UnifiedThreadKernelModule extends Module {
  val io = IO(new Bundle {
    val value = Output(UInt(8.W))
    val done = Output(Bool())
  })

  io.value := DontCare
  io.done := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    this.own(io.value)
    this.own(io.done)

    val proc = spawn(new UnifiedThreadKernelProcess("BackendProc"))
    val daemon = createLogic("Daemon")

    override def entry(): Unit = {
      this.grant(io.value, daemon, GrantAbi.LevelDrivenWire)
      this.grant(io.done, daemon, GrantAbi.LevelDrivenWire)
      this.grantLifecycle(proc.worker, daemon)

      daemon.run {
        when(!proc.worker.active && !proc.worker.done) {
          SysCall.Call(SysCall.start(proc.worker))
        }
        io.value  :=  proc.value
        io.done  :=  proc.worker.done
      }
    }
  }

  Init.build()
}

class UnifiedThreadKernelSpec extends AnyFlatSpec {
  "HardwareThread unified kernel" should "run user threads through the single thread-core path" in {
    simulate(new UnifiedThreadKernelModule) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      var cycles = 0
      while (c.io.done.peek().litValue == 0 && cycles < 10) {
        c.clock.step()
        cycles += 1
      }

      c.io.done.expect(true.B)
      c.io.value.expect(7.U)
    }
  }
}
