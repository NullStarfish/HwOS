package HwOS.kernel.drivers

import chisel3._
import chisel3.util._
import HwOS.kernel._



class PipelinedScoreboardDriver(
    regs: Vec[UInt], 
    kernel: Kernel, 
    meta: DriverMeta,
    maxClients: Int = 8 
  ) extends PhysicalDriver(meta) {


  val clientIntents = Wire(Vec(maxClients, new ScoreboardEntry(32)))
  
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

  private def checkConflict(myId: Int, op: RegOp.Type, addr: UInt): Bool = {
    val higherPriorityUsers = (0 until myId).map { i =>
      clientIntents(i).op === op
    }.foldLeft(0.U)(_ +& _)
    
    val limit = if (op == RegOp.Read) meta.read_clients.U else meta.write_clients.U
    val portAvailable = higherPriorityUsers < limit

    val hazard = if (op == RegOp.Read) {
      (0 until maxClients).map { i =>
        (i.U =/= myId.U) && (clientIntents(i).op === RegOp.Write) && (clientIntents(i).addr === addr)
      }.foldLeft(false.B)(_ || _)
    } else {
      (0 until maxClients).map { i =>
         (i.U =/= myId.U) && (clientIntents(i).op =/= RegOp.Idle) && (clientIntents(i).addr === addr)
      }.foldLeft(false.B)(_ || _)
    }
    
    !portAvailable || hazard
  }

  def readAtomic(addr: UInt)(callback: UInt => Unit): Unit = {
    val myId = allocClientId()

    ContextScope.current match {
      case ThreadCtx(t) => {

        val myOp   = RegInit(RegOp.Idle)
        val myAddr = RegInit(0.U(32.W))


        when (t.abortWire) {
          myOp := RegOp.Idle
        }

        when (myOp =/= RegOp.Idle) {
          clientIntents(myId).op   := myOp
          clientIntents(myId).addr := myAddr
        }

        // --- Stage 1: 发布 (Post Request) ---
        t.Step(s"Reg_Read_Stage1_Post_ID$myId") {
           // 写入寄存器，切断组合逻辑
           myOp   := RegOp.Read
           myAddr := addr
        }

        // --- Stage 2: 裁决 (Resolve) ---
        t.Step(s"Reg_Read_Stage2_Resolve_ID$myId") {
           val stall = checkConflict(myId, RegOp.Read, myAddr)
           
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