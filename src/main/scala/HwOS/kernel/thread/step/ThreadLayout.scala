package HwOS.kernel.thread.step

import chisel3._
import scala.collection.mutable
import HwOS.kernel.system.{RuntimeContext, VirtualStepRecord}
import HwOS.kernel.thread.StepRef
import HwOS.kernel.thread.step.EdgeAction.{HijackMeta, JumpAction, JumpMeta, ReturnAction, ReturnMeta, WaitMeta}

private[kernel] object ThreadLayout {
  final case class StepLayout(name: String, address: Int, standalone: Boolean)
  final case class LayoutPlan(
      suppressedStandalone: Set[Int],
      standaloneIndices: Vector[Int],
      hasReturningStep: Boolean,
  )

  final class LayoutState {
    var runtimeContext: Option[RuntimeContext] = None
    var jumpTargets = Map.empty[String, UInt]
    var currentLoweringStep: Int = -1
    var currentEntryStep: Int = -1
    var currentDebugRecord: Option[VirtualStepRecord] = None
    var currentEdgeGuards: List[Bool] = Nil
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
      afterBody: => Unit = (),
  ): Unit = {
    val saveLowering = layoutState.currentLoweringStep
    val saveEntry = layoutState.currentEntryStep
    val saveRecord = layoutState.currentDebugRecord
    val saveGuards = layoutState.currentEdgeGuards

    if (layoutState.currentEntryStep < 0) {
      layoutState.currentEntryStep = index
    }
    layoutState.currentLoweringStep = index
    layoutState.currentDebugRecord = Some(irState.program.steps(index))
    layoutState.currentEdgeGuards = Nil
    irState.program.steps(index).block()
    afterBody
    layoutState.currentEdgeGuards = saveGuards
    layoutState.currentDebugRecord = saveRecord
    layoutState.currentLoweringStep = saveLowering
    layoutState.currentEntryStep = saveEntry
  }

  def lowerStepNamed(
      irState: ThreadIR.IRState,
      layoutState: LayoutState,
      stepName: String,
  ): Unit = lowerStepAt(irState, layoutState, resolveStepIndex(irState, stepName))

  private def standaloneIndices(irState: ThreadIR.IRState, layoutState: LayoutState): Vector[Int] = {
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

  private def planSuppressedStandalone(
      irState: ThreadIR.IRState,
      layoutState: LayoutState,
  ): Unit = {
    layoutState.suppressedStandalone.clear()
    for ((step, index) <- irState.program.steps.zipWithIndex) {
      val saveLowering = layoutState.currentLoweringStep
      layoutState.currentLoweringStep = index
      try {
        for (effect <- step.edgeActions) {
          effect match {
            case HijackMeta(target) =>
              layoutState.suppressedStandalone += resolveStepRef(irState, layoutState, target)
            case _ =>
          }
        }
      } finally {
        layoutState.currentLoweringStep = saveLowering
      }
    }
  }

  def buildPlan(
      irState: ThreadIR.IRState,
      layoutState: LayoutState,
  ): LayoutPlan = {
    planSuppressedStandalone(irState, layoutState)
    val standalone = standaloneIndices(irState, layoutState)
    val plan = LayoutPlan(
      suppressedStandalone = layoutState.suppressedStandalone.toSet,
      standaloneIndices = standalone,
      hasReturningStep = irState.program.steps.exists(_.edgeActions.exists {
        case ReturnMeta | ReturnAction(_, _) => true
        case _ => false
      }),
    )
    validateJumpTargets(irState, plan)
    plan
  }

  def materializeLayout(
      irState: ThreadIR.IRState,
      layoutState: LayoutState,
      plan: LayoutPlan,
      runtime: RuntimeContext,
  ): Unit = {
    layoutState.suppressedStandalone.clear()
    layoutState.suppressedStandalone ++= plan.suppressedStandalone
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

  private def validateJumpTargets(irState: ThreadIR.IRState, plan: LayoutPlan): Unit = {
    for ((step, stepIndex) <- irState.program.steps.zipWithIndex) {
      val savedLowering = layoutStateScratch.currentLoweringStep
      layoutStateScratch.currentLoweringStep = stepIndex
      try {
        for (effect <- step.edgeActions) {
          effect match {
            case JumpMeta(target) =>
              validateJumpTargetRef(irState, layoutStateScratch, plan, step, target)
            case JumpAction(_, target) =>
              validateJumpTargetRef(irState, layoutStateScratch, plan, step, target)
            case WaitMeta =>
            case ReturnMeta | ReturnAction(_, _) | HijackMeta(_) =>
            case _ =>
          }
        }
      } finally {
        layoutStateScratch.currentLoweringStep = savedLowering
      }
    }
  }

  private val layoutStateScratch = new LayoutState()

  private def validateJumpTargetRef(
      irState: ThreadIR.IRState,
      layoutState: LayoutState,
      plan: LayoutPlan,
      sourceStep: VirtualStepRecord,
      target: StepRef,
  ): Unit = {
    val targetIndex = ThreadLayout.resolveStepRef(irState, layoutState, target)
    if (targetIndex < 0 || targetIndex >= irState.program.steps.length) {
      throw new Exception(s"[Thread] Invalid jump target index '$targetIndex' in program '${irState.programName}'.")
    }
    val targetStep = irState.program.steps(targetIndex)
    if (!plan.standaloneIndices.contains(targetIndex)) {
      throw new Exception(
        s"[Thread] jump target '${targetStep.name}' referenced from '${sourceStep.name}' in program '${irState.programName}' has no standalone code slot.",
      )
    }
  }
}
