package HwOS.kernel.thread

import chisel3._
import scala.collection.mutable.ArrayBuffer
import HwOS.kernel.debug.CallStack

final class ThreadStepNode(val name: String, val block: () => Unit) {
  var prev: ThreadStepNode = _
  var next: ThreadStepNode = _
  var isHijacked: Boolean = false
  var allocatedPC: Int = -1
  val threadCallStack: Seq[String] = CallStack.getSnapshot
  val invokedCalls = ArrayBuffer[Seq[String]]()
}

trait ThreadNextApi {
  def hijack(): Unit
}

trait ThreadControlApi {
  // 抽象控制游标。它表示当前状态切片位置，不承诺具体后端必须用物理 PC 实现。
  def pc: UInt
  def Next: ThreadNextApi

  // Step 是最小状态切片。它是 bottom-up 的时序语义，不是更高层 block DSL。
  def Step(name: String)(block: => Unit): Unit
  // hijack / jump / wait* 都是控制语义，而不是 runtime 细节。
  def hijack(): Unit
  def jump(target: String): Unit
  def waitCondition(cond: Bool): Unit
  def waitAndAct(cond: Bool)(block: => Unit): Unit
  def Global(block: => Unit): Unit
  def entry(block: => Unit): Unit
}
