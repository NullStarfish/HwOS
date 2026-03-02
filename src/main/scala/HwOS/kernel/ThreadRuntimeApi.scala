package HwOS.kernel

import chisel3._

trait ThreadRuntimeApi {
  // runtime 语义只描述 thread 的生命状态。
  // 它不包含控制游标 pc，也不包含系统级 kill / reclaim / lifecycle ACL。
  def active: Bool
  def done: Bool

  // start / exit 是 thread 本身的能力；具体如何落成由 backend 决定。
  def start(): Unit
  def exit(): Unit
}
