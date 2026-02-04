package HwOS.kernel

import chisel3._
import chisel3.util._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel._ 
import HwOS.kernel.drivers._

// ==============================================================================
// 1. 复杂指令 CPU 模块
// ==============================================================================
class ComplexInstCpuModule extends Module {
  val io = IO(new Bundle {
    val start        = Input(Bool())
    
    // 调试信号
    val slot0Active  = Output(Bool())
    val slot1Active  = Output(Bool())
    val finishedCount= Output(UInt(32.W))
    val resultReg    = Output(UInt(32.W)) // 最终计算结果 (R7)
  })

  val kernel = new Kernel()
  
  // --- 物理资源定义 ---
  val phyRegs = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))
  
  // [关键点] 记分牌驱动器
  // maxClients 设为 16，因为每个 Slot 可能会产生 3 个请求端点 (2个读子线程 + 1个写主线程)
  // 如果我们有 2 个 Slot，至少需要 6 个 Client ID。留足余量。
  val meta = DriverMeta("SB_RegFile", VectorResource(32), read_clients=4, write_clients=4, fifo_depth=0)
  val sbDriver = new ScoreboardRegfileDriver(phyRegs, kernel, meta, maxClients=16)
  kernel.mount(sbDriver)


  class ComplexCpuProcess(k: Kernel) extends HwProcess("ComplexCpu", debugEnable = true, parent = None)(k) {
    
    val finishedCounter = RegInit(0.U(32.W))
    val slot0 = createThread("Slot0_Producer")
    val slot1 = createThread("Slot1_Consumer")

    // 两个 Slot 组成的“指令池”
    val slots = Seq(slot0, slot1)

    // Fetch 单元：负责发射指令
    val fetch = createThread("FetchUnit")

    when(io.start) { fetch.start() }

    override def entry(): Unit = {
      
      // ==========================================================================
      // Instruction 0: The "Long Latency Producer"
      // 行为：Write R5 = 10, Write R6 = 20
      // ==========================================================================
      slot0.entry {
        // 模拟一个多周期指令
        slot0.Step("Decode_Exec") {
           // 可以在这里消耗几个周期...
        }

        // 原子写入 R5 (这会锁住 R5 直到写入完成)
        sbDriver.writeAtomic(5.U, 10.U) {
            // Callback: R5 写入完成
        }
        
        // 原子写入 R6
        sbDriver.writeAtomic(6.U, 20.U) {
             // Callback: R6 写入完成
        }

        slot0.Step("Retire") {
           finishedCounter := finishedCounter + 1.U
           slot0.exit()
        }
      }

      // ==========================================================================
      // Instruction 1: The "Parallel Consumer" (SIMD-like structure)
      // 行为：R7 = R5 + R6
      // 结构：主线程 Fork 出两个子线程分别去读 R5 和 R6，拿到数据后主线程汇聚计算
      // ==========================================================================
      slot1.entry {
         // 本地状态 (闭包变量)
         val opA_Ready = RegInit(false.B)
         val opB_Ready = RegInit(false.B)
         val valA      = RegInit(0.U(32.W))
         val valB      = RegInit(0.U(32.W))

         slot1.Step("Init_MicroOps") {
             opA_Ready := false.B
             opB_Ready := false.B
         }

         // --- Micro-Op 1: Fetch Operand A (R5) ---
         // 这里的 fork 是非阻塞的，主线程会继续往下走
         slot1.fork("uOp_FetchA") {
             // 子线程逻辑：尝试读取 R5
             // 如果 Slot 0 还没写完 R5，这里会自动 Stall (Scoreboard 机制)
             sbDriver.readAtomic(5.U) { data =>
                 valA := data
             }
             // 子线程退出
             val t = ContextScope.current match { case ThreadCtx(t) => t; case _ => null }
             t.Step("Exit") { t.exit() }
         } {
             // [Callback] 在主线程上下文中执行
             opA_Ready := true.B
         }

         // --- Micro-Op 2: Fetch Operand B (R6) ---
         slot1.fork("uOp_FetchB") {
             sbDriver.readAtomic(6.U) { data =>
                 valB := data
             }
             val t = ContextScope.current match { case ThreadCtx(t) => t; case _ => null }
             t.Step("Exit") { t.exit() }
         } {
             opB_Ready := true.B
         }

         // --- Main Op: Execute & Writeback ---
         // [修复] Step 1: 等待操作数
         slot1.Step("Wait_Operands") {
             // 屏障同步：等待两个微线程都通过回调设置了 Ready 标志
             slot1.waitCondition(opA_Ready && opB_Ready)
         }

         // [修复] Step 2: 写回 (Atomic Driver Call 必须在 ThreadCtx 中)
         // 计算逻辑是组合逻辑，挂在 ThreadCtx 下没问题，只有当 writeAtomic 的 Step 激活时才会被采样写入
         val sum = valA + valB
         
         // writeAtomic 会自动生成一个新的 Step 追加在 Wait_Operands 之后
         sbDriver.writeAtomic(7.U, sum) {
             // 只有写完了才退出
             finishedCounter := finishedCounter + 1.U
             slot1.exit()
         }
      }


      // ==========================================================================
      // Fetch Logic: Simple Round-Robin Issuer
      // ==========================================================================
      fetch.entry {
         fetch.Step("Issue_Loop") {
             // 简单的发射逻辑：只要 Slot 空闲就发射
             
             // 发射 Slot 0 (Producer)
             when (!slot0.isRunning) {
                 slot0.start()
                 printf("[Fetch] Issued Slot 0 (Producer)\n")
             }

             // 发射 Slot 1 (Consumer)
             when (!slot1.isRunning) {
                 slot1.start()
                 printf("[Fetch] Issued Slot 1 (Consumer)\n")
             }
             
             // 停止 Fetch
             when (slot0.done && slot1.done) {
                 fetch.exit()
             }
         }
      }
    }
  }

  val proc = new ComplexCpuProcess(kernel)
  proc.build()

  io.slot0Active := proc.slot0.isRunning
  io.slot1Active := proc.slot1.isRunning
  io.finishedCount := proc.finishedCounter
  io.resultReg   := phyRegs(7) // 监控 R7
}

