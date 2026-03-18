package HwOS.kernel.lang
import chisel3._
import HwOS.kernel.context.ResourceManager

object HwOSLanguage {
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
