package HwOS.kernel.thread

import chisel3._

trait ThreadRuntimeApi {
  // runtime 语义只暴露 thread 的生命状态观察。
  // 具体的 start / exit 由系统调用层驱动，而不是线程本身对用户暴露的能力。
  def active: Bool
  def done: Bool
}
