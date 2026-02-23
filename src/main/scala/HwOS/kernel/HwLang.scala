package HwOS.kernel
import chisel3._

object HwOSLanguage {
  implicit class SecureAssign[T <: Data](val target: T) extends AnyVal {
    
    def <==(data: T): Unit = assign(data, reqAbort = true)
    def <==!(data: T): Unit = assign(data, reqAbort = false)

    private def assign(data: T, reqAbort: Boolean): Unit = {
      // 1. 获取当前正在执行代码的 Actor (必须是 Thread 或 Logic)
      val currentActor: HardwareAgent = ContextScope.getCurrentAgent()

      // 2. 呼叫统一安全网关进行审计
      ResourceManager.recordDrive(target, currentActor) // 记录驱动者，触发多驱警告
      ResourceManager.checkSignalWrite(target, currentActor) // 检查所有权/授权，违规立刻抛出 SegFault

      // 3. 鉴权通过，执行物理连线
      currentActor match {
        case t: HardwareThread =>
          // Thread 拥有生命周期和时序状态，需施加 active/abort 保护
          val valid = if (reqAbort) (t.active && !t.abortWire) else t.active
          when (valid) { target := data }
        case l: HardwareLogic =>
          // Logic 作为无状态守护进程，直接连线
          target := data
      }
    }
  }
}