package HwOS.kernel.system

import chisel3._
import scala.collection.mutable.ArrayBuffer
import HwOS.kernel.debug.CallStack
import HwOS.kernel.thread.step.EdgeAction
import HwOS.kernel.thread.step.ThreadCompilePlan.PatchTarget
import HwOS.kernel.system.CallProtocolContext.CallSiteSnapshot

final case class RecordedEffect(
    action: EdgeAction,
    guards: List[Bool] = Nil,
)

final case class RecordedEdgePatch(
    target: PatchTarget,
    effects: Seq[RecordedEffect] = Seq.empty,
    emitThunk: Option[() => Unit] = None,
    guards: List[Bool] = Nil,
    inheritPassEdgeGuards: Boolean = false,
)

final class GlobalCodeSegment(
    val ownerName: String,
    val objectName: String,
    val addressObject: AddressObject,
    val labels: Seq[String],
    val addresses: Map[String, Int],
) {
  def startAddress: Int = addressObject.startAddress
  def entryAddress: Int = addressObject.startAddress
  def addressOf(label: String): Int =
    addresses.getOrElse(label, throw new Exception(s"[Kernel] Unknown label '$label' in code segment '$ownerName'."))
  def width: Int = addressObject.width
}

final class VirtualStepRecord(
    val name: String,
    val block: () => Unit,
    val threadCallStack: Seq[String],
    val implicitCallSite: Option[CallSiteSnapshot],
    val invokedCalls: ArrayBuffer[Seq[String]] = ArrayBuffer.empty[Seq[String]],
) {
  var allocatedAddress: Int = -1
  var loweredStandalone: Boolean = true
  val edgeActions: ArrayBuffer[EdgeAction] = ArrayBuffer.empty[EdgeAction]
  val capturedEffects: ArrayBuffer[RecordedEffect] = ArrayBuffer.empty[RecordedEffect]
  val staticEdgePatches: ArrayBuffer[RecordedEdgePatch] = ArrayBuffer.empty[RecordedEdgePatch]
  var passEdgeGuards: List[Bool] = Nil
}

final class VirtualProgram(val ownerName: String) {
  private val records = ArrayBuffer[VirtualStepRecord]()

  def appendStep(name: String, block: () => Unit): VirtualStepRecord = {
    val record = new VirtualStepRecord(
      name,
      block,
      CallStack.getSnapshot,
      CallProtocolContext.currentCallSiteSnapshot,
    )
    records += record
    record
  }

  def steps: Seq[VirtualStepRecord] = records.toSeq
  def labels: Seq[String] = records.map(_.name).toSeq
}
