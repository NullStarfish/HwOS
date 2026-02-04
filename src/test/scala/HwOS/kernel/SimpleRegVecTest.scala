package HwOS.kernel

import chisel3._
import chisel3.util._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

// ==============================================================================
// 1. 极简测试模块
// ==============================================================================
class SimpleVecWriteModule extends Module {
  val io = IO(new Bundle {
    // Port A
    val enA   = Input(Bool())
    val addrA = Input(UInt(5.W))
    val dataA = Input(UInt(32.W))

    // Port B
    val enB   = Input(Bool())
    val addrB = Input(UInt(5.W))
    val dataB = Input(UInt(32.W))

    // Observe
    val out0  = Output(UInt(32.W))
    val out1  = Output(UInt(32.W))
  })

  // 定义寄存器堆
  val regs = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))

  // 逻辑 A: 写入逻辑 (模拟 Thread 0)
  when (io.enA) {
    regs(io.addrA) := io.dataA
  }

  // 逻辑 B: 写入逻辑 (模拟 Thread 1, 代码生成在后)
  when (io.enB) {
    regs(io.addrB) := io.dataB
  }

  io.out0 := regs(0)
  io.out1 := regs(1)
}

// ==============================================================================
// 2. 验证 Chisel 行为的测试用例
// ==============================================================================
class SimpleRegVecTest extends AnyFlatSpec {
  "Reg(Vec)" should "support concurrent writes to different indices from different when blocks" in {
    simulate(new SimpleVecWriteModule) { c =>
      println("\n=== Simple Vec Concurrent Write Test ===")
      
      // 1. Init
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)
      
      // 2. Drive Concurrent Writes
      // Port A -> Reg(0) = 0xAAAA
      // Port B -> Reg(1) = 0xBBBB
      println("[Test] Driving Port A: Addr=0, Data=0xAAAA")
      println("[Test] Driving Port B: Addr=1, Data=0xBBBB")
      
      c.io.enA.poke(true.B)
      c.io.addrA.poke(0.U)
      c.io.dataA.poke(0xAAAA.U)
      
      c.io.enB.poke(true.B)
      c.io.addrB.poke(1.U)
      c.io.dataB.poke(0xBBBB.U)
      
      c.clock.step()
      
      // 3. Check Results
      val r0 = c.io.out0.peek().litValue
      val r1 = c.io.out1.peek().litValue
      
      println(f"[Result] Reg(0) = 0x$r0%X (Expected 0xAAAA)")
      println(f"[Result] Reg(1) = 0x$r1%X (Expected 0xBBBB)")
      
      // 断言检查
      // 如果 Chisel "蠢"，这里其中一个会失败 (通常是先写的被后写的覆盖，或者反之)
      if (r0 == 0xAAAA && r1 == 0xBBBB) {
          println(">>> CONCLUSION: Chisel is SMART! It merged the logic correctly.")
      } else {
          println(">>> CONCLUSION: Chisel is DUMB! One write was lost.")
      }
      
      assert(r0 == 0xAAAA, "Port A write failed! (Overwritten by Port B logic?)")
      assert(r1 == 0xBBBB, "Port B write failed!")
    }
  }
}
