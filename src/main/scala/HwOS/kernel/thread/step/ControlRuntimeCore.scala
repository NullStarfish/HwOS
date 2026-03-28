package HwOS.kernel.thread.step

import HwOS.kernel.context.{AtomicCtx, ContextScope}
import HwOS.kernel.thread.{ControlHost, HardwareThread}
import HwOS.kernel.thread.{ControlHostAdapter, StepRef}
import HwOS.kernel.thread.step.ControlProgram.CompiledControlProgram
import HwOS.kernel.thread.step.ThreadCompilePlan._
import chisel3._

private[kernel] object ControlRuntimeCore {
  def emitJump(
      builder: ControlProgramBuilder,
      compiledProgram: => CompiledControlProgram,
      host: => ControlHostAdapter,
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

    val targetIndex = ThreadLayout.resolveStepRef(builder.state.irState, currentRefContext(builder), target)
    host.writeCursor(host.pcForStep(targetIndex))
  }

  def emitWaitCondition(
      builder: ControlProgramBuilder,
      host: => ControlHostAdapter,
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
      val entryStepIndex = CurrentProgramContext.currentEntryStepIndex.getOrElse(
        throw new Exception(s"[Thread] waitCondition() lost its entry step while lowering '${builder.programName}'."),
      )
      when(!cond) {
        host.writeCursor(host.pcForStep(entryStepIndex))
      }
      return
    }
    throw new Exception(s"[Thread] waitCondition() must be used while analyzing or lowering a Step in '${builder.programName}'.")
  }

  def emitHijack(
      builder: ControlProgramBuilder,
      compiledProgram: => CompiledControlProgram,
      host: => ControlHostAdapter,
      target: StepRef,
  ): Unit = {
    if (PreLoweringAnalysis.isActive) {
      PreLoweringAnalysis.record(EdgeAction.Hijack(target))
      return
    }
    if (!ControlLoweringContext.isActive) {
      throw new Exception(s"[Thread] hijack() can only lower inside an active step in '${builder.programName}'.")
    }

    val victimIndex = ThreadLayout.resolveStepRef(builder.state.irState, currentRefContext(builder), target)
    if (compiledProgram.stepPlan(victimIndex).standalone) {
      throw new Exception(
        s"[Thread] Step '${builder.steps(victimIndex).name}' was expected to be hijack-inlined in '${builder.programName}', but remained standalone.",
      )
    }

    ThreadLayout.lowerStepAt(builder.state.irState, victimIndex, {
      emitCompiledEffects(builder, compiledProgram, host, victimIndex)
    })
  }

  def lowerRuntime(
      builder: ControlProgramBuilder,
      compiledProgram: CompiledControlProgram,
      host: ControlHostAdapter,
  ): Unit = {
    val steps = builder.steps
    val standalone = compiledProgram.standaloneIndices

    for ((index, pos) <- standalone.zipWithIndex) {
      val step = steps(index)
      val nextPc = standalone
        .lift(pos + 1)
        .map(host.pcForStep)
        .getOrElse(host.entryAddress)

      when(
        host.canExecute &&
          host.controlCursor === host.pcForStep(index),
      ) {
        host.writeCursor(nextPc)
        ThreadLayout.lowerStepAt(builder.state.irState, index, {
          emitCompiledEffects(builder, compiledProgram, host, index)
        })
      }
    }
  }

  def recordEdgePatch(
      builder: ControlProgramBuilder,
      target: StepRef,
      block: => Unit,
  ): Unit = {
    val targetIndex = ThreadLayout.resolveStepRef(builder.state.irState, currentRefContext(builder), target)
    val targetStep = builder.steps(targetIndex)

    if (PreLoweringAnalysis.isActive) {
      val patchGuards = target.edgeContext.passEdgeGuards
      val capturedEffects = EdgePatchAnalysis.capture(patchGuards) {
        block
      }
      targetStep.staticEdgePatches += HwOS.kernel.system.RecordedEdgePatch(
        target = PatchTarget.PassEdge,
        effects = capturedEffects,
        emitThunk = None,
        guards = patchGuards,
        inheritPassEdgeGuards = false,
      )
      return
    }

    if (ControlLoweringContext.isActive) {
      val patchOrdinal = ControlLoweringContext.nextEdgePatchOrdinal(targetIndex)
      val existing = targetStep.staticEdgePatches.lift(patchOrdinal)
      existing match {
        case Some(patch) =>
          targetStep.staticEdgePatches.update(
            patchOrdinal,
            patch.copy(emitThunk = Some(() => block)),
          )
        case None =>
          targetStep.staticEdgePatches += HwOS.kernel.system.RecordedEdgePatch(
            target = PatchTarget.PassEdge,
            emitThunk = Some(() => block),
            guards = Nil,
            inheritPassEdgeGuards = true,
          )
      }
      return
    }

    if (CurrentProgramContext.currentLoweringStepIndex.nonEmpty) {
      throw new Exception("[HwOS] StepRef.edge.add(...) cannot be introduced during lowering. Define edge patches before compilation.")
    }

    targetStep.staticEdgePatches += HwOS.kernel.system.RecordedEdgePatch(
      target = PatchTarget.PassEdge,
      emitThunk = Some(() => block),
      guards = Nil,
      inheritPassEdgeGuards = true,
    )
  }

  def emitCompiledEffects(
      builder: ControlProgramBuilder,
      compiledProgram: CompiledControlProgram,
      host: ControlHostAdapter,
      stepIndex: Int,
  ): Unit = {
    val stepPlan = compiledProgram.stepPlan(stepIndex)
    val runtimePatches = builder.steps(stepIndex).staticEdgePatches.toSeq
    var runtimePatchOrdinal = 0

    stepPlan.effects.foreach {
      case WaitEffect(_) =>
        ()
      case JumpEffect(_, _) =>
        ()
      case ReturnEffect(_, _) =>
        ()
      case HijackInlineEffect(_) =>
        ()
      case PatchedEdge(PatchTarget.PassEdge, effects, emitThunk, patchGuards) =>
        val runtimeThunk = runtimePatches
          .lift(runtimePatchOrdinal)
          .flatMap(_.emitThunk)
          .orElse(emitThunk)
        runtimePatchOrdinal += 1
        emitGuarded(patchGuards) {
          // New semantics: replay the full edge patch block during lowering under edge guards.
          withAtomicThreadScope(host) {
            runtimeThunk.foreach(_.apply())
          }
          // Legacy structured effects remain supported for backward compatibility.
          effects.foreach {
            case JumpEffect(targetIndex, guards) =>
              emitGuarded(guards) {
                host.writeCursor(host.pcForStep(targetIndex))
              }
            case ReturnEffect(continuation, guards) =>
              emitGuarded(guards) {
                emitReturnEdgePatch(continuation.flatMap(_.returnEdgePatch))
                continuation.flatMap(_.targetLabel) match {
                  case Some(target) =>
                    host.writeCursor(host.pcForStep(compiledProgram.stepIndex(target)))
                  case None =>
                    host.onControlExit()
                }
              }
            case _ =>
              ()
          }
        }
    }
  }

  private def currentRefContext(builder: ControlProgramBuilder): ThreadLayout.StepRefContext =
    ThreadLayout.StepRefContext(
      CurrentProgramContext.currentLoweringStepIndex.getOrElse(
        builder.steps.length - 1,
      ),
    )

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

  private def withAtomicThreadScope(host: ControlHostAdapter)(block: => Unit): Unit =
    host match {
      case controlHost: ControlHost =>
        controlHost.entity match {
          case thread: HardwareThread =>
            ContextScope.withContext(AtomicCtx(thread)) {
              block
            }
          case _ =>
            block
        }
      case _ =>
        block
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
