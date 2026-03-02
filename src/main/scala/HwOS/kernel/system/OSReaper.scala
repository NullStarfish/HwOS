package HwOS.kernel.system

import chisel3._
import HwOS.kernel.lang.HwOSLanguage._
import HwOS.kernel.process.HwProcess
import HwOS.kernel.thread.HardwareThread

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
