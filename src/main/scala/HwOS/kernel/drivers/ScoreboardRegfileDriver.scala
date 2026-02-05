package HwOS.kernel.drivers

import chisel3._
import chisel3.util._
import HwOS.kernel._

object RegOp extends ChiselEnum {
  val Idle, Read, Write = Value
}

class ScoreboardEntry(val addrWidth: Int) extends Bundle {
  val op   = RegOp()
  val addr = UInt(addrWidth.W)
}

class ScoreboardRegfileDriver(
    regs: Vec[UInt], 
    kernel: Kernel, 
    meta: DriverMeta,
    maxClients: Int = 4 
  ) extends PhysicalDriver(meta) {

  val clientIntents = Wire(Vec(maxClients, new ScoreboardEntry(32)))
  
  for (i <- 0 until maxClients) {
    clientIntents(i).op   := RegOp.Idle
    clientIntents(i).addr := 0.U
  }

  private var clientAllocIdx = 0
  private def allocClientId(): Int = {
    if (clientAllocIdx >= maxClients) throw new Exception(s"RegfileDriver: Max clients ($maxClients) exceeded!")
    val id = clientAllocIdx
    clientAllocIdx += 1
    id
  }

  // 核心冲突检测 (复用)
  private def checkConflict(myId: Int, op: RegOp.Type, addr: UInt): Bool = {
    val higherPriorityUsers = (0 until myId).map { i =>
      clientIntents(i).op === op
    }.foldLeft(0.U)(_ +& _)
    
    val limit = if (op == RegOp.Read) meta.read_clients.U else meta.write_clients.U
    val portAvailable = higherPriorityUsers < limit

    val hazard = if (op == RegOp.Read) {
      (0 until maxClients).map { i =>
        val isWriter = clientIntents(i).op === RegOp.Write
        val addrMatch = clientIntents(i).addr === addr
        (i.U =/= myId.U) && isWriter && addrMatch
      }.foldLeft(false.B)(_ || _)
    } else {
      val waw = (0 until myId).map { i =>
         (i.U =/= myId.U) && (clientIntents(i).op === RegOp.Write) && (clientIntents(i).addr === addr)
      }.foldLeft(false.B)(_ || _)
      val rw = if (meta.conflict_policy == ConflictPolicies.RW_Lock) {
         (0 until maxClients).map { i =>
            (clientIntents(i).op === RegOp.Read) && (clientIntents(i).addr === addr)
         }.foldLeft(false.B)(_ || _)
      } else { false.B }
      waw || rw
    }
    
    val stall = !portAvailable || hazard
    if (meta.name == "SB_RegFile") {
       printf(p"[Driver] ID=$myId Op=$op Addr=$addr Stall=$stall\n")
    }
    stall
  }

  // ==============================================================================
  // Read APIs
  // ==============================================================================

  /**
   * [Thread Semantic] 阻塞式读取
   * 语法: val data = driver.read(addr)
   */
  def read(addr: UInt): UInt = {
    val myId = allocClientId()
    ContextScope.current match {
      case ThreadCtx(t) => {
        val readData = RegInit(0.U(32.W))
        DriverStep(s"Reg_Read_Block_ID$myId") {
          clientIntents(myId).op   := RegOp.Read
          clientIntents(myId).addr := addr
          val stall = checkConflict(myId, RegOp.Read, addr)
          
          t.waitCondition(!stall) 
          when (!stall) {
            readData := regs(addr)
          }
        }
        readData
      }
      case _ => 0.U
    }
  }

  /**
   * [Atomic Semantic] 原子回调式读取
   * 语法: driver.readAtomic(addr) { data => ... }
   */
  def readAtomic(addr: UInt)(callback: UInt => Unit): Unit = {
    val myId = allocClientId()
    ContextScope.current match {
      case ThreadCtx(t) => {
        DriverStep(s"Reg_Read_Atomic_ID$myId") {
          clientIntents(myId).op   := RegOp.Read
          clientIntents(myId).addr := addr
          val stall = checkConflict(myId, RegOp.Read, addr)
          
          // 拿到资源当拍立即执行 callback
          t.waitAndAct(!stall) {
            val data = regs(addr) // 直接读 Wire
            callback(data)
          }
        }
      }
      case _ =>
    }
  }

  // ==============================================================================
  // Write APIs
  // ==============================================================================

  /**
   * [Thread Semantic] 阻塞式写入
   * 语法: driver.write(addr, data)
   */
  def write(addr: UInt, data: UInt): Unit = {
    val myId = allocClientId()
    ContextScope.current match {
      case ThreadCtx(t) => {
        DriverStep(s"Reg_Write_Block_ID$myId") {
          clientIntents(myId).op   := RegOp.Write
          clientIntents(myId).addr := addr
          val stall = checkConflict(myId, RegOp.Write, addr)
          
          t.waitAndAct(!stall) {
            regs(addr) := data
          }
        }
      }
      case _ =>
    }
  }

  /**
   * [Atomic Semantic] 原子回调式写入
   * 语法: driver.writeAtomic(addr, data) { ... }
   */
  def writeAtomic(addr: UInt, data: UInt)(callback: => Unit): Unit = {
    val myId = allocClientId()
    ContextScope.current match {
      case ThreadCtx(t) => {
        DriverStep(s"Reg_Write_Atomic_ID$myId") {
          clientIntents(myId).op   := RegOp.Write
          clientIntents(myId).addr := addr
          val stall = checkConflict(myId, RegOp.Write, addr)
          
          t.waitAndAct(!stall) {
            regs(addr) := data
            callback
          }
        }
      }
      case _ =>
    }
  }
}