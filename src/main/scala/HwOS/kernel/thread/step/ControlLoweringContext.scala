package HwOS.kernel.thread.step

private[kernel] object ControlLoweringContext {
  final case class LoweringStep(entryStepIndex: Int, stepIndex: Int)

  private val activeStep = new ThreadLocal[Option[LoweringStep]] {
    override def initialValue(): Option[LoweringStep] = None
  }
  private val edgePatchOrdinalByTarget = new ThreadLocal[Map[Int, Int]] {
    override def initialValue(): Map[Int, Int] = Map.empty
  }

  def isActive: Boolean = activeStep.get().nonEmpty

  def current: LoweringStep =
    activeStep.get().getOrElse(
      throw new Exception("[Thread] No active lowering step context."),
    )

  def nextEdgePatchOrdinal(targetStepIndex: Int): Int = {
    val currentMap = edgePatchOrdinalByTarget.get()
    val ordinal = currentMap.getOrElse(targetStepIndex, 0)
    edgePatchOrdinalByTarget.set(currentMap.updated(targetStepIndex, ordinal + 1))
    ordinal
  }

  def withStep[T](entryStepIndex: Int, stepIndex: Int)(block: => T): T = {
    val saved = activeStep.get()
    val savedPatchOrdinals = edgePatchOrdinalByTarget.get()
    activeStep.set(Some(LoweringStep(entryStepIndex, stepIndex)))
    edgePatchOrdinalByTarget.set(Map.empty)
    try block
    finally {
      activeStep.set(saved)
      edgePatchOrdinalByTarget.set(savedPatchOrdinals)
    }
  }
}
