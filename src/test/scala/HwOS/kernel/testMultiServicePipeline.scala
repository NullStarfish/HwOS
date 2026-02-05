package HwOS.kernel

import chisel3._
import chisel3.util._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel._ 
import HwOS.kernel.drivers._

// ==============================================================================
// 1. DependencyScoreboard (Fixed)
// ==============================================================================
// Fixes:
// 1. Reuse ID (Token) for Dispatch & Retire to maintain sequence for nextIssueId.
// 2. Fix Chisel warning: nextIssueId width mismatch.

class DependencyScoreboard(kernel: Kernel, maxClients: Int = 32) extends PhysicalDriver(
  DriverMeta("DepSB", VectorResource(32), 4, 2, 0)
) {
  private val busyTable = RegInit(VecInit(Seq.fill(32)(false.B)))
  
  // 顺序发射指针：只有持有此 ID 的线程允许发射
  // [Fix] Width constrained to log2Ceil(maxClients)
  val nextIssueId = RegInit(0.U(log2Ceil(maxClients).W))

  class SbIntent extends Bundle {
    val acquire = Bool()
    val release = Bool()
    val reg     = UInt(5.W)
  }
  
  val intents = Wire(Vec(maxClients, new SbIntent))
  
  for (i <- 0 until maxClients) {
    intents(i).acquire := false.B
    intents(i).release := false.B
    intents(i).reg     := 0.U
  }

  // Update Logic (Busy Table)
  val nextBusy = WireInit(busyTable)
  for (i <- 0 until maxClients) {
    when (intents(i).release) { nextBusy(intents(i).reg) := false.B }
  }
  for (i <- 0 until maxClients) {
    when (intents(i).acquire) { nextBusy(intents(i).reg) := true.B }
  }
  busyTable := nextBusy
  
  // Update Logic (Issue Pointer)
  // When current ID acquires successfully, move to next ID
  when (intents(nextIssueId).acquire) {
    nextIssueId := nextIssueId + 1.U
  }

  private var clientIdAlloc = 0
  private def allocId(): Int = { 
    val id = clientIdAlloc
    if (id >= maxClients) throw new Exception(s"[DependencyScoreboard] ID Overflow: $id >= $maxClients")
    clientIdAlloc += 1
    id 
  }

  // [Fix] Return the allocated ID as a token
  def dispatch(dst: UInt, src1: UInt, src2: Option[UInt] = None): Int = {
    val myId = allocId()
    ContextScope.current match {
      case ThreadCtx(t) =>
        DriverStep(s"SB_Dispatch_ID$myId") {
           // 1. In-Order Dispatch Check
           val isMyTurn = nextIssueId === myId.U

           // 2. WAW Check
           val waw = busyTable(dst(4,0))
           
           // 3. RAW Check
           val concurrentRaw1 = (0 until myId).map { i =>
              intents(i).acquire && intents(i).reg === src1(4,0)
           }.foldLeft(false.B)(_ || _)
           val raw1 = busyTable(src1(4,0)) || concurrentRaw1

           val raw2 = src2.map { r => 
             val concurrentRaw2 = (0 until myId).map { i =>
                intents(i).acquire && intents(i).reg === r(4,0)
             }.foldLeft(false.B)(_ || _)
             busyTable(r(4,0)) || concurrentRaw2
           }.getOrElse(false.B)
           
           // 4. Resource Conflict
           val conf = (0 until myId).map { i => 
              intents(i).acquire && intents(i).reg === dst(4,0)
           }.foldLeft(false.B)(_ || _)

           val stall = !isMyTurn || waw || raw1 || raw2 || conf

           t.waitAndAct(!stall) {
              intents(myId).acquire := true.B
              intents(myId).reg     := dst(4,0)
           }
        }
      case _ =>
    }
    myId // Return token
  }

  // [Fix] Accept token to reuse the same ID slot
  def retire(dst: UInt, ticket: Int): Unit = {
    val myId = ticket
    ContextScope.current match {
      case ThreadCtx(t) =>
        DriverStep(s"SB_Retire_ID$myId") {
           intents(myId).release := true.B
           intents(myId).reg     := dst(4,0)
        }
      case _ =>
    }
  }
}

// ==============================================================================
// 2. MockRamDriver (Fixed)
// ==============================================================================
// Fixes:
// 1. Latch address during request. io.addr is not valid during latency cycles.

