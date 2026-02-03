package HwOS.kernel.drivers

import chisel3._
import chisel3.util._
import HwOS.kernel._

// 复用之前的定义
// object RegOp ...
// class ScoreboardEntry ...

class CentralizedScoreboardDriver(
    regs: Vec[UInt], 
    kernel: Kernel, 
    meta: DriverMeta,
    maxClients: Int = 8
  ) extends PhysicalDriver(meta) {

  // ==============================================================================
  // 1. 全局信号总线 (Global Bus)
  // ==============================================================================
  // [输入] 意图：各线程驱动
  val clientIntents = Wire(Vec(maxClients, new ScoreboardEntry(32)))
  
  // [输出] 裁决结果：集中式逻辑驱动，广播给各线程
  val clientStalls  = Wire(Vec(maxClients, Bool()))

  // 默认值兜底
  for (i <- 0 until maxClients) {
    clientIntents(i).op   := RegOp.Idle
    clientIntents(i).addr := 0.U
    // clientStalls 不需要默认值，因为下面会全覆盖驱动
  }

  // ==============================================================================
  // 2. 集中式仲裁逻辑 (Centralized Arbitration Logic)
  // ==============================================================================
  // 这段代码在 Driver 初始化时执行一次，生成唯一的硬件仲裁模块。
  // 综合工具可以极好地优化这里的逻辑。
  
  for (myId <- 0 until maxClients) {
    val myIntent = clientIntents(myId)
    val op   = myIntent.op
    val addr = myIntent.addr

    // A. 端口资源检查 (Priority Encoder 逻辑)
    // 统计比当前 ID 优先级高的人数
    val higherPriorityUsers = (0 until myId).map { i =>
      clientIntents(i).op === op
    }.foldLeft(0.U)(_ +& _)
    
    val limit = Mux(op === RegOp.Read, meta.read_clients.U, meta.write_clients.U)
    val portAvailable = higherPriorityUsers < limit

    // B. 冲突检测 (Hazard Logic)
    // 只有当自己不是 Idle 时才检测
    val hazard = WireInit(false.B)
    
    when (op === RegOp.Read) {
      // RAW Hazard
      hazard := (0 until maxClients).map { i =>
        (i.U =/= myId.U) && (clientIntents(i).op === RegOp.Write) && (clientIntents(i).addr === addr)
      }.foldLeft(false.B)(_ || _)
    } 
    .elsewhen (op === RegOp.Write) {
      // WAW Hazard
      val waw = (0 until maxClients).map { i =>
         (i.U =/= myId.U) && (clientIntents(i).op =/= RegOp.Idle) && (clientIntents(i).addr === addr)
      }.foldLeft(false.B)(_ || _)
      hazard := waw
    }

    // C. 输出裁决结果
    // 如果我没干活(Idle)，当然不 Stall；否则看资源和冲突
    clientStalls(myId) := (op =/= RegOp.Idle) && (!portAvailable || hazard)
  }

  // ID 分配器
  private var clientAllocIdx = 0
  private def allocClientId(): Int = {
    val id = clientAllocIdx
    clientAllocIdx += 1
    id
  }

  // ==============================================================================
  // 3. 注入式 API (前端接口)
  // ==============================================================================
  // 现在的 API 变得非常“傻瓜”和轻量，只负责连线
  
  def readAtomic(addr: UInt)(callback: UInt => Unit): Unit = {
    val myId = allocClientId()

    ContextScope.current match {
      case ThreadCtx(t) => {
        // 定义意图寄存器 (流水线 Stage 1)
        val myOp   = RegInit(RegOp.Idle)
        val myAddr = RegInit(0.U(32.W))

        // 自动 Abort 清理
        when (t.abortWire) { myOp := RegOp.Idle }

        // 驱动全局总线
        when (myOp =/= RegOp.Idle) {
          clientIntents(myId).op   := myOp
          clientIntents(myId).addr := myAddr
        }

        // Stage 1: Post
        t.Step(s"Reg_Read_Post_ID$myId") {
           myOp   := RegOp.Read
           myAddr := addr
        }

        // Stage 2: Resolve
        t.Step(s"Reg_Read_Resolve_ID$myId") {
           // 【关键变化】不再自己算冲突，直接看集中式仲裁的结果
           val stall = clientStalls(myId)
           
           t.waitAndAct(!stall) {
              val data = regs(myAddr(4,0)) // 消除 width warning
              callback(data)
              myOp := RegOp.Idle 
           }
        }
      }
      case _ =>
    }
  }

  def writeAtomic(addr: UInt, data: UInt)(callback: => Unit): Unit = {
    val myId = allocClientId()
    ContextScope.current match {
      case ThreadCtx(t) => {
        val myOp   = RegInit(RegOp.Idle)
        val myAddr = RegInit(0.U(32.W))

        when (t.abortWire) { myOp := RegOp.Idle }
        when (myOp =/= RegOp.Idle) {
          clientIntents(myId).op   := myOp
          clientIntents(myId).addr := myAddr
        }

        // Stage 1
        t.Step(s"Reg_Write_Post_ID$myId") {
           myOp   := RegOp.Write
           myAddr := addr
        }

        // Stage 2
        t.Step(s"Reg_Write_Resolve_ID$myId") {
           // 直接读取集中式结果
           val stall = clientStalls(myId)
           
           t.waitAndAct(!stall) {
              regs(myAddr(4,0)) := data
              callback
              myOp := RegOp.Idle
           }
        }
      }
      case _ =>
    }
  }
}