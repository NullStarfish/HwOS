package HwOS.kernel

import chisel3._
import chisel3.util._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel._ 
import HwOS.kernel.drivers._

// ==============================================================================
// 1. 多核 GPU 系统顶层
// ==============================================================================
class MultiCoreGpuModule extends Module {
  val io = IO(new Bundle {
    val launch   = Input(Bool())
    val allDone  = Output(Bool())
    
    // 观测 VRAM 结果
    val vramCore0_0 = Output(UInt(32.W)) // Core0 写的第一个数
    val vramCore1_0 = Output(UInt(32.W)) // Core1 写的第一个数
  })

  val kernel = new Kernel()
  
  // --- Shared Resource: Global VRAM ---
  // 模拟显存，所有 Core 共享
  val vram = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))
  // 允许多个 Core 同时轰炸 VRAM (8 clients)
  val meta = DriverMeta("Global_VRAM", VectorResource(32), read_clients=4, write_clients=4, fifo_depth=0)
  val vramDriver = new ScoreboardRegfileDriver(vram, kernel, meta, maxClients=8)
  kernel.mount(vramDriver)

  // ==========================================================================
  // 2. 子进程定义: Compute Core
  // ==========================================================================
  // 参数: coreId 用于计算 VRAM 写入偏移量
  class ComputeCore(name: String, debug: Boolean, parent: Option[HwProcess], k: Kernel, val coreId: Int) 
      extends HwProcess(name, debug, parent)(k) {

    // [Process Interface] 进程间通信寄存器
    // 这些是该进程的"输入引脚"，父进程可以直接写它们
    val cmdTaskCount = RegInit(0.U(32.W)) 
    val statusDone   = RegInit(false.B)

    // 主线程
    val main = createThread("Main")

    override def entry(): Unit = {
      main.entry {
        // --- 局部变量 ---
        val iter = RegInit(0.U(32.W))
        val baseAddr = (coreId * 10).U // Core 0 -> 0..9, Core 1 -> 10..19

        // [Step 0] 初始化
        main.Step("Init") {
           iter := 0.U
           statusDone := false.B
        }
        
        // 记录 Loop Body 的 PC 地址，Scoreboard Driver 的 writeAtomic 恰好生成 1 个 Step
        // PC sequence: 
        // 0: Init
        // 1: WriteAtomic (Auto-generated)
        // 2: LoopCheck
        val pcLoopStart = 1.U

        // [Step 1] 执行计算并写入 VRAM (模拟耗时操作)
        // 注意：addr 的连线是动态的 (baseAddr + iter)，每次跳转回来都会用新的 iter 值
        vramDriver.writeAtomic(baseAddr + iter, 0x100.U * (coreId.U + 1.U) + iter) {
             // Callback: 写入完成，这里什么都不做，等待 Step 自动完成
        }

        // [Step 2] 循环判断
        main.Step("Loop_Check") {
           val next = iter + 1.U
           iter := next
           
           when (next < cmdTaskCount) {
               // Jump Back
               main.pc := pcLoopStart
               printf(p"[$name] Iteration done. Jumping back to PC $pcLoopStart. Next Iter: $next\n")
           } .otherwise {
               // Fall through
               printf(p"[$name] All tasks finished ($next items).\n")
           }
        }
        
        // [Step 3] 结束
        main.Step("Finish") {
           statusDone := true.B
           main.exit()
        }
      }
    }
  }

  // ==========================================================================
  // 3. 父进程定义: GPU Dispatcher
  // ==========================================================================
  class GpuDispatcher(k: Kernel) extends HwProcess("GPU_Dispatcher", true, None)(k) {
    val dispatch = createThread("Scheduler")
    
    // [关键] Spawn 子进程
    // 我们保留了子进程的实例引用 (core0, core1)，以便后续访问它们的 Reg
    val core0 = spawn("Core0") { (n, d, p, k) => new ComputeCore(n, d, p, k, 0) }
    val core1 = spawn("Core1") { (n, d, p, k) => new ComputeCore(n, d, p, k, 1) }

    when(io.launch) { dispatch.start() }

    override def entry(): Unit = {
      dispatch.entry {
        
        // [Step 0] 任务分发 (Task Dispatch)
        dispatch.Step("Dispatch_Job") {
           printf("[GPU] Dispatching jobs to cores...\n")
           // 直接写入子进程的寄存器进行配置
           core0.cmdTaskCount := 3.U  // Core 0 处理 3 个数据
           core1.cmdTaskCount := 4.U  // Core 1 处理 4 个数据
        }

        // [Step 1] 启动核心 (Kickoff)
        dispatch.Step("Kickoff_Cores") {
           printf("[GPU] Starting Cores...\n")
           core0.main.start()
           core1.main.start()
        }

        // [Step 2] 屏障同步 (Barrier Wait)
        // 等待所有子进程报告 Done
        dispatch.Step("Barrier") {
           val allFinished = core0.statusDone && core1.statusDone
           dispatch.waitCondition(allFinished)
        }

        // [Step 3] 退休
        dispatch.Step("Retire") {
           printf("[GPU] All Cores finished. Job Complete.\n")
           dispatch.exit()
        }
      }
    }
  }
  
  val proc = new GpuDispatcher(kernel)
  proc.build()

  io.allDone     := proc.dispatch.done
  io.vramCore0_0 := vram(0)
  io.vramCore1_0 := vram(10)
}

// ==============================================================================
// 4. 测试用例
// ==============================================================================
class MultiCoreProcessTest extends AnyFlatSpec {
  "GpuDispatcher" should "schedule multiple child processes and wait for them (Barrier)" in {
    simulate(new MultiCoreGpuModule) { c =>
      println("\n=== Multi-Core Process Scheduling Test ===")

      // 1. Init
      c.reset.poke(true.B)
      c.clock.step(2)
      c.reset.poke(false.B)
      
      // 2. Launch GPU
      c.io.launch.poke(true.B)
      c.clock.step()
      c.io.launch.poke(false.B)

      // 3. Run Simulation
      // 预计流程:
      // Cycle 0: GPU Start
      // Cycle 1: Dispatch Job (Config Regs)
      // Cycle 2: Kickoff (Start Core0, Core1)
      // Cycle 3+: Core0/1 running in parallel writing VRAM
      // ...
      // Cycle N: Barrier satisfied -> GPU Done
      
      var cycles = 0
      var done = false
      while (cycles < 30 && !done) {
         c.clock.step()
         cycles += 1
         
         if (c.io.allDone.peek().litToBoolean) {
            done = true
            println(f"[Cycle $cycles] GPU Job Finished!")
         }
      }
      
      // 4. Verification
      // Core 0 should write 0x100, 0x101, 0x102 at addr 0, 1, 2
      // Core 1 should write 0x200, 0x201, 0x202, 0x203 at addr 10, 11, 12, 13
      
      val res0 = c.io.vramCore0_0.peek().litValue
      val res1 = c.io.vramCore1_0.peek().litValue
      
      println(s"[Result] Core 0 (Addr 0) = 0x${res0.toString(16)} (Expected 0x100)")
      println(s"[Result] Core 1 (Addr 10)= 0x${res1.toString(16)} (Expected 0x200)")
      
      assert(done, "GPU Timeout!")
      assert(res0 == 0x100, "Core 0 failed to write correct data")
      assert(res1 == 0x200, "Core 1 failed to write correct data")

      println("=== Test Passed: Multi-Process Scheduling Validated ===\n")
    }
  }
}