package HwOS.kernel.debug

import scala.collection.mutable.Stack

/**
 * CallStack: 专门负责管理函数调用层级命名 (Debug Symbols)
 * 它与 ContextScope (时序安全) 完全分离。
 */
object CallStack {
  private val returnCounters = scala.collection.mutable.HashMap.empty[(Int, String), Int]
  private val functionCallCounters = scala.collection.mutable.HashMap.empty[(Int, String), Int]
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

  private def sanitizeStepName(part: String): String = {
    val cleaned = part.replaceAll("[^A-Za-z0-9_]", "_").replaceAll("_+", "_").stripPrefix("_").stripSuffix("_")
    if (cleaned.isEmpty) "Anon" else cleaned
  }

  def freshReturnStepName(threadKey: Int, target: String): String = {
    val prefix = sanitizeStepName(getCurrentPrefix.stripSuffix("_"))
    val targetName = sanitizeStepName(target)
    val semanticBase = s"${prefix}_Return_to_${targetName}"
    val key = (threadKey, semanticBase)
    val nextId = returnCounters.getOrElse(key, 0)
    returnCounters.update(key, nextId + 1)
    if (nextId == 0) semanticBase else s"${semanticBase}_$nextId"
  }

  def freshFunctionCallStepName(threadKey: Int, functionName: String, returnTo: String): String = {
    val prefix = sanitizeStepName(getCurrentPrefix.stripSuffix("_"))
    val functionPart = sanitizeStepName(functionName)
    val targetPart = sanitizeStepName(returnTo)
    val semanticBase =
      if (prefix.isEmpty) s"${functionPart}_CallWait_to_${targetPart}"
      else s"${prefix}_${functionPart}_CallWait_to_${targetPart}"
    val key = (threadKey, semanticBase)
    val nextId = functionCallCounters.getOrElse(key, 0)
    functionCallCounters.update(key, nextId + 1)
    if (nextId == 0) semanticBase else s"${semanticBase}_$nextId"
  }
}
