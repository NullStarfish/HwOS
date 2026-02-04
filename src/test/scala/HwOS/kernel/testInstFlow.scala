package HwOS.kernel

import chisel3._
import chisel3.util._ // Import required for PopCount
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel._ 
import HwOS.kernel.drivers._

// ==============================================================================
// 1. Definition of Services (Pipeline Stages as Drivers)
// ==============================================================================

class ExecuteDriver(kernel: Kernel) extends PhysicalDriver(
  DriverMeta("EXEC", ScalarResource, 4, 4, 0) 
) {
  def process(id: Int): Unit = {
    ContextScope.current match {
      case ThreadCtx(t) => 
        t.Step("EXEC_Stage") {
           // Simulate ALU work
        }
      case _ =>
    }
  }
}

class WritebackDriver(kernel: Kernel) extends PhysicalDriver(
  DriverMeta("WB", ScalarResource, 4, 4, 0)
) {
  def commit(): Unit = {
    ContextScope.current match {
      case ThreadCtx(t) => 
        t.Step("WB_Commit") {
           // The service injects the 'exit' instruction
           t.exit() 
        }
      case _ =>
    }
  }
}

// ==============================================================================
// 2. The "Inst is Thread" CPU Prototype
// ==============================================================================
class InstThreadCpuModule extends Module {
  val io = IO(new Bundle {
    val start        = Input(Bool())
    val activeSlots  = Output(UInt(4.W))    // Bitmask of running slots
    val retiredCount = Output(UInt(32.W))   // Total instructions retired
  })

  val kernel = new Kernel()
  
  // Mount Services
  val execSvc = new ExecuteDriver(kernel)
  val wbSvc   = new WritebackDriver(kernel)
  kernel.mount(execSvc)
  kernel.mount(wbSvc)

  class InstCpuProcess(k: Kernel) extends HwProcess("InstCpu", debugEnable = true, parent = None)(k) {
    
    // A. Instruction Slots (The Threads)
    val numSlots = 4
    val slots = (0 until numSlots).map(i => createThread(s"Slot_$i"))
    
    // A counter to track throughput
    val retiredCounter = RegInit(0.U(32.W))
    
    // B. Fetch Unit (The Producer)
    val fetch = createThread("FetchUnit")

    when(io.start) { fetch.start() }

    override def entry(): Unit = {
      
      // Define the "Instruction Lifecycle"
      slots.zipWithIndex.foreach { case (slot, idx) =>
        slot.entry {
          // 1. Inject Execute Stage
          execSvc.process(idx)
          
          // 2. Inject Writeback Stage
          wbSvc.commit()
          
          // NOTE: We moved the counting logic to Global Scope (below) 
          // to handle parallel retirements correctly.
        }
      }

      // [FIX] Correctly count parallel retirements using PopCount
      // We perform this outside the threads, in the Process scope
      val doneMask = VecInit(slots.map(_.done)).asUInt
      val doneCount = PopCount(doneMask)
      retiredCounter := retiredCounter + doneCount


      // Fetch Loop: Continuously emit threads
      fetch.entry {
        fetch.Step("Fetch_Loop") {
           // Greedy Issue Logic
           slots.foreach { slot =>
             when (!slot.isRunning) {
                slot.start()
             }
           }
           // Infinite Loop
           fetch.pc := 0.U 
        }

        // Dummy Exit Step for compliance
        fetch.Step("Daemon_Exit_Trap") {
           fetch.exit()
        }
      }
    }
  }

  val proc = new InstCpuProcess(kernel)
  proc.build()

  // Outputs
  io.activeSlots := VecInit(proc.slots.map(_.isRunning)).asUInt
  io.retiredCount := proc.retiredCounter
}

// ==============================================================================
// 3. Test Case
// ==============================================================================
class InstThreadLifecycleTest extends AnyFlatSpec {
  "Instruction Threads" should "be continuously spawned by Fetch and terminated by Writeback Service" in {
    simulate(new InstThreadCpuModule) { c =>
      println("\n=== Instruction Thread Injection Test ===")

      // 1. Init
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)
      
      // 2. Start CPU (Wake up Fetch)
      println("[Test] Starting Fetch Unit...")
      c.io.start.poke(true.B)
      c.clock.step()
      c.io.start.poke(false.B)
      
      // 3. Monitor Execution
      val totalCycles = 50
      for (i <- 1 to totalCycles) {
        c.clock.step()
        
        val active = c.io.activeSlots.peek().litValue
        val retired = c.io.retiredCount.peek().litValue
        
        if (i % 10 == 0) {
           println(f"[Cycle $i%2d] ActiveSlots=0x$active%x, Retired=$retired")
        }
      }
      
      // 4. Validation
      val finalRetired = c.io.retiredCount.peek().litValue
      println(s"[Result] Total Retired: $finalRetired")
      
      // Expected throughput calculation:
      // - 4 slots parallel
      // - Latency per inst: ~4-5 cycles (Fetch+Exec+WB+IdleGap)
      // - Theoretical Max: 50 * 4 / 4 = 50 instructions
      // - Previous failure was 16 (detected 1 per batch).
      // - Now we expect ~48-60 range.
      assert(finalRetired > 40, s"Throughput too low ($finalRetired)! Threads might be stuck.")
      assert(c.io.activeSlots.peek().litValue > 0, "CPU died! No slots are active.")

      println("=== Test Passed: Cross-Driver Thread Lifecycle is Stable ===\n")
    }
  }
}
