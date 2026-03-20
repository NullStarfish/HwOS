package HwOS.kernel.system

import chisel3._
import scala.util.Try // 引入 Try
import HwOS.kernel.context.{AtomicCtx, ContextScope, ThreadCtx}
import HwOS.kernel.debug.{CallStack, ContinuationNaming}
import HwOS.kernel.function.{HwFunction, HwInline}
import HwOS.kernel.thread.{HardwareThread, ThreadDebugApi}

object SysCall {
  private sealed trait InlineCallMode
  private case object InlineMode extends InlineCallMode
  private case object CallMode extends InlineCallMode

  private val invokeModeStack = new ThreadLocal[List[InlineCallMode]] {
    override def initialValue(): List[InlineCallMode] = Nil
  }

  private def pushInvokeMode(mode: InlineCallMode): Unit =
    invokeModeStack.set(mode :: invokeModeStack.get())

  private def popInvokeMode(): Unit =
    invokeModeStack.set(invokeModeStack.get().drop(1))

  private def currentInvokeMode: Option[InlineCallMode] =
    invokeModeStack.get().headOption

  // ==========================================
  // 1. Function Linker Layer (逻辑注入)
  // ==========================================

  /**
   * 硬件函数调用：纯粹的代码内联展开，不涉及生命周期权限。
   * 目标线程在此过程中直接获取生成的逻辑所属权。
   */
  def Inline[T](func: HwInline[T]): T = {
    pushInvokeMode(InlineMode)
    try {
      func.emit(ContextScope.getCurrentAgent())
    } finally {
      popInvokeMode()
    }
  }

  @deprecated("Use SysCall.Inline(...) for natural fallthrough segments or SysCall.Call(..., returnTo) for formal calls.", "vNext")
  def Call[T](func: HwInline[T]): T = Inline(func)

  def Call[T](func: HwFunction[T]): T = {
    ContextScope.current match {
      case ThreadCtx(t) =>
        CallStack.currentReturnTarget match {
          case Some(target) => Call(func, target)
          case None =>
            throw new Exception(
              s"[HwOS] Direct SysCall.Call(HwFunction '${func.name}') inside ThreadCtx requires an explicit continuation. " +
                s"Use SysCall.Call(func, returnTo) or func.Invoke(returnTo).",
            )
        }
      case _ =>
        throw new Exception(s"[HwOS] HwFunction '${func.name}' can only be called from ThreadCtx in v1.")
    }
  }

  /**
   * 线程级函数调用：为被调用的 thread-function 静态绑定一个返回地址。
   * Return() 将跳转到该返回地址。
   */
  def Call[T](func: HwInline[T], returnTo: String): T = {
    CallStack.pushCall(func.name, returnTarget = Some(returnTo))
    pushInvokeMode(CallMode)
    try {
      scala.util.Try {
        ContextScope.current match {
          case ThreadCtx(_) | AtomicCtx(_) =>
          case _ =>
            throw new Exception(
              s"[HwOS] Call('$returnTo') with explicit return target must be used inside ThreadCtx or AtomicCtx.",
            )
        }
      }
      func.emit(ContextScope.getCurrentAgent())
    } finally {
      popInvokeMode()
      CallStack.pop()
    }
  }

