package HwOS.kernel.system

import chisel3._

object RuntimeLifecycle {
  val Idle: Int = 0
  val Running: Int = 1
  val Done: Int = 2
}

final class RuntimeContext(
    val binding: BindingTableEntry,
    val cursor: VirtualCursor,
    val stateReg: UInt,
)

trait RuntimeReclaimTarget {
  def resetRuntime(): Unit
  def runtimeActive: Bool
  def runtimeName: String
}
