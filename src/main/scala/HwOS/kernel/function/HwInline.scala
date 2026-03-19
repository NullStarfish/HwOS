package HwOS.kernel.function

import HwOS.kernel.context.{AtomicCtx, ContextScope, LogicCtx, ThreadCtx}
import HwOS.kernel.thread.{HardwareAgent, HardwareThread}

/**
 * HwInline[T] is a portable, composable control code segment.
 * Its return value models the call interface, while declare/export handle
 * environment-facing visibility outside the segment body.
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
  private def emitInContext[T](inlineName: String)(check: PartialFunction[Any, T]): T =
    ContextScope.current match {
      case ctx if check.isDefinedAt(ctx) => check(ctx)
      case _ =>
        throw new Exception(s"[HwOS] Illegal inline call: '$inlineName' was emitted in an incompatible execution context.")
    }

  def apply[T](inlineName: String)(block: HardwareAgent => T): HwInline[T] = {
    new HwInline[T] {
      override def name: String = inlineName
      override def emit(self: HardwareAgent): T = block(self)
    }
  }

  def atomic[T](inlineName: String)(block: HardwareThread => T): HwInline[T] = apply(inlineName) { _ =>
    emitInContext(inlineName) {
      case AtomicCtx(t) => block(t)
    }
  }

  def thread[T](inlineName: String)(block: HardwareThread => T): HwInline[T] = apply(inlineName) { _ =>
    emitInContext(inlineName) {
      case ThreadCtx(t) => block(t)
    }
  }

  def stateless[T](inlineName: String)(block: HardwareAgent => T): HwInline[T] = apply(inlineName) { agent =>
    emitInContext(inlineName) {
      case AtomicCtx(_) => block(agent)
      case LogicCtx(_) => block(agent)
    }
  }

  def bindings[T](inlineName: String)(block: HardwareAgent => T): HwInline[T] = apply(inlineName) {
    agent => block(agent)
  }
}
