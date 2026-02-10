package HwOS.example


import chisel3._
import HwOS.kernel._ // 导入 PipelineModule 和 Kernel 相关定义
import HwOS.kernel.drivers._
import _root_.circt.stage.ChiselStage

class PipelineDebugTop extends PipelineModule {
  
  override val desiredName = "SimpleTop"

  // PipelineModule 中已经实例化了 val kernel = new Kernel()
  // 这里我们需要显式调用 attachMonitor 来植入 DPI 探针
  kernel.attachMonitor()
}

object PipelineDebugGen extends App {
  // 指定输出目录为 generated
  val buildArgs = Array("--target-dir", "generated", "--full-stacktrace")
  println("Generating Verilog for PipelineDebugTop (as SimpleTop)...")

  // 用于捕获 Module 实例以访问 kernel
  var topCaptured: PipelineDebugTop = null

  ChiselStage.emitSystemVerilogFile(
    {
      val t = new PipelineDebugTop
      topCaptured = t
      t
    },
    buildArgs
  )

  if (topCaptured != null) {
    new java.io.File("generated").mkdirs()
    topCaptured.kernel.dumpSymbolTable("generated/hwos.symbols")
    println("[Success] Symbol table dumped to generated/hwos.symbols")
  }

  println("Done. You can now re-compile your C++ testbench.")
}