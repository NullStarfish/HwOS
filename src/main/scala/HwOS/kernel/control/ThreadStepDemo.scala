package HwOS.kernel.control

import chisel3._
import HwOS.kernel.context.HwContextEntity
import HwOS.kernel.system.{Kernel, RuntimeContext}
import HwOS.kernel.thread.{StepRef}
import HwOS.kernel.thread.step.{ThreadIR, ThreadLayout, ThreadRuntimeLogic}

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
      ThreadIR.defineHijack(irState, target) {
        ThreadLayout.lowerStepAt(irState, layoutState, ThreadLayout.resolveStepRef(irState, layoutState, target))
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
      val runtime = ThreadRuntimeLogic.allocateRuntime(
        owner = owner,
        irState = irState,
        layoutState = layoutState,
        initialState = HwOS.kernel.system.RuntimeLifecycle.Running,
      )
      ThreadRuntimeLogic.lowerProgram(irState, layoutState, runtime)
      ThreadLayout.validateJumpTargets(irState)
      runtime
    }

    def layout: Seq[StepLayout] =
      irState.program.steps.map(step => StepLayout(step.name, step.allocatedAddress, step.loweredStandalone))

    def runtime: RuntimeContext =
      layoutState.runtimeContext.getOrElse(throw new Exception(s"[ThreadStepDemo] Program '$name' has not been built yet."))
  }
}
