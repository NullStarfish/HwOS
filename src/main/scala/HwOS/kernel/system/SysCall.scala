package HwOS.kernel.system

import chisel3._
import HwOS.kernel.context.{AtomicCtx, ContextScope, ThreadCtx}
import HwOS.kernel.debug.{CallStack, ContinuationNaming}
import HwOS.kernel.function.HwInline
import HwOS.kernel.thread.{HardwareThread, ThreadDebugApi}
import HwOS.kernel.thread.step.EdgeAction
import HwOS.kernel.thread.step.{EdgeGuardContext, EdgePatchAnalysis, PreLoweringAnalysis}
import HwOS.kernel.thread.step.ThreadRuntimeLogic
import HwOS.kernel.system.CallProtocolContext.{CallSiteBinding, CallSiteSnapshot, ContinuationBinding, ContinuationSnapshot, ReturnEdgePatch}

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

  private[kernel] def withIsolatedInvokeMode[T](block: => T): T = {
    val saved = invokeModeStack.get()
    invokeModeStack.set(Nil)
    try block
    finally invokeModeStack.set(saved)
  }

  private def currentCallSite = CallProtocolContext.currentCallSiteSnapshot
  private def currentContinuation = CallProtocolContext.currentContinuationSnapshot

  final class CallEdge private[system] (private val appendThunk: (() => Unit) => Unit) {
    def add(block: => Unit): Unit = {
      appendThunk(() => block)
    }
  }

  final class CallSiteHandle[T] private[system] (
      val func: HwInline[T],
      val returnTo: Option[String],
  ) {
    private var returnEdgeThunk: () => Unit = () => ()
    val edge: CallEdge = new CallEdge(next => {
      val previous = returnEdgeThunk
      returnEdgeThunk = () => {
        previous()
        next()
      }
    })

    private[system] def continuationBinding(target: Option[String]): ContinuationBinding =
      CallProtocolContext.bindContinuation(
        targetLabel = target,
        requiresExplicitReturn = true,
        returnEdgePatch = Some(ReturnEdgePatch(continuationTarget = target, emitThunk = () => returnEdgeThunk())),
      )
  }

  private def markExplicitReturnOnCurrentThread(): Unit = {
    scala.util.Try(ContextScope.current).toOption.foreach {
      case AtomicCtx(t: ThreadDebugApi) =>
        t.markExplicitReturnEncountered()
        currentCallSite.foreach(site => t.markCallSiteReturned(site.id))
      case ThreadCtx(t: ThreadDebugApi) =>
        t.markExplicitReturnEncountered()
        currentCallSite.foreach(site => t.markCallSiteReturned(site.id))
      case _ =>
    }
  }

  private def recordReturnAction(continuation: Option[ContinuationSnapshot]): Boolean = {
    if (EdgePatchAnalysis.isActive) {
      EdgePatchAnalysis.recordReturn(continuation)
      true
    } else if (PreLoweringAnalysis.isActive) {
      PreLoweringAnalysis.record(
        EdgeAction.Return(
          continuation = continuation,
        ),
      )
      true
    } else {
      false
    }
  }

  private def emitImmediateReturn(t: HardwareThread, continuation: Option[ContinuationSnapshot]): Unit = {
    if (recordReturnAction(continuation)) {
      return
    }
    ThreadRuntimeLogic.emitReturnEdgePatch(continuation.flatMap(_.returnEdgePatch))
    continuation.flatMap(_.targetLabel) match {
      case Some(target) =>
        t.jump(target)
      case None =>
        t.runtimeExit()
    }
  }

  // ==========================================
  // 1. Function Linker Layer (逻辑注入)
  // ==========================================

  /**
   * 硬件函数调用：纯粹的代码内联展开，不涉及生命周期权限。
   * 目标线程在此过程中直接获取生成的逻辑所属权。
   */
  def Inline[T](func: HwInline[T]): T = {
    EdgeGuardContext.withInlineBoundary {
      pushInvokeMode(InlineMode)
      try {
        func.emit(ContextScope.getCurrentAgent())
      } finally {
        popInvokeMode()
      }
    }
  }

  def Call[T](func: HwInline[T]): T = {
    Call(CallSite(func))
  }

  def CallSite[T](func: HwInline[T], returnTo: String): CallSiteHandle[T] =
    new CallSiteHandle(func, Some(returnTo))

  def CallSite[T](func: HwInline[T]): CallSiteHandle[T] =
    new CallSiteHandle(func, None)

  /**
   * 线程级函数调用：为被调用的 thread-function 静态绑定一个返回地址。
   * Return() 将跳转到该返回地址。
   */
  def Call[T](func: HwInline[T], returnTo: String): T = {
    Call(CallSite(func, returnTo))
  }

  def Call[T](site: CallSiteHandle[T]): T = {
    val resolvedReturnTo = site.returnTo.orElse(currentContinuation.flatMap(_.targetLabel))
    pushInvokeMode(CallMode)
    try {
      ContextScope.current match {
        case ThreadCtx(_) | AtomicCtx(_) =>
        case _ =>
          val returnTargetDesc = resolvedReturnTo.getOrElse("<thread-exit>")
          throw new Exception(
            s"[HwOS] Call('$returnTargetDesc') must be used inside ThreadCtx or AtomicCtx.",
          )
      }
      CallProtocolContext.withCallSite(site.func.name, site.continuationBinding(resolvedReturnTo)) { liveSite =>
        ContextScope.current match {
          case ThreadCtx(debugThread: ThreadDebugApi) =>
            debugThread.registerCallSiteReturnRequirement(liveSite.snapshot)
          case AtomicCtx(debugThread: ThreadDebugApi) =>
            debugThread.registerCallSiteReturnRequirement(liveSite.snapshot)
          case _ =>
        }
        val currentAgent = ContextScope.getCurrentAgent()
        ContextScope.current match {
          case AtomicCtx(t) =>
            try {
              site.func.emit(currentAgent)
            } catch {
              case ex: Exception if ex.getMessage != null && ex.getMessage.contains("Illegal inline call") =>
                ContextScope.withContext(ThreadCtx(t)) {
                  site.func.emit(currentAgent)
                }
            }
          case _ =>
            site.func.emit(currentAgent)
        }
      }
    } finally {
      popInvokeMode()
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

    CallProtocolContext.markReturned()
    markExplicitReturnOnCurrentThread()

    if (recordReturnAction(currentContinuation)) {
      return
    }

    ContextScope.current match {
      case AtomicCtx(t: HardwareThread) =>
        emitImmediateReturn(t, currentContinuation)
      case ThreadCtx(t: HardwareThread) =>
        val capturedContinuation = currentContinuation
        val returnStepLabel = capturedContinuation
          .flatMap(_.targetLabel)
          .getOrElse("ThreadExit")
        t.Step(ContinuationNaming.freshReturnStepName(System.identityHashCode(t), returnStepLabel)) {
          emitImmediateReturn(t, capturedContinuation)
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
