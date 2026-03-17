package HwOS.kernel.system

import chisel3._
import HwOS.kernel.context.HwLease
import HwOS.kernel.thread.HardwareAgent

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

final class ThreadRuntimeLease(
    val runtime: RuntimeContext,
    val target: RuntimeReclaimTarget,
) extends HwLease {
  override def isActive: Bool = target.runtimeActive

  override private[kernel] def forceReclaim(agent: HardwareAgent): Unit = {
    target.resetRuntime()
  }
}
