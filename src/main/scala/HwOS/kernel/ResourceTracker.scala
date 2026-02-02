package HwOS.kernel
import chisel3._


class ResourceTracker(val meta: DriverMeta, owner: HwProcess) 
  extends HardwareLogic(s"Tracker_${meta.name}", owner) {

  // ==================================================================
  // 策略工厂：根据 Meta 选择实现
  // ==================================================================
  private val strategy: ResourceStrategy = meta.model match {
    case ScalarResource => 
      new SemaphoreStrategy(meta.read_clients, meta.write_clients)
      
    case VectorResource(depth) => 
      // 对于 RegFile，使用 Ticket 算法
      new TicketStrategy(depth, meta.fifo_depth, meta.conflict_policy)
  }

  // ==================================================================
  // 对外 API (Kernel -> Thread)
  // ==================================================================

  // 1. 发送意图 (发射阶段)
  def send_intent(addr: UInt, op: UInt, id: UInt): Bool = {
    val ok = strategy.canReserve(addr, op, id)
    when(ok) { strategy.reserve(addr, op, id) }
    ok
  }

  // 2. 访问检查 (执行阶段)
  // 返回 true 表示获得了物理访问权
  def access_check(addr: UInt, op: UInt, id: UInt): Bool = {
    strategy.canAccess(addr, op, id)
  }


  // 3. 提交 (写回阶段)
  def commit_done(addr: UInt, op: UInt, id: UInt): Bool = {
    strategy.commit(addr, op, id)
  }
}