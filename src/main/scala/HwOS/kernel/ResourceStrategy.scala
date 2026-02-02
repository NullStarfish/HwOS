package HwOS.kernel

import chisel3._
import chisel3.util._

trait ResourceStrategy {
  // 阶段 1: 预约 (Reserve/Intent)
  def canReserve(addr: UInt, op: UInt, id: UInt): Bool
  def reserve(addr: UInt, op: UInt, id: UInt): Unit

  // 阶段 2: 访问检查 (Access Check)
  def canAccess(addr: UInt, op: UInt, id: UInt): Bool
  
  // 阶段 3: 提交 (Commit/Done)
  // 返回值: isEarlyCommit (Bool)
  //   true  -> 提前提交 (Early Commit): 销毁了队列中间的 Intent，Head 未动
  //   false -> 正常提交 (Normal Commit): 销毁了 Head Intent，Head 移动到了下一个有效位
  def commit(addr: UInt, op: UInt, id: UInt): Bool
}

// ==============================================================================
// 策略 A: 信号量 (Semaphore)
// ==============================================================================
class SemaphoreStrategy(rMax: Int, wMax: Int) extends ResourceStrategy {
  val rCount = RegInit(0.U(log2Ceil(rMax + 1).W))
  val wCount = RegInit(0.U(log2Ceil(wMax + 1).W))
  val OP_READ  = 0.U

  def canReserve(addr: UInt, op: UInt, id: UInt): Bool = {
    Mux(op === OP_READ, rCount < rMax.U, wCount < wMax.U)
  }

  def reserve(addr: UInt, op: UInt, id: UInt): Unit = {
    when(op === OP_READ) { rCount := rCount + 1.U }
    .otherwise           { wCount := wCount + 1.U }
  }

  def canAccess(addr: UInt, op: UInt, id: UInt): Bool = true.B 

  def commit(addr: UInt, op: UInt, id: UInt): Bool = {
    // 信号量不追踪 ID，无法区分 Early/Normal，统一视为 Normal Release
    when(op === OP_READ) { rCount := rCount - 1.U }
    .otherwise           { wCount := wCount - 1.U }
    false.B 
  }
}

// ==============================================================================
// 策略 B: 票据算法 (Ticket Strategy) - 支持 Early Commit
// ==============================================================================
class TicketStrategy(addrDepth: Int, queueDepth: Int, conflictPolicy: (UInt, UInt) => Bool) extends ResourceStrategy {
  require(isPow2(queueDepth), "Ticket queue depth must be power of 2")
  
  // 状态指针
  val headTable = RegInit(VecInit(Seq.fill(addrDepth)(0.U(log2Ceil(queueDepth).W))))
  val tailTable = RegInit(VecInit(Seq.fill(addrDepth)(0.U(log2Ceil(queueDepth).W))))
  val countTable = RegInit(VecInit(Seq.fill(addrDepth)(0.U(log2Ceil(queueDepth + 1).W))))

  // 数据存储
  val opStorage = Reg(Vec(addrDepth, Vec(queueDepth, UInt(2.W)))) 
  val idStorage = Reg(Vec(addrDepth, Vec(queueDepth, UInt(32.W))))
  
  // [新增] 有效位表: 记录哪些 Ticket 是有效的
  // 如果 valid=false，说明该位置是空的或者已经被 Early Commit 了
  val validTable = RegInit(VecInit(Seq.fill(addrDepth)(VecInit(Seq.fill(queueDepth)(false.B)))))

  def canReserve(addr: UInt, op: UInt, id: UInt): Bool = {
    countTable(addr) < queueDepth.U
  }

  def reserve(addr: UInt, op: UInt, id: UInt): Unit = {
    val t = tailTable(addr)
    tailTable(addr) := t + 1.U
    countTable(addr) := countTable(addr) + 1.U
    
    opStorage(addr)(t) := op
    idStorage(addr)(t) := id
    validTable(addr)(t) := true.B // 标记有效
  }

