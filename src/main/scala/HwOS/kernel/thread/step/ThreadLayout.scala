package HwOS.kernel.thread.step

import chisel3._
import scala.collection.mutable
import HwOS.kernel.system.{RuntimeContext, VirtualStepRecord}
import HwOS.kernel.thread.StepRef

private[kernel] object ThreadLayout {
  final case class StepLayout(name: String, address: Int, standalone: Boolean)

  final class LayoutState {
    var runtimeContext: Option[RuntimeContext] = None
    var jumpTargets = Map.empty[String, UInt]
    var currentLoweringStep: Int = -1
    var currentEntryStep: Int = -1
    var currentDebugRecord: Option[VirtualStepRecord] = None
    val suppressedStandalone = mutable.Set[Int]()
  }

  def resolveStepIndex(irState: ThreadIR.IRState, stepName: String): Int = {
    val idx = irState.program.labels.indexOf(stepName)
    if (idx < 0) {
      throw new Exception(s"[Thread] Unknown step '$stepName' in program '${irState.programName}'.")
    }
    idx
  }

  def resolveStepRef(
      irState: ThreadIR.IRState,
      layoutState: LayoutState,
      target: StepRef,
  ): Int = target match {
    case StepRef.NamedStepRef(name) =>
      resolveStepIndex(irState, name)
    case StepRef.NextStepRef =>
      val current = layoutState.currentLoweringStep
      if (current < 0) {
        throw new Exception(s"[Thread] Next step reference requires an active lowering step in program '${irState.programName}'.")
      }
      val next = current + 1
      if (next >= irState.program.steps.length) {
        val currentName = irState.program.steps(current).name
        throw new Exception(s"[Thread] Step '$currentName' tried to reference non-existent next step.")
      }
      next
  }

  def lowerStepAt(
      irState: ThreadIR.IRState,
      layoutState: LayoutState,
      index: Int,
  ): Unit = {
    val saveLowering = layoutState.currentLoweringStep
    val saveEntry = layoutState.currentEntryStep
    val saveRecord = layoutState.currentDebugRecord

    if (layoutState.currentEntryStep < 0) {
      layoutState.currentEntryStep = index
    }
    layoutState.currentLoweringStep = index
    layoutState.currentDebugRecord = Some(irState.program.steps(index))
    irState.program.steps(index).block()
    layoutState.currentDebugRecord = saveRecord
    layoutState.currentLoweringStep = saveLowering
    layoutState.currentEntryStep = saveEntry
  }

  def lowerStepNamed(
      irState: ThreadIR.IRState,
      layoutState: LayoutState,
      stepName: String,
  ): Unit = lowerStepAt(irState, layoutState, resolveStepIndex(irState, stepName))

  def standaloneIndices(irState: ThreadIR.IRState, layoutState: LayoutState): Vector[Int] = {
    irState.program.steps.indices.filterNot(layoutState.suppressedStandalone.contains).toVector
  }

  def nextStandaloneIndex(
      irState: ThreadIR.IRState,
      layoutState: LayoutState,
      after: Int,
  ): Option[Int] = {
    ((after + 1) until irState.program.steps.length).find(idx => !layoutState.suppressedStandalone.contains(idx))
  }

  def suppressStandalone(
      irState: ThreadIR.IRState,
      layoutState: LayoutState,
      index: Int,
  ): Unit = {
    layoutState.suppressedStandalone += index
    irState.program.steps(index).loweredStandalone = false
  }

  def assignStandaloneLayout(
      irState: ThreadIR.IRState,
      layoutState: LayoutState,
      runtime: RuntimeContext,
  ): Unit = {
    val standalone = standaloneIndices(irState, layoutState)
    layoutState.jumpTargets = standalone.iterator.map { index =>
      val step = irState.program.steps(index)
      val addr = runtime.cursor.segment.addressOf(step.name)
      step.allocatedAddress = addr
      step.loweredStandalone = true
      step.name -> runtime.cursor.addressOf(step.name)
    }.toMap

    for ((step, index) <- irState.program.steps.zipWithIndex if layoutState.suppressedStandalone.contains(index)) {
      step.loweredStandalone = false
      step.allocatedAddress = -1
    }
  }

  def validateJumpTargets(irState: ThreadIR.IRState): Unit = {
    for (targetIndex <- irState.pendingJumpTargetIndices) {
      if (targetIndex < 0 || targetIndex >= irState.program.steps.length) {
        throw new Exception(s"[Thread] Invalid jump target index '$targetIndex' in program '${irState.programName}'.")
      }
      val targetStep = irState.program.steps(targetIndex)
      if (!targetStep.loweredStandalone || targetStep.allocatedAddress < 0) {
        throw new Exception(
          s"[Thread] jump target '${targetStep.name}' in program '${irState.programName}' has no standalone code slot.",
        )
      }
    }
  }
}
