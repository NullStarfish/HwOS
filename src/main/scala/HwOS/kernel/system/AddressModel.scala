package HwOS.kernel.system

import chisel3.Data

sealed trait AddressKind {
  def tag: String
}

object AddressKind {
  case object Code extends AddressKind {
    override val tag: String = "code"
  }

  case object State extends AddressKind {
    override val tag: String = "state"
  }
}

final class AddressObject(
    val kind: AddressKind,
    val ownerName: String,
    val objectName: String,
    val startAddress: Int,
    val span: Int,
) {
  def endAddressExclusive: Int = startAddress + span
  def width: Int = chisel3.util.log2Ceil(endAddressExclusive max 2)
}

final class StateTableEntry(val addressObject: AddressObject, val ownerName: String, val signal: Option[Data])
final class CodeTableEntry(val segment: GlobalCodeSegment)
final class BindingTableEntry(
    val bindingName: String,
    val ownerName: String,
    val cursorObject: AddressObject,
    val runtimeStateObject: AddressObject,
    val codeSegment: GlobalCodeSegment,
)
