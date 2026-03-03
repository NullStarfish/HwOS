package HwOS.kernel.lang
import chisel3._
import HwOS.kernel.context.{ContextScope, ResourceManager}

object HwOSLanguage {
  implicit class SecureAssign[T <: Data](val lhs: T) extends AnyVal {
    
   // 1. 常规安全赋值：受 Context.isActive 的绝对物理门控
    def <==(rhs: T): Unit = {
      val agent = ContextScope.getCurrentAgent()
      ResourceManager.checkSignalWrite(lhs, agent.ctx)
      ResourceManager.recordDrive(lhs, agent.ctx)
      
      // 【核心架构跃升】：在底层硬件生成一个全局使能 Mux！
      // 如果当前上下文处于静默/被强杀状态，这行赋值在物理上直接断开空转。
      when (agent.ctx.isActive) {
        lhs := rhs
      }
    }

    // 2. 内核级特权赋值：无视 isActive，用于死神进程 (Reaper) 的强杀兜底
    def <==!(rhs: T): Unit = {
      val agent = ContextScope.getCurrentAgent()
      ResourceManager.checkSignalWrite(lhs, agent.ctx)
      ResourceManager.recordDrive(lhs, agent.ctx)
      
      // 不受 isActive 门控，强制驱动物理连线
      lhs := rhs
    }
  } 
  

  implicit class SecureVecAccess[T <: Data](val vec: Vec[T]) extends AnyVal {
    def at(idx: UInt): T = {
      val childNode = vec(idx)
      ResourceManager.markDynamicVecAccess(childNode, vec)
      childNode
    }
    def at(idx: Int): T  = propagateOwnership(vec(idx))

    private def propagateOwnership(childNode: T): T = {
      // 只有当子节点尚未被注册时，才执行 context 继承 (防止重复注册报错)
      if (ResourceManager.getOwner(childNode).isEmpty) {
        ResourceManager.getOwner(vec).foreach { owner =>
          // 1. 继承父 context 的 ownership
          ResourceManager.registerOwner(childNode, owner)
          
          // 2. 继承父 context 的 ACL
          ResourceManager.getAllowedActors(vec).foreach { actor =>
            if (actor != owner) {
              ResourceManager.delegatePermission(childNode, owner, actor)
            }
          }
        }
      }
      childNode
    }
  }
}
