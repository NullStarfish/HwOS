package HwOS.kernel.thread

import chisel3._

sealed trait StepRef {
  def edge: StepEdge = StepEdge(this)
}

object StepRef {
  final case class NamedStepRef(name: String) extends StepRef
  case object NextStepRef extends StepRef
}

trait ThreadControlApi {
  // 抽象控制游标。它表示当前状态切片位置，不承诺具体后端必须用物理 PC 实现。
  def pc: UInt
  def Next: StepRef
  def Prev: StepRef
  def stepRef(name: String): StepRef = StepRef.NamedStepRef(name)

  // Step 是最小状态切片。它是 bottom-up 的时序语义，不是更高层 block DSL。
  def Step(name: String)(block: => Unit): Unit
  // hijack / jump / wait* 都是控制语义，而不是 runtime 细节。
  def hijack(target: StepRef): Unit
  def jump(target: StepRef): Unit
  // Transitional wrapper while high-level DSLs finish converging on StepRef-first call sites.
  def jump(targetName: String): Unit = jump(stepRef(targetName))
  def waitCondition(cond: Bool): Unit
  def waitAndAct(cond: Bool)(block: => Unit): Unit
  def Global(block: => Unit): Unit
  def entry(block: => Unit): Unit
}
