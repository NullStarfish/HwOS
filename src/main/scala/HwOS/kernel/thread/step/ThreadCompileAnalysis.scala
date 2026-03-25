package HwOS.kernel.thread.step

import HwOS.kernel.thread.step.ControlProgram.{CompiledControlProgram, CompiledProgramLayout}
import HwOS.kernel.system.{RecordedEdgePatch, RecordedEffect, VirtualStepRecord}
import HwOS.kernel.thread.step.ThreadCompilePlan._

private[kernel] object ThreadCompileAnalysis {
  def compile(builder: ControlProgramBuilder): CompiledControlProgram = {
    val irState = builder.state.irState
    val plan = analyze(irState)
    CompiledControlProgram(
      programName = builder.programName,
      program = builder.program,
      compilePlan = plan,
      layout = CompiledProgramLayout(
        standaloneIndices = plan.standaloneIndices,
        suppressedStandalone = plan.suppressedStandalone,
        entryIndex = plan.entryIndex,
        stepIndicesByLabel = irState.program.labels.zipWithIndex.toMap,
        standaloneLabels = plan.standaloneIndices.map(idx => irState.program.steps(idx).name),
      ),
    )
  }

  def analyze(
      irState: ThreadIR.IRState,
  ): ThreadCompilePlan = {
    preAnalyzeProgram(irState)
    val suppressed = collectSuppressedStandalone(irState)
    val standalone = irState.program.steps.indices.filterNot(suppressed.contains).toVector
    val stepPlans = irState.program.steps.zipWithIndex.map { case (step, index) =>
      buildStepPlan(irState, step, index, suppressed)
    }.toVector
    val plan = ThreadCompilePlan(
      stepPlans = stepPlans,
      suppressedStandalone = suppressed,
      standaloneIndices = standalone,
      hasReturningStep = stepPlans.exists(_.effects.exists {
        case _: ReturnEffect => true
        case PatchedEdge(_, patchEffects, _, _) => patchEffects.exists(_.isInstanceOf[ReturnEffect])
        case _ => false
      }),
      entryIndex = 0,
    )
    validateJumpTargets(irState, plan)
    plan
  }

  private def preAnalyzeProgram(irState: ThreadIR.IRState): Unit = {
    for (step <- irState.program.steps) {
      PreLoweringAnalysis.analyzeStep(step) {
        step.block()
      }
    }
  }

  private def collectSuppressedStandalone(
      irState: ThreadIR.IRState,
  ): Set[Int] = {
    irState.program.steps.zipWithIndex.flatMap { case (step, index) =>
      step.capturedEffects.collect {
        case RecordedEffect(action, _) if action.hijackTarget.nonEmpty =>
          val target = action.hijackTarget.get
          ThreadLayout.resolveStepRef(irState, ThreadLayout.StepRefContext(index), target)
      }
    }.toSet
  }

  private def buildStepPlan(
      irState: ThreadIR.IRState,
      step: VirtualStepRecord,
      stepIndex: Int,
      suppressed: Set[Int],
  ): StepPlan = {
    val localRefContext = ThreadLayout.StepRefContext(stepIndex)
    val effects = step.capturedEffects.flatMap(compileEffect(irState, localRefContext, _)).toSeq ++
      step.staticEdgePatches.map(patch =>
        PatchedEdge(
          target = patch.target,
          effects = patch.effects.flatMap(compileEffect(irState, localRefContext, _)).toSeq,
          emitThunk = patch.emitThunk,
          guards = patch.guards,
        ),
      )
    val waits = effects.collect { case wait: WaitEffect => wait }
    StepPlan(
      stepIndex = stepIndex,
      standalone = !suppressed.contains(stepIndex),
      entryPolicy = StepEntryPolicy(waits = waits),
      effects = effects,
    )
  }

  private def compileEffect(
      irState: ThreadIR.IRState,
      refContext: ThreadLayout.StepRefContext,
      recorded: RecordedEffect,
  ): Option[EdgeEffect] = recorded.action match {
    case EdgeAction.Wait(cond) =>
      Some(WaitEffect(cond))
    case EdgeAction.Jump(_, target, _) =>
      Some(JumpEffect(ThreadLayout.resolveStepRef(irState, refContext, target), recorded.guards))
    case EdgeAction.Return(_, continuation, _) =>
      Some(ReturnEffect(continuation, recorded.guards))
    case EdgeAction.Hijack(target) =>
      Some(HijackInlineEffect(ThreadLayout.resolveStepRef(irState, refContext, target)))
  }

  private def validateJumpTargets(
      irState: ThreadIR.IRState,
      plan: ThreadCompilePlan,
  ): Unit = {
    for (stepPlan <- plan.stepPlans) {
      val sourceStep = irState.program.steps(stepPlan.stepIndex)
      for (effect <- stepPlan.effects) {
        effect match {
          case JumpEffect(targetIndex, _) =>
            if (!plan.standaloneIndices.contains(targetIndex)) {
              val targetStep = irState.program.steps(targetIndex)
              throw new Exception(
                s"[Thread] jump target '${targetStep.name}' referenced from '${sourceStep.name}' in program '${irState.programName}' has no standalone code slot.",
              )
            }
          case PatchedEdge(_, patchEffects, _, _) =>
            patchEffects.collect { case JumpEffect(targetIndex, _) => targetIndex }.foreach { targetIndex =>
              if (!plan.standaloneIndices.contains(targetIndex)) {
                val targetStep = irState.program.steps(targetIndex)
                throw new Exception(
                  s"[Thread] jump target '${targetStep.name}' referenced from '${sourceStep.name}' in program '${irState.programName}' has no standalone code slot.",
                )
              }
            }
          case _ =>
        }
      }
    }
  }
}
