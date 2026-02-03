
package HwOS.kernel

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel._ 
import HwOS.kernel.drivers._

class ForwardingTestModule extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    val pc0   = Output(UInt(32.W))
    val pc1   = Output(UInt(32.W))
    val fwdData = Output(UInt(32.W)) // 观察 Slot 1 拿到的数据
  })

  val kernel = new Kernel()
  val phyRegs = RegInit(VecInit(Seq.fill(32)(0.U(32.W))))
  // 初始值：Reg[10] = 0
  phyRegs(10) := 0.U

  val meta = DriverMeta("Fwd_RegFile", VectorResource(32), read_clients=1, write_clients=1, fifo_depth=0)
  val fwdDriver = new ForwardingScoreboardDriver(phyRegs, kernel, meta, maxClients=2)
  kernel.mount(fwdDriver)

  class TestProcess(k: Kernel) extends HwProcess("CPU", debugEnable = true, parent = None)(k) {
    val slot0 = createThread("Inst0_Write")
    val slot1 = createThread("Inst1_Read")
    val res1  = RegInit(0.U(32.W))

    when(io.start) { slot0.start(); slot1.start() }

    override def entry(): Unit = {
      // Slot 0: 写入 0x666 到 Reg 10
      slot0.entry {
        fwdDriver.writeAtomic(10.U, 0x666.U) {
          slot0.exit()
        }
      }

      // Slot 1: 同时从 Reg 10 读取
      slot1.entry {
        fwdDriver.readAtomic(10.U) { data =>
          // 这里应该通过旁路拿到 0x666，而不是物理寄存器里的 0
          res1 := data
          slot1.pc := 2.U 
        }
        slot1.Step("Dummy") { }
        slot1.Step("Done") { slot1.exit() }
      }
    }
  }
  val proc = new TestProcess(kernel)
  proc.build()

  io.pc0 := proc.slot0.pc
  io.pc1 := proc.slot1.pc
  io.fwdData := proc.res1
}

class ForwardingDriverTest extends AnyFlatSpec {
  "ForwardingScoreboard" should "bypass data between instructions in the same cycle" in {
    simulate(new ForwardingTestModule) { c =>
      println("\n=== Forwarding Test ===")
      
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      // 同时启动
      c.io.start.poke(true.B)
      c.clock.step()
      c.io.start.poke(false.B)

      // --- Cycle 1 ---
      // 两个线程都在 PC=0
      // 组合逻辑判定：
      // Slot 0 (Write) -> 正常执行
      // Slot 1 (Read) -> 检测到 Slot 0 正在写同一个地址 -> 触发 Bypass -> 拿到 0x666 -> 不 Stall
      c.clock.step()

      // --- Cycle 2 ---
      // 如果 Forwarding 成功：
      // Slot 0 应该已经 exit 了 (PC=0)
      // Slot 1 应该跳到了 Done (PC=2)，且拿到了旁路数据
      println(s"[Result] PC0=${c.io.pc0.peek().litValue}, PC1=${c.io.pc1.peek().litValue}")
      println(s"[Result] Slot 1 Captured Data: 0x${c.io.fwdData.peek().litValue.toString(16)}")
      
      c.io.pc0.expect(0.U)
      c.io.pc1.expect(2.U) // 证明没被 Stall，直接走完
      c.io.fwdData.expect(0x666.U)

      println("=== Forwarding Test Passed ===\n")
    }
  }
}