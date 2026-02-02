
package HwOS.kernel

import chisel3._
import chisel3.util._
import scala.collection.mutable.{ArrayBuffer, LinkedHashMap}

trait HardwareAgent {
  val owner: HwProcess
  val name: String
  val debugEnable: Boolean

  protected val managedSignals = LinkedHashMap[Data, (Data, Data)]()

  def agentPrint(fmt: String, data: Bits*): Unit = {
    if (debugEnable) {
      printf(s"[$name] " + fmt + "\n", data: _*)
    }
  }

  // 保持原有的信号托管功能，这对Driver非常有用
  def driveManaged[T <: Data](target: T, idle: T, default: T): T = {
    val proxy = Wire(chiselTypeOf(target))
    managedSignals(proxy) = (idle, default) 
    
    if (this.isInstanceOf[HardwareThread]) {
        val t = this.asInstanceOf[HardwareThread]
        // 只有当线程 Active 时才驱动 proxy，否则驱动 idle
        target := Mux(t.isActive, proxy, idle)
    } else {
        target := proxy
    }
    proxy
  }

  def driveManaged[T <: Data](target: T, default: T): T = driveManaged(target, default, default)
}

class HardwareLogic(val name: String, val owner: HwProcess, val debugEnable: Boolean = true) extends HardwareAgent {
  def run(block: => Unit): Unit = {
    ContextScope.withContext(LogicCtx(this)) {
      managedSignals.foreach { case (proxy, (_, default)) => proxy := default }
      block
    }
  }
}

class HardwareThread(val name: String, val owner: HwProcess, val debugEnable: Boolean = true) extends HardwareAgent {

  private val steps = ArrayBuffer[() => Unit]()
  private val stepNames = ArrayBuffer[String]()
  private val globals = ArrayBuffer[() => Unit]()

  // 内部状态寄存器
  private val activeReg = RegInit(false.B)
  private var pcEntity :UInt = _

  // 内部控制信号
  private val startPulse = WireInit(false.B) // 启动脉冲
  private val exitPulse  = WireInit(false.B) // 退出脉冲
  
  // 对外输出信号
  val done = WireInit(false.B) // 完成信号（一周期脉冲）

  def pc: UInt = {
    if (pcEntity == null) {
      throw new Exception(s"[$name] Cannot access thread.pc outside of entry! Make sure to call entry() first.")
    }
    pcEntity
  }

  def isActive: Bool = activeReg

  // ==========================================
  // 新版控制 API (Metaprogramming Style)
  // ==========================================

  /**
   * 启动此线程。
   * 可以在父线程的 Step 中调用，也可以在 Logic 中调用。
   * 产生一个周期的启动脉冲。
   */
  def start(): Unit = {
    startPulse := true.B
  }

  /**
   * 结束当前线程。
   * 必须在 Step 内部调用。
   * 产生 done 信号并拉低 active。
   */
  def exit(): Unit = {
    ContextScope.current match {
      case AtomicCtx(t) if t == this => 
        exitPulse := true.B
      case _ => 
        throw new Exception(s"[$name] exit() must be called from within its own Step.")
    }
  }

  /**
   * Fork 一个子线程，并注册回调。
   * @param child 要启动的子线程
   * @param callback 当子线程 done 时执行的逻辑 (注入到当前线程的 Global 域)
   */
  def fork(child: HardwareThread)(callback: => Unit): Unit = {
    // 1. 在当前上下文中触发子线程启动
    // 如果是在 Step 中调用，则是精确时刻启动；如果是在 Global 中，则是每周期尝试启动(不推荐)
    child.start()

    // 2. 将回调逻辑注入到当前线程的 Global 域
    // 相当于构建了一个静态的监听电路：当 child.done 且当前线程存活时(通常Global一直有效)，执行 callback
    Global {
      when(child.done) {
        callback
      }
    }
  }


  // ==========================================
  // 生成逻辑 (Entry)
  // ==========================================

