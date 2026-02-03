package HwOS.kernel

import chisel3._
import chisel3.util._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel._ 
import HwOS.kernel.drivers._

// ==============================================================================
// 1. 定义 Driver 和 IO
// ==============================================================================
class BlackboxIO extends Bundle {
  val req   = Input(Bool())
  val cmd   = Input(UInt(1.W))    // 0: Read, 1: Write
  val addr  = Input(UInt(32.W))
  val wdata = Input(UInt(32.W))
  
  val valid = Output(Bool())      
  val rdata = Output(UInt(32.W))
}

class BlackboxDriver(val io: BlackboxIO, kernel: Kernel) extends PhysicalDriver(
    DriverMeta("Blackbox", ScalarResource, 1, 1, 0, ConflictPolicies.Full_Mutex)
  ) {

  // FIX 1: 必须为 Wire/Port 类型的 IO 设置默认值
  // 否则在非 Step 激活期间，这些信号会悬空，导致 "sink not fully initialized"
  io.req   := false.B
  io.cmd   := 0.U
  io.addr  := 0.U
  io.wdata := 0.U

  def read(addr: UInt, callback: UInt => Unit): Unit = {
    ContextScope.current match {
      case ThreadCtx(t) => {
        t.Step("BB_Read_Req") {
          io.req   := true.B
          io.cmd   := 0.U 
          io.addr  := addr
        }
        t.Step("BB_Read_Wait") {
          t.waitAndAct(io.valid) {
            callback(io.rdata) 
          }
        }
      }
      case other => throw new Exception(s"Thread context required. Got: $other")
    }
  }

  def write(addr: UInt, data: UInt, callback: () => Unit): Unit = {
    ContextScope.current match {
      case ThreadCtx(t) => {
        t.Step("BB_Write_Req") {
          io.req   := true.B
          io.cmd   := 1.U 
          io.addr  := addr
          io.wdata := data
        }
        t.Step("BB_Write_Wait") {
          t.waitAndAct(io.valid) {
            callback()
          }
        }
      }
      case other => throw new Exception(s"Thread context required. Got: $other")
    }
  }
}

// ==============================================================================
// 2. 模拟一个有延迟的硬件 (Mock Hardware)
// ==============================================================================
class MockSlowDevice(delayCycles: Int) extends Module {
  val io = IO(new Bundle {
    // BlackboxIO 已经是 Slave 视角，不需要 Flipped
    val bus = new BlackboxIO 
  })

  val sIdle :: sBusy :: sDone :: Nil = Enum(3)
  val state = RegInit(sIdle)
  val counter = Reg(UInt(32.W))
  val fakeStorage = RegInit(0.U(32.W)) 

  io.bus.valid := false.B
  io.bus.rdata := fakeStorage

  switch (state) {
    is (sIdle) {
      when (io.bus.req) {
        state   := sBusy
        counter := delayCycles.U
        when (io.bus.cmd === 1.U) {
          fakeStorage := io.bus.wdata
        }
      }
    }
    is (sBusy) {
      counter := counter - 1.U
      when (counter === 0.U) {
        state := sDone
      }
    }
    is (sDone) {
      io.bus.valid := true.B
      state := sIdle 
    }
  }
}

// ==============================================================================
// 3. 测试顶层模块
// ==============================================================================
class BlackboxTestModule extends Module {
  val io = IO(new Bundle {
    val startTrigger = Input(Bool())
    val done         = Output(Bool())
    
    val pc           = Output(UInt(32.W))
    val resultReg    = Output(UInt(32.W)) 
    val writeDone    = Output(Bool())     
  })

  val kernel = new Kernel()
  val mockHw = Module(new MockSlowDevice(delayCycles = 5))
  val driver = new BlackboxDriver(mockHw.io.bus, kernel)
  kernel.mount(driver)

  class TestProcess(k: Kernel) extends HwProcess("TestProc", debugEnable = true, parent = None)(k) {
    val worker = createThread("WorkerThread")
    val dataRecv  = RegInit(0.U(32.W))
    val writeFlag = RegInit(false.B)

