package HwOS.kernel.thread.step

import chisel3._
import HwOS.kernel.context.HwContextEntity
import HwOS.kernel.system.{RecordedEdgePatch, RuntimeContext, RuntimeLifecycle}
import HwOS.kernel.thread.StepRef
import HwOS.kernel.thread.step.ThreadCompilePlan._

private[kernel] object ThreadRuntimeLogic {
  def analyzeControl(
      irState: ThreadIR.IRState,
      layoutState: ThreadLayout.LayoutState,
  ): ThreadCompilePlan =
    ThreadCompileAnalysis.analyze(irState, layoutState)

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
      plan: ThreadCompilePlan,
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
    if (EdgePatchAnalysis.isActive) {
      EdgePatchAnalysis.recordJump(target)
      return
    }
    if (PreLoweringAnalysis.isActive) {
      PreLoweringAnalysis.record(EdgeAction.Jump(target = target))
      return
    }
    if (ControlLoweringContext.isActive) {
      val targetIndex = ThreadLayout.resolveStepRef(irState, layoutState, target)
      val targetStep = irState.program.steps(targetIndex)
      val targetPc = layoutState.jumpTargets.getOrElse(
        targetStep.name,
        throw new Exception(s"[Thread] Unknown jump target '${targetStep.name}' in program '${irState.programName}'."),
      )
      runtime.cursor.reg := targetPc
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
    if (PreLoweringAnalysis.isActive) {
      PreLoweringAnalysis.record(EdgeAction.Wait(cond))
      if (cond.litOption.isEmpty) {
        PreLoweringAnalysis.pushEdgeGuard(cond)
      }
      return
    }
    if (ControlLoweringContext.isActive) {
      if (layoutState.currentEntryStep < 0) {
        throw new Exception(s"[Thread] waitCondition() lost its entry step while lowering '${irState.programName}'.")
      }
      val entryAddr = irState.program.steps(layoutState.currentEntryStep).allocatedAddress
      when(!cond) {
        runtime.cursor.reg := entryAddr.U(runtime.cursor.reg.getWidth.W)
      }
      return
    }
    throw new Exception(s"[Thread] waitCondition() must be used while analyzing or lowering a Step in '${irState.programName}'.")
  }

  def emitHijack(
      irState: ThreadIR.IRState,
      layoutState: ThreadLayout.LayoutState,
      runtime: => RuntimeContext,
      target: StepRef,
  ): Unit = {
    if (PreLoweringAnalysis.isActive) {
      PreLoweringAnalysis.record(EdgeAction.Hijack(target))
      return
    }
    if (!ControlLoweringContext.isActive) {
      throw new Exception(s"[Thread] hijack() can only lower inside an active step in '${irState.programName}'.")
    }

    val plan = currentCompilePlan(layoutState, irState)
    val victimIndex = ThreadLayout.resolveStepRef(irState, layoutState, target)
    if (plan.stepPlan(victimIndex).standalone) {
      throw new Exception(
        s"[Thread] Step '${irState.program.steps(victimIndex).name}' was expected to be hijack-inlined in '${irState.programName}', but remained standalone.",
      )
    }

    ThreadLayout.lowerStepAt(irState, layoutState, victimIndex, {
      emitCompiledEffects(irState, layoutState, runtime, victimIndex)
    })
  }

  def lowerRuntime(
      irState: ThreadIR.IRState,
      layoutState: ThreadLayout.LayoutState,
      plan: ThreadCompilePlan,
      runtime: RuntimeContext,
  ): Unit = {
    val steps = irState.program.steps
    val standalone = plan.standaloneIndices

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
        ThreadLayout.lowerStepAt(irState, layoutState, index, {
          emitCompiledEffects(irState, layoutState, runtime, index)
        })
      }
    }
  }

  def recordEdgePatch(
      irState: ThreadIR.IRState,
      layoutState: ThreadLayout.LayoutState,
      target: StepRef,
      block: => Unit,
  ): Unit = {
    val targetIndex = ThreadLayout.resolveStepRef(irState, layoutState, target)
    val targetStep = irState.program.steps(targetIndex)

    if (PreLoweringAnalysis.isActive) {
      val patchEffects = EdgePatchAnalysis.capture(PreLoweringAnalysis.currentEdgeGuards) {
        block
      }
      targetStep.staticEdgePatches += RecordedEdgePatch(PatchTarget.PassEdge, patchEffects)
      return
    }

    if (ControlLoweringContext.isActive) {
      return
    }

    if (layoutState.currentLoweringStep >= 0) {
      throw new Exception("[HwOS] StepRef.edge.add(...) cannot be introduced during lowering. Define edge patches before compilation.")
    }

    val patchEffects = EdgePatchAnalysis.capture(Nil) {
      block
    }
    targetStep.staticEdgePatches += RecordedEdgePatch(PatchTarget.PassEdge, patchEffects)
  }

  def emitCompiledEffects(
      irState: ThreadIR.IRState,
      layoutState: ThreadLayout.LayoutState,
      runtime: RuntimeContext,
      stepIndex: Int,
  ): Unit = {
    val plan = currentCompilePlan(layoutState, irState)
    val stepPlan = plan.stepPlan(stepIndex)

    stepPlan.effects.foreach {
      case WaitEffect(_) =>
        ()
      case JumpEffect(_, _) =>
        ()
      case ReturnEffect(_, _) =>
        ()
      case HijackInlineEffect(_) =>
        ()
      case PatchedEdge(PatchTarget.PassEdge, effects) =>
        effects.foreach {
          case JumpEffect(targetIndex, guards) =>
            emitGuarded(guards) {
              runtime.cursor.reg := pcForStep(irState, layoutState, runtime, targetIndex)
            }
          case ReturnEffect(continuation, guards) =>
            emitGuarded(guards) {
              emitReturnEdgePatch(continuation.flatMap(_.returnEdgePatch))
              continuation.flatMap(_.targetLabel) match {
                case Some(target) =>
                  val targetPc = layoutState.jumpTargets.getOrElse(
                    target,
                    throw new Exception(
                      s"[HwOS] Unknown return target '$target' while lowering step '${irState.program.steps(stepIndex).name}' in '${irState.programName}'.",
                    ),
                  )
                  runtime.cursor.reg := targetPc
                case None =>
                  runtime.stateReg := RuntimeLifecycle.Done.U(runtime.stateReg.getWidth.W)
              }
            }
          case _ =>
            ()
        }
    }
  }

  private def currentCompilePlan(
      layoutState: ThreadLayout.LayoutState,
      irState: ThreadIR.IRState,
  ): ThreadCompilePlan =
    layoutState.compilePlan.getOrElse(
      throw new Exception(s"[Thread] Missing compile plan for program '${irState.programName}'."),
    )

  private def pcForStep(
      irState: ThreadIR.IRState,
      layoutState: ThreadLayout.LayoutState,
      runtime: RuntimeContext,
      stepIndex: Int,
  ): UInt = {
    val targetStep = irState.program.steps(stepIndex)
    layoutState.jumpTargets.getOrElse(
      targetStep.name,
      throw new Exception(
        s"[Thread] Step '${targetStep.name}' in program '${irState.programName}' has no standalone code slot.",
      ),
    )
  }

  private[kernel] def emitReturnEdgePatch(
      patch: Option[HwOS.kernel.system.CallProtocolContext.ReturnEdgePatch],
  ): Unit = {
    patch match {
      case Some(returnPatch) =>
        returnPatch.emitThunk()
      case None =>
        ()
    }
  }

  private def emitGuarded(guards: List[Bool])(block: => Unit): Unit = guards.reverse match {
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
