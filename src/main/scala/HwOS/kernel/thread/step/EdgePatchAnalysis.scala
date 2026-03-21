package HwOS.kernel.thread.step

import scala.collection.mutable.ArrayBuffer
import HwOS.kernel.debug.CallStack.CallSiteSnapshot
import HwOS.kernel.system.VirtualStepRecord
import HwOS.kernel.thread.StepRef

private[kernel] object EdgePatchAnalysis {
  private final case class ActivePatch(record: VirtualStepRecord, guardDepth: Int, startSize: Int)

  private val activePatch = new ThreadLocal[Option[ActivePatch]] {
    override def initialValue(): Option[ActivePatch] = None
  }

  def isActive: Boolean = activePatch.get().nonEmpty

  def capture(record: VirtualStepRecord, guardDepth: Int)(block: => Unit): Unit = {
    val saved = activePatch.get()
    val startSize = record.edgeActions.size
    activePatch.set(Some(ActivePatch(record, guardDepth, startSize)))
    try {
      block
      if (record.edgeActions.size == startSize) {
        throw new Exception(
          s"[HwOS] StepRef.edge.add(...) currently supports only structured control actions " +
            s"(for example SysCall.Return() or jump(stepRef)) inside the edge patch block.",
        )
      }
    } finally {
      activePatch.set(saved)
    }
  }

  def recordReturn(callSite: Option[CallSiteSnapshot]): Unit = {
    val patch = current
    patch.record.edgeActions += EdgeAction.Return(
      guardDepth = patch.guardDepth,
      returnTarget = callSite.flatMap(_.continuationTarget),
      returnEdgePatch = callSite.flatMap(_.returnEdgePatch),
      emitInLowering = true,
    )
  }

  def recordJump(target: StepRef): Unit = {
    val patch = current
    patch.record.edgeActions += EdgeAction.Jump(patch.guardDepth, target, emitInLowering = true)
  }

  private def current: ActivePatch =
    activePatch.get().getOrElse(
      throw new Exception("[HwOS] No active edge patch analysis context."),
    )
}
