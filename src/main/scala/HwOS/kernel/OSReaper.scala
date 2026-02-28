package HwOS.kernel

import HwOS.kernel.HwOSLanguage._
import chisel3._

class OSReaperProcess(monitoredThreads: Seq[HardwareThread], localName: String)(implicit kernel: Kernel)
    extends HwProcess(localName, overrideDebug = Some(false)) {

  override def entry(): Unit = {
    val daemon = createLogic("Daemon")

    monitoredThreads.foreach { thread =>
      if (thread.lifecycleReady) {
        thread.grantLifecycle(thread, daemon)
      }
    }

    daemon.run {
      monitoredThreads.foreach { thread =>
        if (thread.lifecycleReady) {
          when(thread.ctx.kernelKillSignal) {
            thread.activeReg <==! false.B
            thread.pc        <==! 0.U
            thread.doneReg   <==! false.B

            thread.ctx.activeLeases.foreach { lease =>
              when(lease.isActive) {
                lease.forceReclaim(daemon)
              }
            }
          }
        }
      }
    }
  }
}
