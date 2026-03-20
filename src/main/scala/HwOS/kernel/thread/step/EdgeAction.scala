package HwOS.kernel.thread.step

import HwOS.kernel.thread.StepRef

private[kernel] sealed trait EdgeAction

private[kernel] object EdgeAction {
  case object ReturnMeta extends EdgeAction

  final case class JumpMeta(target: StepRef) extends EdgeAction

  final case class HijackMeta(target: StepRef) extends EdgeAction

  case object WaitMeta extends EdgeAction

  final case class ReturnAction(guardDepth: Int, returnTarget: Option[String]) extends EdgeAction
  final case class JumpAction(guardDepth: Int, target: StepRef) extends EdgeAction
}
