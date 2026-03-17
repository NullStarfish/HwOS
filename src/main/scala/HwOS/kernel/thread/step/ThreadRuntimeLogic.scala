package HwOS.kernel.thread.step

import chisel3._
import HwOS.kernel.context.HwContextEntity
import HwOS.kernel.system.{RuntimeContext, RuntimeLifecycle}
import HwOS.kernel.thread.StepRef

private[kernel] object ThreadRuntimeLogic {
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

  def allocateRuntime(
      owner: HwContextEntity,
      irState: ThreadIR.IRState,
      layoutState: ThreadLayout.LayoutState,
      initialState: Int,
  ): RuntimeContext = {
    if (layoutState.runtimeContext.isDefined) {
      throw new Exception(s"[Thread] Program '${irState.programName}' was built twice.")
    }
    if (irState.program.steps.isEmpty) {
      throw new Exception(s"[Thread] Program '${irState.programName}' has no steps.")
    }

    layoutState.suppressedStandalone.clear()
    for (target <- irState.hijackTargets) {
      target match {
        case StepRef.NamedStepRef(_) =>
          layoutState.suppressedStandalone += ThreadLayout.resolveStepRef(irState, layoutState, target)
        case StepRef.NextStepRef =>
      }
    }

    val standaloneLabels = ThreadLayout
      .standaloneIndices(irState, layoutState)
      .map(idx => irState.program.steps(idx).name)
    val segment = owner.kernel.addressSpace.reserveCodeSegment(irState.programName, standaloneLabels)
    val runtime = owner.kernel.addressSpace.allocateRuntimeContext(
      owner = owner,
      bindingName = s"${irState.programName}_runtime",
      segment = segment,
      initialState = initialState,
    )
    layoutState.runtimeContext = Some(runtime)
    ThreadLayout.assignStandaloneLayout(irState, layoutState, runtime)
    runtime
  }

  def emitJump(
      irState: ThreadIR.IRState,
      layoutState: ThreadLayout.LayoutState,
      runtime: RuntimeContext,
      target: StepRef,
  ): Unit = {
    val targetIndex = ThreadLayout.resolveStepRef(irState, layoutState, target)
    val targetStep = irState.program.steps(targetIndex)
    val targetPc = layoutState.jumpTargets.getOrElse(
      targetStep.name,
      throw new Exception(s"[Thread] Unknown jump target '${targetStep.name}' in program '${irState.programName}'."),
    )
    irState.pendingJumpTargetIndices += targetIndex
    runtime.cursor.reg := targetPc
  }

  def emitWaitCondition(
      irState: ThreadIR.IRState,
      layoutState: ThreadLayout.LayoutState,
      runtime: RuntimeContext,
      cond: Bool,
  ): Unit = {
    if (layoutState.currentEntryStep < 0) {
      throw new Exception(s"[Thread] waitCondition() must be used while lowering a Step in '${irState.programName}'.")
    }
    val entryAddr = irState.program.steps(layoutState.currentEntryStep).allocatedAddress
    when(!cond) {
      runtime.cursor.reg := entryAddr.U(runtime.cursor.reg.getWidth.W)
    }
  }

  def emitHijack(
      irState: ThreadIR.IRState,
      layoutState: ThreadLayout.LayoutState,
      runtime: RuntimeContext,
      target: StepRef,
  ): Unit = {
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

  def lowerProgram(
      irState: ThreadIR.IRState,
      layoutState: ThreadLayout.LayoutState,
      runtime: RuntimeContext,
  ): Unit = {
    val steps = irState.program.steps
    val standalone = ThreadLayout.standaloneIndices(irState, layoutState)

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
}
