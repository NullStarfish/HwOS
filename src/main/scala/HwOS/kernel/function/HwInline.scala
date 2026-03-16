package HwOS.kernel.function

import HwOS.kernel.context.{AtomicCtx, ContextScope, LogicCtx, ThreadCtx}
import HwOS.kernel.thread.{HardwareAgent, HardwareThread}

/**
 * HwInline[T]: 硬件内联代码段定义 (Inline Code Segment)
 * @tparam T 返回值的类型 (通常是 Chisel 的 Data 子类，如 UInt, Bundle，或者是 Reg)
 */
trait HwInline[T] {
  def name: String

  /**
   * 发射逻辑并内联展开到当前 agent。
   * @param self 这段代码将在哪个 agent 上展开
   * @return 生成的硬件结果
   */
  def emit(self: HardwareAgent): T
}

object HwInline {
  def apply[T](inlineName: String)(block: HardwareAgent => T): HwInline[T] = {
    new HwInline[T] {
      override def name: String = inlineName
      override def emit(self: HardwareAgent): T = block(self)
    }
  }

  def atomic[T](inlineName: String)(block: HardwareThread => T): HwInline[T] = apply(inlineName) { _ =>
    ContextScope.current match {
      case AtomicCtx(t) => block(t)
      case _ => throw new Exception(s"[HwOS] 违规调用！'$inlineName' 必须在 Step (AtomicCtx) 内部执行。")
    }
  }

  def thread[T](inlineName: String)(block: HardwareThread => T): HwInline[T] = apply(inlineName) { _ =>
    ContextScope.current match {
      case ThreadCtx(t) => block(t)
      case _ => throw new Exception(s"[HwOS] 违规调用！'$inlineName' 必须在 Thread (ThreadCtx) 内部执行。")
    }
  }

  def stateless[T](inlineName: String)(block: HardwareAgent => T): HwInline[T] = apply(inlineName) { agent =>
    ContextScope.current match {
      case AtomicCtx(_) | LogicCtx(_) => block(agent)
      case _ => throw new Exception(s"[HwOS] 违规调用！'$inlineName' 只能在 Step 或 LogicCtx 中作为组合逻辑执行。")
    }
  }

  def bindings[T](inlineName: String)(block: HardwareAgent => T): HwInline[T] = apply(inlineName) {
    agent => block(agent)
  }
}
