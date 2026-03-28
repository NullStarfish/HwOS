package HwOS.kernel.thread

import HwOS.kernel.thread.step.{ControlProgramBuilder, CurrentProgramContext, PreLoweringAnalysis}

private[kernel] final class ThreadProgramFacade(
    val builder: ControlProgramBuilder,
) {
  def prevRef: StepRef = {
    if (PreLoweringAnalysis.isActive) {
      StepRef.NamedStepRef(
        PreLoweringAnalysis.currentRecord.name,
        StepRef.EdgeContext(passEdgeGuards = PreLoweringAnalysis.currentEdgeGuards),
      )
    } else {
      CurrentProgramContext.currentLoweringStepIndex
        .flatMap(idx => builder.steps.lift(idx).map(step => StepRef.NamedStepRef(step.name)))
        .orElse(builder.lastDefinedStepName.map(StepRef.NamedStepRef(_)))
        .getOrElse(throw new Exception(s"[HwOS] Prev is unavailable before any Step is defined in thread '${builder.programName}'."))
    }
  }
}
