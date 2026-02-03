package HwOS.kernel.drivers

import chisel3._
import chisel3.util._
import HwOS.kernel._

// 复用之前的定义
// object RegOp ...
// class ScoreboardEntry ...

class PipelinedScoreboardDriver(
    regs: Vec[UInt], 
    kernel: Kernel, 
    meta: DriverMeta,
    maxClients: Int = 8 // 即使支持更多客户端，时序也能收敛
  ) extends PhysicalDriver(meta) {

  // 全局记分牌 (Wire)
  // 但这次它将由各线程的 Reg 驱动，而非 Mux
  val clientIntents = Wire(Vec(maxClients, new ScoreboardEntry(32)))
  
  // 默认值兜底 (防止浮空)
  for (i <- 0 until maxClients) {
    clientIntents(i).op   := RegOp.Idle
    clientIntents(i).addr := 0.U
  }

  private var clientAllocIdx = 0
  private def allocClientId(): Int = {
    val id = clientAllocIdx
    clientAllocIdx += 1
    id
  }

  // 复用冲突检测逻辑 (纯组合逻辑)
  private def checkConflict(myId: Int, op: RegOp.Type, addr: UInt): Bool = {
    // ... (代码与之前相同，此处省略以节省篇幅) ...
    // 这里看到的 clientIntents 都是来自 Reg 的输出，时序极佳
    val higherPriorityUsers = (0 until myId).map { i =>
      clientIntents(i).op === op
    }.foldLeft(0.U)(_ +& _)
    
    val limit = if (op == RegOp.Read) meta.read_clients.U else meta.write_clients.U
    val portAvailable = higherPriorityUsers < limit

    // 简化的 Hazard 检测
    val hazard = if (op == RegOp.Read) {
      (0 until maxClients).map { i =>
        (i.U =/= myId.U) && (clientIntents(i).op === RegOp.Write) && (clientIntents(i).addr === addr)
      }.foldLeft(false.B)(_ || _)
    } else {
      // Write hazards...
      (0 until maxClients).map { i =>
         (i.U =/= myId.U) && (clientIntents(i).op =/= RegOp.Idle) && (clientIntents(i).addr === addr)
      }.foldLeft(false.B)(_ || _)
    }
    
    !portAvailable || hazard
  }

  // ==============================================================================
  // Pipelined Read API (2-Stage)
  // ==============================================================================
  
  def readAtomic(addr: UInt)(callback: UInt => Unit): Unit = {
    val myId = allocClientId()

    ContextScope.current match {
      case ThreadCtx(t) => {
        // [关键改进] 1. 定义意图寄存器 (Intent Register)
        // 它的生命周期跨越多个 Step，因此必须定义在 entry 顶层 (即这里的 ThreadCtx)
        val myOp   = RegInit(RegOp.Idle)
        val myAddr = RegInit(0.U(32.W))

        // [关键改进] 2. 自动 Abort 清理
        // 如果线程被 Kill，必须立即释放资源，否则会死锁其他人
        when (t.abortWire) {
          myOp := RegOp.Idle
        }

        // [关键改进] 3. 持续驱动全局记分牌
        // 只要我不是 Idle，我就一直把意图挂在总线上
        when (myOp =/= RegOp.Idle) {
          clientIntents(myId).op   := myOp
          clientIntents(myId).addr := myAddr
        }

        // --- Stage 1: 发布 (Post Request) ---
        t.Step(s"Reg_Read_Stage1_Post_ID$myId") {
           // 写入寄存器，切断组合逻辑
           myOp   := RegOp.Read
           myAddr := addr
           // 这一拍什么都不做，只是发布，下一拍大家才能看到
        }

        // --- Stage 2: 裁决 (Resolve) ---
        t.Step(s"Reg_Read_Stage2_Resolve_ID$myId") {
           // 此时 clientIntents 上的数据是 Stage 1 锁存过的，非常稳定
           val stall = checkConflict(myId, RegOp.Read, myAddr)
           
           // 如果 Stall，就停在这个 Step，保持 myOp 为 Read
           // 这样下一拍我会继续参与仲裁
           t.waitAndAct(!stall) {
                val data = regs(myAddr(4,0)) 
                callback(data)
                myOp := RegOp.Idle
           }
        }
      }
      case _ =>
    }
  }

  // Write API 类似，也是分两级...
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
        t.Step(s"Reg_Write_Stage1_Post_ID$myId") {
           myOp   := RegOp.Write
           myAddr := addr
        }

        // Stage 2
        t.Step(s"Reg_Write_Stage2_Resolve_ID$myId") {
           val stall = checkConflict(myId, RegOp.Write, myAddr)
           
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