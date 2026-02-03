package HwOS.kernel

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel._ 

/* 我们在主线程中定义fork thread，实际上是为了捕获主线程的上下文（闭包环境），
也就是说实际上，我们通过让主线程和子线程共用上下文，让子线程和主线程不需要通过复杂的传参来通信，这是非常巧妙的做法。
同理，在面临类似black box driver那种driver api中需要传入callback函数的driver，
我们依然在主线程作用域处定义callback函数，同样也是起到了共享上下文的作用，这是非常牛逼的 */



class ForkNonBlockingModule extends Module {
  val io = IO(new Bundle {
    val start     = Input(Bool())
    val parentPC  = Output(UInt(32.W))
    val mainReg   = Output(UInt(32.W)) 
    val childReg  = Output(UInt(32.W)) 
    val done      = Output(Bool())
  })

  val kernel = new Kernel()

  class TestProcess(k: Kernel) extends HwProcess("PipelineProc", debugEnable = true, parent = None)(k) {
    val cpu = createThread("CPU")
    val accReg  = RegInit(0.U(32.W))
    val mainReg = RegInit(0.U(32.W))

    when(io.start) { cpu.start() }

    override def entry(): Unit = {
      cpu.entry {
        
        // [Step 0] 启动加速器 (Fork)
        cpu.fork("Accelerator") {
           // --- 子线程 (Load -> Compute -> Store -> Exit) ---
           val t = ContextScope.current match { case ThreadCtx(t) => t; case _ => null }
           t.Step("Load")    { accReg := 10.U }
           t.Step("Compute") { accReg := accReg * 2.U }
           t.Step("Store")   { accReg := accReg + 5.U }
           t.Step("Done")    { t.exit() }
        } {
           // --- Callback (中断) ---
           // 强行跳转到 Step 5，并写回寄存器
           printf("[CPU] INTERRUPT! Accelerator done. Result=%d. Jumping to ISR (Step 5).\n", accReg)
           cpu.pc := 5.U 
           mainReg := accReg 
        }

        // [Step 1] CPU Op A (MainReg + 1)
        cpu.Step("Op_A") { 
            mainReg := mainReg + 1.U 
            printf("[CPU] Op A Executing... (Current: %d -> Next: %d)\n", mainReg, mainReg + 1.U)
        }
        // [Step 2] CPU Op B (MainReg + 1)
        cpu.Step("Op_B") { 
            mainReg := mainReg + 1.U 
            printf("[CPU] Op B Executing... (Current: %d -> Next: %d)\n", mainReg, mainReg + 1.U)
        }
        // [Step 3] CPU Op C (MainReg + 1)
        cpu.Step("Op_C") { 
            mainReg := mainReg + 1.U 
            printf("[CPU] Op C Executing... (Current: %d -> Next: %d)\n", mainReg, mainReg + 1.U)
        }
        
        // [Step 4] Wait Loop
        cpu.Step("Wait_Loop") {
            printf("[CPU] Waiting...\n")
            cpu.waitCondition(false.B) 
        }

        // [Step 5] ISR
        cpu.Step("ISR_Handler") {
            printf("[CPU] ISR Entered. MainReg should be 25. Actual: %d\n", mainReg)
            cpu.exit()
        }
      }
    }
  }

  val proc = new TestProcess(kernel)
  proc.build()

  io.parentPC := proc.cpu.pc
  io.mainReg  := proc.mainReg
  io.childReg := proc.accReg
  io.done     := proc.cpu.done
}

class ForkNonBlockingTest extends AnyFlatSpec {
  "HardwareThread" should "support Non-Blocking Fork and Interrupt-style Callback" in {
    simulate(new ForkNonBlockingModule) { c =>
      println("\n=== Non-Blocking Fork (Pipeline) Test ===")

      // --- Reset ---
      println("[Test] Resetting...")
      c.reset.poke(true.B)
      c.clock.step(2)
      c.reset.poke(false.B)
      c.clock.step()

      // 1. Start
      c.io.start.poke(true.B)
      c.clock.step()
      c.io.start.poke(false.B)

      // 2. Step 0: Fork
      // 当前 MainReg = 0
      c.io.parentPC.expect(0.U)
      c.io.mainReg.expect(0.U) 
      println("[Test] PC=0 (Fork). Child Starting.")
      c.clock.step()

      // 3. Step 1: CPU Op A 正在执行
      // 当前 MainReg 依然是 0 (因为 Step 1 的写入要等到这一拍结束才生效)
      // Child: Load (accReg -> 10)
      c.io.parentPC.expect(1.U)
      c.io.mainReg.expect(0.U) // <--- 修正：这里还是 0
      println("[Test] PC=1 (Op A). Parallel Run.")
      c.clock.step()

      // 4. Step 2: CPU Op B 正在执行
      // 当前 MainReg 变成了 1 (Step 1 的结果)
      // Child: Compute (accReg 10 -> 20)
      c.io.parentPC.expect(2.U)
      c.io.mainReg.expect(1.U) // <--- 修正：这里验证 Step 1 的结果
      c.io.childReg.expect(10.U) // Child Load 完成
      println("[Test] PC=2 (Op B).")
      c.clock.step()

      // 5. Step 3: CPU Op C 正在执行
      // 当前 MainReg 变成了 2 (Step 2 的结果)
      // Child: Store (accReg 20 -> 25)
      c.io.parentPC.expect(3.U)
      c.io.mainReg.expect(2.U)
      c.io.childReg.expect(20.U)
      println("[Test] PC=3 (Op C).")
      c.clock.step()

      // 6. Step 4: CPU Wait Loop 正在执行
      // 当前 MainReg 变成了 3 (Step 3 的结果)
      // Child: Done -> 触发 Callback -> 覆盖 Next PC 为 5, 覆盖 Next Reg 为 25
      c.io.parentPC.expect(4.U)
      c.io.mainReg.expect(3.U)
      c.io.childReg.expect(25.U) // Child 结果已出
      println("[Test] PC=4 (Wait). Expecting Interrupt...")
      c.clock.step()

      // 7. Verify Jump to ISR (Step 5)
      // 中断发生后，PC 变 5，MainReg 被 Callback 强写为 25
      val currentPC = c.io.parentPC.peek().litValue
      val currentReg = c.io.mainReg.peek().litValue
      
      println(s"[Test] PC after interrupt: $currentPC (Expected 5)")
      println(s"[Test] MainReg after interrupt: $currentReg (Expected 25)")
      
      c.io.parentPC.expect(5.U)
      c.io.mainReg.expect(25.U)

      // [FIX] 在这里检查 done，不要 step！
      // done 是个组合逻辑脉冲，只在当前这一拍有效
      println("[Test] Checking DONE pulse...")
      c.io.done.expect(true.B) 

      // 8. Finish
      // 验证完之后再走一拍，确认线程停止
      c.clock.step()
      // 如果需要，可以验证 active 变成了 false，或者 done 变回 false
      // c.io.done.expect(false.B) 
      
      println("=== Test Passed ===\n")
    }
  }
}