package HwOS.kernel.examples

import chisel3._
import HwOS.kernel.HwOSLanguage._
import HwOS.kernel.memory.ExportCapability
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{GrantAbi, Kernel, SysCall}

class ExportDeclareDemoProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  val worker = createThread("Worker")
  private val sharedCounter = RegInit(0.U(8.W))

  override def entry(): Unit = {
    export("demo.counter", sharedCounter, ExportCapability.ReadWrite)

    worker.entry {
      val counter = worker.declare[UInt]("demo.counter", ExportCapability.ReadWrite)
      worker.Step("Bump") {
        counter.write := (counter.read + 1.U).asUInt
      }
      worker.Step("Finish") {}
      SysCall.Call(SysCall.Return())
    }
  }
}

class ExportDeclareDemoModule extends Module {
  val io = IO(new Bundle {
    val done = Output(Bool())
    val counter = Output(UInt(8.W))
  })

  io.done := DontCare
  io.counter := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    own(io.done)
    own(io.counter)
    val proc = spawn(new ExportDeclareDemoProcess("Demo"))
    val daemon = createLogic("Daemon")

    override def entry(): Unit = {
      grant(io.done, daemon, GrantAbi.LevelDrivenWire)
      grant(io.counter, daemon, GrantAbi.LevelDrivenWire)
      grantLifecycle(proc.worker, daemon)

      daemon.run {
        when(!proc.worker.active && !proc.worker.done) {
          SysCall.Call(SysCall.start(proc.worker))
        }
        val counter = daemon.declare[UInt]("demo.counter", ExportCapability.Read)
        io.done  :=  proc.worker.done
        io.counter  :=  counter.read
      }
    }
  }

  Init.build()
}
