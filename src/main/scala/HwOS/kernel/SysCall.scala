package HwOS.kernel

import chisel3._
import scala.util.Try // 引入 Try
import HwOS.kernel.HwOSLanguage._
object SysCall {

  // ==========================================
  // 1. Function Linker Layer (逻辑注入)
  // ==========================================

  /**
   * 硬件函数调用：纯粹的代码内联展开，不涉及生命周期权限。
   * 目标线程在此过程中直接获取生成的逻辑所属权。
   */
  def Call[T](func: HwFunction[T]): T = {
    CallStack.push(func.name)
    try {
      scala.util.Try {
        ContextScope.current match {
          case AtomicCtx(t) =>
            t.recordAtomicCallSnapshot(CallStack.getSnapshot)
          case ThreadCtx(t) => // 线程级调用的暂不挂载到具体的 PC
          case _ =>
        }
      }
      func.emit(ContextScope.getCurrentAgent())
    } finally {
      CallStack.pop()
    }
  }

  // ==========================================
  // 2. Process Control Layer (生命周期与调度)
  // ==========================================


  /**
   * 远程杀手：强制中止目标线程 (他杀)
   */
  def kill(target: HardwareThread): HwFunction[Unit] = HwFunction.stateless("SysCall kill"){ agent =>
    target.requireLifecycleAccess(agent.ctx, "kill")
    ContextScope.withContext(AtomicCtx(target)) {
      target.ctx.kernelKillSignal <==! true.B
    }
  }

  /**
   * 远程启动：唤醒目标线程
   */
  def start(target: HardwareThread): HwFunction[Unit] = HwFunction.stateless("SysCall start"){ agent =>
    target.requireLifecycleAccess(agent.ctx, "start")
    target.start()
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
    child.grantLifecycle(child, parent)

    // 3. 注入逻辑
    child.entry {
      childBody(child)
    }

    // 4. 启动并返回句柄
    Call(start(child))
    child
  }



}