  def entry(block: => Unit): Unit = {
    val totalSteps = steps.length
    // 允许空线程，作为单纯的逻辑容器或占位符
    val width = if (totalSteps > 0) log2Ceil(totalSteps + 1) else 1
    val pcReg = RegInit(0.U(width.W))
    pcEntity = pcReg

    // 1. 建立上下文并执行用户的构造代码 (收集 steps 和 globals)
    ContextScope.withContext(ThreadCtx(this)) { block }
    
    // 初始化托管信号
    managedSignals.foreach { case (proxy, (_, default)) => proxy := default }

    // 2. 状态机逻辑
    // 优先级：Exit > Start > Normal Flow
    
    // Default assignments
    done := false.B

    when (exitPulse) {
      activeReg := false.B
      pcReg := 0.U
      done := true.B // Exit 触发 Done
      if (debugEnable) agentPrint("EXIT (Signal)")
    } .elsewhen (startPulse) {
      activeReg := true.B
      pcReg := 0.U
      if (debugEnable) agentPrint("START (Signal)")
    } .elsewhen (activeReg) {
      // 只有 Active 时才执行 Step 逻辑
      
      // 执行当前 PC 对应的 Step
      if (totalSteps > 0) {
        for ((func, idx) <- steps.zipWithIndex) {
          when (pcReg === idx.U) { func() }
        }
      }

      // PC 递增逻辑
      // 注意：现在的 Step 里的 waitCondition 只是保持 PC 不变
      // 如果没有 waitCondition 阻挡，PC 默认自增
      // 这里需要检测 Step 内部是否触发了 exitPulse，如果触发了，上面 exitPulse 的逻辑会在下一周期生效(或同周期生效取决于优先级，这里是寄存器更新逻辑)
      
      // 更正：exitPulse 是 Wire，在 Step 内部被赋值。
      // 由于 Chisel 的 Last Connect 语义，我们需要确保 exitPulse 的处理逻辑覆盖掉下面的 PC 更新
      
      // 我们将 PC 更新逻辑放在 elsewhen 中，确保 exit 优先级最高
      // 但 exitPulse 是在本周期产生的，activeReg 下周期变低。
      
      // 检查是否"跑飞" (Runaway)
      if (totalSteps > 0) {
        when (pcReg >= totalSteps.U) {
           // Runaway 状态：线程结束了所有 Step 但没有调用 exit()。
           // 这种设计允许开发者在最后一步做一些维持操作，或者是一个 Bug。
           // 这里我们选择保持 Active 但不重置 PC，直到外部干预或 Reset。
           if (debugEnable) agentPrint("Runaway Warning: PC exceeded steps without exit()")
        } .otherwise {
           pcReg := pcReg + 1.U
        }
      }
    }

    // 3. 全局逻辑注入 (Global)
    // 无论 Active 与否，Global 逻辑都会生成硬件电路 (通常用于中断监听、Fork 回调等)
    // 开发者可以在 Global 块内部判断 isActive 
    globals.foreach(_()) 


    // ==========================================
    // Debug 打印
    // ==========================================
    if (debugEnable) {
      val wasActive = RegNext(activeReg)
      val lastPc    = RegNext(pcReg)
      
      when (!wasActive && activeReg) { agentPrint("--- ONLINE ---") }
      when (wasActive && !activeReg) { agentPrint("--- OFFLINE ---") }
      
      if (totalSteps > 0) {
        for ((name, idx) <- stepNames.zipWithIndex) {
          when (activeReg && pcReg === idx.U && (pcReg =/= lastPc || (!wasActive && activeReg))) { 
            agentPrint(s"EXEC [PC $idx] $name") 
          }
        }
      }
    }
  }

  // ==========================================
  // DSL 方法
  // ==========================================
  
  def Step(name: String)(block: => Unit): Unit = {
    ContextScope.current match {
      case ThreadCtx(t) if t == this => // OK
      case _ => throw new Exception(s"[$name] Step must be defined inside Thread.entry context.")
    }
    stepNames += name
    steps += { () => 
      ContextScope.withContext(AtomicCtx(this)) { block }
    }
  }

  def waitCondition(cond: Bool): Unit = { 
    ContextScope.current match {
      case AtomicCtx(t) if t == this => // OK
      case _ => throw new Exception("waitCondition must be used inside a Step.")
    }
    // 阻塞逻辑：如果条件不满足，PC 保持不变 (撤销默认的 +1)
    when(!cond) { 
      pcEntity := pcEntity 
    } 
  }

  def Global(block: => Unit): Unit = { 
    ContextScope.current match {
      case ThreadCtx(t) if t == this => // OK
      case _ => throw new Exception("Global must be defined inside Thread.entry context.")
    }
    globals += { () => block } 
  }
}