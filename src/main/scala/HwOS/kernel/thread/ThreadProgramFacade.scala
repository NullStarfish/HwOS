package HwOS.kernel.thread

import HwOS.kernel.thread.step.{ControlProgramBuilder, CurrentProgramContext, PreLoweringAnalysis}

private[kernel] final class ThreadProgramFacade(
    val builder: ControlProgramBuilder,
) {
  private def snapshotRef(stepIndex: Int): StepRef = {
    val step = builder.steps(stepIndex)
    StepRef.NamedStepRef(
      step.name,
      StepRef.EdgeContext(passEdgeGuards = step.passEdgeGuards),
    )
  }

  def prevRef: StepRef = {
    if (PreLoweringAnalysis.isActive) {
      StepRef.NamedStepRef(
        PreLoweringAnalysis.currentRecord.name,
        StepRef.EdgeContext(passEdgeGuards = PreLoweringAnalysis.currentEdgeGuards),
      )
    } else {
      CurrentProgramContext.currentLoweringStepIndex
        .flatMap(idx => builder.steps.lift(idx).map(_ => snapshotRef(idx)))
        .orElse(builder.steps.indices.lastOption.map(snapshotRef))
        .getOrElse(throw new Exception(s"[HwOS] Prev is unavailable before any Step is defined in thread '${builder.programName}'."))
    }
  }
}
