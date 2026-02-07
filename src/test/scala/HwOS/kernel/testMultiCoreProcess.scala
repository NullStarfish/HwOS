package HwOS.kernel

import chisel3._
import chisel3.util._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel._ 
import HwOS.kernel.drivers._

class MultiCoreGpuModule(val numCores: Int = 4) extends Module {
  val io = IO(new Bundle {
    val launch   = Input(Bool())
    val allDone  = Output(Bool())
    // [Dynamic IO] 使用 Vec 动态暴露出每个核心负责的第一个 VRAM 地址的值用于 Debug
    val debugVrams = Output(Vec(numCores, UInt(32.W)))
  })

  val kernel = new Kernel()
  
  // [Dynamic Config] VRAM 大小随核心数扩展，防止地址冲突 (每个核步进10)
  // 确保至少有32个寄存器，或者根据 numCores * 10
  val vramDepth = math.max(32, numCores * 10)
  
  // maxClients 设为 numCores，或者留一些余量
  val meta = DriverMeta("Global_VRAM", VectorResource(vramDepth), read_clients=4, write_clients=2, fifo_depth=0)
  val vram = RegInit(VecInit(Seq.fill(vramDepth)(0.U(32.W))))
  
  // 注意：maxClients 必须足够大以容纳所有核心
  val vramDriver = new ScoreboardRegfileDriver(vram, kernel, meta, maxClients = numCores + 2)
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
        val multiplier = 0x100 * (coreId + 1)
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

  class GpuDispatcher(k: Kernel) extends HwProcess("GPU_Dispatcher", false, None)(k) {
    val dispatch = createThread("Scheduler")
    
    // [Dynamic Spawn] 使用 Scala 集合生成多个核心进程
    val cores = (0 until numCores).map { i =>
        spawn(s"Core$i") { (n, d, p, k) => new ComputeCore(n, d, p, k, i) }
    }

    when(io.launch) { dispatch.start() }

    override def entry(): Unit = {
      dispatch.entry {
        dispatch.Step("Dispatch_Job") {
           // [Dynamic Logic] 循环赋值任务数
           // 这里简单地给偶数核分配3个任务，奇数核分配4个任务，模拟负载不均
           cores.zipWithIndex.foreach { case (core, i) =>
             core.cmdTaskCount := (if (i % 2 == 0) 3.U else 4.U)
           }
        }
        dispatch.Step("Kickoff_Cores") {
           // [Dynamic Logic] 启动所有核心
           cores.foreach(_.main.start())
        }
        dispatch.Step("Barrier") {
           // [Dynamic Logic] 聚合所有核心的 Done 信号
           // VecInit(...).asUInt.andR 等价于 "所有位都为1"
           val allFinished = VecInit(cores.map(_.statusDone)).asUInt.andR
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

  io.allDone := proc.dispatch.done
  
  // [Dynamic IO Mapping] 将 VRAM 中对应每个核心起始位置的数据连到 IO
  for (i <- 0 until numCores) {
    io.debugVrams(i) := vram(i * 10)
  }
}

class MultiCoreProcessTest extends AnyFlatSpec {
  "GpuDispatcher" should "schedule dynamically configured N child processes" in {
    // 这里我们可以配置测试的核心数量，例如 4 核
    val TEST_CORES = 16
    
    simulate(new MultiCoreGpuModule(numCores = TEST_CORES)) { c =>
      println(s"\n=== Multi-Core Process Scheduling Test ($TEST_CORES Cores) ===")

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
      // 适当增加超时时间以适应更多核心竞争总线的情况
      while (cycles < 100 && !done) {
         c.clock.step()
         cycles += 1
         if (c.io.allDone.peek().litToBoolean) {
            done = true
            println(f"[Cycle $cycles] GPU Job Finished!")
         }
      }
      
      // 4. Verification
      assert(done, "GPU Timeout!")

      // [Dynamic Check] 循环检查所有核心的结果
      for (i <- 0 until TEST_CORES) {
        val res = c.io.debugVrams(i).peek().litValue
        val expected = 0x100 * (i + 1)
        
        println(s"[Result] Core $i (Addr ${i*10}) = 0x${res.toString(16)} (Expected 0x${expected.toHexString})")
        assert(res == expected, s"Core $i failed. Got 0x${res.toString(16)}, expected 0x${expected.toHexString}")
      }

      println(s"=== Test Passed: $TEST_CORES-Core Scheduling Validated ===\n")
    }
  }
}