package HwOS.kernel.thread.step

import HwOS.kernel.debug.CallStack
import HwOS.kernel.system.{CallProtocolContext, RuntimeContext}
import HwOS.kernel.thread.StepRef
import HwOS.kernel.thread.step.ControlProgram.{CompiledControlProgram, CompiledProgramLayout}

private[kernel] object ThreadLayout {
  final case class StepLayout(name: String, address: Int, standalone: Boolean)
  final case class StepRefContext(currentStepIndex: Int)

  def resolveStepIndex(irState: ThreadIR.IRState, stepName: String): Int = {
    val idx = irState.program.labels.indexOf(stepName)
    if (idx < 0) {
      throw new Exception(s"[Thread] Unknown step '$stepName' in program '${irState.programName}'.")
    }
    idx
  }

  def resolveStepRef(
      irState: ThreadIR.IRState,
      refContext: StepRefContext,
      target: StepRef,
  ): Int = target match {
    case StepRef.NamedStepRef(name) =>
      resolveStepIndex(irState, name)
    case StepRef.NextStepRef =>
      val current = refContext.currentStepIndex
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
      index: Int,
      afterBody: => Unit = (),
  ): Unit = {
    val step = irState.program.steps(index)
    val entryStepIndex = CurrentProgramContext.currentEntryStepIndex.getOrElse(index)
    ControlLoweringContext.withStep(entryStepIndex, index) {
      CurrentProgramContext.withLowering(entryStepIndex, index, step) {
        CallProtocolContext.withCallSiteSnapshot(step.implicitCallSite) {
          CallStack.withFrame(step.name, None, step.implicitCallSite) {
            step.block()
          }
        }
        afterBody
      }
    }
  }

  def materializeLayout(
      irState: ThreadIR.IRState,
      compiledProgram: CompiledControlProgram,
      runtime: RuntimeContext,
  ): CompiledControlProgram = {
    val plan = compiledProgram.compilePlan
    val stepAddresses = plan.standaloneIndices.iterator.map { index =>
      val step = irState.program.steps(index)
      val addr = runtime.cursor.segment.addressOf(step.name)
      step.allocatedAddress = addr
      step.loweredStandalone = true
      index -> addr
    }.toMap

    for ((step, index) <- irState.program.steps.zipWithIndex if plan.suppressedStandalone.contains(index)) {
      step.loweredStandalone = false
      step.allocatedAddress = -1
    }
    compiledProgram.withLayout(
      compiledProgram.layout.copy(
        stepAddresses = stepAddresses,
      ),
    )
  }
}
