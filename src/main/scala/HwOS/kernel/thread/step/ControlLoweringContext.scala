package HwOS.kernel.thread.step

private[kernel] object ControlLoweringContext {
  final case class LoweringStep(entryStepIndex: Int, stepIndex: Int)

  private val activeStep = new ThreadLocal[Option[LoweringStep]] {
    override def initialValue(): Option[LoweringStep] = None
  }

  def isActive: Boolean = activeStep.get().nonEmpty

  def current: LoweringStep =
    activeStep.get().getOrElse(
      throw new Exception("[Thread] No active lowering step context."),
    )

  def withStep[T](entryStepIndex: Int, stepIndex: Int)(block: => T): T = {
    val saved = activeStep.get()
    activeStep.set(Some(LoweringStep(entryStepIndex, stepIndex)))
    try block
    finally {
      activeStep.set(saved)
    }
  }
}
