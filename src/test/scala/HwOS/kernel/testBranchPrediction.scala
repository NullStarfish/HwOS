package HwOS.kernel

import chisel3._
import chisel3.util._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel._ 
import HwOS.kernel.drivers._

// ==============================================================================
// 1. 分支预测 CPU 模块
// ==============================================================================
class BranchCpuModule extends Module {
  val io = IO(new Bundle {
    val start        = Input(Bool())
    
    // 状态观测
    val regGood      = Output(UInt(32.W)) // 正确路径写入的寄存器
    val regBad       = Output(UInt(32.W)) // 错误路径试图写入的寄存器
    
    val branchResolved = Output(Bool())
    val correctPathDone = Output(Bool())
    val wrongPathKilled = Output(Bool())
  })

  val kernel = new Kernel()
  
  // 32个物理寄存器
  val phyRegs = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))
  
  // Scoreboard Driver
  val meta = DriverMeta("SB_RegFile", VectorResource(32), read_clients=4, write_clients=4, fifo_depth=0)
  val sbDriver = new ScoreboardRegfileDriver(phyRegs, kernel, meta, maxClients=8)
  kernel.mount(sbDriver)

  class BranchProcess(k: Kernel) extends HwProcess("BranchCpu", debugEnable = true, parent = None)(k) {
    
    val branchUnit = createThread("BranchUnit")
    
    // 状态标志
    val resolvedFlag = RegInit(false.B)
    val killedFlag   = RegInit(false.B)
    val correctDone  = RegInit(false.B)

    when(io.start) { branchUnit.start() }

    override def entry(): Unit = {
      
      branchUnit.entry {
        // [Setup] 定义两个推测路径 (Speculative Paths)
        // 注意：我们使用 prepareThread 而不是 fork，这样我们可以拿到线程句柄 (Handle)
        // 从而在后续逻辑中对特定线程执行 abort()
        
        // --- 路径 A: 预测正确 (Correct Path) ---
        // 行为: 立即请求写入 R1 (Good Reg) = 0xCAFE
        val pathCorrect = branchUnit.prepareThread("Path_Correct") {
            val t = ContextScope.current match { case ThreadCtx(t) => t; case _ => null }
            
            // 模拟一些计算延迟
            t.Step("Calc_Correct") { }
            
            // 写入正确结果
            sbDriver.writeAtomic(1.U, 0xCAFE.U) {
                // Callback
            }
            
            t.Step("Exit") { t.exit() }
        } {
            // Callback when done
            correctDone := true.B
            printf("[BranchCpu] Correct Path Finished Successfully.\n")
        }

        // --- 路径 B: 预测错误 (Wrong Path) ---
        // 行为: 模拟较长的流水线延迟，然后试图破坏 R2 (Bad Reg)
        // 关键点: 我们必须在它造成破坏之前杀掉它！
        val pathWrong = branchUnit.prepareThread("Path_Wrong") {
            val t = ContextScope.current match { case ThreadCtx(t) => t; case _ => null }
            
            // 模拟取指/解码延迟 (Latency)
            // 只要这个延迟比分支决断逻辑长，或者 abort 及时赶到，
            // 这条写指令就不应该被提交。
            t.Step("Fetch_Delay_1") { printf("[WrongPath] Speculative Running...\n") }
            t.Step("Fetch_Delay_2") { printf("[WrongPath] Speculative Running...\n") }
            t.Step("Fetch_Delay_3") { printf("[WrongPath] Speculative Running...\n") }
            
            // 危险操作！如果这一步执行了，测试就失败了
            sbDriver.writeAtomic(2.U, 0xDEAD.U) {
                 printf("[WrongPath] OOPS! I wrote to the register file!\n")
            }
            
            t.Step("Exit") { t.exit() }
        } {
            printf("[BranchCpu] Wrong Path Finished (Should NOT happen if aborted!).\n")
        }


        // [Step 1] 发射推测线程 (Speculative Issue)
        branchUnit.Step("Issue_Speculation") {
            printf("[BranchCpu] Branch Instruction Encountered. Forking both paths...\n")
            pathCorrect.start()
            pathWrong.start()
        }

        // [Step 2] 分支决断 (Resolution)
        // 模拟 ALU 计算条件需要一定时间
        branchUnit.Step("ALU_Resolve_Wait") {
            // Wait 1 cycle
        }
        
        branchUnit.Step("Branch_Decision") {
            printf("[BranchCpu] Condition Resolved: Path_Correct is Valid.\n")
            resolvedFlag := true.B
            
            // [Critical] 杀掉错误路径
            // 这会拉高 pathWrong 的 abortWire，使其立即复位
            printf("[BranchCpu] *** FLUSHING Wrong Path ***\n")
            pathWrong.abort()
            killedFlag := true.B
        }
        
        // [Step 3] 等待正确路径提交
        branchUnit.Step("Wait_Commit") {
            branchUnit.waitCondition(correctDone)
        }
        
        branchUnit.Step("Retire") {
            branchUnit.exit()
        }
      }
    }
  }

  val proc = new BranchProcess(kernel)
  proc.build()

  io.regGood        := phyRegs(1)
  io.regBad         := phyRegs(2)
  io.branchResolved := proc.resolvedFlag
  io.correctPathDone:= proc.correctDone
  io.wrongPathKilled:= proc.killedFlag
}


class BranchPredictionTest extends AnyFlatSpec {
  "BranchUnit" should "speculatively execute paths and abort the wrong one before side-effects" in {
    simulate(new BranchCpuModule) { c =>
      println("\n=== Branch Prediction & Speculative Abort Test ===")

      // 1. Init
      c.reset.poke(true.B)
      c.clock.step(2)
      c.reset.poke(false.B)
      
      // 2. Start
      c.io.start.poke(true.B)
      c.clock.step()
      c.io.start.poke(false.B)

      // 3. Monitor Execution
      var cycles = 0
      var abortSeen = false
      var correctFinished = false
      
      // 我们预期整个过程大概 10 个周期内结束
      while (cycles < 15) {
        c.clock.step()
        cycles += 1
        
        val resolved = c.io.branchResolved.peek().litToBoolean
        val killed   = c.io.wrongPathKilled.peek().litToBoolean
        val done     = c.io.correctPathDone.peek().litToBoolean
        val badVal   = c.io.regBad.peek().litValue
        
        if (killed && !abortSeen) {
            println(f"[Cycle $cycles] Branch Resolved. Flush Signal Sent.")
            abortSeen = true
        }
        
        if (done && !correctFinished) {
            println(f"[Cycle $cycles] Correct Path Retired.")
            correctFinished = true
        }
        
        // 安全检查：错误寄存器必须始终为 0
        assert(badVal == 0, f"Speculative Execution Failure! Reg[2] was polluted with 0x$badVal%x")
      }

      // 4. Final Validation
      val goodVal = c.io.regGood.peek().litValue
      val badVal  = c.io.regBad.peek().litValue
      
      // [修复] 使用 toString(16) 替代 toHexString
      println(s"[Result] Reg[1] (Good): 0x${goodVal.toString(16)} (Expected 0xcafe)")
      println(s"[Result] Reg[2] (Bad) : 0x${badVal.toString(16)}  (Expected 0x0)")

      assert(goodVal == 0xCAFE, "Correct path did not commit result.")
      assert(badVal == 0x0, "Wrong path was not aborted in time!")
      assert(abortSeen, "Abort signal was never generated.")

      println("=== Test Passed: Branch Prediction Model Validated ===\n")
    }
  }
}