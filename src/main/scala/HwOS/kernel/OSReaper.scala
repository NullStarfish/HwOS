package HwOS.kernel

import HwOS.kernel.HwOSLanguage._
import chisel3._

class OSReaperProcess(monitoredThreads: Seq[HardwareThread], localName: String)(implicit kernel: Kernel)
    extends HwProcess(localName, overrideDebug = Some(false)) {

  override def entry(): Unit = {
    val daemon = createLogic("Daemon")
    monitoredThreads.foreach { thread =>
      thread.grant(thread.ctx.kernelKillSignal, daemon)
    }

    daemon.run {
      monitoredThreads.foreach { thread =>
        when(thread.ctx.kernelKillSignal) {
          thread.ctx.activeLeases.foreach { lease =>
            when(lease.isActive) {
              lease.forceReclaim(daemon)
            }
          }
          thread.ctx.kernelKillSignal <==! false.B
        }
      }
    }
  }
}
