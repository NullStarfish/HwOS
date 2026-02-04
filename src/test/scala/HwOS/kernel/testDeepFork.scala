package HwOS.kernel

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel._ 
import HwOS.kernel.drivers._

// ==============================================================================
// 1. 测试模块定义
// ==============================================================================
class DeepForkModule extends Module {
  val io = IO(new Bundle {
    val start      = Input(Bool())
    
    // 状态观测信号
    val rootDone   = Output(Bool())       // 根线程完成
    val childDone  = Output(Bool())       // 子线程完成（通过回调置位）
    val gcWriteDone= Output(Bool())       // 孙线程写完成
    val readVal    = Output(UInt(32.W))   // 子线程读到的数据
    
    // 调试 PC
    val childPC    = Output(UInt(32.W))
    val gcPC       = Output(UInt(32.W))
  })

  val kernel = new Kernel()
  val phyRegs = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))
  
  // 使用标准的 Scoreboard Driver
  // read_clients/write_clients 设大一点，防止因端口不足导致的 stall，专注于测试 Hazard stall
  val meta = DriverMeta("SB_Deep", VectorResource(32), read_clients=4, write_clients=4, fifo_depth=0)
  val sbDriver = new ScoreboardRegfileDriver(phyRegs, kernel, meta, maxClients=8)
  kernel.mount(sbDriver)

  class TestProcess(k: Kernel) extends HwProcess("DeepProc", debugEnable = true, parent = None)(k) {
    val root = createThread("Root")
    
    // 用于跨线程通信/观测的寄存器
    val rootDoneReg = RegInit(false.B)
    val childDoneReg = RegInit(false.B)
    val gcDoneReg    = RegInit(false.B)
    val capturedVal  = RegInit(0.U(32.W))

    when(io.start) { root.start() }

    override def entry(): Unit = {
      root.entry {
        // [Level 1] Root Fork Child
        root.fork("Child") {
           // ================= Child Thread Context =================
           // 注意：这里是在 Child 的 entry 内部执行
           val child = ContextScope.current match { case ThreadCtx(t) => t; case _ => null }
           
           // [Level 2] Child Fork GrandChild
           // Child 的第一个 Step 将是 Kick GrandChild
           child.fork("GrandChild") {
               // ================= GrandChild Thread Context =================
               val gc = ContextScope.current match { case ThreadCtx(t) => t; case _ => null }
               
               // [GC Step 0-1] Atomic Write (Competing with Child Read)
               // GrandChild 试图写入 0xBEEF
               sbDriver.writeAtomic(10.U, 0xBEEF.U) {
                  // [Callback] 写入完成回调
                  gcDoneReg := true.B
               }
               
               // [GC Step 2] Exit
               gc.Step("GC_Exit") { gc.exit() }
           } {
               // [Child Scope] GrandChild 完成后的回调（可选）
               // 这里留空，演示非阻塞特性
           }

           // [Child Step 1-2] Atomic Read (Competing with GC Write)
           // Child 试图读取 Reg 10
           // 预期：Child 会检测到 GC 正在写，因此 Stall，直到 GC 写完
           sbDriver.readAtomic(10.U) { data =>
               // [Callback] 读取完成回调
               capturedVal := data
           }
           
           // [Child Step 3] Exit
           child.Step("Child_Exit") { child.exit() }
           
        } {
           // [Root Scope] Child 完成后的回调
           // 当 Child 线程结束时触发（必须等 Child 里的 atomic read 完成）
           childDoneReg := true.B
        }
        
        // Root 等待 Child 完成
        root.Step("Root_Wait") {
           root.waitCondition(childDoneReg)
        }
        
        root.Step("Root_Exit") { 
           rootDoneReg := true.B
           root.exit() 
        }
      }
    }
    
    // 暴露内部句柄给 IO
    // 注意：需要在 build() 之后才能确信线程已生成，但这里是惰性引用
    def getChild = root 
  }

  val proc = new TestProcess(kernel)
  proc.build()

  io.rootDone    := proc.rootDoneReg
  io.childDone   := proc.childDoneReg
  io.gcWriteDone := proc.gcDoneReg
  io.readVal     := proc.capturedVal
  
  // 无法直接获取内部动态生成的线程句柄，因为它们是局部变量
  // 但我们可以通过观测 IO 信号来验证
  io.childPC := 0.U // 简化
  io.gcPC    := 0.U 
}

// ==============================================================================
// 2. 测试用例执行
// ==============================================================================
class DeepForkTest extends AnyFlatSpec {
  "ScoreboardRegfileDriver" should "handle contention in deep nested forks (Grandchild Write vs Child Read)" in {
    simulate(new DeepForkModule) { c =>
      println("\n=== Deep Fork Stability Test ===")

      // 1. Reset
      c.reset.poke(true.B)
      c.clock.step(2)
      c.reset.poke(false.B)
      c.clock.step()

      // 2. Start
      println("[Test] Starting Root...")
      c.io.start.poke(true.B)
      c.clock.step()
      c.io.start.poke(false.B)

      // --- Simulation Loop ---
      // 我们预期发生的事情：
      // Cycle 0: Root Start
      // Cycle 1: Root Step 0 (Kick Child)
      // Cycle 2: Child Active. Child Step 0 (Kick GrandChild).
      // Cycle 3: GC Active. Child Step 1 (Read Req). GC Step 0 (Write Req).
      //          -> Contention! 
      //          -> Child (Read) sees GC (Write) hazard -> Stall.
      //          -> GC proceeds.
      // Cycle 4: GC Write Done (0xBEEF). GC Callback (gcWriteDone=1).
      // Cycle 5: GC Exit. Child Retry -> Success.
      //          -> Child Read (0xBEEF). Child Callback (capturedVal=0xBEEF).
      // Cycle 6: Child Exit.
      // Cycle 7: Root Callback (childDone=1).
      // Cycle 8: Root Exit.
      
      var cycles = 0
      var gcDone = false
      var childDone = false
      var rootDone = false
      
      while (!rootDone && cycles < 20) {
        c.clock.step()
        cycles += 1
        
        val rVal = c.io.readVal.peek().litValue
        val isGcDone = c.io.gcWriteDone.peek().litToBoolean
        val isChildDone = c.io.childDone.peek().litToBoolean
        val isRootDone = c.io.rootDone.peek().litToBoolean
        
        if (!gcDone && isGcDone) {
          println(s"[Time $cycles] GrandChild Write Finished! (Priority Winner)")
          gcDone = true
        }
        
        if (!childDone && isChildDone) {
          println(s"[Time $cycles] Child Finished! Read Data: 0x${rVal.toString(16)}")
          childDone = true
          // 关键断言：Child 必须读到 GC 写的新值，证明顺序是 Write -> Read
          assert(rVal == 0xBEEF, s"Error: Child read 0x${rVal.toString(16)}, expected 0xBEEF (Stale Read!)")
        }
        
        if (isRootDone) {
          println(s"[Time $cycles] Root Finished. Test Complete.")
          rootDone = true
        }
      }
      
      assert(gcDone, "Error: GrandChild never finished")
      assert(childDone, "Error: Child never finished")
      assert(rootDone, "Error: Root never finished")
      
      println("=== Deep Fork Test Passed ===\n")
    }
  }
}