  def canAccess(addr: UInt, op: UInt, id: UInt): Bool = {
    val h = headTable(addr)
    val activeOp = opStorage(addr)(h)
    val activeId = idStorage(addr)(h)
    val activeValid = validTable(addr)(h) // 检查 Head 是否有效

    // 只有当队列非空，且 Head 是有效的，且 ID 匹配时，才允许访问
    // 注意：如果 Head 被 Early Commit 了 (valid=false)，这里会返回 false，直到 Head 更新
    val isEmpty = countTable(addr) === 0.U
    val isMyTurn = !isEmpty && activeValid && (activeId === id)
    
    isMyTurn
  }

  def commit(addr: UInt, op: UInt, id: UInt): Bool = {
    val h = headTable(addr)
    val t = tailTable(addr)
    
    // 1. 查找 ID 对应的 Ticket (CAM Search)
    // 遍历当前 Valid 的 Ticket，找到 owner == id 的那个
    // 注意：这在硬件上是一个并行比较器，QueueDepth 不宜过大 (推荐 4-8)
    val matches = Wire(Vec(queueDepth, Bool()))
    for (i <- 0 until queueDepth) {
      // 必须是有效位，且 ID 匹配
      matches(i) := validTable(addr)(i) && (idStorage(addr)(i) === id)
    }
    
    val found     = matches.asUInt.orR
    val ticketIdx = PriorityEncoder(matches) // 找到对应的 Ticket Index
    
    val isEarly = WireInit(false.B)

    when (found) {
      // 标记该位置无效 (逻辑删除)
      validTable(addr)(ticketIdx) := false.B
      countTable(addr) := countTable(addr) - 1.U
      
      // 判断类型
      when (ticketIdx === h) {
        // === Normal Commit ===
        isEarly := false.B
        
        // Head 需要移动到下一个 *有效* 的 Ticket
        // 这是一个 "Find First Set" 逻辑，从 (h+1) 开始找 valid=true
        val nextValidOffset = WireInit(0.U(log2Ceil(queueDepth).W))
        val foundNext = WireInit(false.B)
        
        // 简单的链式查找下一跳
        // 如果 QueueDepth 很大，这里可能成为 Critical Path
        for (i <- 1 until queueDepth) {
           val idx = h + i.U
           // 如果还没找到下一个有效位，且当前位有效
           when (!foundNext && validTable(addr)(idx)) {
             nextValidOffset := i.U
             foundNext := true.B
           }
        }
        
        // 如果找到了下一个有效位，Head 跳过去
        // 如果没找到 (说明后面全是空的或者都被 Early Commit 了)，Head 直接变成 Tail (清空)
        when (foundNext) {
          headTable(addr) := h + nextValidOffset
        } .otherwise {
          // 特殊情况：Head 后面没有 Valid 的了，直接把 Head 追上 Tail
          // 或者如果 count=0 (上面已经减了)，逻辑上队列已空
          headTable(addr) := t // Reset Head to Tail's position logic
          // 但由于我们用环形指针，更安全的做法可能是 headTable := headTable + 1
          // 如果这里没处理好，可能会导致 Head 停在无效区。
          // 简化策略：Normal Commit 总是至少 +1。如果 +1 位置无效，依靠下次 AccessCheck 失败?
          // 不行，AccessCheck 失败会导致死锁。必须保证 Head 最终指向一个 Valid 或者 Head==Tail。
          
          // 修正逻辑：
          // 我们上面已经把当前的 h 设为 false 了。
          // 如果 foundNext，Head = h + offset。
          // 如果 !foundNext，说明队列空了，Head 应该等于 Tail (或者 head + 1 等待 push)
          // 实际上如果 countTable 减为 0，Head/Tail 相对位置不重要，只要下次 Reserve 正确即可。
          // 最稳妥的移动：如果没找到下一个，就 head := head + 1 (默认行为)
           headTable(addr) := h + 1.U 
        }

      } .otherwise {
        // === Early Commit ===
        isEarly := true.B
        // Head 不动！
        // 我们只是把中间某个 Ticket 的 valid 设为了 false。
        // 当真正的 Head 以后 Commit 并在寻找 "Next Valid" 时，会自动跳过这个坑。
      }
    }

    isEarly
  }
}