package HwOS.kernel.debug

import scala.collection.mutable.Stack

/**
 * CallStack is primarily a debug-oriented call-frame tracker.
 * It keeps stack snapshots and return targets, while continuation naming
 * is delegated to dedicated helpers.
 */
object CallStack {
  final class Frame(
      val name: String,
      val returnTarget: Option[String],
  ) {
    var returned: Boolean = false
  }

  // 使用 ThreadLocal 确保并行编译时的安全性
  private val stack = new ThreadLocal[Stack[Frame]] {
    override def initialValue(): Stack[Frame] = Stack[Frame]()
  }

  private def pushFrame(frame: Frame): Unit = stack.get().push(frame)

  def withFrame[T](name: String, returnTarget: Option[String])(block: => T): T = {
    pushFrame(new Frame(name, returnTarget))
    try {
      block
    } finally {
      pop()
    }
  }

  def pushCall(name: String, returnTarget: Option[String]): Unit =
    pushFrame(new Frame(name, returnTarget))
  
  def pop(): Option[Frame] = Option.when(stack.get().nonEmpty)(stack.get().pop())

  def currentFrame: Option[Frame] = stack.get().headOption

  def currentReturnTarget: Option[String] = {
    stack.get().iterator.collectFirst { case frame if frame.returnTarget.nonEmpty => frame.returnTarget.get }
  }

  def markReturned(): Unit = {
    currentFrame.foreach(_.returned = true)
  }

  /**
   * 获取当前命名前缀
   * 例如栈为: ["Main", "FPU", "Add"]
   * 返回: "Main_FPU_Add_"
   */
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
