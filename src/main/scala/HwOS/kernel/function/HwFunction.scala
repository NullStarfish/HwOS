package HwOS.kernel.function

import chisel3._
import HwOS.kernel.debug.CallStack
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{OSReaper, OSReaperManagedLogic, SysCall}
import HwOS.kernel.thread.{HardwareAgent, HardwareThread}

/**
 * 真正的硬件函数。
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
  private[kernel] final class FunctionCallBindingState(
      val activation: HardwareThread,
      val callActive: Bool,
      val activeBindingId: UInt,
  )

  private[kernel] final class FunctionCallLease(
      val bindingId: Int,
      val caller: HardwareThread,
      val activation: HardwareThread,
      val binding: FunctionCallBindingState,
      val callPending: Bool,
  ) {
    private val bindingIdValue = bindingId.U(binding.activeBindingId.getWidth.W)

    def isActive: Bool =
      binding.callActive && binding.activeBindingId === bindingIdValue

    private[kernel] def forceReclaim(agent: HardwareAgent): Unit = {
      activation.grant(binding.callActive, agent)
      activation.grant(binding.activeBindingId, agent)
      caller.grant(callPending, agent)

      when(isActive) {
        activation.grantLifecycleAccess(agent.ctx)
        OSReaper.reclaimThread(activation, agent.kernel.managedReaperEntities, agent)
        OSReaper.forceAssign(binding.callActive, false.B)
        OSReaper.forceAssign(binding.activeBindingId, 0.U(binding.activeBindingId.getWidth.W))
      }

      OSReaper.forceAssign(callPending, false.B)
    }
  }

  private var activationOwner: Option[HwProcess] = None
  private var activationThread: Option[HardwareThread] = None
  private var resultHandle: Option[T] = None
  private var callBindingState: Option[FunctionCallBindingState] = None
  private var reaperHolder: Option[OSReaperManagedLogic] = None
  private var nextBindingId: Int = 1

  private[kernel] def ensureActivation(owner: HwProcess): HardwareThread = {
    activationThread match {
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
        val activation = owner.createThread(
          name = s"${name}_activation",
        )
        var built: Option[T] = None
        CallStack.withIsolatedStack {
          activation.entry {
            built = Some(body(activation))
          }
        }
        if (built.isEmpty) {
          throw new Exception(s"[HwOS] Function '$name' did not produce a build result while generating its activation body.")
        }
        activationThread = Some(activation)
        resultHandle = built
        activation
    }
  }

  private[kernel] def ensureResultHandle(owner: HwProcess): T = {
    ensureActivation(owner)
    resultHandle.getOrElse(throw new Exception(s"[HwOS] Function '$name' result handle was not initialized."))
  }

  private[kernel] def ensureCallBinding(owner: HwProcess): FunctionCallBindingState = {
    ensureActivation(owner)
    callBindingState match {
      case Some(existing) => existing
      case None =>
        val activation = activationThread.getOrElse(
          throw new Exception(s"[HwOS] Function '$name' activation thread was not initialized."),
        )
        val binding = new FunctionCallBindingState(
          activation = activation,
          callActive = activation.own(RegInit(false.B)),
          activeBindingId = activation.own(RegInit(0.U(32.W))),
        )
        callBindingState = Some(binding)
        binding
    }
  }

  private def ensureReaperHolder(owner: HwProcess): OSReaperManagedLogic =
    reaperHolder match {
      case Some(existing) => existing
      case None =>
        val holder = owner.createReaperManagedLogic(s"${name}_reaper")
        reaperHolder = Some(holder)
        holder
    }

  private[kernel] def allocateCallLease(caller: HardwareThread): FunctionCallLease = {
    val binding = ensureCallBinding(caller.owner)
    val holder = ensureReaperHolder(caller.owner)
    val lease = new FunctionCallLease(
      bindingId = nextBindingId,
      caller = caller,
      activation = binding.activation,
      binding = binding,
      callPending = caller.own(RegInit(false.B)),
    )
    nextBindingId += 1
    holder.registerReclaimEntry(caller, lease.isActive) { agent =>
      lease.forceReclaim(agent)
    }
    lease
  }

  private[kernel] def debugActivationThread: Option[HardwareThread] = activationThread

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
