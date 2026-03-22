package HwOS.kernel.thread

import HwOS.kernel.context.ContextScope

private[kernel] trait StepEdgeRecorder {
  private[kernel] def recordEdgePatch(target: StepRef)(block: => Unit): Unit
}

final class StepEdge private[thread] (private val target: StepRef) {
  def add(block: => Unit): Unit = {
    ContextScope.getCurrentThread() match {
      case t: StepEdgeRecorder =>
        t.recordEdgePatch(target) {
          block
        }
      case _ =>
        throw new Exception("[HwOS] StepRef.edge.add(...) requires an active HardwareThread context.")
    }
  }
}

object StepEdge {
  def apply(target: StepRef): StepEdge = new StepEdge(target)
}
