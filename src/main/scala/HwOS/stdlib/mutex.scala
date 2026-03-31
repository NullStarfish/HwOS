package HwOS.stdlib.sync

import chisel3._
import HwOS.kernel.function.HwInline
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{Kernel, SysCall}
import HwOS.kernel.thread.HardwareThread

// Lightweight single-owner mutual exclusion service.
// Unlike SemaphoreProcess, this keeps only explicit ownership state.
class MutexProcess(val maxClients: Int, localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  require(maxClients > 0, "MutexProcess requires at least one client")

  private val ownerValid = RegInit(false.B)
  private val ownerId = RegInit(0.U(chisel3.util.log2Ceil(maxClients max 2).W))
  private val acquires = WireInit(VecInit(Seq.fill(maxClients)(false.B)))
  private val anyAcquire = acquires.reduce(_ || _)
  private val winnerAcquireId = chisel3.util.PriorityEncoder(acquires)

  override def entry(): Unit = {}

  class MutexLease(val id: Int) {
    def isActive: Bool = ownerValid && ownerId === id.U

    def Acquire(): HwInline[Unit] = HwInline.atomic(s"MutexAcquire_$id") { t =>
      acquires(id) := true.B
      val alreadyOwnedByMe = ownerValid && ownerId === id.U
      val canWinThisCycle = !ownerValid && anyAcquire && winnerAcquireId === id.U
      val canAcquire = alreadyOwnedByMe || canWinThisCycle
      t.waitCondition(canAcquire)

      when(canWinThisCycle) {
        ownerValid := true.B
        ownerId := id.U
      }
    }

    def Release(): HwInline[Unit] = HwInline.stateless(s"MutexRelease_$id") { _ =>
      when(ownerValid && ownerId === id.U) {
        ownerValid := false.B
      }
    }

    def forceReclaim(): Unit = {
      when(ownerValid && ownerId === id.U) {
        ownerValid := false.B
      }
    }
  }

  private val leases = Array.tabulate(maxClients)(i => new MutexLease(i))

  def RequestLease(id: Int): HwInline[MutexLease] = HwInline.bindings(s"ReqMutexLease_$id") { _ =>
    leases(id)
  }

  def Available(): HwInline[Bool] = HwInline.stateless("MutexAvailable") { _ =>
    !ownerValid
  }

  def HeldBy(id: Int): HwInline[Bool] = HwInline.stateless(s"MutexHeldBy_$id") { _ =>
    ownerValid && ownerId === id.U
  }

  def AcquireLock(id: Int): HwInline[Unit] = HwInline.atomic(s"AcquireLock_$id") { _ =>
    val lease = SysCall.Inline(RequestLease(id))
    SysCall.Inline(lease.Acquire())
  }

  def ReleaseLock(id: Int): HwInline[Unit] = HwInline.stateless(s"ReleaseLock_$id") { _ =>
    val lease = SysCall.Inline(RequestLease(id))
    SysCall.Inline(lease.Release())
  }

  def WithLock(id: Int)(body: HardwareThread => Unit): HwInline[Unit] = HwInline.thread(s"WithLock_$id") { t =>
    t.Step(s"AcquireLock_$id") {
      SysCall.Inline(AcquireLock(id))
    }
    body(t)
    t.Step(s"ReleaseLock_$id") {
      SysCall.Inline(ReleaseLock(id))
    }
    ()
  }
}
