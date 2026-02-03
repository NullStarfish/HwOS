
package HwOS.kernel

import chisel3._
import chisel3.util._
import scala.collection.mutable.{ArrayBuffer, LinkedHashMap}

trait HardwareAgent {
  val owner: HwProcess
  val name: String
  val debugEnable: Boolean

  protected val managedSignals = LinkedHashMap[Data, (Data, Data)]()

  def agentPrint(p:Printable): Unit = {
    if (debugEnable) {
      printf(p"[$name] " + p + p"\n")
    }
  }
  def agentPrint(msg: String): Unit = {
    if (debugEnable) {
      printf(p"[$name] $msg\n")
    }
  }

  def driveManaged[T <: Data](target: T, idle: T, default: T): T = {
    val proxy = Wire(chiselTypeOf(target))
    managedSignals(proxy) = (idle, default) 
    
    if (this.isInstanceOf[HardwareThread]) {
        val t = this.asInstanceOf[HardwareThread]
        target := Mux(t.isRunning, proxy, idle)
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

class HardwareThread(val name: String, val owner: HwProcess, val debugEnable: Boolean = true, val isMealy: Boolean = false) extends HardwareAgent {

  private val steps = ArrayBuffer[() => Unit]()
  private val stepNames = ArrayBuffer[String]()
  private val globals = ArrayBuffer[() => Unit]()

  private val activeReg = RegInit(false.B)
  dontTouch(activeReg)

  private var pcEntity :UInt = _

  private var hasExit: Boolean = false


  def pc: UInt = {
    if (pcEntity == null) {
      agentPrint("Cannot access thread.pc outside of entry!!!")
      throw new Exception("pc not set")
    }

    pcEntity
  }

  val startWire  = WireInit(false.B) 
  dontTouch(startWire)
  private val doneWire   = WireInit(false.B)
  

  private var _generated = false


  val freeze = WireInit(false.B)

  def isRunning: Bool = if (isMealy) (activeReg || startWire) else activeReg
  def done: Bool = doneWire


  def start(): Unit = {
    printf(p"Call Start!!\n")
    startWire := true.B
    if (isMealy) {
      assert(pc === 0.U, "mealy should ensure start with pc = 0!")
    }
  }
  
  def exit(): Unit = {
    ContextScope.current match {
      case AtomicCtx(t) => {
        if (t != this) throw new Exception("Cannot exit another thread!")
        t.hasExit = true
      }
      case _ => throw new Exception("exit() must be called inside a Step!")
    }
    
    activeReg := false.B
    pc  := 0.U
    doneWire  := true.B
  }
  



  def entry(block: => Unit): Unit = {
    if (_generated) {
      agentPrint("generated twice!!!")
      throw new Exception("generate thread twice")
    }
    _generated = true




    ContextScope.withContext(ThreadCtx(this)) { block } //注意，这是非常重要的
    val totalSteps = steps.length
    if (totalSteps == 0) return

    val width = log2Ceil(totalSteps + 1)
    val pcReg = RegInit(0.U(width.W))
    pcEntity = pcReg


    managedSignals.foreach { case (proxy, (_, default)) => proxy := default }



    val active = isRunning 

    if (debugEnable) {
      val wasActive = RegNext(active)
      val lastPc    = RegNext(pcReg)
      val watchDog  = RegInit(0.U(32.W))
      when (!wasActive && active) { agentPrint("--- ONLINE ---") }
      when (wasActive && !active) { agentPrint("--- OFFLINE ---") }
      val justStarted = active && !wasActive
      when ((active && pcReg =/= lastPc) || justStarted) {
        for ((name, idx) <- stepNames.zipWithIndex) {
          when (pcReg === idx.U) { agentPrint(s"EXEC [PC $idx] $name") }
        }
      }
      when (active && (pc === lastPc)) {
        watchDog := watchDog + 1.U
      } .otherwise {
        watchDog := 0.U
      }
      when(watchDog >= 1000.U) {
        //assert (false.B, "Detected dead lock! ")
      }
    }



    doneWire := false.B


    //分情况：当moore时：active = activeReg。当没有启动的时候，发送脉冲，activeReg变高，在下一拍，逻辑开始工作。当active的时候发送start，并没有用，因为此时active为高，只有在active为低的时候，start才有效：我们必须修复这一点：在done的时候，也可以进行start：
    //当mealy的时候，第一拍，active就是startWire，随后activeReg就启动了，来维持自己的状态，但是我们发现下面这个条件永远不可能满足
    //
    when (active) {
      if (isMealy) {
        activeReg := true.B //维持状态
      }

      pcReg := pcReg + 1.U

      for ((func, idx) <- steps.zipWithIndex) {
        when (pcReg === idx.U) { func() } //状态机硬件生成
      }

      when(startWire && doneWire) { //捕获最后一拍的启动
        pcReg := 0.U //对于mealy，startWire其实也是一样的
      }
      

    } .otherwise {
      when (startWire) {
        activeReg := true.B 
        pcReg     := 0.U
      }
    }

    if (!this.hasExit) {
      agentPrint("The thread doesn't have an exit!!!")
      throw new Exception
    }

    globals.foreach(_()) //最后生成，覆盖全局，而且可以访问pc


    if (debugEnable) { //放在最后防止被覆盖
      when (this.freeze) {
        this.pc := this.pc
      }
    }
  }

  
  def Step(name: String)(block: => Unit): Unit = {
    ContextScope.current match {
      case ThreadCtx(t) => {}
      case AtomicCtx(t) => {
        agentPrint("HwOS does not support step inside step!")
        throw new Exception
      }
      case _ =>{
        agentPrint("Do not use step outside entry !!!")
        throw new Exception
      }
    }
    stepNames += name
    steps += { () => 
      ContextScope.withContext(AtomicCtx(this)) { block }
    }
  }

  def waitCondition(cond: Bool): Unit = { 
    ContextScope.current match {
      case AtomicCtx(t) => {}
      case _ => {agentPrint("Do not use waitCondition outside entry!!!"); throw new Exception("waitCondition outside entry")}
    }

    when(!cond) { 
      this.pc := this.pc
    } 
  }

  def waitAndAct(cond: Bool)(block: => Unit): Unit = {
    ContextScope.current match {
      case AtomicCtx(t) => {}
      case _ => {agentPrint("Do not use waitCondition outside entry!!!"); throw new Exception("waitCondition outside entry")}
    }

    when (!cond) {
      this.pc := this.pc
    } .otherwise {
      block
    }
  }

  def Global(block: => Unit): Unit = { 
    ContextScope.current match {
      case ThreadCtx(t) => {}
      case _ => {agentPrint("Do not use Global outside entry!!!"); throw new Exception("global outside entry")}
    }
    globals += { () => block } 
  } //可以用来写全局中断
}