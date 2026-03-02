package HwOS.kernel.debug

import scala.collection.mutable.Stack

/**
 * CallStack: 专门负责管理函数调用层级命名 (Debug Symbols)
 * 它与 ContextScope (时序安全) 完全分离。
 */
object CallStack {
  // 使用 ThreadLocal 确保并行编译时的安全性
  private val stack = new ThreadLocal[Stack[String]] {
    override def initialValue(): Stack[String] = Stack[String]()
  }

  def push(name: String): Unit = stack.get().push(name)
  
  def pop(): Unit = {
    if (stack.get().nonEmpty) stack.get().pop()
  }

  /**
   * 获取当前命名前缀
   * 例如栈为: ["Main", "FPU", "Add"]
   * 返回: "Main_FPU_Add_"
   */
  def getCurrentPrefix: String = {
    val s = stack.get()
    if (s.isEmpty) "" else s.reverse.mkString("_") + "_"
  }

  def getSnapshot: Seq[String] = stack.get().toSeq.reverse
}
