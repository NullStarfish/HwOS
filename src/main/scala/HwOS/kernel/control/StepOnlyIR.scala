package HwOS.kernel.control

import HwOS.kernel.system.Kernel

private[kernel] object StepOnlyIR {
  final case class StepLayout(name: String, address: Int, standalone: Boolean)

  final class HijackAction(private val emitThunk: () => Unit) {
    def apply(): Unit = emitThunk()
  }

  final class IRState(val ir: HwOS.kernel.system.VirtualProgram) {
    val hijackedTargetNames = scala.collection.mutable.Set[String]()
  }

  def defineStep(irState: IRState, programName: String, stepName: String)(block: => Unit): Unit = {
    if (irState.ir.labels.contains(stepName)) {
      throw new Exception(s"[StepOnlyPrototype] Duplicate step '$stepName' in program '$programName'.")
    }

    irState.ir.appendStep(stepName, () => block)
  }

  def defineHijack(irState: IRState, targetName: String)(emitThunk: => Unit): HijackAction = {
    irState.hijackedTargetNames += targetName
    new HijackAction(() => emitThunk)
  }
}
