package HwOS.kernel.control

import chisel3._
import HwOS.kernel.context.HwContextEntity
import HwOS.kernel.system.{Kernel, RuntimeContext}

object StepOnlyPrototype {
  type StepLayout = StepOnlyIR.StepLayout
  val StepLayout = StepOnlyIR.StepLayout
  type HijackAction = StepOnlyIR.HijackAction

  final class Program(val name: String)(implicit kernel: Kernel) {
    private val irState = new StepOnlyIR.IRState(kernel.addressSpace.createVirtualProgram(name))
    private val layoutState = new StepOnlyLayout.LayoutState()

    def Step(stepName: String)(block: => Unit): Unit = {
      StepOnlyIR.defineStep(irState, name, stepName) {
        val save = layoutState.currentLoweringStep
        layoutState.currentLoweringStep = irState.ir.labels.indexOf(stepName)
        block
        layoutState.currentLoweringStep = save
      }
    }

    def hijack(targetName: String): HijackAction =
      StepOnlyIR.defineHijack(irState, targetName) {
        StepOnlyLayout.lowerStepNamed(irState, layoutState, name, targetName)
      }

    def jump(targetName: String): Unit = {
      val pc = runtime.cursor.reg
      val targetPc = layoutState.jumpTargets.getOrElse(
        targetName,
        throw new Exception(s"[StepOnlyPrototype] Unknown jump target '$targetName' in '$name'."),
      )
      pc := targetPc
    }

    def waitCondition(cond: Bool): Unit = {
      val pc = runtime.cursor.reg
      if (layoutState.currentEntryStep < 0) {
        throw new Exception(s"[StepOnlyPrototype] waitCondition() must be used while lowering a Step in '$name'.")
      }
      val entryAddr = irState.ir.steps(layoutState.currentEntryStep).allocatedAddress
      when(!cond) {
        pc := entryAddr.U(pc.getWidth.W)
      }
    }

    def build(owner: HwContextEntity): RuntimeContext =
      StepOnlyLayout.buildProgram(name, irState, layoutState, owner)

    def layout: Seq[StepLayout] =
      irState.ir.steps.map(step => StepLayout(step.name, step.allocatedAddress, step.loweredStandalone))

    def runtime: RuntimeContext =
      layoutState.runtimeContext.getOrElse(throw new Exception(s"[StepOnlyPrototype] Program '$name' has not been built yet."))
  }
}
