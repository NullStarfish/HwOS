package HwOS.stdlib.sync

import chisel3._
import chisel3.util._
import HwOS.kernel.function.HwInline
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{Kernel, SysCall}

class OrderedWindowProcess(val maxClients: Int, val maxInFlight: Int, localName: String)(implicit kernel: Kernel)
    extends HwProcess(localName) {
  private val tokenWidth = log2Ceil((maxInFlight + 1) max 2)

  private case class WindowReq(reserve: Bool, commit: Bool, retire: Bool, reclaim: Bool)
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

  private case class ReserveGrant(valid: Bool, grantOH: UInt)
  private case class WindowProgress(retireOH: UInt, reclaimOH: UInt, retireFire: Bool, reclaimCount: UInt, reclaimTouchesHead: Bool)

  private def SelectReserveGrant(): HwInline[ReserveGrant] = HwInline.stateless("SelectReserveGrant") { _ =>
    val requestOH = PriorityEncoderOH(VecInit((0 until maxClients).map(i => reqs(i).reserve && !entries(i).active)).asUInt)
    val grantOH = Mux(inFlight < maxInFlight.U, requestOH, 0.U(maxClients.W))
    ReserveGrant(grantOH.orR, grantOH)
  }

  private def ComputeWindowProgress(): HwInline[WindowProgress] = HwInline.stateless("ComputeWindowProgress") { _ =>
    val retireOH = PriorityEncoderOH(
      VecInit((0 until maxClients).map(i => reqs(i).retire && entries(i).active && entries(i).commitPending && entries(i).token === nextCommit)).asUInt
    )
    val reclaimOH = VecInit((0 until maxClients).map(i => reqs(i).reclaim && entries(i).active)).asUInt
    val reclaimTouchesHead = VecInit((0 until maxClients).map(i => reclaimOH(i) && entries(i).token === nextCommit)).asUInt.orR
    WindowProgress(retireOH, reclaimOH, retireOH.orR, PopCount(reclaimOH), reclaimTouchesHead)
  }

  private def UpdateEntryState(reserveGrant: ReserveGrant, progress: WindowProgress): HwInline[Unit] =
    HwInline.stateless("UpdateWindowEntryState") { _ =>
      for ((entry, i) <- entries.zipWithIndex) {
        when(reserveGrant.grantOH(i)) {
          entry.active := true.B
          entry.commitPending := false.B
          entry.token := nextIssue
        }
        when(reqs(i).commit && entry.active) {
          entry.commitPending := true.B
        }
        when(progress.retireOH(i) || progress.reclaimOH(i)) {
          entry.active := false.B
          entry.commitPending := false.B
        }
      }
    }

  private def UpdateWindowState(reserveGrant: ReserveGrant, progress: WindowProgress): HwInline[Unit] =
    HwInline.stateless("UpdateWindowState") { _ =>
      val reserveDelta = Mux(reserveGrant.valid, 1.U, 0.U)
      val retireDelta = Mux(progress.retireFire, 1.U, 0.U)

      when(reserveGrant.valid) {
        nextIssue := nextIssue + 1.U
      }
      when(progress.retireFire || progress.reclaimOH.orR) {
        when(progress.retireFire) {
          nextCommit := nextCommit + 1.U
        }.otherwise {
          when(progress.reclaimTouchesHead) {
            nextCommit := nextCommit + 1.U
          }
        }
        inFlight := inFlight + reserveDelta - retireDelta - progress.reclaimCount
      }.elsewhen(reserveGrant.valid) {
        inFlight := inFlight + 1.U
      }
    }

  override def entry(): Unit = {
    val daemon = createLogic("OrderDaemon")

    daemon.run {
      val reserveGrant = SysCall.Inline(SelectReserveGrant())
      val progress = SysCall.Inline(ComputeWindowProgress())

      SysCall.Inline(UpdateEntryState(reserveGrant, progress))
      SysCall.Inline(UpdateWindowState(reserveGrant, progress))
    }
  }

  // Protocol handle: resource-usage lease, not the old HwLease model.
  class WindowLease(val id: Int) {
    def isActive: Bool = entries(id).active

    def Reserve(): HwInline[Unit] = HwInline.atomic(s"Reserve_$id") { t =>
      reqs(id).reserve := true.B
      val granted = entries(id).active
      t.waitCondition(granted)
    }

    def Commit(): HwInline[Unit] = HwInline.stateless(s"Commit_$id") { _ =>
      reqs(id).commit := true.B
    }

    def Committed(): HwInline[Bool] = HwInline.stateless(s"Committed_$id") { _ =>
      val entry = entries(id)
      entry.active && entry.commitPending && entry.token === nextCommit
    }

    def Retire(): HwInline[Unit] = HwInline.stateless(s"Retire_$id") { _ =>
      reqs(id).retire := true.B
    }

    def forceReclaim(): Unit = {
      when(entries(id).active) {
        reqs(id).reclaim := true.B
      }
    }
  }

  private val leases = Array.tabulate(maxClients)(i => new WindowLease(i))

  // Protocol-level API: callers obtain a per-client lease and drive it directly.
  def RequestLease(id: Int): HwInline[WindowLease] = HwInline.bindings(s"ReqWindowLease_$id") { _ =>
    leases(id)
  }
}
