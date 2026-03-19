package HwOS.stdlib.sync

import chisel3._
import chisel3.util._
import HwOS.kernel.function.HwInline
import HwOS.kernel.lang.HwOSLanguage._
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{Kernel, OSReaper, SysCall}
import HwOS.kernel.thread.{HardwareAgent, HardwareThread}

class BaseScoreboardProcess(val resourceCount: Int, val zeroAlwaysFree: Boolean = false, localName: String)(implicit kernel: Kernel)
    extends HwProcess(localName) {
  private val busyTable = RegInit(VecInit(Seq.fill(resourceCount)(false.B)))

  override def entry(): Unit = {}

  def ReadBusy(addr: UInt): HwInline[Bool] = HwInline.stateless("BaseSB_ReadBusy") { _ =>
    if (zeroAlwaysFree) Mux(addr === 0.U, false.B, busyTable(addr)) else busyTable(addr)
  }

  def SetBusy(addr: UInt): HwInline[Unit] = HwInline.stateless("BaseSB_SetBusy") { _ =>
    if (zeroAlwaysFree) {
      when(addr =/= 0.U) {
        busyTable.at(addr) := true.B
      }
    } else {
      busyTable.at(addr) := true.B
    }
    ()
  }

  def ClearBusy(addr: UInt): HwInline[Unit] = HwInline.stateless("BaseSB_ClearBusy") { _ =>
    busyTable.at(addr) := false.B
    ()
  }
}

class SemaphoreScoreboardProcess(
    val resourceCount: Int,
    val maxClients: Int,
    val maxUpdateSlots: Int,
    val zeroAlwaysFree: Boolean = false,
    localName: String,
)(implicit kernel: Kernel)
    extends HwProcess(localName) {

  private val base = spawn(new BaseScoreboardProcess(resourceCount, zeroAlwaysFree, "Base"))
  private val updateSlots = spawn(new SemaphoreProcess(maxClients, initialCount = maxUpdateSlots, "UpdateSlots"))

  override def entry(): Unit = {}

  def ReadBusy(addr: UInt): HwInline[Bool] = base.ReadBusy(addr)

  // Protocol handle for scoreboard update slot ownership.
  class BusyPort(val clientId: Int) {
    def Acquire(): HwInline[Unit] = HwInline.atomic(s"AcquireBusySlot_$clientId") { _ =>
      val slotLease = SysCall.Call(updateSlots.RequestLease(clientId))
      SysCall.Call(slotLease.Acquire())
    }

    def SetBusy(addr: UInt): HwInline[Unit] = HwInline.stateless(s"SetBusy_$clientId") { _ =>
      SysCall.Call(base.SetBusy(addr))
    }

    def ClearBusy(addr: UInt): HwInline[Unit] = HwInline.stateless(s"ClearBusy_$clientId") { _ =>
      SysCall.Call(base.ClearBusy(addr))
    }

    def Release(): HwInline[Unit] = HwInline.stateless(s"ReleaseBusySlot_$clientId") { _ =>
      val slotLease = SysCall.Call(updateSlots.RequestLease(clientId))
      SysCall.Call(slotLease.Release())
    }
  }

  private val busyPorts = Array.tabulate(maxClients)(i => new BusyPort(i))

  def RequestBusyPort(clientId: Int): HwInline[BusyPort] = HwInline.bindings(s"ReqSemaSBPort_$clientId") { _ =>
    busyPorts(clientId)
  }
}

