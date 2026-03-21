package HwOS.kernel.system

import chisel3._
import scala.collection.mutable.ArrayBuffer
import HwOS.kernel.debug.CallStack
import HwOS.kernel.thread.step.EdgeAction

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

final class VirtualCursor(val reg: UInt, val segment: GlobalCodeSegment, val addressObject: AddressObject) {
  def entryAddress: UInt = segment.entryAddress.U(reg.getWidth.W)
  def addressOf(label: String): UInt = segment.addressOf(label).U(reg.getWidth.W)
}

final class VirtualStepRecord(
    val name: String,
    val block: () => Unit,
    val threadCallStack: Seq[String],
    val implicitReturnTarget: Option[String],
    val invokedCalls: ArrayBuffer[Seq[String]] = ArrayBuffer.empty[Seq[String]],
) {
  var allocatedAddress: Int = -1
  var loweredStandalone: Boolean = true
  val staticEdgeActions: ArrayBuffer[EdgeAction] = ArrayBuffer.empty[EdgeAction]
  val edgeActions: ArrayBuffer[EdgeAction] = ArrayBuffer.empty[EdgeAction]
}

final class VirtualProgram(val ownerName: String) {
  private val records = ArrayBuffer[VirtualStepRecord]()

  def appendStep(name: String, block: () => Unit): VirtualStepRecord = {
    val record = new VirtualStepRecord(name, block, CallStack.getSnapshot, CallStack.currentReturnTarget)
    records += record
    record
  }

  def steps: Seq[VirtualStepRecord] = records.toSeq
  def labels: Seq[String] = records.map(_.name).toSeq
}
