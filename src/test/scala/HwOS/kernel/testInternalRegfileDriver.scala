package HwOS.kernel

import chisel3._
import chisel3.util._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel.drivers._ 

// ==============================================================================
// 1. 瘦身版 Intent (去掉 wdata)
// ==============================================================================
class RegIntentLite(val addrWidth: Int) extends Bundle {
  val op   = RegOp()
  val addr = UInt(addrWidth.W)
  // 删除了 wdata，数据不再上总线
}

// ==============================================================================
// 2. 分布式写入 Driver
// ==============================================================================
class InternalRegfileDriverDirect(
    kernel: Kernel,
    name: String,
    depth: Int = 32,
    readPorts: Int = 4,   
    writePorts: Int = 1,  
    maxClients: Int = 8
  ) extends PhysicalDriver(
    DriverMeta(name, VectorResource(depth), readPorts, writePorts, 0)
  ) {

  // 资源依然由 Driver 持有
  private val regs = RegInit(VecInit(Seq.fill(depth)(0.U(32.W))))
  
  // 总线变窄了，只传控制信号
  val clientIntents = Wire(Vec(maxClients, new RegIntentLite(log2Ceil(depth))))

  for (i <- 0 until maxClients) {
    clientIntents(i).op   := RegOp.Idle
    clientIntents(i).addr := 0.U
  }

  // 仲裁逻辑 (完全复用，负责发 "通行证")
  def checkConflict(myId: Int, op: RegOp.Type, addr: UInt): Bool = {
    val higherPriorityUsers = (0 until myId).map { i =>
      clientIntents(i).op === op
    }.foldLeft(0.U)(_ +& _)
    
    val limit = Mux(op === RegOp.Read, meta.read_clients.U, meta.write_clients.U)
    val portAvailable = higherPriorityUsers < limit

    val hazard = if (op == RegOp.Read) {
      (0 until maxClients).map { i =>
        (clientIntents(i).op === RegOp.Write) && (clientIntents(i).addr === addr)
      }.foldLeft(false.B)(_ || _)
    } else {
      (0 until myId).map { i =>
         (clientIntents(i).op === RegOp.Write) && (clientIntents(i).addr === addr)
      }.foldLeft(false.B)(_ || _)
    }
    
    !portAvailable || hazard
  }

  // 【变化点 1】删除了这里的 "for (...) regs(...) := ..." 集中写入循环
  // 写入动作下放给 API 调用者

  private var clientAllocIdx = 0
  private def allocClientId(): Int = { val id = clientAllocIdx; clientAllocIdx += 1; id }

  // [API]
  def writeAtomic(addr: UInt, data: UInt)(callback: => Unit): Unit = {
    val myId = allocClientId()
    ContextScope.current match {
      case ThreadCtx(t) => {
        t.Step(s"Direct_Write_ID$myId") {
          // 1. 仅仅发布意图 (占座)
          clientIntents(myId).op   := RegOp.Write
          clientIntents(myId).addr := addr
          // 注意：wdata 不再赋值给总线

          val stall = checkConflict(myId, RegOp.Write, addr)
          
          t.waitAndAct(!stall) {
            // 【变化点 2】直接在这里写入！
            // Chisel 会收集所有调用这里的 when 块，自动生成多路选择器
            regs(addr) := data 
            callback
          }
        }
      }
      case ctx => 
         throw new Exception(s"Context Error: $ctx")
    }
  }
  
  // 读逻辑保持不变 (读本来就是分布式的)
  def readAtomic(addr: UInt)(callback: UInt => Unit): Unit = {
    val myId = allocClientId()
    ContextScope.current match {
      case ThreadCtx(t) => {
        t.Step(s"Direct_Read_ID$myId") {
          clientIntents(myId).op   := RegOp.Read
          clientIntents(myId).addr := addr
          
          val stall = checkConflict(myId, RegOp.Read, addr)
          t.waitAndAct(!stall) {
            callback(regs(addr))
          }
        }
      }
      case _ =>
    }
  }

  def debugPeek(idx: Int): UInt = regs(idx)
}

// ==============================================================================
// 3. 测试模块 (逻辑不变，换用新 Driver)
// ==============================================================================
class DirectWriteTestModule extends Module {
  val io = IO(new Bundle {
    val start   = Input(Bool())
    val done    = Output(Bool())
    val reg0Val = Output(UInt(32.W))
    val reg1Val = Output(UInt(32.W))
  })

  val kernel = new Kernel()
  
  // 使用新版 Driver, writePorts=2
  val rf = new InternalRegfileDriverDirect(kernel, "DirectRF", depth=32, writePorts=2)
  kernel.mount(rf)

  class DualWriteProcess(k: Kernel) extends HwProcess("DualProc", debugEnable = true, parent = None)(k) {
    val threadA = createThread("WriterA")
    val threadB = createThread("WriterB")
    val doneA = RegInit(false.B)
    val doneB = RegInit(false.B)

    when(io.start) { threadA.start(); threadB.start() }

    override def entry(): Unit = {
      threadA.entry {
         rf.writeAtomic(0.U, 0xAAAA.U) { doneA := true.B; threadA.exit() }
      }
      threadB.entry {
         rf.writeAtomic(1.U, 0xBBBB.U) { doneB := true.B; threadB.exit() }
      }
    }
    def areBothDone: Bool = doneA && doneB
  }

  val proc = new DualWriteProcess(kernel)
  proc.build()

  io.done    := proc.areBothDone
  io.reg0Val := rf.debugPeek(0)
  io.reg1Val := rf.debugPeek(1)
}

// ==============================================================================
// 4. Test Spec
// ==============================================================================
class InternalRegfileDirectTest extends AnyFlatSpec {
  "InternalRegfileDriverDirect" should "infer Muxes correctly for distributed writes" in {
    simulate(new DirectWriteTestModule) { c =>
      println("\n=== Internal Regfile Direct Write Test ===")
      
      c.reset.poke(true.B)
      c.clock.step(2)
      c.reset.poke(false.B)

      c.io.start.poke(true.B)
      c.clock.step()
      c.io.start.poke(false.B)

      // Cycle 1: Execute Writes
      c.clock.step()

      val r0 = c.io.reg0Val.peek().litValue
      val r1 = c.io.reg1Val.peek().litValue
      
      println(f"[Result] Reg(0) = 0x$r0%X")
      println(f"[Result] Reg(1) = 0x$r1%X")
      
      assert(r0 == 0xAAAA)
      assert(r1 == 0xBBBB)
      assert(c.io.done.peek().litToBoolean)
      
      println("=== Test Passed: Distributed Writes worked! ===\n")
    }
  }
}