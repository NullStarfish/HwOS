package HwOS.kernel
import chisel3._


object HwOSLanguage {
  implicit class SecureAssign[T <: Data](val target: T) extends AnyVal {
    
    def <==(data: T): Unit = assign(data, reqAbort = true)
    def <==!(data: T): Unit = assign(data, reqAbort = false)

    private def assign(data: T, reqAbort: Boolean): Unit = {
      // 1. 获取当前正在执行代码的 Actor (必须是 Thread 或 Logic)
      val currentActor: HwOwner = ContextScope.current match {
        case ThreadCtx(t) => t
        case AtomicCtx(t) => t
        case LogicCtx(l)  => l
        case _ => throw new Exception("[HwOS] <== must be used inside a Thread or Logic entry!")
      }

      // 2. 编译期鉴权
      val ownerOpt = ResourceManager.getOwner(target)
      
      ownerOpt.foreach { owner =>
        val isOwner = (owner == currentActor)
        val isGranted = owner.grantedSignals.get(target).exists(_.contains(currentActor)) 
        if (!isOwner && !isGranted) {
           throw new Exception(s"[HwOS SegFault] '${currentActor.name}' cannot write to resource owned by '${owner.name}'.")
        }
      }

      // 3. 执行物理连线 (依然是由 currentActor 来施加 active/abort 保护)
      val idleValue = 0.U.asTypeOf(target)
      ContextScope.getCurrentAgent() match {
        case t: HardwareThread =>
          if (ownerOpt.contains(currentActor)) {
             target := Mux(t.active, data, idleValue) // 内部资源：省 Mux
          } else {
             if (reqAbort) target := Mux(t.active && !t.abortWire, data, idleValue)
             else          target := Mux(t.active, data, idleValue)
          }
        case l: HardwareLogic =>
          target := data
      }
    }
  }
}