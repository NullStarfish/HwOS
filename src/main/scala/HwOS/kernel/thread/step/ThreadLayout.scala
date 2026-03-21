package HwOS.kernel.thread.step

import chisel3._
import HwOS.kernel.debug.CallStack
import HwOS.kernel.system.{CallProtocolContext, RuntimeContext, VirtualStepRecord}
import HwOS.kernel.thread.StepRef
import HwOS.kernel.thread.step.ThreadCompilePlan.ThreadCompilePlan

private[kernel] object ThreadLayout {
  final case class StepLayout(name: String, address: Int, standalone: Boolean)

  final class LayoutState {
    var runtimeContext: Option[RuntimeContext] = None
    var compilePlan: Option[ThreadCompilePlan] = None
    var jumpTargets = Map.empty[String, UInt]
    var currentLoweringStep: Int = -1
    var currentEntryStep: Int = -1
    var currentDebugRecord: Option[VirtualStepRecord] = None
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
      afterBody: => Unit = (),
  ): Unit = {
    val saveLowering = layoutState.currentLoweringStep
    val saveEntry = layoutState.currentEntryStep
    val saveRecord = layoutState.currentDebugRecord

    if (layoutState.currentEntryStep < 0) {
    layoutState.currentEntryStep = index
    }
    layoutState.currentLoweringStep = index
    layoutState.currentDebugRecord = Some(irState.program.steps(index))
    val step = irState.program.steps(index)
    ControlLoweringContext.withStep(layoutState.currentEntryStep, index) {
      CallProtocolContext.withCallSiteSnapshot(step.implicitCallSite) {
        CallStack.withFrame(step.name, None, step.implicitCallSite) {
          step.block()
        }
      }
      afterBody
    }
    layoutState.currentDebugRecord = saveRecord
    layoutState.currentLoweringStep = saveLowering
    layoutState.currentEntryStep = saveEntry
  }

  def materializeLayout(
      irState: ThreadIR.IRState,
      layoutState: LayoutState,
      plan: ThreadCompilePlan,
      runtime: RuntimeContext,
  ): Unit = {
    layoutState.compilePlan = Some(plan)
    layoutState.jumpTargets = plan.standaloneIndices.iterator.map { index =>
      val step = irState.program.steps(index)
      val addr = runtime.cursor.segment.addressOf(step.name)
      step.allocatedAddress = addr
      step.loweredStandalone = true
      step.name -> runtime.cursor.addressOf(step.name)
    }.toMap

    for ((step, index) <- irState.program.steps.zipWithIndex if plan.suppressedStandalone.contains(index)) {
      step.loweredStandalone = false
      step.allocatedAddress = -1
    }
  }
}
