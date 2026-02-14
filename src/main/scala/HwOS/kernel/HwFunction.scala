package HwOS.kernel

/**
 * HwFunction: 硬件指令流定义 (Code Segment)
 * 它是位置无关的，只有被 Syscall.Call 时才会实例化。
 */
trait HwFunction {
  def name: String
  
  /**
   * 发射指令 (Emit Instructions)
   * @param self 这段代码将在哪个线程上展开 (Target Thread)
   */
  def emit(self: HardwareThread): Unit 
}

// 伴生对象：提供语法糖
object HwFunction {
  /**
   * 快速定义无参函数
   * val myTask = HwFunction("MyTask") { t => ... }
   */
  def apply(funcName: String)(block: HardwareThread => Unit): HwFunction = {
    new HwFunction {
      override def name: String = funcName
      override def emit(self: HardwareThread): Unit = block(self)
    }
  }
}