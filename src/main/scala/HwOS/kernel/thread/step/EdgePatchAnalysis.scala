package HwOS.kernel.thread.step

import HwOS.kernel.system.CallProtocolContext.ContinuationSnapshot
import HwOS.kernel.system.RecordedEffect
import HwOS.kernel.thread.StepRef
import scala.collection.mutable.ArrayBuffer

private[kernel] object EdgePatchAnalysis {
  private final case class ActivePatch(effects: ArrayBuffer[RecordedEffect], guards: List[chisel3.Bool])

  private val activePatch = new ThreadLocal[Option[ActivePatch]] {
    override def initialValue(): Option[ActivePatch] = None
  }

  def isActive: Boolean = activePatch.get().nonEmpty

  def capture(guards: List[chisel3.Bool])(block: => Unit): Seq[RecordedEffect] = {
    val saved = activePatch.get()
    val effects = ArrayBuffer.empty[RecordedEffect]
    activePatch.set(Some(ActivePatch(effects, guards)))
    try {
      block
      effects.toSeq
    } finally {
      activePatch.set(saved)
    }
  }

  def recordReturn(continuation: Option[ContinuationSnapshot]): Unit = {
    val patch = current
    patch.effects += RecordedEffect(
      EdgeAction.Return(
        continuation = continuation,
      ),
      patch.guards,
    )
  }

  def recordJump(target: StepRef): Unit = {
    val patch = current
    patch.effects += RecordedEffect(EdgeAction.Jump(target = target), patch.guards)
  }

  private def current: ActivePatch =
    activePatch.get().getOrElse(
      throw new Exception("[HwOS] No active edge patch analysis context."),
    )
}
