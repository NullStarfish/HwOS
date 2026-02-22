
package HwOS.kernel

import chisel3._
import chisel3.util._
import scala.collection.mutable.{ArrayBuffer, LinkedHashMap}

trait HardwareAgent extends HwOwner {
  val owner: HwProcess
  val name: String
  val debugEnable: Boolean


  def agentPrint(p:Printable): Unit = {
    if (debugEnable) {
      printf(p"[$name] " + p + p"\n")
    }
  }
  def agentPrint(msg: String): Unit = {
    if (debugEnable) {
      printf(s"[$name] $msg\n")
    }
  }


}

class HardwareLogic(val name: String, val owner: HwProcess, val debugEnable: Boolean = true) extends HardwareAgent {
  def run(block: => Unit): Unit = {
    ContextScope.withContext(LogicCtx(this)) {
      block
    }
  }
}

class HardwareThread(val name: String, val owner: HwProcess, val debugEnable: Boolean = true, val isMealy: Boolean = false) extends HardwareAgent  {
  val tls = scala.collection.mutable.Map[String, Any]()

  class StepNode(val name: String, val block: () => Unit) {
    var prev: StepNode = _
    var next: StepNode = _
    
    // 核心状态：是否被前一个节点抢占（吞噬）
    // 如果为 true，这个节点将失去独立的 PC，变成上一节点逻辑的一部分
    var isHijacked: Boolean = false
    
    // 调试用的 ID
    var allocatedPC: Int = -1
  }

  // 1. 存储节点而非直接生成逻辑
  val nodes = ArrayBuffer[StepNode]()
  // 用于在 block 内部查找当前节点
  private var currentGeneratingNode: StepNode = _

  object Next {
    /**
     * hijack: 抢夺下一个 Step 的控制权
     * 1. 立即在当前位置展开下一个 Step 的逻辑 (Inline)
     * 2. 剥夺下一个 Step 的独立 PC (Shield PC decoding)
     */
    def hijack(): Unit = {
      val me = currentGeneratingNode
      val victim = me.next
      
      if (victim == null) {
        throw new Exception(s"[HwOS] Step '${me.name}' tried to hijack non-existent next step!")
      }
      
      if (victim.isHijacked) {
         // 防止重复 hijack (虽然逻辑上允许 A hijack B, B hijack C 形成长链)
         // 这里允许链式抢占
      }

      // 动作 1: 标记受害者被吞噬
      // 这一步至关重要：当主循环遍历到 victim 时，会看到这个标记并跳过它
      victim.isHijacked = true
      
      // 动作 2: 立即展开受害者的逻辑 (Inline)
      // 注意：这里是在 currentGeneratingNode 的 when(pc===...) 作用域内
      // 所以 victim 的逻辑成为了 me 的一部分
      ContextScope.withContext(AtomicCtx(HardwareThread.this)) {
        // 我们临时把 currentGeneratingNode 切换为 victim，
        // 这样如果 victim 里面也叫了 Next.hijack，它能正确找到 victim.next
        val save = currentGeneratingNode
        currentGeneratingNode = victim
        victim.block()
        currentGeneratingNode = save
      }
    }
  }



  def Step(name: String)(block: => Unit): Unit = {
    // 获取完整的调试路径名
    val prefix = CallStack.getCurrentPrefix
    val fullName = s"${prefix}${name}"
    
    // 此时只创建节点，不生成硬件！
    val node = new StepNode(fullName, () => {
      ContextScope.withContext(AtomicCtx(this)) {
        block
      }
    })
    nodes += node
  }





  private val globals = ArrayBuffer[() => Unit]()

  private val activeReg = RegInit(false.B)
  private val doneReg = RegInit(false.B)

  private var pcEntity :UInt = _
  private var _generated = false

  private var hasExit: Boolean = false

  val startWire  = WireInit(false.B) 
  val doneWire   = WireInit(false.B)
  val abortWire = WireInit(false.B)

  val freeze = WireInit(false.B)


  def pc: UInt = {
    if (pcEntity == null) {
      agentPrint("Cannot access thread.pc outside of entry!!!")
      throw new Exception("pc not set")
    }

    pcEntity
  }
  





  def active: Bool = if (isMealy) (activeReg || startWire) else activeReg
  def done: Bool = doneWire || doneReg


  def start(): Unit = {
    startWire := true.B
    if (isMealy) {
      assert(pc === 0.U, "mealy should ensure start with pc = 0!")
    }
  }

  def abort(): Unit = {
    abortWire := true.B
    if (debugEnable) printf(p"[$name] *** ABORT SIGNAL RECEIVED ***\n")
  }
  
