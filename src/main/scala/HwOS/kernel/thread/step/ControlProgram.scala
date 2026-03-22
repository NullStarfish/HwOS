package HwOS.kernel.thread.step

import HwOS.kernel.system.{RuntimeContext, VirtualProgram, VirtualStepRecord}

private[kernel] object ControlProgram {
  type PatchTarget = ThreadCompilePlan.PatchTarget
  val PatchTarget = ThreadCompilePlan.PatchTarget

  type CompiledEdgeEffect = ThreadCompilePlan.EdgeEffect
  type CompiledStepPlan = ThreadCompilePlan.StepPlan

  final case class CompiledProgramLayout(
      standaloneIndices: Vector[Int],
      suppressedStandalone: Set[Int],
      entryIndex: Int,
      stepIndicesByLabel: Map[String, Int],
      standaloneLabels: Vector[String],
      stepAddresses: Map[Int, Int] = Map.empty,
  )

  final case class CompiledControlProgram(
      programName: String,
      program: VirtualProgram,
      compilePlan: ThreadCompilePlan.ThreadCompilePlan,
      layout: CompiledProgramLayout,
  ) {
    def steps: Seq[VirtualStepRecord] = program.steps
    def stepPlan(stepIndex: Int): CompiledStepPlan = compilePlan.stepPlan(stepIndex)
    def standaloneIndices: Vector[Int] = compilePlan.standaloneIndices
    def suppressedStandalone: Set[Int] = compilePlan.suppressedStandalone
    def hasReturningStep: Boolean = compilePlan.hasReturningStep
    def entryIndex: Int = compilePlan.entryIndex
    def stepIndex(stepName: String): Int =
      layout.stepIndicesByLabel.getOrElse(
        stepName,
        throw new Exception(s"[Thread] Unknown step '$stepName' in program '$programName'."),
      )
    def stepAddress(stepIndex: Int): Int =
      layout.stepAddresses.getOrElse(
        stepIndex,
        throw new Exception(s"[Thread] Step index '$stepIndex' in program '$programName' has no allocated address."),
      )
    def stepAddressOf(stepName: String): Int =
      stepAddress(stepIndex(stepName))

    def withLayout(resolvedLayout: CompiledProgramLayout): CompiledControlProgram =
      copy(layout = resolvedLayout)
  }
}

private[kernel] final class ProgramBuilderState(
    val programName: String,
    val program: VirtualProgram,
) {
  val irState: ThreadIR.IRState = new ThreadIR.IRState(programName, program)
}

private[kernel] final class ControlProgramBuilder(
    val programName: String,
    val program: VirtualProgram,
) {
  val state: ProgramBuilderState = new ProgramBuilderState(programName, program)

  def defineStep(stepName: String)(block: => Unit): Unit =
    ThreadIR.defineStep(state.irState, stepName)(block)

  def defineGlobal(block: => Unit): Unit =
    ThreadIR.defineGlobal(state.irState)(block)

  def runGlobals(): Unit =
    ThreadIR.runGlobals(state.irState)

  def steps: Seq[VirtualStepRecord] = program.steps

  def lastDefinedStepName: Option[String] = program.steps.lastOption.map(_.name)

  def compiled(): ControlProgram.CompiledControlProgram =
    ThreadCompileAnalysis.compile(this)
}

private[kernel] object CurrentProgramContext {
  private final case class LoweringState(
      entryStepIndex: Int,
      currentStepIndex: Int,
      currentDebugRecord: VirtualStepRecord,
  )

  private val loweringState = new ThreadLocal[List[LoweringState]] {
    override def initialValue(): List[LoweringState] = Nil
  }

  def withLowering[T](
      entryStepIndex: Int,
      currentStepIndex: Int,
      currentDebugRecord: VirtualStepRecord,
  )(block: => T): T = {
    loweringState.set(LoweringState(entryStepIndex, currentStepIndex, currentDebugRecord) :: loweringState.get())
    try block
    finally loweringState.set(loweringState.get().drop(1))
  }

  def currentLoweringStepIndex: Option[Int] =
    loweringState.get().headOption.map(_.currentStepIndex)

  def currentEntryStepIndex: Option[Int] =
    loweringState.get().headOption.map(_.entryStepIndex)

  def currentDebugRecord: Option[VirtualStepRecord] =
    loweringState.get().headOption.map(_.currentDebugRecord)
}

private[kernel] final case class LoweredProgramRuntime(
    compiledProgram: ControlProgram.CompiledControlProgram,
    runtime: RuntimeContext,
)
