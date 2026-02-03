package HwOS.kernel.drivers

import chisel3._
import chisel3.util._
import HwOS.kernel._

class ForwardingScoreboardDriver(
    regs: Vec[UInt], 
    kernel: Kernel, 
    meta: DriverMeta,
    maxClients: Int = 4
  ) extends PhysicalDriver(meta) {
    class ScoreboardEntry(val addrWidth: Int, val dataWidth: Int) extends Bundle {
        val op   = RegOp()
        val addr = UInt(addrWidth.W)
        val data = UInt(dataWidth.W) // 新增：记录该 Slot 准备写入的数据
    }

  // 1. 全局记分牌 (Intent + Data)
  val clientIntents = Wire(Vec(maxClients, new ScoreboardEntry(32, 32)))
  val clientStalls  = Wire(Vec(maxClients, Bool()))
  val forwardedResults = Wire(Vec(maxClients, UInt(32.W))) // 广播给每个读操作的旁路数据

  // 默认值兜底
  for (i <- 0 until maxClients) {
    clientIntents(i).op   := RegOp.Idle
    clientIntents(i).addr := 0.U
    clientIntents(i).data := 0.U
  }

  // ==============================================================================
  // 2. 集中式仲裁与旁路逻辑 (Forwarding Logic)
  // ==============================================================================
  for (myId <- 0 until maxClients) {
    val myIntent = clientIntents(myId)
    val op   = myIntent.op
    val addr = myIntent.addr

    // A. 旁路搜索 (Forwarding Search)
    // 逻辑：寻找比我优先级高（ID更小/更老）且正在写同一个地址的最年轻（ID最大）的指令
    val bypassData = WireInit(0.U(32.W))
    val hitBypass  = WireInit(false.B)

    // 从 ID=0 扫描到 myId-1，后面的覆盖前面的（保证取到最新的旧指令数据）
    for (i <- 0 until myId) {
      when (clientIntents(i).op === RegOp.Write && clientIntents(i).addr === addr) {
        bypassData := clientIntents(i).data
        hitBypass  := true.B
      }
    }
    forwardedResults(myId) := Mux(hitBypass, bypassData, regs(addr(4,0)))

    // B. 冲突与 Stall 判定
    // 统计资源竞争
    val higherPriorityUsers = (0 until myId).map { i =>
      clientIntents(i).op === op
    }.foldLeft(0.U)(_ +& _)
    
    val limit = Mux(op === RegOp.Read, meta.read_clients.U, meta.write_clients.U)
    val portAvailable = higherPriorityUsers < limit

    // Hazard 判定：有了 Forwarding，RAW 不再需要 Stall！
    // 只有写写冲突 (WAW) 或资源不足时才 Stall
    val hazard = WireInit(false.B)
    when (op === RegOp.Write) {
      // WAW Hazard: 依然需要顺序写入
      hazard := (0 until maxClients).map { i =>
        (i.U =/= myId.U) && (clientIntents(i).op === RegOp.Write) && (clientIntents(i).addr === addr)
      }.foldLeft(false.B)(_ || _)
    }

    clientStalls(myId) := (op =/= RegOp.Idle) && (!portAvailable || hazard)
  }

  private var clientAllocIdx = 0
  private def allocClientId(): Int = { val id = clientAllocIdx; clientAllocIdx += 1; id }

  // ==============================================================================
  // 3. 注入式 API
  // ==============================================================================

  // 原子读：支持旁路
  def readAtomic(addr: UInt)(callback: UInt => Unit): Unit = {
    val myId = allocClientId()
    ContextScope.current match {
      case ThreadCtx(t) => {
        t.Step(s"Reg_Read_Fwd_ID$myId") {
          clientIntents(myId).op   := RegOp.Read
          clientIntents(myId).addr := addr
          
          val stall = clientStalls(myId)
          t.waitAndAct(!stall) {
            // 重要：这里不再直接读 regs，而是读取旁路网络计算出的结果
            callback(forwardedResults(myId))
          }
        }
      }
      case _ =>
    }
  }

  // 原子写：广播数据
  def writeAtomic(addr: UInt, data: UInt)(callback: => Unit = {}): Unit = {
    val myId = allocClientId()
    ContextScope.current match {
      case ThreadCtx(t) => {
        t.Step(s"Reg_Write_Fwd_ID$myId") {
          clientIntents(myId).op   := RegOp.Write
          clientIntents(myId).addr := addr
          clientIntents(myId).data := data // 将数据广播到总线上供旁路
          
          val stall = clientStalls(myId)
          t.waitAndAct(!stall) {
            regs(addr(4,0)) := data
            callback
          }
        }
      }
      case _ =>
    }
  }
}