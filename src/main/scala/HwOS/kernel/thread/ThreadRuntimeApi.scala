package HwOS.kernel.thread

import chisel3._

trait ThreadRuntimeApi {
  // runtime 语义只暴露 thread 的生命状态观察。
  // start / exit 仍由系统调用层驱动；reset 是 thread 自己的正式运行时复位语义。
  def active: Bool
  def done: Bool
  def reset(): Unit
  def registerReset(block: => Unit): Unit
}
