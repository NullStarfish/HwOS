package HwOS.kernel.control

import chisel3._
import HwOS.kernel.context.HwContextEntity
import HwOS.kernel.system.{RuntimeContext, RuntimeLifecycle}

private[kernel] object StepOnlyLayout {
  final class LayoutState {
    var runtimeContext: Option[RuntimeContext] = None
    var jumpTargets = Map.empty[String, UInt]
    var currentLoweringStep: Int = -1
    var currentEntryStep: Int = -1
  }

  def resolveStepIndex(irState: StepOnlyIR.IRState, programName: String, stepName: String): Int = {
    val idx = irState.ir.labels.indexOf(stepName)
    if (idx < 0) {
      throw new Exception(s"[StepOnlyPrototype] Unknown step '$stepName' in program '$programName'.")
    }
    idx
  }

  def lowerStepAt(
      irState: StepOnlyIR.IRState,
      layoutState: LayoutState,
      index: Int,
  ): Unit = {
    val saveLowering = layoutState.currentLoweringStep
    val saveEntry = layoutState.currentEntryStep
    if (layoutState.currentEntryStep < 0) {
      layoutState.currentEntryStep = index
    }
    layoutState.currentLoweringStep = index
    irState.ir.steps(index).block()
    layoutState.currentLoweringStep = saveLowering
    layoutState.currentEntryStep = saveEntry
  }

  def lowerStepNamed(
      irState: StepOnlyIR.IRState,
      layoutState: LayoutState,
      programName: String,
      stepName: String,
  ): Unit = lowerStepAt(irState, layoutState, resolveStepIndex(irState, programName, stepName))

  def buildProgram(
      programName: String,
      irState: StepOnlyIR.IRState,
      layoutState: LayoutState,
      owner: HwContextEntity,
  ): RuntimeContext = {
    if (layoutState.runtimeContext.isDefined) {
      throw new Exception(s"[StepOnlyPrototype] Program '$programName' was built twice.")
    }
    if (irState.ir.steps.isEmpty) {
      throw new Exception(s"[StepOnlyPrototype] Program '$programName' has no steps.")
    }

    val kernel = owner.kernel
    val hijackedTargets = irState.hijackedTargetNames.map(resolveStepIndex(irState, programName, _)).toSet
    val standaloneSteps = irState.ir.steps.zipWithIndex.collect { case (step, index) if !hijackedTargets.contains(index) => step }
    val segment = kernel.addressSpace.reserveCodeSegment(programName, standaloneSteps.map(_.name))
    val allocatedRuntime = kernel.addressSpace.allocateRuntimeContext(
      owner = owner,
      bindingName = s"${programName}_runtime",
      segment = segment,
      initialState = RuntimeLifecycle.Running,
    )
    layoutState.runtimeContext = Some(allocatedRuntime)

    layoutState.jumpTargets = standaloneSteps.iterator.map(step => step.name -> allocatedRuntime.cursor.addressOf(step.name)).toMap
    for (step <- standaloneSteps) {
      step.allocatedAddress = segment.addressOf(step.name)
      step.loweredStandalone = true
    }
    for ((step, index) <- irState.ir.steps.zipWithIndex if hijackedTargets.contains(index)) {
      step.loweredStandalone = false
    }

    val standaloneIndices = irState.ir.steps.indices.filterNot(hijackedTargets.contains).toVector
    for ((index, pos) <- standaloneIndices.zipWithIndex) {
      val step = irState.ir.steps(index)
      val nextAddr = standaloneIndices.lift(pos + 1).map(nextIndex => irState.ir.steps(nextIndex).allocatedAddress).getOrElse(step.allocatedAddress)
      when(
        allocatedRuntime.stateReg === RuntimeLifecycle.Running.U(allocatedRuntime.stateReg.getWidth.W) &&
          allocatedRuntime.cursor.reg === step.allocatedAddress.U(allocatedRuntime.cursor.reg.getWidth.W),
      ) {
        allocatedRuntime.cursor.reg := nextAddr.U(allocatedRuntime.cursor.reg.getWidth.W)
        lowerStepAt(irState, layoutState, index)
      }
    }

    allocatedRuntime
  }
}
