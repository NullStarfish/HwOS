package HwOS.kernel.thread.step

import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import HwOS.kernel.debug.CallStack
import HwOS.kernel.system.VirtualProgram

private[kernel] object ThreadIR {
  final class HijackAction(private val emitThunk: () => Unit) {
    def apply(): Unit = emitThunk()
  }

  final class IRState(
      val programName: String,
      val program: VirtualProgram,
  ) {
    val globals = ArrayBuffer[() => Unit]()
  }

  def defineStep(irState: IRState, stepName: String)(block: => Unit): Unit = {
    if (irState.program.labels.contains(stepName)) {
      throw new Exception(s"[Thread] Duplicate step '$stepName' in program '${irState.programName}'.")
    }

    irState.program.appendStep(stepName, () => block)
  }

  def defineHijack(irState: IRState)(emitThunk: => Unit): HijackAction = {
    new HijackAction(() => emitThunk)
  }

  def defineGlobal(irState: IRState)(block: => Unit): Unit = {
    irState.globals += { () => block }
  }

  def runGlobals(irState: IRState): Unit = {
    irState.globals.foreach(_())
  }

  def callSnapshot: Seq[String] = CallStack.getSnapshot
}
