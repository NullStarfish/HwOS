package HwOS.kernel

/**
 * HwFunction[T]: 硬件指令流定义 (Code Segment)
 * @tparam T 返回值的类型 (通常是 Chisel 的 Data 子类，如 UInt, Bundle，或者是 Reg)
 */
trait HwFunction[T] {
  def name: String
  
  /**
   * 发射指令 (Emit Instructions)
   * @param self 这段代码将在哪个线程上展开 (Target Thread)
   * @return 生成的硬件结果 (The hardware artifact created by this function)
   */
  def emit(self: HardwareThread): T 
}

// 伴生对象：提供语法糖
object HwFunction {
  /**
   * 定义带返回值的函数
   * val myTask = HwFunction("MyTask") { t => ...; resultReg }
   */
  def apply[T](funcName: String)(block: HardwareThread => T): HwFunction[T] = {
    new HwFunction[T] {
      override def name: String = funcName
      override def emit(self: HardwareThread): T = block(self)
    }
  }
}