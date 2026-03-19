package HwOS.kernel.system

import chisel3._
import HwOS.kernel.context.HwContextEntity
import HwOS.kernel.process.HwProcess
import HwOS.kernel.thread.{HardwareAgent, HardwareLogic, HardwareThread}
import scala.collection.mutable.ArrayBuffer

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
    localReaperEntries += new ReclaimEntry(target, isActive, forceReclaim)
  }
}

private[HwOS] object OSReaper {
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

  private[kernel] def requestReclaimForTarget(target: HwContextEntity, managedEntities: Seq[OSReaperManaged]): Unit = {
    managedEntities.foreach { managed =>
      val hasMatchingEntries = managed.reaperEntries.exists(entry => shouldReclaimEntry(target, entry))
      when(hasMatchingEntries.B) {
        requestManagedKill(managed)
      }
    }
  }

  private[kernel] def requestManagedKill(target: OSReaperManaged): Unit = {
    forceAssign(target.reaperKillLatch, true.B)
  }

  private[HwOS] def kill(target: HwContextEntity, agent: HardwareAgent): Unit = {
    target match {
      case managed: OSReaperManaged =>
        requestManagedKill(managed)
      case _ =>
        throw new Exception(s"[HwOS] OSReaper.kill requires OSReaper-managed context service: '${target.name}' does not opt in.")
    }
  }

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
    requestReclaimForTarget(thread, managedEntities)
    thread.reset()
  }
}

class OSReaperProcess(
    managedEntities: Seq[OSReaperManaged],
    localName: String,
)(implicit kernel: Kernel)
    extends HwProcess(localName, overrideDebug = Some(false)) {

  override def entry(): Unit = {
    val daemon = createLogic("Daemon")

    daemon.run {
      managedEntities.foreach { managed =>
        when(managed.reaperKillLatch && managed.reaperIsActive) {
          OSReaper.reclaimManaged(managed, daemon)
        }
      }
    }
  }
}
