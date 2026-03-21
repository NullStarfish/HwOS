package HwOS.kernel.thread

import HwOS.kernel.thread.step.EdgeAction
import scala.collection.mutable.ArrayBuffer

final class DebugStepRecord(
    val name: String,
    val allocatedPC: Int,
    val isStandalone: Boolean,
    val threadCallStack: Seq[String],
    val invokedCalls: ArrayBuffer[Seq[String]],
    val effectKinds: Seq[String] = Seq.empty,
)

private[kernel] trait ThreadDebugApi {
  def debugSteps: Seq[DebugStepRecord]
  def hasReturningStep: Boolean
  def debugStepActions: Map[String, Seq[EdgeAction]]
  def recordAtomicCallSnapshot(snapshot: Seq[String]): Unit
  def markExplicitReturnEncountered(): Unit
  def registerCallSiteReturnRequirement(callSite: HwOS.kernel.system.CallProtocolContext.CallSiteSnapshot): Unit
  def markCallSiteReturned(callSiteId: Int): Unit
}
