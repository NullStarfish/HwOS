package HwOS.kernel.thread.step

import HwOS.kernel.thread.StepRef

private[kernel] sealed trait SystemEffect

private[kernel] object SystemEffect {
  case object ReturnEffect extends SystemEffect
  final case class JumpEffect(target: StepRef) extends SystemEffect
  final case class HijackEffect(target: StepRef) extends SystemEffect
  case object WaitEffect extends SystemEffect
}
