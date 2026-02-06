package HwOS.kernel

import chisel3._
import chisel3.util._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel._ 
import HwOS.kernel.drivers._ // 引用刚才固化的 Driver

class MultiCoreGpuModule extends Module {
  val io = IO(new Bundle {
    val launch   = Input(Bool())
    val allDone  = Output(Bool())
    val vramCore0_0 = Output(UInt(32.W))
    val vramCore1_0 = Output(UInt(32.W))
  })

  val kernel = new Kernel()
  
  // 保持 write_clients=1，确保绝对的安全性
  val meta = DriverMeta("Global_VRAM", VectorResource(32), read_clients=4, write_clients=2, fifo_depth=0)
  val vram = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))
  val vramDriver = new ScoreboardRegfileDriver(vram, kernel, meta, maxClients=8)
  kernel.mount(vramDriver)

  class ComputeCore(name: String, debug: Boolean, parent: Option[HwProcess], k: Kernel, val coreId: Int) 
      extends HwProcess(name, debug, parent)(k) {

    val cmdTaskCount = RegInit(0.U(32.W)) 
    val statusDone   = RegInit(false.B)
    val main = createThread("Main")

    override def entry(): Unit = {
      main.entry {
        val iter = RegInit(0.U(32.W))
        val baseAddr = (coreId * 10).U
        val pcLoopStart = 1.U

        // Step 0: Init
        main.Step("Init") {
           iter := 0.U
           statusDone := false.B
        }
        
        // Step 1: Write to VRAM
        // [Safety Fix] 在 Scala 中预计算常量乘法，避免 Chisel 宽度推断问题
        val multiplier = 0x100 * (coreId + 1) // Core0->0x100, Core1->0x200
        val writeData  = multiplier.U + iter
        
        vramDriver.writeAtomic(baseAddr + iter, writeData) { }

        // Step 2: Loop Check
        main.Step("Loop_Check") {
           val next = iter + 1.U
           iter := next
           
           when (next < cmdTaskCount) {
               main.pc := pcLoopStart
           } .otherwise {
               // Fall through to Finish
           }
        }
        
        // Step 3: Finish
        main.Step("Finish") {
           statusDone := true.B
           main.exit()
        }
      }
    }
  }

  class GpuDispatcher(k: Kernel) extends HwProcess("GPU_Dispatcher", true, None)(k) {
    val dispatch = createThread("Scheduler")
    val core0 = spawn("Core0") { (n, d, p, k) => new ComputeCore(n, d, p, k, 0) }
    val core1 = spawn("Core1") { (n, d, p, k) => new ComputeCore(n, d, p, k, 1) }

    when(io.launch) { dispatch.start() }

    override def entry(): Unit = {
      dispatch.entry {
        dispatch.Step("Dispatch_Job") {
           core0.cmdTaskCount := 3.U
           core1.cmdTaskCount := 4.U
        }
        dispatch.Step("Kickoff_Cores") {
           core0.main.start()
           core1.main.start()
        }
        dispatch.Step("Barrier") {
           val allFinished = core0.statusDone && core1.statusDone
           dispatch.waitCondition(allFinished)
        }
        dispatch.Step("Retire") {
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

class MultiCoreProcessTest extends AnyFlatSpec {
  "GpuDispatcher" should "schedule multiple child processes and wait for them (Barrier)" in {
    simulate(new MultiCoreGpuModule) { c =>
      println("\n=== Multi-Core Process Scheduling Test (Final) ===")

      // 1. Init
      c.reset.poke(true.B)
      c.clock.step(2)
      c.reset.poke(false.B)
      
      // 2. Launch
      c.io.launch.poke(true.B)
      c.clock.step()
      c.io.launch.poke(false.B)

      // 3. Run
      var cycles = 0
      var done = false
      while (cycles < 50 && !done) {
         c.clock.step()
         cycles += 1
         if (c.io.allDone.peek().litToBoolean) {
            done = true
            println(f"[Cycle $cycles] GPU Job Finished!")
         }
      }
      
      // 4. Verification
      val res0 = c.io.vramCore0_0.peek().litValue
      val res1 = c.io.vramCore1_0.peek().litValue
      
      println(s"[Result] Core 0 (Addr 0) = 0x${res0.toString(16)} (Expected 0x100)")
      println(s"[Result] Core 1 (Addr 10)= 0x${res1.toString(16)} (Expected 0x200)")
      
      assert(done, "GPU Timeout!")
      assert(res0 == 0x100, s"Core 0 failed. Got 0x${res0.toString(16)}")
      assert(res1 == 0x200, s"Core 1 failed. Got 0x${res1.toString(16)}")

      println("=== Test Passed: Multi-Process Scheduling Validated ===\n")
    }
  }
}