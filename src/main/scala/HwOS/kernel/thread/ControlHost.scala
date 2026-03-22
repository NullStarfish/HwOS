package HwOS.kernel.thread

import HwOS.kernel.context.HwContextEntity
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.RuntimeContext
import HwOS.kernel.thread.step.ControlProgram.CompiledControlProgram
import chisel3.{Bool, UInt}

private[kernel] trait ControlHostAdapter {
  def controlCursor: UInt
  def writeCursor(target: UInt): Unit
  def canExecute: Bool
  def onControlExit(): Unit
  def resetControl(): Unit
  def entryAddress: UInt
  def pcForStep(stepIndex: Int): UInt
}

private[kernel] trait ControlHost extends ControlHostAdapter {
  def hostName: String
  def owner: HwProcess
  def runtimeStateHandle: RuntimeContext
  def pc: UInt = controlCursor
  def isActive: Bool
  def isDone: Bool
  def start(): Unit
  def exit(): Unit
  def reset(): Unit
  def onProgramBuilt(compiledProgram: CompiledControlProgram, runtimeHandle: RuntimeContext): Unit = ()
  def entity: HwContextEntity
}
