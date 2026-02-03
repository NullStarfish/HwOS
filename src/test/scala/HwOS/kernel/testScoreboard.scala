package HwOS.kernel

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel._ 
import HwOS.kernel.drivers._

class ScoreboardTestModule extends Module {
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

  val meta = DriverMeta("SB_RegFile", VectorResource(32), read_clients=1, write_clients=1, fifo_depth=0)
  val sbDriver = new ScoreboardRegfileDriver(phyRegs, kernel, meta, maxClients=2)
  kernel.mount(sbDriver)

  class TestProcess(k: Kernel) extends HwProcess("FetchUnit", debugEnable = true, parent = None)(k) {
    val slot0 = createThread("Slot0")
    val slot1 = createThread("Slot1")
    val readVal0 = RegInit(0.U(32.W))
    val readVal1 = RegInit(0.U(32.W))

    when(io.start) { slot0.start(); slot1.start() }
    when(io.killSlot0) { slot0.abort() }

    override def entry(): Unit = {
      slot0.entry {
        val r = sbDriver.read(10.U)
        slot0.Step("WB") { readVal0 := r; slot0.exit() }
      }
      slot1.entry {
        val r = sbDriver.read(20.U)
        slot1.Step("WB") { readVal1 := r; slot1.exit() }
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

class ScoreboardDriverTest extends AnyFlatSpec {
  "ScoreboardDriver" should "handle priority stalling and instant abort recovery" in {
    simulate(new ScoreboardTestModule) { c =>
      println("\n=== Scoreboard Driver Test ===")
      
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      // ==========================================
      // Case 1: Priority Test
      // ==========================================
      println("[Test] Case 1: Start both. Slot 0 should win.")
      c.io.start.poke(true.B)
      c.clock.step() 
      c.io.start.poke(false.B)

      // Cycle 1: Check Logic active
      c.clock.step()

      // Cycle 2: Result
      // Slot 0 (ID=0, Stall=0) -> PC=1
      // Slot 1 (ID=1, Stall=1) -> PC=0
      println(s"[Cycle 2] PC0=${c.io.pc0.peek().litValue}, PC1=${c.io.pc1.peek().litValue}")
      c.io.pc0.expect(1.U)
      c.io.pc1.expect(0.U)
      
      c.clock.step()

      // Cycle 3: Slot 0 Done, Slot 1 Start
      println(s"[Cycle 3] PC0=${c.io.pc0.peek().litValue}, PC1=${c.io.pc1.peek().litValue}")
      c.io.pc0.expect(0.U)
      c.io.pc1.expect(1.U) 
      c.io.data0.expect(0xAAAA.U) // Slot 0 Data Ready
      
      c.clock.step()
      c.io.data1.expect(0xBBBB.U) // Slot 1 Data Ready
      println("[Test] Case 1 Passed.\n")

      // ==========================================
      // Case 2: Abort Test
      // ==========================================
      println("[Test] Case 2: Abort Slot 0.")
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      c.io.start.poke(true.B)
      c.clock.step()
      c.io.start.poke(false.B)
      
      // Kill Slot 0
      c.io.killSlot0.poke(true.B)
      c.clock.step()
      c.io.killSlot0.poke(false.B)

      // Cycle 2:
      // Slot 0 Dead (PC=0)
      // Slot 1 Advanced Immediately (PC=1)
      println(s"[Result] PC0=${c.io.pc0.peek().litValue}, PC1=${c.io.pc1.peek().litValue}")
      c.io.pc0.expect(0.U) 
      c.io.pc1.expect(1.U) 
      
      c.clock.step()
      c.io.data1.expect(0xBBBB.U)

      println("=== Scoreboard Test Passed ===\n")
    }
  }
}