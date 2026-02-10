package HwOS.kernel

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel._ 

class MonitorBundle extends Bundle {
  val valid = Output(Bool())
  val data  = Output(UInt(32.W))
}

class MonitorDriver(io: MonitorBundle, kernel: Kernel) extends PhysicalDriver(
  DriverMeta("Monitor", ScalarResource, 1, 1, 0)
) {
  io.valid := false.B
  io.data  := 0.U
  /*这个默认值是必须的，每个驱动都必须赋默认值*/

  def fire(value: UInt): Unit = {
    ContextScope.current match {
      case ThreadCtx(t) => 
        t.Step("Fire") {
          io.valid := true.B
          io.data  := value
        }
      case AtomicCtx(t) => 
        io.valid := true.B
        io.data  := value
      case _ =>
    }
  }
}

class AbortTestModule extends Module {
  val io = IO(new Bundle {
    val start      = Input(Bool())
    val kill       = Input(Bool())
    val threadPC   = Output(UInt(32.W))
    val isActive   = Output(Bool())
    val monitorVal = Output(Bool())
    val callbackHit= Output(Bool())
  })

  val kernel = new Kernel()
  val monitorWire = Wire(new MonitorBundle)
  val monitor     = new MonitorDriver(monitorWire, kernel)
  kernel.mount(monitor)

  class TestProcess(k: Kernel) extends HwProcess("AbortProc", debugEnable = true, parent = None)(k) {
    val victim = createThread("Victim")
    val callbackFlag = RegInit(false.B)

    when(io.start) { victim.start() }
    when(io.kill)  { victim.abort() }

    override def entry(): Unit = {
      victim.entry {
        // [Step 0] Run
        victim.Step("Run_0") {
          // just running
        }

        // [Step 1] Fire (这里我们会尝试杀掉它)
        victim.Step("Run_1_Fire") {
          monitor.fire(0xDEAD.U)
        }

        // [Step 2] Exit
        victim.Step("Run_2_Exit") {
          victim.exit()
        }

        victim.Global {
          when(victim.done) {
            printf("[System] Callback Triggered! (Should NOT happen if aborted)\n")
            callbackFlag := true.B
          }
        }
      }
    }
  }

  val proc = new TestProcess(kernel)
  proc.build()

  io.threadPC    := proc.victim.pc
  io.isActive    := proc.victim.isRunning
  io.monitorVal  := monitorWire.valid
  io.callbackHit := proc.callbackFlag
}

class AbortTest extends AnyFlatSpec {
  "HardwareThread" should "abort immediately and suppress outputs" in {
    simulate(new AbortTestModule) { c =>
      println("\n=== Abort Test Start ===")
      
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      // 1. Start
      c.io.start.poke(true.B)
      c.clock.step()
      c.io.start.poke(false.B)
      
      // Step 0
      c.io.isActive.expect(true.B)
      c.io.threadPC.expect(0.U)
      c.clock.step()

      // 2. Step 1 (Attempt Fire) + KILL
      println("[Test] Step 1 Running... KILLING IT NOW!")
      c.io.threadPC.expect(1.U)
      
      // 在这一拍同时拉高 Kill
      c.io.kill.poke(true.B)
      
      // 检查：monitorVal 应该被 driveManaged 强制拉低为 false
      // 尽管代码里写了 io.valid := true.B
      c.io.monitorVal.expect(false.B) 
      
      c.clock.step()

      // 3. Check Dead
      c.io.kill.poke(false.B)
      c.io.isActive.expect(false.B) // 应该变回 idle
      c.io.threadPC.expect(0.U)     // PC 归零

      // 4. Check No Callback
      c.io.callbackHit.expect(false.B)
      
      println("=== Abort Test Passed ===\n")
    }
  }
}