
package HwOS.kernel

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel._ 
import HwOS.kernel.drivers._

class MixedSemanticsTestModule extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    val pc0   = Output(UInt(32.W))
    val pc1   = Output(UInt(32.W))
    val data0 = Output(UInt(32.W))
    val check1= Output(UInt(32.W))
  })

  val kernel = new Kernel()
  val phyRegs = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))
  phyRegs(10) := 0xAAAA.U
  phyRegs(20) := 0xBBBB.U

  val meta = DriverMeta("SB_RegFile", VectorResource(32), read_clients=1, write_clients=1, fifo_depth=0)
  val sbDriver = new ScoreboardRegfileDriver(phyRegs, kernel, meta, maxClients=2)
  kernel.mount(sbDriver)

  class TestProcess(k: Kernel) extends HwProcess("Proc", debugEnable = true, parent = None)(k) {
    val slot0 = createThread("Slot0_Blocking")
    val slot1 = createThread("Slot1_Atomic")
    
    val readVal0 = RegInit(0.U(32.W))
    val checkReg1 = RegInit(0.U(32.W))

    when(io.start) { slot0.start(); slot1.start() }

    override def entry(): Unit = {
      // Slot 0: 阻塞式 API
      slot0.entry {
        val r = sbDriver.read(10.U) // 使用 read
        slot0.Step("Use_Data") {
           readVal0 := r
           slot0.exit()
        }
      }

      // Slot 1: 原子式 API
      slot1.entry {
        // [修改] 使用 readAtomic
        sbDriver.readAtomic(20.U) { data =>
           printf("[Slot1] Atomic Callback! Data=%x\n", data)
           checkReg1 := data
           slot1.pc := 2.U 
        }

        slot1.Step("Skipped") { checkReg1 := 0xDEAD.U }

        slot1.Step("Exit") { slot1.exit() }
      }
    }
  }
  val proc = new TestProcess(kernel)
  proc.build()

  io.pc0   := proc.slot0.pc
  io.pc1   := proc.slot1.pc
  io.data0 := proc.readVal0
  io.check1:= proc.checkReg1
}

class MixedSemanticsTest extends AnyFlatSpec {
  "ScoreboardDriver" should "support both Blocking and Atomic APIs correctly" in {
    simulate(new MixedSemanticsTestModule) { c =>
      println("\n=== Mixed Semantics Test ===")
      
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      c.io.start.poke(true.B)
      c.clock.step()
      c.io.start.poke(false.B)

      // Cycle 2: Result Check
      c.clock.step()
      println(s"[Cycle 2] PC0=${c.io.pc0.peek().litValue}, PC1=${c.io.pc1.peek().litValue}")
      c.io.pc0.expect(1.U) // Slot 0 Wins -> Advances
      c.io.pc1.expect(0.U) // Slot 1 Stalls -> Waits
      
      c.clock.step()
      
      // Cycle 3: Slot 0 Done, Slot 1 Executes
      println(s"[Cycle 3] PC0=${c.io.pc0.peek().litValue}, PC1=${c.io.pc1.peek().litValue}")
      c.io.pc0.expect(0.U)
      c.io.data0.expect(0xAAAA.U) 
      
      c.io.pc1.expect(2.U)        // Slot 1 Jumped!
      c.io.check1.expect(0xBBBB.U)
      
      c.clock.step()
      println("=== Mixed Test Passed ===\n")
    }
  }
}