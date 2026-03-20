package HwOS.stdlib.sync

import chisel3._
import chisel3.util._
import HwOS.kernel.function.HwInline
import HwOS.kernel.lang.HwOSLanguage._
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{Kernel, OSReaper, SysCall}
import HwOS.kernel.thread.{HardwareAgent, HardwareThread}

// Single service/component for permit arbitration.
// initialCount = 1 means single-permit mutual exclusion.
class SemaphoreProcess(val maxClients: Int, val initialCount: Int, localName: String)(implicit kernel: Kernel)
    extends HwProcess(localName) {
  private val reaper = createReaperManagedLogic("Reaper")

  private val count = RegInit(initialCount.U(32.W))
  private val acquires = WireInit(VecInit(Seq.fill(maxClients)(false.B)))
  private val releases = WireInit(VecInit(Seq.fill(maxClients)(false.B)))

  for (i <- 0 until maxClients) {
    (acquires(i))
    (releases(i))
  }

  private val anyAcquire = acquires.reduce(_ || _)
  private val winnerAcqIdx = PriorityEncoder(acquires)
  private val totalRelease = PopCount(releases)

  override def entry(): Unit = {
    val main = createLogic("Main")
    main.run {
      when(anyAcquire && (count > 0.U)) {
        count := count + totalRelease - 1.U
      }.otherwise {
        count := count + totalRelease
      }
    }
  }

  // Protocol handle: a resource-usage lease, not the old HwLease model.
  class SemaphoreLease(val id: Int) {
    val isHeld = RegInit(false.B)
    (isHeld)

    def isActive: Bool = isHeld

    def Acquire(): HwInline[Unit] = HwInline.atomic(s"Acquire_$id") { t =>
      acquires(id) := true.B
      val canAcquire = isHeld || ((count > 0.U) && (winnerAcqIdx === id.U))
      t.waitCondition(canAcquire)

      when(!isHeld && canAcquire) {
        isHeld := true.B
      }
      reaper.registerReclaimEntry(t, isActive) { agent =>
        forceReclaim(agent)
      }
    }

    def Release(): HwInline[Unit] = HwInline.stateless(s"Release_$id") { _ =>
      when(isHeld) {
        releases(id) := true.B
        isHeld := false.B
      }
    }

    def forceReclaim(agent: HardwareAgent): Unit = {
      OSReaper.forceAssign(releases(id), true.B)
      OSReaper.forceAssign(isHeld, false.B)
    }
  }

  private val leases = Array.tabulate(maxClients)(i => new SemaphoreLease(i))
  private[sync] def directLease(id: Int): SemaphoreLease = leases(id)

  // Protocol-level API
  def RequestLease(id: Int): HwInline[SemaphoreLease] = HwInline.bindings(s"ReqSemLease_$id") { _ =>
    leases(id)
  }

  // Facade-first API
  def Available(): HwInline[Bool] = HwInline.stateless("SemAvailable") { _ =>
    count > 0.U
  }

  def AcquirePermit(id: Int): HwInline[Unit] = HwInline.atomic(s"AcquirePermit_$id") { _ =>
    val lease = SysCall.Inline(RequestLease(id))
    SysCall.Inline(lease.Acquire())
  }

  def ReleasePermit(id: Int): HwInline[Unit] = HwInline.stateless(s"ReleasePermit_$id") { _ =>
    val lease = SysCall.Inline(RequestLease(id))
    SysCall.Inline(lease.Release())
  }

  def WithPermit(id: Int)(body: HardwareThread => Unit): HwInline[Unit] = HwInline.thread(s"WithPermit_$id") { t =>
    t.Step(s"AcquirePermit_$id") {
      SysCall.Inline(AcquirePermit(id))
    }
    body(t)
    t.Step(s"ReleasePermit_$id") {
      SysCall.Inline(ReleasePermit(id))
    }
    ()
  }
}