class MockRamDriver(kernel: Kernel) extends PhysicalDriver(
  DriverMeta("MainMemory", ScalarResource, 1, 1, 0)
) {
  private val mem = RegInit(VecInit(Seq.fill(128)(0.U(32.W))))
  
  val io = Wire(new Bundle {
    val req   = Bool()
    val isWr  = Bool()
    val addr  = UInt(32.W)
    val wdata = UInt(32.W)
    val valid = Bool()
    val rdata = UInt(32.W)
  })
  
  io.req := false.B; io.isWr := false.B; io.addr := 0.U; io.wdata := 0.U
  
  val sIdle :: sBusy :: sDone :: Nil = Enum(3)
  val state = RegInit(sIdle)
  val timer = Reg(UInt(4.W))
  val rdataBuffer = Reg(UInt(32.W))
  
  // [Fix] Latch the address!
  val addrLatch = Reg(UInt(32.W))
  
  io.valid := (state === sDone)
  io.rdata := rdataBuffer

  switch (state) {
    is (sIdle) {
      when (io.req) {
        state := sBusy
        timer := 2.U 
        addrLatch := io.addr // Capture address
        when (io.isWr) { mem(io.addr(6,0)) := io.wdata }
      }
    }
    is (sBusy) {
      timer := timer - 1.U
      when (timer === 0.U) {
        state := sDone
        // [Fix] Use latched address
        rdataBuffer := mem(addrLatch(6,0))
      }
    }
    is (sDone) {
      state := sIdle
    }
  }

  def access(isWrite: Boolean, addr: UInt, data: UInt)(callback: UInt => Unit): Unit = {
    ContextScope.current match {
      case ThreadCtx(t) =>
        DriverStep(if(isWrite) "RAM_Write_Req" else "RAM_Read_Req") {
           io.req   := true.B
           io.isWr  := isWrite.B
           io.addr  := addr
           io.wdata := data
        }
        DriverStep("RAM_Wait") {
           t.waitAndAct(io.valid) { callback(io.rdata) }
        }
      case _ =>
    }
  }
}

// ==============================================================================
// 3. Service Layer (AluService & LsuService)
// ==============================================================================

class AluService(rf: ScoreboardRegfileDriver, sb: DependencyScoreboard) {
  def emitAdd(rd: UInt, rs1: UInt, rs2: UInt): Unit = {
    ContextScope.current match {
      case ThreadCtx(t) =>
        val op1 = RegInit(0.U(32.W)); val op2 = RegInit(0.U(32.W)); val res = RegInit(0.U(32.W))
        val token = sb.dispatch(dst=rd, src1=rs1, src2=Some(rs2)) // Capture Token
        
        rf.readAtomic(rs1) { d => op1 := d }
        rf.readAtomic(rs2) { d => op2 := d }
        t.Step("ALU_Execute") { res := op1 + op2 }
        rf.writeAtomic(rd, res) { }
        
        sb.retire(rd, token) // Use Token
        t.Step("Inst_Retire") { t.exit() }
      case _ =>
    }
  }
  
  def emitAddi(rd: UInt, rs1: UInt, imm: UInt): Unit = {
    ContextScope.current match {
      case ThreadCtx(t) =>
        val op1 = RegInit(0.U(32.W)); val res = RegInit(0.U(32.W))
        val token = sb.dispatch(dst=rd, src1=rs1)
        
        rf.readAtomic(rs1) { d => op1 := d }
        t.Step("ALU_Execute_I") { res := op1 + imm }
        rf.writeAtomic(rd, res) { }
        
        sb.retire(rd, token)
        t.Step("Inst_Retire") { t.exit() }
      case _ =>
    }
  }
}

class LsuService(rf: ScoreboardRegfileDriver, ram: MockRamDriver, sb: DependencyScoreboard) {
  
  def emitStore(addrReg: UInt, dataReg: UInt): Unit = {
    ContextScope.current match {
      case ThreadCtx(t) =>
        val addrVal = RegInit(0.U(32.W)); val dataVal = RegInit(0.U(32.W))
        val token = sb.dispatch(dst=0.U, src1=addrReg, src2=Some(dataReg))
        
        rf.readAtomic(addrReg) { d => addrVal := d }
        rf.readAtomic(dataReg) { d => dataVal := d }
        ram.access(isWrite = true, addrVal, dataVal) { _ => }
        
        sb.retire(0.U, token)
        t.Step("Inst_Retire") { t.exit() }
      case _ =>
    }
  }

