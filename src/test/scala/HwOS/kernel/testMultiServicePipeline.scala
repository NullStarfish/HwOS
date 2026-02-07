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
// 1. Non-Blocking Scoreboard (High Throughput)
// ==============================================================================
class PipelinedScoreboard(kernel: Kernel, maxClients: Int = 32) extends PhysicalDriver(
  DriverMeta("PpSB", VectorResource(32), 4, 2, 0)
) {
  // 记录寄存器是否被占用（Busy Bit Table）
  private val busyTable = RegInit(VecInit(Seq.fill(32)(false.B)))
  
  // 顺序发射指针
  val nextIssueId = RegInit(0.U(log2Ceil(maxClients).W))

  class SbIntent extends Bundle {
    val acquire = Bool()
    val release = Bool()
    val check   = Bool() // 新增：仅检查
    val reg     = UInt(5.W)
  }
  
  val intents = Wire(Vec(maxClients, new SbIntent))
  
  for (i <- 0 until maxClients) {
    intents(i).acquire := false.B
    intents(i).release := false.B
    intents(i).check   := false.B
    intents(i).reg     := 0.U
  }

  // --- 状态更新逻辑 (Update Logic) ---
  // 必须处理同一拍既 Release 又 Acquire 的情况（Forwarding/Chain）
  // 这里简化为：Release 优先清零，Acquire 在下一拍置位（或同拍置位保持 busy）
  val nextBusy = WireInit(busyTable)
  
  // 1. Apply Releases
  for (i <- 0 until maxClients) {
    when (intents(i).release) { nextBusy(intents(i).reg) := false.B }
  }
  // 2. Apply Acquires (Overwrite release if same reg - maintaining busy)
  for (i <- 0 until maxClients) {
    when (intents(i).acquire) { nextBusy(intents(i).reg) := true.B }
  }
  busyTable := nextBusy

  // Update Issue Pointer
  when (intents(nextIssueId).acquire) {
    nextIssueId := nextIssueId + 1.U
  }

  // --- Client ID Allocator ---
  private var clientIdAlloc = 0
  private def allocId(): Int = { 
    val id = clientIdAlloc; clientIdAlloc += 1; id 
  }

  // --- API 1: Dispatch (Non-Blocking RAW) ---
  // 只检查 WAW 和 顺序发射。RAW 留给子线程处理。
  def dispatch(dst: UInt): Int = {
    val myId = allocId()
    ContextScope.current match {
      case ThreadCtx(t) =>
        DriverStep(s"SB_Dispatch_ID$myId", t) {
           val isMyTurn = nextIssueId === myId.U
           
           // WAW Check: 只有当我要写的目标寄存器已经被别人占用了，我才需要停顿
           // 这保证了对同一个寄存器的写入顺序
           val waw = busyTable(dst(4,0))
           
           // Resource Conflict (Structural)
           val conf = (0 until myId).map { i => 
              intents(i).acquire && intents(i).reg === dst(4,0)
           }.foldLeft(false.B)(_ || _)

           val stall = !isMyTurn || waw || conf

           t.waitAndAct(!stall) {
              intents(myId).acquire := true.B
              intents(myId).reg     := dst(4,0)
           }
        }
      case _ =>
    }
    myId
  }

  // --- API 2: Retire (Release) ---
  def retire(dst: UInt, ticket: Int): Unit = {
    val myId = ticket
    ContextScope.current match {
      case ThreadCtx(t) =>
        DriverStep(s"SB_Retire_ID$myId", t) {
           intents(myId).release := true.B
           intents(myId).reg     := dst(4,0)
        }
      case _ =>
    }
  }

  // --- API 3: Wait Hazard (New!) ---
  // 子线程调用此方法来等待操作数就绪
  def waitHazard(src: UInt): Unit = {
    // 这一步本身不占用 ID，或者复用临时 ID，这里简化为只读操作，不需要 ID 仲裁
    // 只要 busyTable(src) 为 true，就阻塞
    ContextScope.current match {
      case ThreadCtx(t) =>
        DriverStep(s"SB_Wait_${src.litValue}", t) { // 这里无法在生成时获取 src 的动态值，名字只是标识
           val regIdx = src(4,0)
           // 如果 busy 为 true，说明有在途指令正在写这个寄存器 -> Stall
           // 注意：这里我们读取的是当前拍的 busyTable。
           // 如果 writer 在这一拍 retire，busyTable 下一拍变 false，我们下一拍就能走。
           val isBusy = busyTable(regIdx)
           t.waitCondition(!isBusy)
        }
      case _ =>
    }
  }
}

