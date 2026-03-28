package HwOS.kernel.control

import chisel3._
import HwOS.kernel.context.HwContextEntity
import HwOS.kernel.system.{Kernel, RuntimeContext}
import HwOS.kernel.thread.{RuntimeControlHostAdapter, StepRef}
import HwOS.kernel.thread.step.EdgeAction
import HwOS.kernel.thread.step.{ControlProgramBuilder, PreLoweringAnalysis, ThreadIR, ThreadLayout, ThreadRuntimeLogic}

object ThreadStepDemo {
  type StepLayout = ThreadLayout.StepLayout
  val StepLayout = ThreadLayout.StepLayout
  type HijackAction = ThreadIR.HijackAction

  final class Program(val name: String)(implicit kernel: Kernel) {
    private val builder = new ControlProgramBuilder(name, kernel.addressSpace.createVirtualProgram(name))
    private val irState = builder.state.irState
    private var compiledProgramOpt: Option[HwOS.kernel.thread.step.ControlProgram.CompiledControlProgram] = None
    private var runtimeOpt: Option[RuntimeContext] = None
    private var hostOpt: Option[RuntimeControlHostAdapter] = None

    def Step(stepName: String)(block: => Unit): Unit = {
      builder.defineStep(stepName)(block)
    }

    def stepRef(stepName: String): StepRef = StepRef.NamedStepRef(stepName)

    def Next: StepRef = StepRef.NextStepRef()

    def hijack(target: StepRef): HijackAction =
      ThreadIR.defineHijack(irState) {
        if (PreLoweringAnalysis.isActive) {
          PreLoweringAnalysis.record(EdgeAction.Hijack(target))
        } else {
          ThreadRuntimeLogic.emitHijack(builder, compiledProgram, host, target)
        }
      }

    // Transitional demo convenience wrapper. The main thread API is StepRef-first.
    def hijack(targetName: String): HijackAction = hijack(stepRef(targetName))

    def jump(target: StepRef): Unit = {
      ThreadRuntimeLogic.emitJump(builder, compiledProgram, host, target)
    }

    // Transitional demo convenience wrapper. The main thread API is StepRef-first.
    def jump(targetName: String): Unit = jump(stepRef(targetName))

    def waitCondition(cond: => Bool): Unit = {
      ThreadRuntimeLogic.emitWaitCondition(builder, host, cond)
    }

    def build(owner: HwContextEntity): RuntimeContext = {
      val plan = ThreadRuntimeLogic.compileProgram(builder)
      val hostAdapter = new RuntimeControlHostAdapter(
        entity = owner,
        hostName = name,
      )
      val lowered = hostAdapter.materializeProgram(
        builder = builder,
        compiledProgram = plan,
        initialState = HwOS.kernel.system.RuntimeLifecycle.Running,
      )
      compiledProgramOpt = Some(lowered.compiledProgram)
      runtimeOpt = Some(lowered.runtime)
      hostOpt = Some(hostAdapter)
      ThreadRuntimeLogic.lowerRuntime(builder, lowered.compiledProgram, hostAdapter)
      lowered.runtime
    }

    def layout: Seq[StepLayout] =
      compiledProgram.steps.map(step => StepLayout(step.name, step.allocatedAddress, step.loweredStandalone))

    def preAnalyzeOnly(): Unit = {
      compiledProgramOpt = Some(ThreadRuntimeLogic.compileProgram(builder))
    }

    def plannedStandaloneLabels: Seq[String] = {
      compiledProgram.layout.standaloneLabels
    }

    def hasReturningStep: Boolean =
      compiledProgram.hasReturningStep

    def stepEffects(stepName: String): Seq[String] =
      irState.program.steps
        .find(_.name == stepName)
        .toSeq
        .flatMap(_.edgeActions)
        .map(_.kindName)

    def stepActions(stepName: String): Seq[EdgeAction] =
      irState.program.steps
        .find(_.name == stepName)
        .toSeq
        .flatMap(_.edgeActions)

    def runtime: RuntimeContext =
      runtimeOpt.getOrElse(throw new Exception(s"[ThreadStepDemo] Program '$name' has not been built yet."))

    private def compiledProgram =
      compiledProgramOpt.getOrElse(throw new Exception(s"[ThreadStepDemo] Program '$name' has not been compiled yet."))

    private def host =
      hostOpt.getOrElse(throw new Exception(s"[ThreadStepDemo] Program '$name' has not been built yet."))
  }
}