  def emitLoad(destReg: UInt, addrReg: UInt): Unit = {
    ContextScope.current match {
      case ThreadCtx(t) =>
        val addrVal = RegInit(0.U(32.W)); val memData = RegInit(0.U(32.W))
        
        val token = sb.dispatch(dst=destReg, src1=addrReg, src2=Some(0.U))
        
        rf.readAtomic(addrReg) { d => addrVal := d }
        
        t.Step("RAM_Req_Bridge") {
           ram.io.req  := true.B
           ram.io.isWr := false.B
           ram.io.addr := addrVal
        }
        t.Step("RAM_Wait_Bridge") {
           t.waitAndAct(ram.io.valid) { memData := ram.io.rdata }
        }
        rf.writeAtomic(destReg, memData) { }
        
        sb.retire(destReg, token)
        t.Step("Inst_Retire") { t.exit() }
      case _ =>
    }
  }
}

// ==============================================================================
// 4. PipelineModule
// ==============================================================================
class PipelineModule extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    val done  = Output(Bool())
    val r1 = Output(UInt(32.W)); val r2 = Output(UInt(32.W))
    val r3 = Output(UInt(32.W)); val r4 = Output(UInt(32.W))
  })

  val kernel = new Kernel()
  
  val phyRegs = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))
  val rfMeta = DriverMeta("RF", VectorResource(32), read_clients=4, write_clients=2, fifo_depth=0)
  val rfDriver = new ScoreboardRegfileDriver(phyRegs, kernel, rfMeta, maxClients=20) 
  kernel.mount(rfDriver)
  
  val ramDriver = new MockRamDriver(kernel)
  kernel.mount(ramDriver)
  
  val depSb = new DependencyScoreboard(kernel, maxClients=32)
  kernel.mount(depSb)

  val aluSvc = new AluService(rfDriver, depSb)
  val lsuSvc = new LsuService(rfDriver, ramDriver, depSb)

  class CpuProcess(k: Kernel) extends HwProcess("PipelineCpu", debugEnable = true, parent = None)(k) {
    val i0 = createThread("I0_Addi")
    val i1 = createThread("I1_Addi")
    val i2 = createThread("I2_Store")
    val i3 = createThread("I3_Load")
    val i4 = createThread("I4_Add")

    when(io.start) {
      i0.start(); i1.start(); i2.start(); i3.start(); i4.start()
    }

    override def entry(): Unit = {
      i0.entry { aluSvc.emitAddi(1.U, 0.U, 10.U) } 
      i1.entry { aluSvc.emitAddi(2.U, 1.U, 20.U) } 
      i2.entry { lsuSvc.emitStore(1.U, 2.U) }      
      i3.entry { lsuSvc.emitLoad(3.U, 1.U) }       
      i4.entry { aluSvc.emitAdd(4.U, 3.U, 1.U) }   
    }
  }

  val proc = new CpuProcess(kernel)
  proc.build()

  io.done := proc.i4.done
  io.r1 := phyRegs(1); io.r2 := phyRegs(2)
  io.r3 := phyRegs(3); io.r4 := phyRegs(4)
}

// ==============================================================================
// 5. Test
// ==============================================================================
class MultiServicePipelineTest extends AnyFlatSpec {
  "ServiceBasedPipeline" should "auto-sequence instructions using DependencyScoreboard" in {
    simulate(new PipelineModule) { c =>
      println("\n=== Multi-Service Injection Pipeline Test (Final Fix) ===")

      c.reset.poke(true.B); c.clock.step(2); c.reset.poke(false.B)
      c.io.start.poke(true.B); c.clock.step(); c.io.start.poke(false.B)

      var cycles = 0
      var done = false
      while (cycles < 150 && !done) {
        c.clock.step()
        cycles += 1
        if (c.io.done.peek().litToBoolean) done = true
      }
      
      val r1 = c.io.r1.peek().litValue
      val r2 = c.io.r2.peek().litValue
      val r3 = c.io.r3.peek().litValue
      val r4 = c.io.r4.peek().litValue
      
      println(s"[Result] R1 = $r1")
      println(s"[Result] R2 = $r2")
      println(s"[Result] R3 = $r3")
      println(s"[Result] R4 = $r4")
      
      assert(done, "Timeout")
      assert(r1 == 10, s"I0 Failed")
      assert(r2 == 30, s"I1 Failed")
      assert(r3 == 30, s"I3 Failed (Mem Hazard?)")
      assert(r4 == 40, s"I4 Failed")

      println("=== Test Passed ===\n")
    }
  }
}