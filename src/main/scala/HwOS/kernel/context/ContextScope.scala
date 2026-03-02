package HwOS.kernel.context

import scala.collection.mutable.Stack
import HwOS.kernel.thread.{HardwareAgent, HardwareLogic, HardwareThread}


// 上下文定义
sealed trait ExecutionContext
case class LogicCtx(l: HardwareLogic)   extends ExecutionContext
case class ThreadCtx(t: HardwareThread)  extends ExecutionContext
case class AtomicCtx(t: HardwareThread)  extends ExecutionContext 


// 上下文管理器 (单例)
object ContextScope {
  private val scopeStack = new ThreadLocal[Stack[ExecutionContext]] {
    override def initialValue(): Stack[ExecutionContext] = Stack[ExecutionContext]()
  }

  // 压栈并执行代码块
  def withContext[T](ctx: ExecutionContext)(block: => T): T = {
    scopeStack.get().push(ctx)
    try {
      block
    } finally {
      scopeStack.get().pop()
    }
  }

  // 获取当前上下文
  def current: ExecutionContext = {
    val currentStack = scopeStack.get()
    if (currentStack.isEmpty) {
      throw new Exception("the scope is empty")
    }
    currentStack.top
  }

  def getCurrentThread(): HardwareThread =  {
    val current = this.current match {
      case AtomicCtx(t) => t
      case ThreadCtx(t) => t
      case LogicCtx(l) => throw new Exception("Current is a logic")
      case _ => throw new Exception("There is no thread")
    }
    current
  }

  def getCurrentAgent(): HardwareAgent = {
    val current = this.current match {
      case AtomicCtx(t) => t
      case ThreadCtx(t) => t
      case LogicCtx(l) => l
      case _ => throw new Exception("There is not a agent")
    }
    current
  }
}
