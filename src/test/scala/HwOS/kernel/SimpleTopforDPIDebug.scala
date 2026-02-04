package HwOS.example

import chisel3._
import chisel3.util._
import HwOS.kernel._
import _root_.circt.stage.ChiselStage // 或者 import chisel3.stage.ChiselStage，取决于你的 Chisel 版本

// ==========================================
// 1. 硬件模块定义 (Module)
// ==========================================
class SimpleTop extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    // io.done 依然保留端口定义，防止 C++ 编译报错，但我们不再依赖它的逻辑
    val done  = Output(Bool()) 
  })

  val kernel = new Kernel()

  class DemoProcess(k: Kernel) extends HwProcess("DemoProc", debugEnable = true, parent = None)(k) {
    val producer = createThread("Producer")
    val consumer = createThread("Consumer")
    val sharedCounter = RegInit(0.U(32.W))
    
    // 【修改】删除了 pDone, cDone 等锁存寄存器

    when(io.start) {
      producer.start()
      consumer.start()
    }

    override def entry(): Unit = {
      // --- Producer (0..9) ---
      producer.entry {
        producer.Step("Init") { sharedCounter := 0.U }
        for (i <- 1 to 8) {
          producer.Step(s"Count_$i") {
            sharedCounter := sharedCounter + 1.U
          }
        }
        producer.Step("Exit") { producer.exit() }
      }

      // --- Consumer (0..2) ---
      consumer.entry {
        consumer.Step("Wait_For_5") { consumer.waitCondition(sharedCounter >= 5.U) }
        consumer.Step("Action")     { /* ... */ }
        consumer.Step("Exit")       { consumer.exit() }
      }
      
      // 【修改】删除了 when(done) { ... } 的锁存逻辑
    }
  }

  val proc = new DemoProcess(kernel)
  proc.build()

  // io.done 不再用于判断结束，仅仅作为调试观察（或者是 false）
  // 这里直接输出瞬时状态（基本上永远不会同时为高，所以 C++ 侧不再用它做退出条件）
  io.done := proc.producer.done && proc.consumer.done

  kernel.attachMonitor()
}

// ==========================================
// 2. Verilog 生成器入口 (App)
// ==========================================
object SimpleTopMain extends App {
  // 指定生成目录为 generated
  val buildArgs = Array("--target-dir", "generated", "--full-stacktrace")
  println("Generating Verilog for SimpleTop...")
  val verilog = _root_.circt.stage.ChiselStage.emitSystemVerilogFile(
    new SimpleTop,
    buildArgs
  )
  println("Done. Files generated in ./generated/")
}