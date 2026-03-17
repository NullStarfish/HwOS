package HwOS.kernel.system

import chisel3._
import HwOS.kernel.lang.HwOSLanguage._
import HwOS.kernel.process.HwProcess
import HwOS.kernel.thread.{HardwareAgent, HardwareThread}

private[kernel] object OSReaper {
  def shouldReclaimLease(thread: HardwareThread, lease: HwOS.kernel.context.HwLease): Boolean = true

  def reclaimThread(thread: HardwareThread, agent: HardwareAgent): Unit = {
    thread.grant(thread.ctx.kernelKillSignal, agent, GrantAbi.LevelDrivenWire)
    thread.ctx.activeLeases.foreach { lease =>
      if (shouldReclaimLease(thread, lease)) {
        when(lease.isActive) {
          lease.forceReclaim(agent)
        }
      }
    }
    thread.ctx.kernelKillSignal <==! false.B
  }
}

class OSReaperProcess(monitoredThreads: Seq[HardwareThread], localName: String)(implicit kernel: Kernel)
    extends HwProcess(localName, overrideDebug = Some(false)) {

  override def entry(): Unit = {
    val daemon = createLogic("Daemon")
    monitoredThreads.foreach { thread =>
      thread.grant(thread.ctx.kernelKillSignal, daemon, GrantAbi.LevelDrivenWire)
    }

    daemon.run {
      monitoredThreads.foreach { thread =>
        when(thread.ctx.kernelKillSignal) {
          OSReaper.reclaimThread(thread, daemon)
        }
      }
    }
  }
}
