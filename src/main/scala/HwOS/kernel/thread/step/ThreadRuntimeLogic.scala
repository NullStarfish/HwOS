package HwOS.kernel.thread.step

import HwOS.kernel.thread.ControlHostAdapter
import HwOS.kernel.thread.StepRef
import HwOS.kernel.thread.step.ControlProgram.CompiledControlProgram
import HwOS.kernel.thread.step.ThreadCompilePlan.ThreadCompilePlan
import chisel3.Bool

private[kernel] object ThreadRuntimeLogic {
  def analyzeControl(irState: ThreadIR.IRState): ThreadCompilePlan =
    ThreadCompileAnalysis.analyze(irState)

  def compileProgram(builder: ControlProgramBuilder): CompiledControlProgram =
    ThreadCompileAnalysis.compile(builder)

  def emitJump(
      builder: ControlProgramBuilder,
      compiledProgram: => CompiledControlProgram,
      host: => ControlHostAdapter,
      target: StepRef,
  ): Unit = {
    ControlRuntimeCore.emitJump(builder, compiledProgram, host, target)
  }

  def emitWaitCondition(
      builder: ControlProgramBuilder,
      host: => ControlHostAdapter,
      cond: Bool,
  ): Unit = {
    ControlRuntimeCore.emitWaitCondition(builder, host, cond)
  }

  def emitHijack(
      builder: ControlProgramBuilder,
      compiledProgram: => CompiledControlProgram,
      host: => ControlHostAdapter,
      target: StepRef,
  ): Unit = {
    ControlRuntimeCore.emitHijack(builder, compiledProgram, host, target)
  }

  def lowerRuntime(
      builder: ControlProgramBuilder,
      compiledProgram: CompiledControlProgram,
      host: ControlHostAdapter,
  ): Unit = {
    ControlRuntimeCore.lowerRuntime(builder, compiledProgram, host)
  }

  def recordEdgePatch(
      builder: ControlProgramBuilder,
      target: StepRef,
      block: => Unit,
  ): Unit = {
    ControlRuntimeCore.recordEdgePatch(builder, target, block)
  }

  def emitCompiledEffects(
      builder: ControlProgramBuilder,
      compiledProgram: CompiledControlProgram,
      host: ControlHostAdapter,
      stepIndex: Int,
  ): Unit = {
    ControlRuntimeCore.emitCompiledEffects(builder, compiledProgram, host, stepIndex)
  }

  private[kernel] def emitReturnEdgePatch(
      patch: Option[HwOS.kernel.system.CallProtocolContext.ReturnEdgePatch],
  ): Unit = {
    ControlRuntimeCore.emitReturnEdgePatch(patch)
  }
}
