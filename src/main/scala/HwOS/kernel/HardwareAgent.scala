
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

  private var pcEntity :UInt = _


  def pc: UInt = {
    if (pcEntity == null) {
      agentPrint("Cannot access thread.pc outside of entry!!!")
      throw new Exception("pc not set")
    }

    pcEntity
  }

  private val startWire  = WireInit(false.B)
  private val doneWire   = WireInit(false.B)
  

  private var _generated = false


  val freeze = WireInit(false.B)

  def isRunning: Bool = if (isMealy) (activeReg || startWire) else activeReg
  def done: Bool = doneWire


  def start(): Unit = {
    startWire := true.B
    if (isMealy) {
      assert(pc === 0.U, "mealy should ensure start with pc = 0!")
    }
  }
  
  def exit(): Unit = {
    ContextScope.current match {
      case AtomicCtx(t) => 
        if (t != this) throw new Exception("Cannot exit another thread!")
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

    val totalSteps = steps.length
    if (totalSteps == 0) return

    val width = log2Ceil(totalSteps + 1)
    val pcReg = RegInit(0.U(width.W))
    pcEntity = pcReg



    ContextScope.withContext(ThreadCtx(this)) { block } //注意，这是非常重要的
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
        assert (false.B, "Detected dead lock! ")
      }
    }



    doneWire := false.B


    when (active) {//mealy需要保证启动的时候，pc为0
      pcReg := pcReg + 1.U

      for ((func, idx) <- steps.zipWithIndex) {
        when (pcReg === idx.U) { func() }
      }
      

    } .otherwise {
      when (startWire) {
        activeReg := true.B //这一条对mealy没有什么意义
        pcReg     := 0.U
      }
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

  def Global(block: => Unit): Unit = { 
    ContextScope.current match {
      case ThreadCtx(t) => {}
      case _ => {agentPrint("Do not use Global outside entry!!!"); throw new Exception("global outside entry")}
    }
    globals += { () => block } 
  } //可以用来写全局中断
}