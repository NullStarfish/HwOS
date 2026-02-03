package HwOS.kernel

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel._ 
import HwOS.kernel.drivers.RegfileDriver

class RegfileTestModule extends Module {
  val io = IO(new Bundle {
    val startTrigger = Input(Bool())
    val done         = Output(Bool())
    val debugReg5    = Output(UInt(32.W)) 
    val debugReadVal = Output(UInt(32.W))
    val pc           = Output(UInt(32.W)) // 暴露 PC 用于调试
    val active       = Output(Bool())     // 暴露 Active 用于调试
  })

  val kernel = new Kernel()

  val phyRegs = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))
  val rfDriver = new RegfileDriver(phyRegs, kernel)
  kernel.mount(rfDriver)

  class TestProcess(k: Kernel) extends HwProcess("TestProc", debugEnable = true, parent = None)(k) {
    val worker = createThread("WorkerThread")
    val debugReadReg = RegInit(0.U(32.W))

    when(io.startTrigger) {
       worker.start()
    }

    override def entry(): Unit = {
      worker.entry {
        // [Step 0] Write
        rfDriver.write(addr = 5.U, data = 0x12345678.U)
        
        // [Step 1] Read
        val readResult = rfDriver.read(addr = 5.U)
        debugReadReg := readResult

        // [Step 2] Check
        worker.Step("CHECK_RESULT") {
           printf(p"[Check] Read Back Value: 0x${Hexadecimal(readResult)}\n")
        }

        // [Step 3] Exit
        worker.Step("EXIT") {
          worker.exit()
        }
      }
    }
    
    // 暴露内部状态

  }

  val proc = new TestProcess(kernel)
  proc.build()
  io.pc     := proc.worker.pc
  io.active := proc.worker.isRunning
  io.done      := proc.worker.done
  io.debugReg5 := phyRegs(5)
  io.debugReadVal := proc.debugReadReg 
}

class RegfileDriverTest extends AnyFlatSpec {
  "RegfileDriver" should "integrate with Thread steps to read/write registers" in {
    simulate(new RegfileTestModule) { c =>
      
      println("\n=== Regfile Driver Integration Test ===")
      
      // 1. 强制复位 (确保 startWire 和 activeReg 归零)
      println("[Test] Resetting...")
      c.io.startTrigger.poke(false.B)
      c.reset.poke(true.B)
      c.clock.step(2)
      c.reset.poke(false.B)
      c.clock.step()

      // 2. 检查是否有“幽灵运行” (Ghost Run)
      // 如果此时 isRunning=true，说明有 bug 导致复位后自动启动，我们多跑几拍把它消耗掉
      var safetyLimit = 0
      while (c.io.active.peek().litToBoolean && safetyLimit < 10) {
        println(s"[Test] Thread is actively running (PC=${c.io.pc.peek().litValue}). Waiting for idle...")
        c.clock.step()
        safetyLimit += 1
      }
      
      c.io.active.expect(false.B)
      println("[Test] System Idle. Ready to Start.")

      // --- 3. 正式启动 ---
      println("[Test] Poking StartTrigger...")
      c.io.startTrigger.poke(true.B)
      c.clock.step()
      c.io.startTrigger.poke(false.B)

      // 此时应该是 Active 且 PC=0 (准备执行 Step 0)
      c.io.active.expect(true.B)
      println(s"[Test] Started! PC=${c.io.pc.peek().litValue}")

      // --- 4. Step 0: Write ---
      println("[Test] Executing Write Step...")
      c.clock.step() 
      c.io.debugReg5.expect(0x12345678.U)
      println(s"[Test] Reg[5] updated. PC is now ${c.io.pc.peek().litValue}")

      // --- 5. Step 1: Read ---
      println("[Test] Executing Read Step...")
      c.clock.step()
      println(s"[Test] Read done. PC is now ${c.io.pc.peek().litValue}")

      // --- 6. Step 2: Check -> Transition to Exit ---
      println("[Test] Executing Check Step...")
      c.clock.step()
      c.io.debugReadVal.expect(0x12345678.U)
      
      // 此时 PC 应该指向 EXIT (Step 3)
      // 并且组合逻辑 done 应该为高
      println(s"[Test] Checking DONE. PC=${c.io.pc.peek().litValue}")
      c.io.done.expect(true.B)

      // --- 7. Step 3: Execute Exit ---
      c.clock.step()
      c.io.active.expect(false.B)
      c.io.done.expect(false.B)
      println("[Test] Thread Finished.")
      
      println("=== Test Passed ===\n")
    }
  }
}