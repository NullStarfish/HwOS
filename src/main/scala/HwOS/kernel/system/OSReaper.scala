package HwOS.kernel.system

import chisel3._
import HwOS.kernel.context.HwContextEntity
import HwOS.kernel.process.HwProcess
import HwOS.kernel.thread.{HardwareAgent, HardwareLogic, HardwareThread}
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable

private[HwOS] final class ReclaimEntry(
    val target: HwContextEntity,
    val isActive: Bool,
    val forceReclaim: HardwareAgent => Unit,
)

private[HwOS] trait OSReaperManaged { self: HwContextEntity =>
  def reaperIsActive: Bool
  def reaperKillLatch: Bool
  def reaperEntries: Seq[ReclaimEntry]
}

private[kernel] final class OSReaperManagedLogic(
    name: String,
    owner: HwProcess,
    debugEnable: Boolean = true,
) extends HardwareLogic(name, owner, debugEnable)
    with OSReaperManaged {
  private val localReaperEntries = ArrayBuffer[ReclaimEntry]()
  private val localKillLatch = RegInit(false.B)

  override def reaperIsActive: Bool = true.B
  override def reaperKillLatch: Bool = localKillLatch
  override def reaperEntries: Seq[ReclaimEntry] = localReaperEntries.toSeq

  def registerReclaimEntry(target: HwContextEntity, isActive: Bool)(forceReclaim: HardwareAgent => Unit): Unit = {
    target match {
      case thread: HardwareThread => OSReaper.registerManagedThread(thread)
      case _ =>
    }
    localReaperEntries += new ReclaimEntry(target, isActive, forceReclaim)
  }
}

private[HwOS] object OSReaper {
  private val threadKillLatches = mutable.HashMap.empty[HardwareThread, Bool]
  private val managedThreads = mutable.HashSet.empty[HardwareThread]

  private[kernel] def attachThreadKillLatch(thread: HardwareThread, killLatch: Bool): Unit = {
    threadKillLatches(thread) = killLatch
  }

  private[kernel] def registerManagedThread(thread: HardwareThread): Unit = {
    managedThreads += thread
  }

  private[kernel] def usesManagedThreadKill(thread: HardwareThread): Boolean =
    managedThreads.contains(thread)

  private[kernel] def threadKillLatchOf(thread: HardwareThread): Bool =
    threadKillLatches.getOrElse(
      thread,
      throw new Exception(s"[HwOS] Missing OSReaper thread-kill latch for '${thread.name}'."),
    )

  private[kernel] def requestThreadKill(thread: HardwareThread): Unit = {
    forceAssign(threadKillLatchOf(thread), true.B)
  }

  def gatedAssign[T <: Data](enable: Bool, lhs: T, rhs: T): Unit = {
    when(enable) {
      lhs := rhs
    }
  }

  def forceAssign[T <: Data](lhs: T, rhs: T): Unit = {
    lhs := rhs
  }

  def shouldReclaimEntry(target: HwContextEntity, entry: ReclaimEntry): Boolean =
    (entry.target eq target) || (target.isInstanceOf[OSReaperManaged] && entry.target.eq(target))

  def reclaimManaged(target: OSReaperManaged, agent: HardwareAgent): Unit = {
    target.reaperEntries.foreach { entry =>
      when(entry.isActive) {
        entry.forceReclaim(agent)
      }
    }
    forceAssign(target.reaperKillLatch, false.B)
  }

  def reclaimThread(thread: HardwareThread, managedEntities: Seq[OSReaperManaged], agent: HardwareAgent): Unit = {
    managedEntities.foreach { managed =>
      managed.reaperEntries.foreach { entry =>
        if (shouldReclaimEntry(thread, entry)) {
          when(entry.isActive) {
            entry.forceReclaim(agent)
          }
        }
      }
    }
    forceAssign(threadKillLatchOf(thread), false.B)
    thread.reset()
  }
}

class OSReaperProcess(
    monitoredThreads: Seq[HardwareThread],
    managedEntities: Seq[OSReaperManaged],
    localName: String,
)(implicit kernel: Kernel)
    extends HwProcess(localName, overrideDebug = Some(false)) {

  override def entry(): Unit = {
    val daemon = createLogic("Daemon")

    daemon.run {
      monitoredThreads.foreach { thread =>
        when(OSReaper.threadKillLatchOf(thread)) {
          OSReaper.reclaimThread(thread, managedEntities, daemon)
        }
      }

      managedEntities.foreach { managed =>
        when(managed.reaperKillLatch && managed.reaperIsActive) {
          OSReaper.reclaimManaged(managed, daemon)
        }
      }
    }
  }
}
