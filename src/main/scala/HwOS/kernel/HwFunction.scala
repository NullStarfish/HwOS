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
  def emit(self: HardwareAgent): T 
}

// 伴生对象：提供语法糖
object HwFunction {
  // 基础 apply：保留给需要极度自定义 Context 行为的底层 API
  def apply[T](funcName: String)(block: HardwareAgent => T): HwFunction[T] = {
    new HwFunction[T] {
      override def name: String = funcName
      override def emit(self: HardwareAgent): T = block(self)
    }
  }

  // ==========================================
  // 语法糖 1：限定在 Step 内部执行 (带阻塞、握手等时序动作)
  // ==========================================
  def atomic[T](funcName: String)(block: HardwareThread => T): HwFunction[T] = apply(funcName) { agent =>
    ContextScope.current match {
      case AtomicCtx(t) => block(t)
      case _ => throw new Exception(s"[HwOS] 违规调用！'$funcName' 必须在 Step (AtomicCtx) 内部执行。")
    }
  }

  // ==========================================
  // 语法糖 2：限定在 Thread 内部执行 (通常用于展开一组连续的 Step)
  // ==========================================
  def thread[T](funcName: String)(block: HardwareThread => T): HwFunction[T] = apply(funcName) { agent =>
    ContextScope.current match {
      case ThreadCtx(t) => block(t)
      case _ => throw new Exception(s"[HwOS] 违规调用！'$funcName' 必须在 Thread (ThreadCtx) 内部执行。")
    }
  }

  // ==========================================
  // 语法糖 3：Logic 与 Atomic 共享的一致逻辑 (纯组合逻辑/无状态)
  // 只要处于 AtomicCtx 或 LogicCtx 均可调用，完全抹平两者的差异
  // ==========================================
  def stateless[T](funcName: String)(block: HardwareAgent => T): HwFunction[T] = apply(funcName) { agent =>
    ContextScope.current match {
      case AtomicCtx(_) | LogicCtx(_) => block(agent)
      case _ => throw new Exception(s"[HwOS] 违规调用！'$funcName' 只能在 Step 或 LogicCtx 中作为组合逻辑执行。")
    }
  }

  def bindings[T](funcName: String)(block: HardwareAgent => T): HwFunction[T] = apply(funcName) {
    agent => block(agent)
  }
}