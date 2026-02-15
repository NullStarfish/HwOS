package HwOS.kernel

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel._ 
import HwOS.kernel.drivers._

// 1. 补全 IO 端口，增加 done 信号以便观测完成状态
class ScoreboardTestModule extends Module {
  val io = IO(new Bundle {
    val start     = Input(Bool())
    val killSlot0 = Input(Bool())
    val pc0       = Output(UInt(32.W))
    val pc1       = Output(UInt(32.W))
    val data0     = Output(UInt(32.W))
    val data1     = Output(UInt(32.W))
    val done0     = Output(Bool()) // 新增
    val done1     = Output(Bool()) // 新增
  })


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
       // printf(p"[Driver] ID=$myId Op=$op Addr=$addr Stall=$stall\n")
    }
    stall
  }

 
  // 普通 Read 保持不变，用于阻塞式读取
  def read(addr: UInt): UInt = {
    val myId = allocClientId()
    ContextScope.current match {
      case ThreadCtx(t) => {
        val readData = RegInit(0.U(32.W))
        t.Step(s"Reg_Read_Block_ID$myId") {
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

  // [修改] readAtomic: 使用 hijack 实现零周期开销
  // 现在的行为：如果 stall，保持在当前 Step；如果 !stall，直接 hijack 下一个 Step（通常是用户逻辑）
  def readAtomic(addr: UInt): UInt = {
    val myId = allocClientId()
    // 返回 Wire，因为如果成功，数据在当拍有效，直接供下一级逻辑使用
    val rdata = Wire(UInt(32.W))
    rdata := 0.U // default

    ContextScope.current match {
      case ThreadCtx(t) => {
        // 创建仲裁 Step
        t.Step(s"Reg_Read_Atomic_ID$myId") {
          clientIntents(myId).op   := RegOp.Read
          clientIntents(myId).addr := addr
          val stall = checkConflict(myId, RegOp.Read, addr)
          
          t.waitCondition(!stall)
          
          when(!stall) {
            rdata := regs(addr)
            // 关键改变：抢占下一个 Step 的控制权，使其在当前周期立即执行
            t.Next.hijack() 
          }
        }
      }
      case _ =>
    }
    rdata
  }


  def write(addr: UInt, data: UInt): Unit = {
    val myId = allocClientId()
    ContextScope.current match {
      case ThreadCtx(t) => {
        t.Step(s"Reg_Write_Block_ID$myId") {
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

  // [修改] writeAtomic: 使用 hijack
  def writeAtomic(addr: UInt, data: UInt): Unit = {
    val myId = allocClientId()
    ContextScope.current match {
      case ThreadCtx(t) => {
        t.Step(s"Reg_Write_Atomic_ID$myId") {
          clientIntents(myId).op   := RegOp.Write
          clientIntents(myId).addr := addr
          val stall = checkConflict(myId, RegOp.Write, addr)
          
          t.waitCondition(!stall)
          
          when(!stall) {
            regs(addr) := data
            t.Next.hijack() // 写入完成，立即执行下一条指令
          }
        }
      }
      case _ =>
    }
  }
}


  val kernel = new Kernel()
  val phyRegs = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))
  phyRegs(10) := 0xAAAA.U
  phyRegs(20) := 0xBBBB.U

  val meta = DriverMeta("SB_RegFile", VectorResource(32), read_clients=1, write_clients=1, fifo_depth=0)
  val sbDriver = new ScoreboardRegfileDriver(phyRegs, kernel, meta, maxClients=2)
  kernel.mount(sbDriver)

  class TestProcess(k: Kernel) extends HwProcess("FetchUnit", debugEnable = true, parent = None)(k) {
    val slot0 = createThread("Slot0")
    val slot1 = createThread("Slot1")
    val readVal0 = RegInit(0.U(32.W))
    val readVal1 = RegInit(0.U(32.W))

    when(io.start) { slot0.start(); slot1.start() }
    when(io.killSlot0) { slot0.abort() }

    override def entry(): Unit = {
      slot0.entry {
        // Hijack 生效：如果仲裁成功，readAtomic 会吞噬 WB Step，在当拍完成
        val r = sbDriver.readAtomic(10.U)
        slot0.Step("WB") { 
          readVal0 := r
          slot0.exit() 
        }
      }
      slot1.entry {
        val r = sbDriver.readAtomic(20.U)
        slot1.Step("WB") { 
          readVal1 := r
          slot1.exit() 
        }
      }
    }
  }
  val proc = new TestProcess(kernel)
  proc.build()

  io.pc0   := proc.slot0.pc
  io.pc1   := proc.slot1.pc
  io.data0 := proc.readVal0
  io.data1 := proc.readVal1
  io.done0 := proc.slot0.done
  io.done1 := proc.slot1.done
}

class ScoreboardDriverTest extends AnyFlatSpec {
  "ScoreboardDriver" should "handle priority stalling and instant hijack completion" in {
    simulate(new ScoreboardTestModule) { c =>
      println("\n=== Scoreboard Driver Test (Hijack Adjusted) ===")
      
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      // ==========================================
      // Case 1: Priority Test
      // ==========================================
      println("[Test] Case 1: Start both. Slot 0 should finish INSTANTLY (Cycle 1).")
      c.io.start.poke(true.B)
      
      // 我们在 Step 之前 Peek，查看组合逻辑的响应（Mealy 类型或当拍 Hijack）
      // 注意：EphemeralSimulator 的 step() 包含 翻转时钟 + 结算组合逻辑
      c.clock.step() 
      c.io.start.poke(false.B)

      // === Cycle 1 Analysis ===
      // Slot 0: 优先级高 -> 获得锁 -> readAtomic 成功 -> hijack WB -> 执行 exit()
      // 结果: Done 信号在当拍拉高，PC 重置为 0
      println(s"[Cycle 1] Done0=${c.io.done0.peek().litValue}, PC0=${c.io.pc0.peek().litValue}")
      
      c.io.done0.expect(true.B)  // Slot 0 应该在第一拍就宣告完成！
      c.io.pc0.expect(0.U)       // 并且 PC 已经复位
      
      // Slot 1: 优先级低 -> 冲突 -> 阻塞在 Read 状态
      c.io.done1.expect(false.B)
      c.io.pc1.expect(0.U)       // 阻塞在第 0 步 (Read)

      c.clock.step()

      // === Cycle 2 Analysis ===
      // Slot 0: 已经结束，Reg中的数据应该已经稳定
      // Slot 1: 看到锁释放 -> 获得锁 -> hijack WB -> 完成
      
      c.io.data0.expect(0xAAAA.U) // 验证 Slot 0 数据已写入
      println(s"[Cycle 2] Data0 Valid: ${c.io.data0.peek().litValue.toString(16)}")

      // Slot 1 应该在这一拍完成
      println(s"[Cycle 2] Done1=${c.io.done1.peek().litValue}")
      c.io.done1.expect(true.B)
      
      c.clock.step()
      c.io.data1.expect(0xBBBB.U) // 验证 Slot 1 数据已写入
      
      println("[Test] Case 1 Passed.\n")

      // ==========================================
      // Case 2: Abort Test
      // ==========================================
      println("[Test] Case 2: Abort Slot 0.")
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      c.io.start.poke(true.B)
      c.io.killSlot0.poke(true.B) // 同时启动并杀死 Slot 0
      c.clock.step()
      c.io.start.poke(false.B)
      c.io.killSlot0.poke(false.B)

      // Slot 0 被 Abort，不应该产生 Done 信号，也不占用资源
      // Slot 1 应该捡漏成功，直接完成
      println(s"[Result] Done0=${c.io.done0.peek().litValue}, Done1=${c.io.done1.peek().litValue}")
      
      c.io.done0.expect(false.B) 
      c.io.done1.expect(true.B)  // Slot 1 成功
      
      c.clock.step()
      c.io.data1.expect(0xBBBB.U)

      println("=== Scoreboard Test Passed ===\n")
    }
  }
}