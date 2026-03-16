package HwOS.kernel.function

import HwOS.kernel.system.SysCall
import HwOS.kernel.thread.HardwareThread

/**
 * HwFunction[T]: 真正的硬件函数抽象。
 * 当前最小 MVP 先将其建模为一个具名、可调用的 thread-level control-flow 单元，
 * 本质上是携带独立调用边界的 HwInline。
 */
final class HwFunction[T] private (val name: String, private val impl: HwInline[T]) {
  def inline: HwInline[T] = impl

  def Invoke(returnTo: String): HwInline[T] =
    HwInline.thread(s"${name}_call") { _ =>
      SysCall.Call(impl, returnTo)
    }

  def Invoke(): HwInline[T] =
    HwInline.thread(s"${name}_call") { _ =>
      SysCall.Call(impl)
    }
}

object HwFunction {
  def fromInline[T](name: String)(impl: HwInline[T]): HwFunction[T] =
    new HwFunction[T](name, impl)

  /**
   * MVP: 真正函数目前只支持 thread-level body。
   * 调用时仍然复用现有静态 return target 机制。
   */
  def thread[T](name: String)(body: HardwareThread => T): HwFunction[T] =
    fromInline(name)(HwInline.thread(name)(body))
}
