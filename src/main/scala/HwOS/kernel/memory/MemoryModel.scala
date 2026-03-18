package HwOS.kernel.memory

import chisel3._
import scala.reflect.ClassTag
import HwOS.kernel.system.AddressObject

final case class ExportCapability(read: Boolean, write: Boolean, execute: Boolean) {
  def allows(requested: ExportCapability): Boolean =
    (!requested.read || read) &&
      (!requested.write || write) &&
      (!requested.execute || execute)

  def render: String = {
    val caps = Seq(
      Option.when(read)("read"),
      Option.when(write)("write"),
      Option.when(execute)("execute"),
    ).flatten
    if (caps.isEmpty) "none" else caps.mkString("+")
  }
}

object ExportCapability {
  val None: ExportCapability = ExportCapability(read = false, write = false, execute = false)
  val Read: ExportCapability = ExportCapability(read = true, write = false, execute = false)
  val Write: ExportCapability = ExportCapability(read = false, write = true, execute = false)
  val ReadWrite: ExportCapability = ExportCapability(read = true, write = true, execute = false)
  val Execute: ExportCapability = ExportCapability(read = false, write = false, execute = true)
}

final class ExportedMemoryEntry(
    val symbolName: String,
    val ownerName: String,
    val backingSignal: Data,
    val addressObject: AddressObject,
    val capability: ExportCapability,
    val typeSummary: String,
)

final class MemoryDependencyEntry(
    val requesterName: String,
    val symbolName: String,
    val resolvedOwnerName: String,
    val requestedCapability: ExportCapability,
    val resolvedCapability: ExportCapability,
)

final class ExportedSymbol[T <: Data] private[kernel] (
    val entry: ExportedMemoryEntry,
    val signal: T,
)

final class VirtualWriteChannel[T <: Data] private[kernel] (
    private val symbolName: String,
    private val requesterName: String,
    private val capability: ExportCapability,
    private val backingSignal: T,
) {
  def :=(rhs: T): Unit = {
    if (!capability.write) {
      throw new Exception(
        s"[HwOS Memory] '$requesterName' does not have write permission for exported symbol '$symbolName'.",
      )
    }
    backingSignal := rhs
  }
}

final class VirtualHandle[T <: Data: ClassTag] private[kernel] (
    val symbolName: String,
    val requesterName: String,
    val capability: ExportCapability,
    private val backingSignal: T,
) {
  if (!capability.read && !capability.write && !capability.execute) {
    throw new Exception(s"[HwOS Memory] Virtual handle '$symbolName' for '$requesterName' was declared with no capabilities.")
  }

  def read: T = {
    if (!capability.read) {
      throw new Exception(
        s"[HwOS Memory] '$requesterName' does not have read permission for exported symbol '$symbolName'.",
      )
    }
    backingSignal
  }

  val write: VirtualWriteChannel[T] =
    new VirtualWriteChannel[T](symbolName, requesterName, capability, backingSignal)
}