// ==============================================================================
// 2. 测试用例
// ==============================================================================
class ComplexInstFlowTest extends AnyFlatSpec {
  "Complex Instructions" should "handle internal parallelism (uOps) and external contention (RAW)" in {
    simulate(new ComplexInstCpuModule) { c =>
      println("\n=== Complex Instruction Flow Test ===")

      // 1. Init
      c.reset.poke(true.B)
      c.clock.step(2)
      c.reset.poke(false.B)
      
      // 2. Start CPU
      println("[Test] Starting Fetch Unit...")
      c.io.start.poke(true.B)
      c.clock.step()
      c.io.start.poke(false.B)
      
      // 3. Monitor Execution Cycle by Cycle
      var cycles = 0
      var resultReady = false
      
      while (cycles < 20 && !resultReady) {
        c.clock.step()
        cycles += 1
        
        val s0 = c.io.slot0Active.peek().litToBoolean
        val s1 = c.io.slot1Active.peek().litToBoolean
        val res = c.io.resultReg.peek().litValue
        val cnt = c.io.finishedCount.peek().litValue

        // println(f"[Cycle $cycles%2d] Slot0=$s0 Slot1=$s1 Retired=$cnt Result=0x$res%x")

        if (cnt == 2) {
             resultReady = true
             println(f"[Cycle $cycles] All instructions retired!")
        }
      }
      
      // 4. Verification
      // Slot 0 wrote: R5=10, R6=20
      // Slot 1 read R5, R6 and wrote R7 = 10 + 20 = 30
      val finalRes = c.io.resultReg.peek().litValue
      println(s"[Result] Final R7 Value: $finalRes (Expected 30)")
      
      assert(finalRes == 30, "Computation failed! Dependency logic might be broken.")
      assert(resultReady, "Timed out! Instructions did not finish.")

      println("=== Test Passed: Superscalar Behavior Verified ===\n")
    }
  }
}
