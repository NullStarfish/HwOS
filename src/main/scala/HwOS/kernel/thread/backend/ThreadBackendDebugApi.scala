package HwOS.kernel.thread.backend

import HwOS.kernel.thread.ThreadStepNode

private[kernel] trait ThreadBackendDebugApi {
  def threadNodes: Seq[ThreadStepNode]
  def recordAtomicCallSnapshot(snapshot: Seq[String]): Unit
}