// ==============================================================================
// 2. High-Throughput ALU Driver
// ==============================================================================
class FastAluDriver(kernel: Kernel, rf: ScoreboardRegfileDriver, sb: PipelinedScoreboard) extends PhysicalDriver(
  DriverMeta("FastALU", ScalarResource, 1, 1, 0)
) {
  private def currentThread: HardwareThread = {
    ContextScope.current match { case ThreadCtx(t) => t; case _ => null }
  }

  def emitAdd(rd: UInt, rs1: UInt, rs2: UInt): Unit = {
    ContextScope.current match {
      case ThreadCtx(parent) =>
        // 1. Dispatch (Decode Stage): 极快，只卡 WAW，不卡 RAW
        val token = sb.dispatch(rd) 

        // 2. Fork (Execute Stage)
        parent.fork("Ex_Add") {
           val child = currentThread
           val op1 = RegInit(0.U(32.W))
           val op2 = RegInit(0.U(32.W))
           val res = RegInit(0.U(32.W))

           // [关键优化] 在读取前，先等待数据依赖解决
           // 这会让子线程挂起，而不是阻塞父线程
           sb.waitHazard(rs1)
           sb.waitHazard(rs2)

           // 读取操作数 (此时已保证 Safety)
           rf.readAtomic(rs1) { d => op1 := d }
           rf.readAtomic(rs2) { d => op2 := d }

           DriverStep("ALU_Execute", child) {
             res := op1 + op2
           }

           rf.writeAtomic(rd, res) { }
           sb.retire(rd, token)
           DriverStep("Ex_Term", child) { child.exit() }
        } {
           parent.exit() // 任务完成，父线程销毁
        }
        
        // 3. Parent Continue: 父线程不等待子线程，直接退出(或者处理下一条)
        // 在 Service Pipeline 模型中，emit 只是发射。
        // 为了能在 HwOSgdb 中看到 parent 稍微停留一下(模拟 Decode 耗时)，可以加一拍
        DriverStep("Decode_Done", parent) {
           parent.waitCondition(false.B) // 这里仍然挂起等待回调，保证 io.done 正确
           // 如果是纯流水线，父线程应该是一个死循环不断 fetch，但在测试中 parent 代表"一条指令的生命周期"
           // 所以保持 waitCondition(false.B) 是对的：父线程代表指令本身，指令未退休前不能消失。
           // 但因为 Dispatch 不阻塞，后续指令的父线程可以并行启动（由 CpuProcess 决定）。
        }
      case _ =>
    }
  }
  
  // emitAddi 类似实现 ...
  def emitAddi(rd: UInt, rs1: UInt, imm: UInt): Unit = {
    ContextScope.current match {
      case ThreadCtx(parent) =>
        val token = sb.dispatch(rd)
        parent.fork("Ex_Addi") {
           val child = currentThread
           val op1 = RegInit(0.U(32.W)); val res = RegInit(0.U(32.W))
           
           sb.waitHazard(rs1) // Wait for dependency
           
           rf.readAtomic(rs1) { d => op1 := d }
           DriverStep("ALU_Exec_I", child) { res := op1 + imm }
           rf.writeAtomic(rd, res) { }
           sb.retire(rd, token)
           DriverStep("Ex_Term", child) { child.exit() }
        } { parent.exit() }
        DriverStep("Decode_Done", parent) { parent.waitCondition(false.B) }
      case _ =>
    }
  }
}


