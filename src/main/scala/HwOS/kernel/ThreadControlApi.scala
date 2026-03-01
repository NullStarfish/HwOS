package HwOS.kernel

import chisel3._
import scala.collection.mutable.ArrayBuffer

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
  def pc: UInt
  def Next: ThreadNextApi

  def Step(name: String)(block: => Unit): Unit
  def hijack(): Unit
  def jump(target: String): Unit
  def waitCondition(cond: Bool): Unit
  def waitAndAct(cond: Bool)(block: => Unit): Unit
  def Global(block: => Unit): Unit
  def entry(block: => Unit): Unit
}
