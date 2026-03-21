package HwOS.kernel.thread.step

import HwOS.kernel.system.CallProtocolContext.ContinuationSnapshot
import HwOS.kernel.thread.StepRef
import chisel3.Bool

private[kernel] sealed trait EdgeAction {
  def kindName: String
  def guardDepth: Int = 0
  def isReturning: Boolean = false
  def jumpTarget: Option[StepRef] = None
  def hijackTarget: Option[StepRef] = None
  def emitInLowering: Boolean = false
}

private[kernel] object EdgeAction {
  final case class Return(
      override val guardDepth: Int = 0,
      continuation: Option[ContinuationSnapshot] = None,
      override val emitInLowering: Boolean = false,
  ) extends EdgeAction {
    override val kindName: String = "return"
    override val isReturning: Boolean = true

    def returnTarget: Option[String] = continuation.flatMap(_.targetLabel)
    def returnEdgePatch = continuation.flatMap(_.returnEdgePatch)
  }

  final case class Jump(
      override val guardDepth: Int = 0,
      target: StepRef,
      override val emitInLowering: Boolean = false,
  ) extends EdgeAction {
    override val kindName: String = "jump"
    override val jumpTarget: Option[StepRef] = Some(target)
  }

  final case class Hijack(target: StepRef) extends EdgeAction {
    override val kindName: String = "hijack"
    override val hijackTarget: Option[StepRef] = Some(target)
  }

  final case class Wait(cond: Bool) extends EdgeAction {
    override val kindName: String = "wait"
  }
}
