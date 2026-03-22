package HwOS.kernel.thread

import HwOS.kernel.system.RuntimeContext
import HwOS.kernel.thread.step.ControlProgram.CompiledControlProgram
import chisel3._

private[kernel] final class ThreadHost(
    thread: HardwareThread,
    initialRuntimeContext: Option[RuntimeContext] = None,
) extends RuntimeControlHostAdapter(thread, thread.name, initialRuntimeContext)
    with ControlHost {
  override val hostName: String = thread.name
  override def owner = thread.owner
  override def runtimeStateHandle: RuntimeContext = runtimeContext
  override val entity = thread

  override def isActive = canExecute
  override def isDone =
    runtimeStateHandle.stateReg === HwOS.kernel.system.RuntimeLifecycle.Done.U(runtimeStateHandle.stateReg.getWidth.W)

  override def start(): Unit = {
    runtimeStateHandle.cursor.reg := runtimeStateHandle.cursor.entryAddress
    runtimeStateHandle.stateReg := HwOS.kernel.system.RuntimeLifecycle.Running.U(runtimeStateHandle.stateReg.getWidth.W)
  }

  override def exit(): Unit = onControlExit()

  override def reset(): Unit = resetControl()

  override def onProgramBuilt(compiledProgram: CompiledControlProgram, runtimeHandle: RuntimeContext): Unit = {
    installProgram(compiledProgram, runtimeHandle)
  }
}