// ==============================================================================
// 3. High-Throughput LSU Driver (Fixed for Memory RAW)
// ==============================================================================
class FastLsuDriver(kernel: Kernel, rf: ScoreboardRegfileDriver, ram: MockRamDriver, sb: PipelinedScoreboard) extends PhysicalDriver(
  DriverMeta("FastLSU", ScalarResource, 1, 1, 0)
) {
  private def currentThread: HardwareThread = {
    ContextScope.current match { case ThreadCtx(t) => t; case _ => null }
  }

  def emitLoad(destReg: UInt, addrReg: UInt): Unit = {
    ContextScope.current match {
      case ThreadCtx(parent) =>
        val token = sb.dispatch(destReg)
        
        parent.fork("Ex_Load") {
           val child = currentThread
           val addrVal = RegInit(0.U(32.W)); val memData = RegInit(0.U(32.W))
           
           // [Fix] Memory RAW Hazard:
           // 必须等待所有在途的 Store (它们持有 Reg 0 的锁) 完成后，才能进行 Load。
           sb.waitHazard(0.U) 
           
           // Wait for AGU dependency
           sb.waitHazard(addrReg) 
           
           rf.readAtomic(addrReg) { d => addrVal := d }
           DriverStep("RAM_Bridge", child) {
              ram.io.req := true.B; ram.io.isWr := false.B; ram.io.addr := addrVal
           }
           DriverStep("RAM_Wait", child) {
              child.waitAndAct(ram.io.valid) { memData := ram.io.rdata }
           }
           rf.writeAtomic(destReg, memData) { }
           sb.retire(destReg, token)
           DriverStep("Ex_Term", child) { child.exit() }
        } { parent.exit() }
        DriverStep("Decode_Done", parent) { parent.waitCondition(false.B) }
      case _ =>
    }
  }
  
  def emitStore(addrReg: UInt, dataReg: UInt): Unit = {
    ContextScope.current match {
      case ThreadCtx(parent) =>
        // Dispatch to 0.U acts as a "Memory Write Lock"
        // 这会序列化所有的 Store 操作，并阻塞后续的 Load (因为 Load 会 waitHazard(0))
        val token = sb.dispatch(0.U) 
        
        parent.fork("Ex_Store") {
           val child = currentThread
           val addrVal = RegInit(0.U(32.W)); val dataVal = RegInit(0.U(32.W))
           
           sb.waitHazard(addrReg)
           sb.waitHazard(dataReg)
           
           rf.readAtomic(addrReg) { d => addrVal := d }
           rf.readAtomic(dataReg) { d => dataVal := d }
           
           // MockRamDriver handles the actual write cycle
           ram.access(isWrite = true, addrVal, dataVal) { _ => }
           
           sb.retire(0.U, token) // Release Memory Lock
           DriverStep("Ex_Term", child) { child.exit() }
        } { parent.exit() }
        DriverStep("Decode_Done", parent) { parent.waitCondition(false.B) }
      case _ =>
    }
  }
}

// ==============================================================================
// 3. Updated Pipeline Module
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
  val rfDriver = new ScoreboardRegfileDriver(phyRegs, kernel, rfMeta, maxClients=16) 
  kernel.mount(rfDriver)
  
  val ramDriver = new MockRamDriver(kernel)
  kernel.mount(ramDriver)
  

  val ppSb = new PipelinedScoreboard(kernel)
  kernel.mount(ppSb)

  // 实例化新的 Driver
  val aluDriver = new FastAluDriver(kernel, rfDriver, ppSb)
  kernel.mount(aluDriver)

  val lsuDriver = new FastLsuDriver(kernel, rfDriver, ramDriver, ppSb)
  kernel.mount(lsuDriver)

  class CpuProcess(k: Kernel) extends HwProcess("PipelineCpu", debugEnable = false, parent = None)(k) {
    val i0 = createThread("I0_Addi")
    val i1 = createThread("I1_Addi")
    val i2 = createThread("I2_Store")
    val i3 = createThread("I3_Load")
    val i4 = createThread("I4_Add")

    when(io.start) {
      i0.start(); i1.start(); i2.start(); i3.start(); i4.start()
    }

    override def entry(): Unit = {
      // 现在的调用看起来和之前一样，但内部机制已经是 Fork/Async 了
      i0.entry { aluDriver.emitAddi(1.U, 0.U, 10.U) } 
      i1.entry { aluDriver.emitAddi(2.U, 1.U, 20.U) } 
      i2.entry { lsuDriver.emitStore(1.U, 2.U) }      
      i3.entry { lsuDriver.emitLoad(3.U, 1.U) }       
      i4.entry { aluDriver.emitAdd(4.U, 3.U, 1.U) }   
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