// Scoreboard service: control protocol wrapper over busy-table + update-slot services.
class ScoreboardProcess(val resourceCount: Int, val maxConcurrentPorts: Int, val zeroAlwaysFree: Boolean = false, localName: String)(
    implicit kernel: Kernel
) extends HwProcess(localName) {
  private val reaper = createReaperManagedLogic("Reaper")
  private val semaScoreboard =
    spawn(new SemaphoreScoreboardProcess(resourceCount, maxConcurrentPorts, maxConcurrentPorts, zeroAlwaysFree, "Sema"))

  override def entry(): Unit = {}

  // Facade-first API
  def Guard(addr: UInt): HwInline[Bool] = HwInline.atomic("Guard") { t =>
    val isBusy = SysCall.Call(semaScoreboard.ReadBusy(addr))
    t.waitCondition(!isBusy)
    when(!isBusy) { t.hijack(t.Next) }
    !isBusy
  }

  // Protocol handle: resource-usage lease, not the old HwLease model.
  class ScoreboardLease(val portIdx: Int) {
    val isReserved = RegInit(false.B)
    val reservedAddr = RegInit(0.U(log2Ceil(resourceCount).W))

    (isReserved)
    (reservedAddr)

    def isActive: Bool = isReserved

    def Reserve(addr: UInt): HwInline[Unit] = HwInline.atomic(s"Reserve_$portIdx") { t =>
      val busyPort = SysCall.Call(semaScoreboard.RequestBusyPort(portIdx))
      val isBusy = SysCall.Call(semaScoreboard.ReadBusy(addr))
      val alreadyReserved = isReserved && (reservedAddr === addr)
      val canReserve = alreadyReserved || !isBusy
      t.waitCondition(canReserve)

      when(!alreadyReserved && !isBusy) {
        SysCall.Call(busyPort.Acquire())
        SysCall.Call(busyPort.SetBusy(addr))
        isReserved := true.B
        reservedAddr := addr
      }
      reaper.registerReclaimEntry(t, isActive) { agent =>
        forceReclaim(agent)
      }
    }

    def Release(): HwInline[Unit] = HwInline.stateless(s"Release_$portIdx") { _ =>
      val busyPort = SysCall.Call(semaScoreboard.RequestBusyPort(portIdx))
      when(isReserved) {
        SysCall.Call(busyPort.ClearBusy(reservedAddr))
        SysCall.Call(busyPort.Release())
        isReserved := false.B
      }
    }

    def forceReclaim(agent: HardwareAgent): Unit = {
      val busyPort = SysCall.Call(semaScoreboard.RequestBusyPort(portIdx))
      when(isReserved) {
        SysCall.Call(busyPort.ClearBusy(reservedAddr))
        SysCall.Call(busyPort.Release())
        OSReaper.forceAssign(isReserved, false.B)
      }
    }
  }

  private val leases = Array.tabulate(maxConcurrentPorts)(i => new ScoreboardLease(i))

  // Protocol-level API
  def RequestLease(portIdx: Int): HwInline[ScoreboardLease] = HwInline.bindings(s"ReqSBLease_$portIdx") { _ =>
    leases(portIdx)
  }

  // Facade-first API
  def WaitUntilFree(addr: UInt): HwInline[Unit] = HwInline.atomic("WaitUntilFree") { _ =>
    SysCall.Call(Guard(addr))
    ()
  }

  def Reserve(portIdx: Int, addr: UInt): HwInline[Unit] = HwInline.atomic(s"SBReserve_$portIdx") { _ =>
    val lease = SysCall.Call(RequestLease(portIdx))
    SysCall.Call(lease.Reserve(addr))
  }

  def Release(portIdx: Int): HwInline[Unit] = HwInline.stateless(s"SBRelease_$portIdx") { _ =>
    val lease = SysCall.Call(RequestLease(portIdx))
    SysCall.Call(lease.Release())
  }

  def WithReservation(portIdx: Int, addr: UInt)(body: HardwareThread => Unit): HwInline[Unit] =
    HwInline.thread(s"WithReservation_$portIdx") { t =>
      t.Step(s"ReserveBusy_$portIdx") {
        SysCall.Call(Reserve(portIdx, addr))
      }
      body(t)
      t.Step(s"ReleaseBusy_$portIdx") {
        SysCall.Call(Release(portIdx))
      }
      ()
    }
}
