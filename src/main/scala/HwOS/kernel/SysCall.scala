package HwOS.kernel

object SysCall {

  /**
   * Call[T]: 注入硬件函数并获取其返回的硬件对象
   * * @param t    目标线程
   * @param func 目标函数 (HwFunction[T])
   * @return     func 内部生成的硬件对象 (例如运算结果的寄存器)
   */
  def Call[T](t: HardwareThread, func: HwFunction[T]): T = {
    // 1. 上下文检查
    ContextScope.current match {
      case ThreadCtx(current) => 
        if (current != t) println(s"[SysCall.Call Warning] Context mismatch: Injecting into ${t.name} while inside ${current.name}.")
      case _ => 
    }

    // 2. 压栈：进入函数命名空间
    CallStack.push(func.name)

    try {
      // 3. 注入逻辑并返回结果
      // 这里执行 block(t)，不仅生成了 Step，还将 block 的最后一行表达式作为返回值传出去
      func.emit(t)
    } finally {
      // 4. 弹栈
      CallStack.pop()
    }
  }
}