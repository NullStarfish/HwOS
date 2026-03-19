package HwOS.kernel.function

import HwOS.kernel.debug.CallStack
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.SysCall
import HwOS.kernel.thread.HardwareThread

/**
 * HwFunction v1 is a single-slot, entry-arbitrated, service-owned control
 * segment. It is implemented with a hidden activation thread and a blocking
 * call protocol. This is not yet the pure linkable function model.
 *
 * v1 语义：
 * - function 拥有一个独立 activation slot（内部隐藏 thread）
 * - caller 通过阻塞式 call 协议启动该 slot，并等待其 done
 * - function body 内的局部状态静态归属于该 activation slot
 */
final class HwFunction[T] private (
    val name: String,
    private val body: HardwareThread => T,
) {
  private var activationOwner: Option[HwProcess] = None
  private var runtimeHost: Option[FunctionRuntimeHost[T]] = None

  private[kernel] def ensureRuntimeHost(owner: HwProcess): FunctionRuntimeHost[T] = {
    runtimeHost match {
      case Some(existing) =>
        activationOwner.foreach { existingOwner =>
          if (existingOwner ne owner) {
            throw new Exception(
              s"[HwOS] Function '$name' is already bound to process '${existingOwner.name}' and cannot be reused from '${owner.name}' in v1.",
            )
          }
        }
        existing
      case None =>
        activationOwner = Some(owner)
        val created = new FunctionRuntimeHost[T](name, body, owner)
        runtimeHost = Some(created)
        created
    }
  }

  private[kernel] def ensureActivation(owner: HwProcess): HardwareThread =
    ensureRuntimeHost(owner).activation

  private[kernel] def ensureResultHandle(owner: HwProcess): T =
    ensureRuntimeHost(owner).result

  private[kernel] def ensureCallBinding(owner: HwProcess): FunctionCallBindingState =
    ensureRuntimeHost(owner).bindingState

  private[kernel] def allocateCallLease(caller: HardwareThread): FunctionCallLease =
    ensureRuntimeHost(caller.owner).allocateCallLease(caller)

  private[kernel] def debugActivationThread: Option[HardwareThread] = runtimeHost.map(_.activation)
  private[kernel] def debugRuntimeHost: Option[FunctionRuntimeHost[T]] = runtimeHost

  def Invoke(returnTo: String): HwInline[T] =
    HwInline.thread(s"${name}_call") { _ =>
      SysCall.Call(this, returnTo)
    }

  def Invoke(): HwInline[T] =
    HwInline.thread(s"${name}_call") { _ =>
      SysCall.Call(this)
    }
}

object HwFunction {
  def thread[T](name: String)(body: HardwareThread => T): HwFunction[T] =
    new HwFunction[T](name, body)
}