  def exit(): Unit = {
    ContextScope.current match {
      case AtomicCtx(t) => {
        if (t != this) throw new Exception("Cannot exit another thread!")
        t.hasExit = true
      }
      case ThreadCtx(t) => {
        if (t != this) throw new Exception("Cannot exit another thread!")
        t.hasExit = true // 标记该线程拥有合法的退出路径
      }
      case _ => throw new Exception("exit() must be called inside a Step or Thread context!")
    }
    
    // 硬件退出逻辑对两种情况都适用
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
    if (nodes.isEmpty) {return}

    for (i <- 0 until nodes.length) {
      if (i > 0) nodes(i).prev = nodes(i-1)
      if (i < nodes.length - 1) nodes(i).next = nodes(i+1)
    }



    var pcCounter = 0

    //生成pcReg，让综合器自己优化去吧
    val maxSteps = nodes.length 
    val pcWidth  = log2Ceil(maxSteps + 1)
    val pcReg    = RegInit(0.U(pcWidth.W))
    pcEntity     = pcReg 




    val execAllowed = WireInit(false.B)



    for (node <- nodes) {
      // 只有当节点没有被前面的节点 hijack 时，才分配 PC 和生成译码逻辑
      if (!node.isHijacked) {
        node.allocatedPC = pcCounter
        currentGeneratingNode = node // 设置上下文，供 Next.hijack 使用
        when ((pcEntity === pcCounter.U) && execAllowed) {
          pcReg := pcReg + 1.U
          node.block() //注意，此时block中的hijack已经被调用，这使得我们能够告知编译器，下一个node已经被hijack了
        }
        pcCounter += 1
      } else {
        if (debugEnable) {
          // printf(p"Step ${node.name} is hijacked, logic merged.\n")
        }
      }
    }
    // 上面我们完成了：pc译码分配block的逻辑，这是静态的，下面我们通过active等逻辑，让他动起来










    // if (debugEnable) {
    //   val wasActive = RegNext(active)
    //   val lastPc    = RegNext(pcReg)
    //   val watchDog  = RegInit(0.U(32.W))
    //   when (!wasActive && active) { agentPrint("--- ONLINE ---") }
    //   when (wasActive && !active) { agentPrint("--- OFFLINE ---") }
    //   val justStarted = active && !wasActive
    //   when ((active && pcReg =/= lastPc) || justStarted) {
    //     for ((name, idx) <- stepNames.zipWithIndex) {
    //       when (pcReg === idx.U) { agentPrint(s"EXEC [PC $idx] $name") }
    //     }
    //   }
    //   when (active && (pc === lastPc)) {
    //     watchDog := watchDog + 1.U
    //   } .otherwise {
    //     watchDog := 0.U
    //   }
    //   when(watchDog >= 1000.U) {
    //     //assert (false.B, "Detected dead lock! ")
    //   }
    // }





    //分情况：当moore时：active = activeReg。当没有启动的时候，发送脉冲，activeReg变高，在下一拍，逻辑开始工作。当active的时候发送start，并没有用，因为此时active为高，只有在active为低的时候，start才有效：我们必须修复这一点：在done的时候，也可以进行start：
    //当mealy的时候，第一拍，active就是startWire，随后activeReg就启动了，来维持自己的状态，但是我们发现下面这个条件永远不可能满足
    

    when (abortWire) {
      activeReg := false.B
      pc := 0.U
      doneWire := false.B
      doneReg := false.B
    }
    .elsewhen (active) {
      execAllowed := true.B
      if (isMealy) {
        activeReg := true.B //维持状态
        doneReg := false.B //保证在mealy的时候，doneReg也能被下拉
      }

      when(doneWire) {
        when (startWire) {
          activeReg := true.B
          doneReg   := false.B // <--- 关键！重启时，强制清除 Done 状态
          pc := 0.U
        } .otherwise {
          doneReg := true.B//维持状态
          activeReg := false.B
        }
      }

      ContextScope.withContext(ThreadCtx(this)) {
        globals.foreach(_()) 
      }
    } .otherwise {
      when (startWire) {
        activeReg := true.B
        doneReg := false.B
        pcReg     := 0.U
      }
    }

    if (!this.hasExit) {
      agentPrint("The thread doesn't have an exit!!!")
      throw new Exception
    }


    if (debugEnable) { //放在最后防止被覆盖
      when (this.freeze) {
        this.pc := this.pc
      }
    }



  }


  def waitCondition(cond: Bool): Unit = { 
    ContextScope.current match {
      case AtomicCtx(t) => {}
      case _ => {agentPrint(p"Do not use waitCondition outside entry!!!"); throw new Exception("waitCondition outside entry")}
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
  } 
}