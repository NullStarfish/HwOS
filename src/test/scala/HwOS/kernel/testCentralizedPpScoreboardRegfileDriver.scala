package HwOS.kernel

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel._ 
import HwOS.kernel.drivers._
import _root_.HwOS.kernel.drivers.CentralizedScoreboardDriver

class CentralizedPpTestModule extends Module {
  val io = IO(new Bundle {
    val start     = Input(Bool())
    val killSlot0 = Input(Bool())
    val pc0       = Output(UInt(32.W))
    val pc1       = Output(UInt(32.W))
    val data0     = Output(UInt(32.W))
    val data1     = Output(UInt(32.W))
  })

  val kernel = new Kernel()
  val phyRegs = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))
  phyRegs(10) := 0xAAAA.U
  phyRegs(20) := 0xBBBB.U

  val meta = DriverMeta("Pipe_RegFile", VectorResource(32), read_clients=1, write_clients=1, fifo_depth=0)
  val pipeDriver = new CentralizedScoreboardDriver(phyRegs, kernel, meta, maxClients=2)
  kernel.mount(pipeDriver)

  class TestProcess(k: Kernel) extends HwProcess("FetchUnit", debugEnable = true, parent = None)(k) {
    val slot0 = createThread("Slot0")
    val slot1 = createThread("Slot1")
    
    val readVal0 = RegInit(0.U(32.W))
    val readVal1 = RegInit(0.U(32.W))

    when(io.start) { slot0.start(); slot1.start() }
    when(io.killSlot0) { slot0.abort() }

    override def entry(): Unit = {
      // ==========================
      // Slot 0 Logic
      // ==========================
      slot0.entry {
        // readAtomic 会生成 Step 0 (Post) 和 Step 1 (Resolve)
        pipeDriver.readAtomic(10.U) { data =>
           readVal0 := data
           // [Fix] 完成后跳到 "Done" (Step 2)
           slot0.pc := 2.U 
        }
        
        // [Fix] 去掉了无用的占位 Step，因为 Step 是自动生成的，没必要手动占位
        // 现在的 Step 2 就是 Done
        
        slot0.Step("Done") { 
           // [Fix] 必须调用 exit()，否则 HardwareThread 会报错
           slot0.exit() 
        } 
      }

      // ==========================
      // Slot 1 Logic
      // ==========================
      slot1.entry {
        pipeDriver.readAtomic(20.U) { data =>
           readVal1 := data
           slot1.pc := 2.U
        }
        // Step 2
        slot1.Step("Done") { 
           slot1.exit() 
        }
      }
    }
  }
  val proc = new TestProcess(kernel)
  proc.build()

  io.pc0   := proc.slot0.pc
  io.pc1   := proc.slot1.pc
  io.data0 := proc.readVal0
  io.data1 := proc.readVal1
}

class CentralizedPptest extends AnyFlatSpec {
  "PipelinedScoreboardDriver" should "incur 2-cycle latency and handle aborts gracefully" in {
    simulate(new CentralizedPpTestModule) { c =>
      println("\n=== Pipelined Driver Test ===")
      
      // Init
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)
      c.io.killSlot0.poke(false.B)

      // =========================================================
      // Case 1: Normal Contention
      // =========================================================
      println("[Test] Case 1: Start both threads.")
      c.io.start.poke(true.B)
      c.clock.step()
      c.io.start.poke(false.B)

      // Cycle 1: Post Stage (PC=0)
      println(s"[Cycle 1] PC0=${c.io.pc0.peek().litValue}, PC1=${c.io.pc1.peek().litValue}")
      c.io.pc0.expect(0.U)
      c.io.pc1.expect(0.U)

      c.clock.step()

      // Cycle 2: Resolve Stage (PC=1)
      // Slot 0 wins, Slot 1 stalls
      println(s"[Cycle 2] PC0=${c.io.pc0.peek().litValue}, PC1=${c.io.pc1.peek().litValue}")
      c.io.pc0.expect(1.U)
      c.io.pc1.expect(1.U)
      
      c.clock.step()

      // Cycle 3: Slot 0 Done (PC=2), Slot 1 Retry (PC=1)
      // 注意：Slot 0 此时在执行 Done Step，将在这一拍 exit，下一拍 PC->0
      // Slot 1 此时终于拿到资源，执行 Callback，PC->2
      println(s"[Cycle 3] PC0=${c.io.pc0.peek().litValue}, PC1=${c.io.pc1.peek().litValue}")
      c.io.pc0.expect(2.U)        
      c.io.data0.expect(0xAAAA.U) 
      c.io.pc1.expect(1.U)        
      
      c.clock.step()

      // Cycle 4: Slot 1 Done (PC=2)
      println(s"[Cycle 4] PC0=${c.io.pc0.peek().litValue}, PC1=${c.io.pc1.peek().litValue}")
      c.io.pc1.expect(2.U)        
      c.io.data1.expect(0xBBBB.U) 
      
      println("[Test] Contention & Latency Check Passed.")


      // =========================================================
      // Case 2: Abort Recovery
      // =========================================================
      println("\n[Test] Case 2: Start both, but ABORT Slot 0 during Resolve.")
      
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      c.io.start.poke(true.B)
      c.clock.step()
      c.io.start.poke(false.B)

      // Cycle 1: Post
      c.clock.step()
      
      // Cycle 2: Resolve + Kill Slot 0
      println("[Cycle 2] Asserting KILL on Slot 0...")
      c.io.killSlot0.poke(true.B)
      
      c.clock.step()
      c.io.killSlot0.poke(false.B)

      // Cycle 3:
      // Slot 0 Dead (PC=0)
      // Slot 1 should have noticed Slot 0 is gone and proceeded?
      // 由于是寄存器复位，Slot 1 可能在 Cycle 2 看到的是旧值而 Stall 了一拍，
      // 所以 Cycle 3 时 Slot 1 还在 PC=1，但此时资源已空闲，它通过了。
      // 下一拍 (Cycle 4) PC=2。
      
      println(s"[Result] PC0=${c.io.pc0.peek().litValue}, PC1=${c.io.pc1.peek().litValue}")
      c.io.pc0.expect(0.U) 
      
      // 我们允许 1 周期或 0 周期的恢复延迟，取决于具体的复位时序
      if (c.io.pc1.peek().litValue == 1) {
          println("   -> Slot 1 stalled 1 cycle (Expected). Proceeding...")
          c.clock.step()
          c.io.pc1.expect(2.U)
      } else {
          c.io.pc1.expect(2.U)
      }

      c.io.data1.expect(0xBBBB.U)
      println("[Test] Abort Recovery Passed.")

      println("=== Pipelined Test Passed ===\n")
    }
  }
}