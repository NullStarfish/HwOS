package HwOS.kernel

import chisel3._
import chisel3.util._
import HwOS.kernel.HwOSLanguage._

sealed trait ThreadPolicy

object ThreadPolicy {
  case object Persistent extends ThreadPolicy
  case object Auto extends ThreadPolicy
  case object InlinePreferred extends ThreadPolicy
}

final class ThreadCapabilities {
  var hasMultiCycleWait: Boolean = false
  var usesExternalStart: Boolean = false
  var usesExternalKill: Boolean = false
  var exposesDone: Boolean = false
  var exposesActive: Boolean = false
  var exposesLifecycle: Boolean = false
  var usesLeaseTracking: Boolean = false
  var usesFork: Boolean = false
  var debugVisible: Boolean = false

  def inlineBlockers: Seq[String] = Seq(
    if (usesExternalStart) Some("external-start") else None,
    if (usesExternalKill) Some("external-kill") else None,
    if (exposesLifecycle) Some("lifecycle-granted") else None,
    if (usesLeaseTracking) Some("lease-tracking") else None,
    if (usesFork) Some("fork") else None,
  ).flatten

  def canUseInlineRuntime: Boolean = inlineBlockers.isEmpty

  def summary: String = {
    val blockers = inlineBlockers
    if (blockers.isEmpty) "inline-candidate"
    else s"persistent-required[${blockers.mkString(",")}]"
  }
}

trait ThreadRuntime {
  def thread: HardwareThread
  def policy: ThreadPolicy

  def supportsExplicitStart: Boolean
  def supportsKill: Boolean
  def supportsLifecycleGrant: Boolean
  def lifecycleReady: Boolean
  def active: Bool
  def done: Bool
  def pc: UInt
  def killSignal: Bool

  def bindContext(): Unit
  def allocatePc(maxSteps: Int): UInt
  def activate(): Unit
  def kill(): Unit
  def exit(): Unit
  def grantLifecycle(target: HwOwner): Unit
}

final class PersistentThreadRuntime(
    val thread: HardwareThread,
    val policy: ThreadPolicy,
) extends ThreadRuntime {
  private val activeReg = thread.own(RegInit(false.B))
  private val doneReg = thread.own(RegInit(false.B))
  private var pcEntity: UInt = _

  override def supportsExplicitStart: Boolean = true
  override def supportsKill: Boolean = true
  override def supportsLifecycleGrant: Boolean = true
  override def lifecycleReady: Boolean = pcEntity != null

  override def active: Bool = activeReg
  override def done: Bool = doneReg

  override def pc: UInt = {
    if (pcEntity == null) {
      thread.agentPrint("Cannot access thread.pc outside of entry!!!")
      throw new Exception("pc not set")
    }
    pcEntity
  }

  override def killSignal: Bool = thread.ctx.kernelKillSignal

  override def bindContext(): Unit = {
    thread.ctx.isActive := activeReg && !killSignal
  }

  override def allocatePc(maxSteps: Int): UInt = {
    if (pcEntity != null) {
      throw new Exception(s"[HwOS] PC allocated twice for thread '${thread.name}'")
    }
    val pcWidth = log2Ceil(maxSteps + 1)
    pcEntity = thread.own(RegInit(0.U(pcWidth.W)))
    pcEntity
  }

  override def activate(): Unit = {
    activeReg <== true.B
    pc <== 0.U
    doneReg <== false.B
    killSignal <== false.B
  }

  override def kill(): Unit = {
    killSignal <== true.B
  }

  override def exit(): Unit = {
    pc <== 0.U
    doneReg <== true.B
    activeReg <== false.B
  }

  override def grantLifecycle(target: HwOwner): Unit = {
    thread.grant(activeReg, target)
    thread.grant(pc, target)
    thread.grant(doneReg, target)
    thread.grant(killSignal, target)
  }
}

final class InlineThreadRuntime(
    val thread: HardwareThread,
    val policy: ThreadPolicy,
) extends ThreadRuntime {
  private var pcEntity: UInt = _
  private var terminalPc: UInt = _

  override def supportsExplicitStart: Boolean = false
  override def supportsKill: Boolean = false
  override def supportsLifecycleGrant: Boolean = false
  override def lifecycleReady: Boolean = pcEntity != null

  override def active: Bool = {
    if (pcEntity == null || terminalPc == null) {
      throw new Exception(s"[HwOS] Inline runtime for '${thread.name}' is not initialized")
    }
    pcEntity =/= terminalPc
  }

  override def done: Bool = {
    if (pcEntity == null || terminalPc == null) {
      throw new Exception(s"[HwOS] Inline runtime for '${thread.name}' is not initialized")
    }
    pcEntity === terminalPc
  }

  override def pc: UInt = {
    if (pcEntity == null) {
      thread.agentPrint("Cannot access thread.pc outside of entry!!!")
      throw new Exception("pc not set")
    }
    pcEntity
  }

  override def killSignal: Bool = false.B

  override def bindContext(): Unit = {
    thread.ctx.isActive := active
  }

  override def allocatePc(maxSteps: Int): UInt = {
    if (pcEntity != null) {
      throw new Exception(s"[HwOS] PC allocated twice for thread '${thread.name}'")
    }
    val pcWidth = log2Ceil(maxSteps + 1)
    pcEntity = thread.own(RegInit(0.U(pcWidth.W)))
    terminalPc = maxSteps.U(pcWidth.W)
    pcEntity
  }

  override def activate(): Unit = {
    throw new Exception(s"[HwOS] Thread '${thread.name}' uses InlineRuntime and cannot be started explicitly.")
  }

  override def kill(): Unit = {
    throw new Exception(s"[HwOS] Thread '${thread.name}' uses InlineRuntime and cannot be killed.")
  }

  override def exit(): Unit = {
    pc <== terminalPc
  }

  override def grantLifecycle(target: HwOwner): Unit = {
    throw new Exception(s"[HwOS] Thread '${thread.name}' uses InlineRuntime and does not expose lifecycle control.")
  }
}
