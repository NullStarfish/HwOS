package HwOS.kernel.control

import chisel3._
import HwOS.kernel.context.HwContextEntity
import HwOS.kernel.system.Kernel

object StepOnlyPrototype {
  final case class StepLayout(name: String, address: Int, standalone: Boolean)
  final class HijackAction private[StepOnlyPrototype] (private val emitThunk: () => Unit) {
    def apply(): Unit = emitThunk()
  }

  final class Program(val name: String)(implicit kernel: Kernel) {
    private val ir = kernel.createVirtualProgram(name)
    private var runtimeContext: Option[Kernel#RuntimeContext] = None
    private var jumpTargets = Map.empty[String, UInt]
    private var currentLoweringStep: Int = -1
    private var currentEntryStep: Int = -1
    private val hijackedTargetNames = scala.collection.mutable.Set[String]()

    def Step(stepName: String)(block: => Unit): Unit = {
      if (ir.labels.contains(stepName)) {
        throw new Exception(s"[StepOnlyPrototype] Duplicate step '$stepName' in program '$name'.")
      }

      ir.appendStep(stepName, () => {
        val save = currentLoweringStep
        currentLoweringStep = ir.labels.indexOf(stepName)
        block
        currentLoweringStep = save
      })
    }

    def hijack(targetName: String): HijackAction = {
      hijackedTargetNames += targetName
      new HijackAction(() => lowerStepNamed(targetName))
    }

    def jump(targetName: String): Unit = {
      val pc = runtime.cursor.reg
      val targetPc = jumpTargets.getOrElse(
        targetName,
        throw new Exception(s"[StepOnlyPrototype] Unknown jump target '$targetName' in '$name'."),
      )
      pc := targetPc
    }

    def waitCondition(cond: Bool): Unit = {
      val pc = runtime.cursor.reg
      if (currentEntryStep < 0) {
        throw new Exception(s"[StepOnlyPrototype] waitCondition() must be used while lowering a Step in '$name'.")
      }
      val entryAddr = ir.steps(currentEntryStep).allocatedAddress
      when(!cond) {
        pc := entryAddr.U(pc.getWidth.W)
      }
    }

    def build(owner: HwContextEntity): Kernel#RuntimeContext = {
      if (runtimeContext.isDefined) {
        throw new Exception(s"[StepOnlyPrototype] Program '$name' was built twice.")
      }
      if (ir.steps.isEmpty) {
        throw new Exception(s"[StepOnlyPrototype] Program '$name' has no steps.")
      }

      val hijackedTargets = hijackedTargetNames.map(resolveStepIndex).toSet
      val standaloneSteps = ir.steps.zipWithIndex.collect { case (step, index) if !hijackedTargets.contains(index) => step }
      val segment = kernel.reserveCodeSegment(name, standaloneSteps.map(_.name))
      val allocatedRuntime = kernel.allocateRuntimeContext(
        owner = owner,
        bindingName = s"${name}_runtime",
        segment = segment,
        initialState = kernel.RuntimeLifecycle.Running,
      )
      runtimeContext = Some(allocatedRuntime)

      jumpTargets = standaloneSteps.iterator.map(step => step.name -> allocatedRuntime.cursor.addressOf(step.name)).toMap
      for (step <- standaloneSteps) {
        step.allocatedAddress = segment.addressOf(step.name)
        step.loweredStandalone = true
      }
      for ((step, index) <- ir.steps.zipWithIndex if hijackedTargets.contains(index)) {
        step.loweredStandalone = false
      }

      val standaloneIndices = ir.steps.indices.filterNot(hijackedTargets.contains).toVector
      for ((index, pos) <- standaloneIndices.zipWithIndex) {
        val step = ir.steps(index)
        val nextAddr = standaloneIndices.lift(pos + 1).map(nextIndex => ir.steps(nextIndex).allocatedAddress).getOrElse(step.allocatedAddress)
        when(
          allocatedRuntime.stateReg === kernel.RuntimeLifecycle.Running.U(allocatedRuntime.stateReg.getWidth.W) &&
            allocatedRuntime.cursor.reg === step.allocatedAddress.U(allocatedRuntime.cursor.reg.getWidth.W),
        ) {
          allocatedRuntime.cursor.reg := nextAddr.U(allocatedRuntime.cursor.reg.getWidth.W)
          lowerStepAt(index)
        }
      }

      allocatedRuntime
    }

    def layout: Seq[StepLayout] =
      ir.steps.map(step => StepLayout(step.name, step.allocatedAddress, step.loweredStandalone))

    def runtime: Kernel#RuntimeContext =
      runtimeContext.getOrElse(throw new Exception(s"[StepOnlyPrototype] Program '$name' has not been built yet."))

    private def resolveStepIndex(stepName: String): Int = {
      val idx = ir.labels.indexOf(stepName)
      if (idx < 0) {
        throw new Exception(s"[StepOnlyPrototype] Unknown step '$stepName' in program '$name'.")
      }
      idx
    }

    private def lowerStepNamed(stepName: String): Unit = lowerStepAt(resolveStepIndex(stepName))

    private def lowerStepAt(index: Int): Unit = {
      val saveLowering = currentLoweringStep
      val saveEntry = currentEntryStep
      if (currentEntryStep < 0) {
        currentEntryStep = index
      }
      currentLoweringStep = index
      ir.steps(index).block()
      currentLoweringStep = saveLowering
      currentEntryStep = saveEntry
    }
  }
}
