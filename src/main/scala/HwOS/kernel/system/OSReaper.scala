package HwOS.kernel.system

import chisel3._
import HwOS.kernel.context.HwContextEntity
import HwOS.kernel.process.HwProcess
import HwOS.kernel.thread.{HardwareAgent, HardwareThread}

private[HwOS] object OSReaper {
  def gatedAssign[T <: Data](enable: Bool, lhs: T, rhs: T): Unit = {
    when(enable) {
      lhs := rhs
    }
  }

  def forceAssign[T <: Data](lhs: T, rhs: T): Unit = {
    lhs := rhs
  }

  def shouldReclaimEntry(target: HwContextEntity, entry: Kernel#ReaperEntry): Boolean = true

  def reclaimEntity(target: HwContextEntity, agent: HardwareAgent): Unit = {
    agent.kernel.reaperEntriesOf(target).foreach { entry =>
      if (shouldReclaimEntry(target, entry)) {
        when(entry.isActive) {
          entry.forceReclaim(agent)
        }
      }
    }
    forceAssign(agent.kernel.contextKillLatch(target), false.B)
  }

  def reclaimThread(thread: HardwareThread, agent: HardwareAgent): Unit = {
    reclaimEntity(thread, agent)
    forceAssign(agent.kernel.threadKillLatch(thread), false.B)
  }
}

class OSReaperProcess(monitoredThreads: Seq[HardwareThread], localName: String)(implicit kernel: Kernel)
    extends HwProcess(localName, overrideDebug = Some(false)) {

  override def entry(): Unit = {
    val daemon = createLogic("Daemon")

    daemon.run {
      monitoredThreads.foreach { thread =>
        when(kernel.threadKillLatch(thread)) {
          OSReaper.reclaimThread(thread, daemon)
        }
      }

      kernel.allEntities.foreach { entity =>
        when(kernel.contextKillLatch(entity)) {
          OSReaper.reclaimEntity(entity, daemon)
        }
      }
    }
  }
}