  def Call[T](func: HwFunction[T], returnTo: String): T = {
    CallStack.pushCall(func.name, returnTarget = Some(returnTo))
    pushInvokeMode(CallMode)
    try {
      ContextScope.current match {
        case ThreadCtx(caller) =>
          val activation = func.ensureActivation(caller.owner)
          val callLease = func.allocateCallLease(caller)
          val result = func.ensureResultHandle(caller.owner)
          val callStepName = ContinuationNaming.freshFunctionCallStepName(System.identityHashCode(caller), func.name, returnTo)

          caller.Step(callStepName) {
            val binding = callLease.binding
            val bindingId = callLease.bindingId
            val bindingIdValue = bindingId.U(binding.activeBindingId.getWidth.W)
            val pending = callLease.callPending
            val thisCallActive = callLease.isActive

            when(!pending) {
              when(!binding.callActive && !activation.active) {
                binding.activeBindingId  :=  bindingIdValue
                binding.callActive  :=  true.B
                Inline(start(activation))
                pending  :=  true.B
              }
              caller.waitCondition(false.B)
            }.elsewhen(thisCallActive && activation.done) {
              binding.callActive  :=  false.B
              binding.activeBindingId  :=  0.U(binding.activeBindingId.getWidth.W)
              pending  :=  false.B
              caller.jump(returnTo)
            }.otherwise {
              caller.waitCondition(false.B)
            }
          }
          result
        case _ =>
          throw new Exception(s"[HwOS] Call('$returnTo') on HwFunction '${func.name}' must be used inside ThreadCtx.")
      }
    } finally {
      popInvokeMode()
      CallStack.pop()
    }
  }

  /**
   * 从当前 thread-function 的静态调用帧返回到预绑定的 continuation。
   * 在 step body 内会直接作用于当前 thread；如果在 thread-entry 顶层调用，
   * 则会自动补一个尾部 return step。
   */
  def Return(): Unit = {
    currentInvokeMode match {
      case Some(InlineMode) =>
        throw new Exception(
          s"[HwOS] SysCall.Return() is illegal inside Inline(...). " +
            s"Use SysCall.Inline(...) only for natural-fallthrough segments.",
        )
      case _ =>
    }

    ContextScope.current match {
      case AtomicCtx(t: ThreadDebugApi) =>
        CallStack.markReturned()
        CallStack.currentReturnTarget match {
          case Some(target) =>
            t.jump(target)
          case None =>
            t.runtimeExit()
        }
      case ThreadCtx(t) =>
        CallStack.markReturned()
        CallStack.currentReturnTarget match {
          case Some(target) =>
            t.Step(ContinuationNaming.freshReturnStepName(System.identityHashCode(t), target)) {
              t.jump(target)
            }
          case None =>
            t.Step(ContinuationNaming.freshReturnStepName(System.identityHashCode(t), "ThreadExit")) {
              t.runtimeExit()
            }
        }
      case _ =>
        throw new Exception("[HwOS] SysCall.Return() can only be used inside ThreadCtx or AtomicCtx.")
    }
  }

  // ==========================================
  // 2. Process Control Layer (生命周期与调度)
  // ==========================================


  /**
   * 线程级 kill sugar：本质上就是 reset。
   */
  def kill(target: HardwareThread): HwInline[Unit] = HwInline.stateless("SysCall kill"){ agent =>
    target.reset()
  }

  /**
   * 远程启动：唤醒目标线程
   */
  def start(target: HardwareThread): HwInline[Unit] = HwInline.stateless("SysCall start"){ agent =>
    target.runtimeStart()
  }

  /**
   * 当前线程主动结束生命周期。
   * 这是系统级 runtime 操作，只允许内核在 root Return() 等场景内部使用。
   */
  private[kernel] def exit(): HwInline[Unit] = HwInline.atomic("SysCall exit current") { _ =>
    ContextScope.getCurrentThread().runtimeExit()
  }

  /**
   * 创建子线程
   * 生命周期控制权限由目标 thread 自己的 lifecycle ACL 管理，
   * 不是 HwContext 的普通 resource ACL。
   */
  def fork(name: String)(childBody: HardwareThread => Unit): HardwareThread = {
    val parent = ContextScope.getCurrentThread()
    
    // 1. 创建子线程
    val childName = s"${parent.name.split("/").last}_fork_$name"
    val child = parent.owner.createThread(childName) // 调用 Process 的方法创建
    
    // 2. 核心魔法：所有权注册 (Ownership Registration)
    // 父线程显式获得子线程的生命周期控制权，取代隐式的“父子血缘”逻辑
    // 3. 注入逻辑
    child.entry {
      childBody(child)
    }

    // 4. 启动并返回句柄
    Inline(start(child))
    child
  }



}
