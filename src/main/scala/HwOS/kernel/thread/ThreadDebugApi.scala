package HwOS.kernel.thread

import scala.collection.mutable.ArrayBuffer

final class DebugStepRecord(
    val name: String,
    val allocatedPC: Int,
    val isStandalone: Boolean,
    val threadCallStack: Seq[String],
    val invokedCalls: ArrayBuffer[Seq[String]],
)

private[kernel] trait ThreadDebugApi {
  def debugSteps: Seq[DebugStepRecord]
  def recordAtomicCallSnapshot(snapshot: Seq[String]): Unit
}
