package HwOS.kernel

import chisel3._
import chisel3.util._

// ==============================================================================
// 抽象基类：纯资源调度策略
// 职责：管理并发槽位、检测读写冲突 (RAW/WAW/WAR)。
// 不负责：ID 身份验证、全局锁控制。
// ==============================================================================
abstract class ResourceStrategy(val meta: DriverMeta) {
  /**
   * 尝试分配资源
   * @return success: True 表示分配成功，False 表示资源忙或冲突
   */
  def alloc(addr: UInt, op: UInt): Bool

  /**
   * 释放资源
   * @return void
   */
  def free(addr: UInt, op: UInt): Unit
}

// ==============================================================================
// 策略 A: 信号量 (Semaphore) - 适用于标量资源 (如总线端口)
// ==============================================================================
class SemaphoreStrategy(meta: DriverMeta) extends ResourceStrategy(meta) {
  val rMax = meta.read_clients
  val wMax = meta.write_clients
  
  val rCount = RegInit(0.U(log2Ceil(rMax + 1).W))
  val wCount = RegInit(0.U(log2Ceil(wMax + 1).W))

  val rInc = WireInit(false.B)
  val rDec = WireInit(false.B)
  val wInc = WireInit(false.B)
  val wDec = WireInit(false.B)

  // 统一更新逻辑 (放在类构造体最后)
  rCount := rCount + rInc.asUInt - rDec.asUInt
  wCount := wCount + wInc.asUInt - wDec.asUInt

  override def alloc(addr: UInt, op: UInt): Bool = {
    val success = Wire(Bool())
    
    // 纯组合逻辑判断容量
    success := Mux(op === ConflictPolicies.OP_READ, rCount < rMax.U, wCount < wMax.U)
    
    // 状态更新 (时序逻辑)
    when(success) {
      when (op === ConflictPolicies.OP_READ) { rInc := true.B }
      .otherwise { wInc := true.B }
    }
    success
  }

  override def free(addr: UInt, op: UInt): Unit = {
    when(op === ConflictPolicies.OP_READ) {
      when(rCount > 0.U) { rDec := true.B }
    } .otherwise {
      when(wCount > 0.U) { wDec := true.B }
    }
  }
}

// ==============================================================================
// 策略 B: 计分板 (Scoreboard) - 适用于向量资源 (如寄存器堆)
// ==============================================================================
class ScoreboardStrategy(meta: DriverMeta) extends ResourceStrategy(meta) {
  // 内部复用 Semaphore 管理物理端口限制
  val portManager = new SemaphoreStrategy(meta)

  val depth = meta.model match {
    case VectorResource(d) => d
    case _ => 1
  }
  
  // 只需要记录由谁占用 (Occupied)，不需要记录具体 ID，ID 校验上移到 Tracker
  // busyTable: [Addr] -> Bool
  val busyTable = RegInit(VecInit(Seq.fill(depth)(false.B)))
  val opTable   = Reg(Vec(depth, UInt(2.W)))

  override def alloc(addr: UInt, op: UInt): Bool = {
    // 1. 先申请物理端口
    val portOk = portManager.alloc(addr, op)
    
    // 2. 检查地址冲突
    // 注意：这里我们做简化，假设该资源是互斥的，如果该地址忙，则冲突。
    // 如果需要支持读者-读者共享，这里需要扩展 busyTable 为计数器或位掩码
    val slotFree = !busyTable(addr)

    // 3. 冲突策略检测 (RAW/WAW 等)
    // 如果 Slot 不空，检查当前操作和正在进行的操作是否兼容
    val policyOk = Wire(Bool())
    when (!slotFree) {
        policyOk := !meta.conflict_policy(opTable(addr), op)
    } .otherwise {
        policyOk := true.B
    }

    val success = portOk && policyOk

    when (success) {
      busyTable(addr) := true.B
      opTable(addr)   := op
    }
    
    success
  }

  override def free(addr: UInt, op: UInt): Unit = {
    portManager.free(addr, op)
    busyTable(addr) := false.B
  }
}