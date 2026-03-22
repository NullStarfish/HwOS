package HwOS.kernel.thread

import HwOS.kernel.context.HwContextEntity
import HwOS.kernel.system.{RuntimeContext, RuntimeLifecycle}
import HwOS.kernel.thread.step.{ControlProgramBuilder, ThreadLayout}
import HwOS.kernel.thread.step.ControlProgram.CompiledControlProgram
import chisel3._

private[kernel] final case class MaterializedRuntimeProgram(
    compiledProgram: CompiledControlProgram,
    runtime: RuntimeContext,
)

private[kernel] class RuntimeControlHostAdapter(
    val entity: HwContextEntity,
    val hostName: String,
    private var runtimeContextOpt: Option[RuntimeContext] = None,
    private var compiledProgramOpt: Option[CompiledControlProgram] = None,
) extends ControlHostAdapter {
  protected def runtimeContext: RuntimeContext =
    runtimeContextOpt.getOrElse(throw new Exception(s"[Thread] Runtime host '$hostName' has no runtime context."))

  protected def compiledProgram: CompiledControlProgram =
    compiledProgramOpt.getOrElse(throw new Exception(s"[Thread] Runtime host '$hostName' has no compiled program."))

  protected final def installProgram(compiledProgram: CompiledControlProgram, runtimeHandle: RuntimeContext): Unit = {
    runtimeContextOpt = Some(runtimeHandle)
    compiledProgramOpt = Some(compiledProgram)
  }

  def materializeProgram(
      builder: ControlProgramBuilder,
      compiledProgram: CompiledControlProgram,
      initialState: Int,
  ): MaterializedRuntimeProgram = {
    if (runtimeContextOpt.isDefined) {
      throw new Exception(s"[Thread] Program '${builder.programName}' was built twice.")
    }
    if (builder.steps.isEmpty) {
      throw new Exception(s"[Thread] Program '${builder.programName}' has no steps.")
    }

    val segment = entity.kernel.addressSpace.reserveCodeSegment(builder.programName, compiledProgram.layout.standaloneLabels)
    val runtime = entity.kernel.addressSpace.allocateRuntimeContext(
      owner = entity,
      bindingName = s"${builder.programName}_runtime",
      segment = segment,
      initialState = initialState,
    )
    val stepAddresses = compiledProgram.layout.standaloneIndices.iterator.map { index =>
      index -> segment.addressOf(builder.steps(index).name)
    }.toMap
    val loweredProgram = ThreadLayout.materializeLayout(builder.state.irState, compiledProgram, stepAddresses)
    onProgramBuilt(loweredProgram, runtime)
    MaterializedRuntimeProgram(loweredProgram, runtime)
  }

  def onProgramBuilt(compiledProgram: CompiledControlProgram, runtimeHandle: RuntimeContext): Unit = {
    installProgram(compiledProgram, runtimeHandle)
  }

  override def controlCursor: UInt = runtimeContext.cursor.reg

  override def writeCursor(target: UInt): Unit = {
    runtimeContext.cursor.reg := target
  }

  override def canExecute: Bool =
    runtimeContext.stateReg === RuntimeLifecycle.Running.U(runtimeContext.stateReg.getWidth.W)

  override def onControlExit(): Unit = {
    runtimeContext.stateReg := RuntimeLifecycle.Done.U(runtimeContext.stateReg.getWidth.W)
  }

  override def resetControl(): Unit = {
    runtimeContext.cursor.reg := runtimeContext.cursor.entryAddress
    runtimeContext.stateReg := RuntimeLifecycle.Idle.U(runtimeContext.stateReg.getWidth.W)
  }

  override def entryAddress: UInt = runtimeContext.cursor.entryAddress

  override def pcForStep(stepIndex: Int): UInt =
    compiledProgram.stepAddress(stepIndex).U(runtimeContext.cursor.reg.getWidth.W)
}
