package HwOS.kernel.thread.step

import chisel3._
import HwOS.kernel.context.HwContextEntity
import HwOS.kernel.system.{RuntimeContext, RuntimeLifecycle}
import HwOS.kernel.thread.StepRef
import HwOS.kernel.thread.step.EdgeAction.{HijackMeta, JumpAction, JumpMeta, ReturnAction, ReturnMeta, WaitMeta}

private[kernel] object ThreadRuntimeLogic {
  def preAnalyzeProgram(irState: ThreadIR.IRState): Unit = {
    for (step <- irState.program.steps) {
      PreLoweringAnalysis.analyzeStep(step) {
        step.block()
      }
    }
  }

  def analyzeControl(
      irState: ThreadIR.IRState,
      layoutState: ThreadLayout.LayoutState,
  ): ThreadLayout.LayoutPlan = {
    preAnalyzeProgram(irState)
    ThreadLayout.buildPlan(irState, layoutState)
  }

  def isRunning(runtime: RuntimeContext): Bool =
    runtime.stateReg === RuntimeLifecycle.Running.U(runtime.stateReg.getWidth.W)

  def isDone(runtime: RuntimeContext): Bool =
    runtime.stateReg === RuntimeLifecycle.Done.U(runtime.stateReg.getWidth.W)

  def start(runtime: RuntimeContext): Unit = {
    runtime.cursor.reg := runtime.cursor.entryAddress
    runtime.stateReg := RuntimeLifecycle.Running.U(runtime.stateReg.getWidth.W)
  }

  def exit(runtime: RuntimeContext): Unit = {
    runtime.stateReg := RuntimeLifecycle.Done.U(runtime.stateReg.getWidth.W)
  }

  def resetToIdle(runtime: RuntimeContext): Unit = {
    runtime.cursor.reg := runtime.cursor.entryAddress
    runtime.stateReg := RuntimeLifecycle.Idle.U(runtime.stateReg.getWidth.W)
  }

  def materializeRuntime(
      owner: HwContextEntity,
      irState: ThreadIR.IRState,
      layoutState: ThreadLayout.LayoutState,
      plan: ThreadLayout.LayoutPlan,
      initialState: Int,
  ): RuntimeContext = {
    if (layoutState.runtimeContext.isDefined) {
      throw new Exception(s"[Thread] Program '${irState.programName}' was built twice.")
    }
    if (irState.program.steps.isEmpty) {
      throw new Exception(s"[Thread] Program '${irState.programName}' has no steps.")
    }

    val standaloneLabels = plan.standaloneIndices.map(idx => irState.program.steps(idx).name)
    val segment = owner.kernel.addressSpace.reserveCodeSegment(irState.programName, standaloneLabels)
    val runtime = owner.kernel.addressSpace.allocateRuntimeContext(
      owner = owner,
      bindingName = s"${irState.programName}_runtime",
      segment = segment,
      initialState = initialState,
    )
    layoutState.runtimeContext = Some(runtime)
    ThreadLayout.materializeLayout(irState, layoutState, plan, runtime)
    runtime
  }

  def emitJump(
      irState: ThreadIR.IRState,
      layoutState: ThreadLayout.LayoutState,
      runtime: => RuntimeContext,
      target: StepRef,
  ): Unit = {
    if (PreLoweringAnalysis.isActive) {
      if (EdgePatchAnalysis.isActive) {
        EdgePatchAnalysis.recordJump(target)
      } else {
        PreLoweringAnalysis.record(JumpMeta(target))
      }
      return
    }
    val targetIndex = ThreadLayout.resolveStepRef(irState, layoutState, target)
    val targetStep = irState.program.steps(targetIndex)
    val targetPc = layoutState.jumpTargets.getOrElse(
      targetStep.name,
      throw new Exception(s"[Thread] Unknown jump target '${targetStep.name}' in program '${irState.programName}'."),
    )
    runtime.cursor.reg := targetPc
  }

  def emitWaitCondition(
      irState: ThreadIR.IRState,
      layoutState: ThreadLayout.LayoutState,
      runtime: => RuntimeContext,
      cond: Bool,
  ): Unit = {
    val pushPassGuard = cond.litOption.isEmpty
    val keepLoweringGuards =
      layoutState.currentLoweringStep >= 0 &&
        irState.program.steps(layoutState.currentLoweringStep).edgeActions.exists {
          case ReturnAction(_, _) | JumpAction(_, _) => true
          case _ => false
        }

    if (PreLoweringAnalysis.isActive) {
      PreLoweringAnalysis.record(WaitMeta)
      if (pushPassGuard) {
        PreLoweringAnalysis.pushEdgeGuard(cond)
      }
      return
    }
    if (layoutState.currentEntryStep < 0) {
      throw new Exception(s"[Thread] waitCondition() must be used while lowering a Step in '${irState.programName}'.")
    }
    val entryAddr = irState.program.steps(layoutState.currentEntryStep).allocatedAddress
    wrapGuards(layoutState.currentEdgeGuards) {
      when(!cond) {
        runtime.cursor.reg := entryAddr.U(runtime.cursor.reg.getWidth.W)
      }
    }
    if (pushPassGuard && keepLoweringGuards) {
      layoutState.currentEdgeGuards = cond :: layoutState.currentEdgeGuards
    }
  }

  def emitHijack(
      irState: ThreadIR.IRState,
      layoutState: ThreadLayout.LayoutState,
      runtime: => RuntimeContext,
      target: StepRef,
  ): Unit = {
    if (PreLoweringAnalysis.isActive) {
      PreLoweringAnalysis.record(HijackMeta(target))
      return
    }
    val victimIndex = ThreadLayout.resolveStepRef(irState, layoutState, target)
    val steps = irState.program.steps
    if (layoutState.currentLoweringStep < 0 || victimIndex >= steps.length) {
      val currentName = if (layoutState.currentLoweringStep >= 0) steps(layoutState.currentLoweringStep).name else "<unknown>"
      throw new Exception(s"[Thread] Step '$currentName' tried to hijack an invalid step target.")
    }

    ThreadLayout.suppressStandalone(irState, layoutState, victimIndex)
    val nextPc = ThreadLayout
      .nextStandaloneIndex(irState, layoutState, layoutState.currentLoweringStep)
      .map(nextIdx => runtime.cursor.segment.addressOf(steps(nextIdx).name).U(runtime.cursor.reg.getWidth.W))
      .getOrElse(runtime.cursor.entryAddress)
    runtime.cursor.reg := nextPc
    ThreadLayout.lowerStepAt(irState, layoutState, victimIndex)
  }

  def lowerRuntime(
      irState: ThreadIR.IRState,
      layoutState: ThreadLayout.LayoutState,
      plan: ThreadLayout.LayoutPlan,
      runtime: RuntimeContext,
  ): Unit = {
    val steps = irState.program.steps
    val standalone = plan.standaloneIndices

    def lowerMainBody(): Unit = {
      for ((index, pos) <- standalone.zipWithIndex) {
        val step = steps(index)
        val nextPc = standalone
          .lift(pos + 1)
          .map(nextIdx => runtime.cursor.segment.addressOf(steps(nextIdx).name).U(runtime.cursor.reg.getWidth.W))
          .getOrElse(runtime.cursor.entryAddress)

        when(
          isRunning(runtime) &&
            runtime.cursor.reg === step.allocatedAddress.U(runtime.cursor.reg.getWidth.W),
        ) {
          runtime.cursor.reg := nextPc
          ThreadLayout.lowerStepAt(irState, layoutState, index)
        }
      }
    }
    lowerMainBody()
  }

  def recordEdgePatch(
      irState: ThreadIR.IRState,
      layoutState: ThreadLayout.LayoutState,
      target: StepRef,
      block: => Unit,
  ): Unit = {
    if (PreLoweringAnalysis.isActive) {
      val current = PreLoweringAnalysis.currentRecord
      val targetIndex = ThreadLayout.resolveStepRef(irState, layoutState, target)
      val targetStep = irState.program.steps(targetIndex)
      if (targetStep.name != current.name) {
        throw new Exception(
          s"[HwOS] StepRef.edge.add(...) in v1 can only patch the current step. " +
            s"Tried to patch '${targetStep.name}' while analyzing '${current.name}'.",
        )
      }
      EdgePatchAnalysis.capture(current, PreLoweringAnalysis.currentEdgeGuards.length) {
        block
      }
      return
    }

    if (layoutState.currentLoweringStep < 0) {
      throw new Exception(s"[HwOS] StepRef.edge.add(...) must be used while lowering a Step in '${irState.programName}'.")
    }

    val targetIndex = ThreadLayout.resolveStepRef(irState, layoutState, target)
    if (targetIndex != layoutState.currentLoweringStep) {
      val currentName = irState.program.steps(layoutState.currentLoweringStep).name
      val targetName = irState.program.steps(targetIndex).name
      throw new Exception(
        s"[HwOS] StepRef.edge.add(...) in v1 can only patch the current step. " +
          s"Tried to patch '$targetName' while lowering '$currentName'.",
        )
    }

    wrapGuards(layoutState.currentEdgeGuards) {
      block
    }
  }

  private def wrapGuards(guards: List[Bool])(block: => Unit): Unit = guards.reverse match {
    case Nil => block
    case xs =>
      def loop(remaining: List[Bool]): Unit = remaining match {
        case Nil => block
        case h :: t =>
          when(h) {
            loop(t)
          }
      }
      loop(xs)
  }

}
