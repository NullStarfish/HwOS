package HwOS.kernel.thread.step

import HwOS.kernel.thread.StepRef

private[kernel] sealed trait EdgeAction {
  def guardDepth: Int
}

private[kernel] object EdgeAction {
  final case class ReturnAction(guardDepth: Int, returnTarget: Option[String]) extends EdgeAction
  final case class JumpAction(guardDepth: Int, target: StepRef) extends EdgeAction
}
