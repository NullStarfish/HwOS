package HwOS.kernel.thread.step

import HwOS.kernel.context.{AtomicCtx, ContextScope, ThreadCtx}
import HwOS.kernel.thread.ThreadCore

private[kernel] object EdgeGuardContext {
  def withInlineBoundary[T](block: => T): T = {
    val run = currentThreadScope match {
      case Some(t) => () => t.withScopedEdgeGuards(block)
      case None => () => block
    }

    if (PreLoweringAnalysis.isActive) {
      PreLoweringAnalysis.withScopedEdgeGuards {
        run()
      }
    } else {
      run()
    }
  }

  private def currentThreadScope: Option[ThreadCore] = ContextScope.current match {
    case ThreadCtx(t: ThreadCore) => Some(t)
    case AtomicCtx(t: ThreadCore) => Some(t)
    case _ => None
  }
}
