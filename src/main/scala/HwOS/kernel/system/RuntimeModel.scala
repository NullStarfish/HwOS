package HwOS.kernel.system

import chisel3._

object RuntimeLifecycle {
  val Idle: Int = 0
  val Running: Int = 1
  val Done: Int = 2
}

final class RuntimeCursor(
    val reg: UInt,
    val segment: GlobalCodeSegment,
    val addressObject: AddressObject,
) {
  def entryAddress: UInt = segment.entryAddress.U(reg.getWidth.W)
  def addressOf(label: String): UInt = segment.addressOf(label).U(reg.getWidth.W)
}

final class RuntimeContext(
    val binding: BindingTableEntry,
    val cursor: RuntimeCursor,
    val stateReg: UInt,
)
