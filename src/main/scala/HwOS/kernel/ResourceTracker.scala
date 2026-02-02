package HwOS.kernel
import chisel3._

class ResourceTracker(val meta: DriverMeta) {
  
  val strategy: ResourceStrategy = meta.conflict_policy match {
    case _ if meta.model == ScalarResource => new SemaphoreStrategy(meta)
    case _ => new ScoreboardStrategy(meta)
  }

  // 1. Lock Table: 由 Process 调用 sys_lock 控制
  // 只有持有 Lock 的 ID 才能进行 alloc，或者 Lock 处于空闲状态
  val depth = meta.model match {
      case ScalarResource => 1
      case VectorResource(d) => d
  }
  val lockTable = RegInit(VecInit(Seq.fill(depth)(false.B)))
  val lockOwner = Reg(Vec(depth, UInt(32.W))) // 记录谁锁的

  // 2. Transaction Record: 记录当前正在执行的操作是谁发起的
  // 用于 secure_done 时验证 ID
  // 注意：对于 Scoreboard，我们需要对每个地址记录 Owner
  // 对于 Semaphore，通常只要计数不为负即可，但为了严格审查，我们使用 FIFO 或 Table 记录
  // 这里简化演示：假设 Scoreboard 模式下每个地址对应一个 Owner
  val txnOwner = Reg(Vec(depth, UInt(32.W))) 


  // --- Intent: 用户程序(CPU Decode)调用 ---
  def sys_intent(addr: UInt, op: UInt, id: UInt): Bool = {
    // A. 检查 Lock 状态
    val isLocked = lockTable(addr)
    val owner    = lockOwner(addr)
    val lockPass = !isLocked || (isLocked && owner === id)

    // B. 如果 Lock 通过，尝试通过 Strategy 分配物理资源
    val success = WireDefault(false.B)
    when (lockPass) {
       success := strategy.alloc(addr, op)
    }

    // C. 记录 Owner (用于后续 commit 校验)
    when (success) {
       txnOwner(addr) := id
    }

    success
  }

  // --- Secure Done: Driver 硬件完成时调用 ---
  def secure_done(addr: UInt, op: UInt, id: UInt): Unit = {
    // A. 严格 ID 审查：只有发起者才能结束
    // 这一步非常关键！防止恶意 Driver 或逻辑错误释放了别人的资源
    val recordID = txnOwner(addr)
    val idMatch  = recordID === id

    when (idMatch) {
       strategy.free(addr, op)
    } .otherwise {
       // 严重错误：ID 不匹配
       printf(p"[Kernel Security] ID Mismatch in Done! Res:${meta.name} Addr:$addr Expected:$recordID Got:$id\n")
       // 可以选择这里 assert(false.B) 或者触发异常中断
    }
  }

  // --- Sys Lock: 用户程序手动加锁 ---
  def sys_lock(addr: UInt, id: UInt): Unit = {
     // 简单的 Test-and-Set 或 覆盖
     // 实际 OS 中这里可能需要 wait，但硬件层直接置位
     lockTable(addr) := true.B
     lockOwner(addr) := id
  }

  // --- Sys Unlock: 用户程序手动解锁 ---
  def sys_unlock(addr: UInt, id: UInt): Unit = {
     // 只有拥有者能解锁
     when (lockTable(addr) && lockOwner(addr) === id) {
        lockTable(addr) := false.B
     }
  }
}