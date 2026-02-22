package HwOS.kernel

import chisel3._

object SysCall {

  // ==========================================
  // 1. Function Linker Layer (逻辑注入)
  // ==========================================

  /**
   * 硬件函数调用：纯粹的代码内联展开，不涉及生命周期权限。
   * 目标线程在此过程中直接获取生成的逻辑所属权。
   */
  def Call[T](t: HardwareThread, func: HwFunction[T]): T = {
    CallStack.push(func.name)
    try {
      func.emit(t)
    } finally {
      CallStack.pop()
    }
  }

  // ==========================================
  // 2. Process Control Layer (生命周期与调度)
  // ==========================================

  /**
   * 线程自我终结 (自杀)
   */
  def exit(): Unit = {
    val t = ContextScope.getCurrentThread()
    t.exit() 
  }

  /**
   * 远程杀手：强制中止目标线程 (他杀)
   */
  def kill(target: HardwareThread): Unit = {
    checkLifecyclePermission(target, "kill")
    target.abort()
  }

  /**
   * 远程启动：唤醒目标线程
   */
  def start(target: HardwareThread): Unit = {
    checkLifecyclePermission(target, "start")
    target.start()
  }

  /**
   * 创建子线程
   * 这里彻底移除了 parentThread 的概念，将权限管理 100% 委托给 HwOwner 体系。
   */
  def fork(name: String)(childBody: HardwareThread => Unit): HardwareThread = {
    val parent = ContextScope.getCurrentThread()
    
    // 1. 创建子线程
    val childName = s"${parent.name.split("/").last}_fork_$name"
    val child = parent.owner.createThread(childName) // 调用 Process 的方法创建
    
    // 2. 核心魔法：所有权注册 (Ownership Registration)
    // 父线程显式获得子线程的生命周期控制权，取代隐式的“父子血缘”逻辑
    child.grantLifecycle(parent)

    // 3. 注入逻辑
    child.entry {
      childBody(child)
    }

    // 4. 启动并返回句柄
    child.start()
    child
  }


  // ==========================================
  // 内部鉴权方法 (纯粹基于 HwOwner 模型)
  // ==========================================
  private def checkLifecyclePermission(target: HardwareThread, action: String): Unit = {
    // 获取当前试图执行控制流的执行者 (HwOwner 身份)
    val currentActor: HwOwner = ContextScope.current match {
      case ThreadCtx(t) => t
      case AtomicCtx(t) => t
      case LogicCtx(l)  => l
      case _ => throw new Exception(s"[HwOS Security] Cannot $action outside of a valid Agent context.")
    }

    // 鉴权逻辑变得极其纯粹：
    // 1. currentActor 是否是目标线程的领主？(比如 Process 实例本身)
    val isOwner = (target.owner == currentActor)
    // 2. currentActor 是否被显式授予了生命周期控制权？(包含 Fork 带来的授权)
    val isGranted = target.grantedLifecycle.contains(currentActor)

    if (!isOwner && !isGranted) {
      throw new Exception(s"[HwOS SegFault] Access Denied! Agent '${currentActor.name}' lacks lifecycle permission to $action Thread '${target.name}'. Requires explicit grantLifecycle().")
    }
  }
}