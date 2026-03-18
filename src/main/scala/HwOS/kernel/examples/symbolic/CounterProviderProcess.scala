package HwOS.kernel.examples.symbolic

import HwOS.kernel.HwOSLanguage._
import HwOS.kernel.memory.ExportCapability
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{Kernel, SysCall}
import HwOS.kernel.thread.HardwareThread
import chisel3._

/**
 * Provider process: exports backing resources, installs the symbolic thread
 * unit, and owns thread lifecycle orchestration.
 */
class CounterProviderProcess(
    localName: String,
    initialCounter: Int = 0,
    limitValue: Int = 3,
)(implicit kernel: Kernel)
    extends HwProcess(localName) {
  private val counterReg = RegInit(initialCounter.U(8.W))
  private val limitReg = RegInit(limitValue.U(8.W))
  private val daemon = createLogic("Daemon")
  lazy val worker: HardwareThread = install(CounterWorkerThreadUnit, "Worker")

  override def entry(): Unit = {
    export(CounterWorkerThreadUnit.CounterSymbol, counterReg, ExportCapability.ReadWrite)
    export(CounterWorkerThreadUnit.LimitSymbol, limitReg, ExportCapability.Read)

    val installedWorker = worker
    daemon.run {
      when(!installedWorker.active && !installedWorker.done) {
        SysCall.Call(SysCall.start(installedWorker))
      }
    }
  }

  def currentCounter: UInt = counterReg
  def configuredLimit: UInt = limitReg
}

class AltCounterProviderProcess(localName: String)(implicit kernel: Kernel)
    extends CounterProviderProcess(
      localName = localName,
      initialCounter = 2,
      limitValue = 5,
    )

class CounterProviderDemoModule extends Module {
  val io = IO(new Bundle {
    val done = Output(Bool())
    val counter = Output(UInt(8.W))
    val limit = Output(UInt(8.W))
  })

  io.done := DontCare
  io.counter := DontCare
  io.limit := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    private val proc = spawn(new CounterProviderProcess("CounterProvider"))
    private val daemon = createLogic("Observer")

    override def entry(): Unit = {
      daemon.run {
        when(!proc.worker.active && !proc.worker.done) {
          SysCall.Call(SysCall.start(proc.worker))
        }
        io.done := proc.worker.done
        io.counter := proc.currentCounter
        io.limit := proc.configuredLimit
      }
    }
  }

  Init.build()
}

class AltCounterProviderDemoModule extends Module {
  val io = IO(new Bundle {
    val done = Output(Bool())
    val counter = Output(UInt(8.W))
    val limit = Output(UInt(8.W))
  })

  io.done := DontCare
  io.counter := DontCare
  io.limit := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    private val proc = spawn(new AltCounterProviderProcess("AltCounterProvider"))
    private val daemon = createLogic("Observer")

    override def entry(): Unit = {
      daemon.run {
        when(!proc.worker.active && !proc.worker.done) {
          SysCall.Call(SysCall.start(proc.worker))
        }
        io.done := proc.worker.done
        io.counter := proc.currentCounter
        io.limit := proc.configuredLimit
      }
    }
  }

  Init.build()
}
