package HwOS.kernel

import chisel3._

trait ThreadRuntimeApi {
  def active: Bool
  def done: Bool
  def exit(): Unit
}
