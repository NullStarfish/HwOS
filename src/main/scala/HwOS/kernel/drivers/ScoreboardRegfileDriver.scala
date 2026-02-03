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
  
  // 默认值兜底
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

  def read(addr: UInt): UInt = {
    val myId = allocClientId()

    ContextScope.current match {
      case ThreadCtx(t) => {
        val readData = RegInit(0.U(32.W))
        
        t.Step(s"Reg_Read_Check_ID$myId") {
          clientIntents(myId).op   := RegOp.Read
          clientIntents(myId).addr := addr
          
          val higherPriorityReaders = (0 until myId).map { i =>
             clientIntents(i).op === RegOp.Read
          }.foldLeft(0.U)(_ +& _)
          
          val portAvailable = higherPriorityReaders < meta.read_clients.U

          val rawHazard = (0 until maxClients).map { i =>
            val isWriter = clientIntents(i).op === RegOp.Write
            val addrMatch = clientIntents(i).addr === addr
            (i.U =/= myId.U) && isWriter && addrMatch
          }.foldLeft(false.B)(_ || _)
          
          val stall = !portAvailable || rawHazard

          if (meta.name == "SB_RegFile") {
             printf(p"[Driver] ID=$myId Op=Read Addr=$addr | Higher=$higherPriorityReaders Available=$portAvailable Hazard=$rawHazard => Stall=$stall\n")
          }

          // [FIX] 语义反转：waitCondition 接受的是“通行证”，所以要取反
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

  def write(addr: UInt, data: UInt): Unit = {
    val myId = allocClientId()

    ContextScope.current match {
      case ThreadCtx(t) => {
        t.Step(s"Reg_Write_Check_ID$myId") {
          clientIntents(myId).op   := RegOp.Write
          clientIntents(myId).addr := addr
          
          val higherPriorityWriters = (0 until myId).map { i =>
            clientIntents(i).op === RegOp.Write
          }.foldLeft(0.U)(_ +& _)
          
          val portAvailable = higherPriorityWriters < meta.write_clients.U
          
          val wawHazard = (0 until maxClients).map { i =>
             (i.U =/= myId.U) && (clientIntents(i).op === RegOp.Write) && (clientIntents(i).addr === addr)
          }.foldLeft(false.B)(_ || _)
          
          val rwConflict = if (meta.conflict_policy == ConflictPolicies.RW_Lock) {
             (0 until maxClients).map { i =>
                (clientIntents(i).op === RegOp.Read) && (clientIntents(i).addr === addr)
             }.foldLeft(false.B)(_ || _)
          } else { false.B }

          val stall = !portAvailable || wawHazard || rwConflict
          
          if (meta.name == "SB_RegFile") {
             printf(p"[Driver] ID=$myId Op=Write Addr=$addr | Higher=$higherPriorityWriters Available=$portAvailable Hazard=$wawHazard => Stall=$stall\n")
          }

          // [FIX] 同上，waitCondition(!stall)
          t.waitAndAct(!stall) {
            regs(addr) := data
          }
          
        }
      }
      case _ =>
    }
  }
}