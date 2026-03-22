package HwOS.kernel.thread

import HwOS.kernel.system.CallProtocolContext.CallSiteSnapshot
import HwOS.kernel.thread.step.ControlProgram.CompiledControlProgram
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer

private[kernel] final class ThreadDebugValidation {
  private val requiredReturningCallSites = ArrayBuffer.empty[CallSiteSnapshot]
  private val returnedCallSiteIds = mutable.Set.empty[Int]
  private var explicitReturnEncountered: Boolean = false

  def recordAtomicCallSnapshot(currentRecord: Option[HwOS.kernel.system.VirtualStepRecord], snapshot: Seq[String]): Unit =
    currentRecord.foreach(_.invokedCalls += snapshot)

  def markExplicitReturnEncountered(): Unit = {
    explicitReturnEncountered = true
  }

  def registerCallSiteReturnRequirement(callSite: CallSiteSnapshot): Unit = {
    requiredReturningCallSites += callSite
  }

  def markCallSiteReturned(callSiteId: Int): Unit = {
    returnedCallSiteIds += callSiteId
  }

  def hasReturningStep(compiledProgramOpt: Option[CompiledControlProgram]): Boolean =
    explicitReturnEncountered || compiledProgramOpt.exists(_.hasReturningStep)

  def validateRequiredReturningCallSites(compiledProgram: CompiledControlProgram): Unit = {
    val steps = compiledProgram.steps
    for (callSite <- requiredReturningCallSites) {
      val hasExplicitReturn = steps.exists { step =>
        step.implicitCallSite.exists(_.id == callSite.id) &&
        step.edgeActions.exists(_.isReturning)
      } || returnedCallSiteIds.contains(callSite.id)
      if (callSite.requiresExplicitReturn && !hasExplicitReturn) {
        throw new Exception(
          s"[HwOS] Callable segment '${callSite.name}' was used with SysCall.Call(...) but has no explicit SysCall.Return(). " +
            s"Call-terminated segments must end through SysCall.Return().",
        )
      }
    }
  }
}
