package HwOS.kernel.debug

import HwOS.kernel.system.CallProtocolContext.CallSiteSnapshot
import scala.collection.mutable.Stack

/**
 * CallStack is a debug-oriented frame tracker.
 * It carries naming and stack snapshots only; continuation protocol state
 * lives in CallProtocolContext.
 */
object CallStack {
  final class Frame(
      val name: String,
      val callSiteId: Option[Int] = None,
  )

  private val stack = new ThreadLocal[Stack[Frame]] {
    override def initialValue(): Stack[Frame] = Stack[Frame]()
  }

  private def pushFrame(frame: Frame): Unit = stack.get().push(frame)

  def withFrame[T](name: String)(block: => T): T =
    withFrame(name, Option.empty[Int])(block)

  def withFrame[T](name: String, callSiteId: Option[Int])(block: => T): T = {
    pushFrame(new Frame(name, callSiteId))
    try {
      block
    } finally {
      pop()
    }
  }

  def withFrame[T](name: String, returnTarget: Option[String], callSiteSnapshot: Option[CallSiteSnapshot])(block: => T): T =
    withFrame(name, callSiteSnapshot.map(_.id))(block)

  def pop(): Option[Frame] = Option.when(stack.get().nonEmpty)(stack.get().pop())

  def currentFrame: Option[Frame] = stack.get().headOption

  def getCurrentPrefix: String = {
    val s = stack.get()
    if (s.isEmpty) "" else s.reverse.map(_.name).mkString("_") + "_"
  }

  def getSnapshot: Seq[String] = stack.get().toSeq.reverse.map(_.name)

  def withIsolatedStack[T](block: => T): T = {
    val saved = stack.get().clone()
    stack.set(Stack.empty[Frame])
    try {
      block
    } finally {
      stack.set(saved)
    }
  }
}