    when(io.startTrigger) { worker.start() }

    override def entry(): Unit = {
      worker.entry {
        
        // --- 1. 写操作测试 ---
        worker.Step("WRITE_TEST_MSG") {
           printf("[Thread] sending Write Request...\n")
        }
        
        // FIX 2: driver.write 必须放在 Step 之外！
        // 这样它的上下文才是 ThreadCtx，而不是 AtomicCtx
        driver.write(addr = 0x10.U, data = 0xCAFEBABEL.U, callback = () => {
           writeFlag := true.B
           printf("[Callback] Write Complete Callback triggered!\n")
        })

        // --- 2. 读操作测试 ---
        worker.Step("READ_TEST_MSG") {
           printf("[Thread] sending Read Request...\n")
        }

        // 同样，read 也要放在 Step 之外
        driver.read(addr = 0x10.U, callback = (data) => {
           dataRecv := data
           printf(p"[Callback] Read Complete! Got data: 0x${Hexadecimal(data)}\n")
        })
        
        // --- 3. 结果检查 ---
        worker.Step("CHECK") {
           when (dataRecv === 0xCAFEBABEL.U) {
              printf("[Thread] Data Verification SUCCESS!\n")
           } .otherwise {
              printf("[Thread] Data Verification FAILED! Got: 0x%x\n", dataRecv)
           }
        }

        worker.Step("EXIT") {
          worker.exit()
        }
      }
    }
    

  }

  val proc = new TestProcess(kernel)
  proc.build()

  io.done := proc.worker.done
  io.pc        := proc.worker.pc
  io.resultReg := proc.dataRecv
  io.writeDone := proc.writeFlag
}

// ==============================================================================
// 4. 测试用例
// ==============================================================================
// ==============================================================================
// 4. 测试用例 (修复版)
// ==============================================================================
class BlackboxDriverTest extends AnyFlatSpec {
  "BlackboxDriver" should "handle asynchronous latency using callbacks" in {
    simulate(new BlackboxTestModule) { c =>
      println("\n=== Blackbox Driver Async Test ===")

      // 1. Reset
      c.io.startTrigger.poke(false.B)
      c.reset.poke(true.B)
      c.clock.step(2)
      c.reset.poke(false.B)
      c.clock.step()

      // 2. Start
      println("[Test] Starting Thread...")
      c.io.startTrigger.poke(true.B)
      c.clock.step()
      c.io.startTrigger.poke(false.B)

      // --- Phase 1: Wait for Write ---
      println("[Test] Waiting for Write...")
      var timeout = 0
      while (!c.io.writeDone.peek().litToBoolean && timeout < 20) {
        c.clock.step()
        timeout += 1
      }
      c.io.writeDone.expect(true.B)
      println(s"[Test] Write Callback verified (latency: $timeout cycles).")

      // --- Phase 2: Wait for Read & Done ---
      println("[Test] Waiting for Read result and Thread Exit...")
      
      timeout = 0
      var readVerified = false
      var doneDetected = false
      
      // 我们设定一个最大 50 周期的窗口，在此期间检测数据和结束信号
      while (timeout < 50 && !doneDetected) {
         // 1. 实时检查数据是否回来
         if (!readVerified && c.io.resultReg.peek().litValue == 0xCAFEBABEL) {
             println(s"[Test] Read Callback verified at cycle $timeout. Data = 0xCAFEBABE")
             readVerified = true
         }
         
         // 2. 实时检查 Done 脉冲 (防止错过)
         if (c.io.done.peek().litToBoolean) {
             println(s"[Test] Done signal detected at cycle $timeout.")
             doneDetected = true
         }
         
         c.clock.step()
         timeout += 1
      }
      
      // 最后统一断言
      assert(readVerified, "Error: Read callback never triggered or data mismatch.")
      assert(doneDetected, "Error: Thread never finished (missed done pulse?).")
      
      println("=== Test Passed ===\n")
    }
  }
}