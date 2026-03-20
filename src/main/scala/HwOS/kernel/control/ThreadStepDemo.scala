package HwOS.kernel.control

import chisel3._
import HwOS.kernel.context.HwContextEntity
import HwOS.kernel.system.{Kernel, RuntimeContext}
import HwOS.kernel.thread.{StepRef}
import HwOS.kernel.thread.step.{PreLoweringAnalysis, ThreadIR, ThreadLayout, ThreadRuntimeLogic}

object ThreadStepDemo {
  type StepLayout = ThreadLayout.StepLayout
  val StepLayout = ThreadLayout.StepLayout
  type HijackAction = ThreadIR.HijackAction

  final class Program(val name: String)(implicit kernel: Kernel) {
    private val irState = new ThreadIR.IRState(name, kernel.addressSpace.createVirtualProgram(name))
    private val layoutState = new ThreadLayout.LayoutState()

    def Step(stepName: String)(block: => Unit): Unit = {
      ThreadIR.defineStep(irState, stepName)(block)
    }

    def stepRef(stepName: String): StepRef = StepRef.NamedStepRef(stepName)

    def Next: StepRef = StepRef.NextStepRef

    def hijack(target: StepRef): HijackAction =
      ThreadIR.defineHijack(irState) {
        if (PreLoweringAnalysis.isActive) {
          PreLoweringAnalysis.record(HwOS.kernel.thread.step.SystemEffect.HijackEffect(target))
        } else {
          ThreadLayout.lowerStepAt(irState, layoutState, ThreadLayout.resolveStepRef(irState, layoutState, target))
        }
      }

    // Transitional demo convenience wrapper. The main thread API is StepRef-first.
    def hijack(targetName: String): HijackAction = hijack(stepRef(targetName))

    def jump(target: StepRef): Unit = {
      ThreadRuntimeLogic.emitJump(irState, layoutState, runtime, target)
    }

    // Transitional demo convenience wrapper. The main thread API is StepRef-first.
    def jump(targetName: String): Unit = jump(stepRef(targetName))

    def waitCondition(cond: Bool): Unit = {
      ThreadRuntimeLogic.emitWaitCondition(irState, layoutState, runtime, cond)
    }

    def build(owner: HwContextEntity): RuntimeContext = {
      val plan = ThreadRuntimeLogic.analyzeControl(irState, layoutState)
      val runtime = ThreadRuntimeLogic.materializeRuntime(
        owner = owner,
        irState = irState,
        layoutState = layoutState,
        plan = plan,
        initialState = HwOS.kernel.system.RuntimeLifecycle.Running,
      )
      ThreadRuntimeLogic.lowerRuntime(irState, layoutState, plan, runtime)
      runtime
    }

    def layout: Seq[StepLayout] =
      irState.program.steps.map(step => StepLayout(step.name, step.allocatedAddress, step.loweredStandalone))

    def preAnalyzeOnly(): Unit = {
      ThreadRuntimeLogic.analyzeControl(irState, layoutState)
    }

    def plannedStandaloneLabels: Seq[String] = {
      ThreadRuntimeLogic.analyzeControl(irState, layoutState).standaloneIndices.map(idx => irState.program.steps(idx).name)
    }

    def hasReturningStep: Boolean =
      ThreadRuntimeLogic.analyzeControl(irState, layoutState).hasReturningStep

    def stepEffects(stepName: String): Seq[String] =
      irState.program.steps
        .find(_.name == stepName)
        .toSeq
        .flatMap(_.effects)
        .map {
          case HwOS.kernel.thread.step.SystemEffect.ReturnEffect    => "return"
          case HwOS.kernel.thread.step.SystemEffect.JumpEffect(_)   => "jump"
          case HwOS.kernel.thread.step.SystemEffect.HijackEffect(_) => "hijack"
          case HwOS.kernel.thread.step.SystemEffect.WaitEffect      => "wait"
        }

    def runtime: RuntimeContext =
      layoutState.runtimeContext.getOrElse(throw new Exception(s"[ThreadStepDemo] Program '$name' has not been built yet."))
  }
}
