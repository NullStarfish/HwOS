package HwOS.stdlib.sync

import chisel3._
import chisel3.util._
import HwOS.kernel.function.HwInline
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{Kernel, OSReaper, SysCall}
import HwOS.kernel.thread.HardwareAgent

class OrderedWindowProcess(val maxClients: Int, val maxInFlight: Int, localName: String)(implicit kernel: Kernel)
    extends HwProcess(localName) {
  private val reaper = createReaperManagedLogic("Reaper")
  private val tokenWidth = log2Ceil((maxInFlight + 1) max 2)

  private case class WindowReq(reserve: Bool, commit: Bool, forceCommit: Bool, reclaim: Bool)
  private case class WindowEntry(active: Bool, commitPending: Bool, token: UInt)

  private val nextIssue = RegInit(0.U(tokenWidth.W))
  private val nextCommit = RegInit(0.U(tokenWidth.W))
  private val inFlight = RegInit(0.U(tokenWidth.W))
  private val reqs = Array.tabulate(maxClients) { _ =>
    WindowReq(WireInit(false.B), WireInit(false.B), WireInit(false.B), WireInit(false.B))
  }
  private val entries = Array.tabulate(maxClients) { _ =>
    WindowEntry(RegInit(false.B), RegInit(false.B), RegInit(0.U(tokenWidth.W)))
  }

  private def reserveRequests: UInt =
    PriorityEncoderOH(VecInit((0 until maxClients).map(i => reqs(i).reserve && !entries(i).active)).asUInt)

  private def forceCommitRequests: UInt =
    PriorityEncoderOH(
      VecInit((0 until maxClients).map(i => reqs(i).forceCommit && entries(i).active && entries(i).commitPending && entries(i).token === nextCommit)).asUInt
    )

  override def entry(): Unit = {
    val daemon = createLogic("OrderDaemon")

    daemon.run {
      val reserveGrantOH = Mux(inFlight < maxInFlight.U, reserveRequests, 0.U(maxClients.W))
      val reserveFire = reserveGrantOH.orR
      val forceCommitOH = forceCommitRequests
      val commitFire = forceCommitOH.orR
      val reclaimOH = VecInit((0 until maxClients).map(i => reqs(i).reclaim && entries(i).active)).asUInt
      val reclaimCount = PopCount(reclaimOH)

      for ((entry, i) <- entries.zipWithIndex) {
        when(reserveGrantOH(i)) {
          entry.active := true.B
          entry.commitPending := false.B
          entry.token := nextIssue
        }
        when(reqs(i).commit && entry.active) {
          entry.commitPending := true.B
        }
        when(forceCommitOH(i) || reclaimOH(i)) {
          entry.active := false.B
          entry.commitPending := false.B
        }
      }

      when(reserveFire) {
        nextIssue := nextIssue + 1.U
      }
      when(commitFire || reclaimOH.asUInt.orR) {
        when(commitFire) {
          nextCommit := nextCommit + 1.U
        }.otherwise {
          val reclaimHead = VecInit((0 until maxClients).map(i => reclaimOH(i) && entries(i).token === nextCommit)).asUInt.orR
          when(reclaimHead) {
            nextCommit := nextCommit + 1.U
          }
        }
        inFlight := inFlight + Mux(reserveFire, 1.U, 0.U) - Mux(commitFire, 1.U, 0.U) - reclaimCount
      }.elsewhen(reserveFire) {
        inFlight := inFlight + 1.U
      }
    }
  }

  // Protocol handle: resource-usage lease, not the old HwLease model.
  class WindowLease(val id: Int) {
    def isActive: Bool = entries(id).active

    def Reserve(): HwInline[Unit] = HwInline.atomic(s"Reserve_$id") { t =>
      reqs(id).reserve := true.B
      val granted = entries(id).active
      t.waitCondition(granted)
      reaper.registerReclaimEntry(t, isActive) { agent =>
        forceReclaim(agent)
      }
    }

    def Commit(): HwInline[Unit] = HwInline.stateless(s"Commit_$id") { _ =>
      reqs(id).commit := true.B
    }

    def Committed(): HwInline[Bool] = HwInline.stateless(s"Committed_$id") { _ =>
      val entry = entries(id)
      entry.active && entry.commitPending && entry.token === nextCommit
    }

    def ForceCommit(): HwInline[Unit] = HwInline.stateless(s"ForceCommit_$id") { _ =>
      reqs(id).forceCommit := true.B
    }

    def forceReclaim(agent: HardwareAgent): Unit = {
      OSReaper.forceAssign(reqs(id).reclaim, true.B)
    }
  }

  private val leases = Array.tabulate(maxClients)(i => new WindowLease(i))

  // Protocol-level API
  def RequestLease(id: Int): HwInline[WindowLease] = HwInline.bindings(s"ReqWindowLease_$id") { _ =>
    leases(id)
  }

  // Facade-first API
  def Reserve(id: Int): HwInline[Unit] = HwInline.atomic(s"WindowReserve_$id") { _ =>
    val lease = SysCall.Call(RequestLease(id))
    SysCall.Call(lease.Reserve())
  }

  def Commit(id: Int): HwInline[Unit] = HwInline.stateless(s"WindowCommit_$id") { _ =>
    val lease = SysCall.Call(RequestLease(id))
    SysCall.Call(lease.Commit())
  }

  def Committed(id: Int): HwInline[Bool] = HwInline.stateless(s"WindowCommitted_$id") { _ =>
    val lease = SysCall.Call(RequestLease(id))
    SysCall.Call(lease.Committed())
  }

  def ForceCommit(id: Int): HwInline[Unit] = HwInline.stateless(s"WindowForceCommit_$id") { _ =>
    val lease = SysCall.Call(RequestLease(id))
    SysCall.Call(lease.ForceCommit())
  }
}
