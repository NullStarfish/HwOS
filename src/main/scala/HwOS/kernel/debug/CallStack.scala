package HwOS.kernel.debug

import scala.collection.mutable.Stack

/**
 * CallStack is primarily a debug-oriented call-frame tracker.
 * It keeps stack snapshots and return targets, while continuation naming
 * is delegated to dedicated helpers.
 */
object CallStack {
  final case class Frame(name: String, returnTarget: Option[String])

  // 使用 ThreadLocal 确保并行编译时的安全性
  private val stack = new ThreadLocal[Stack[Frame]] {
    override def initialValue(): Stack[Frame] = Stack[Frame]()
  }

  def push(name: String, returnTarget: Option[String] = None): Unit = stack.get().push(Frame(name, returnTarget))
  
  def pop(): Unit = {
    if (stack.get().nonEmpty) stack.get().pop()
  }

  def currentReturnTarget: Option[String] = {
    stack.get().iterator.collectFirst { case Frame(_, Some(target)) => target }
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
