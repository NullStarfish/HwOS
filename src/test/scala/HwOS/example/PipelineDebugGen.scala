package HwOS.example


import chisel3._
import HwOS.kernel._ // 导入 PipelineModule 和 Kernel 相关定义
import HwOS.kernel.drivers._
import _root_.circt.stage.ChiselStage

// 1. 定义顶层封装模块
class PipelineDebugTop extends PipelineModule {
  // [关键] 将生成的 Verilog 模块名强制改为 "SimpleTop"
  // 这样 Verilator 会生成 VSimpleTop.h，你的 HwOSgdb.cpp (依赖 VSimpleTop) 就可以直接复用了
  override val desiredName = "SimpleTop"

  // 2. 挂载 DPI Monitor
  // PipelineModule 中已经实例化了 val kernel = new Kernel()
  // 这里我们需要显式调用 attachMonitor 来植入 DPI 探针
  kernel.attachMonitor()
}

// 3. 编写生成器 App
object PipelineDebugGen extends App {
  // 指定输出目录为 generated
  val buildArgs = Array("--target-dir", "generated", "--full-stacktrace")
  println("Generating Verilog for PipelineDebugTop (as SimpleTop)...")

  // 用于捕获 Module 实例以访问 kernel
  var topCaptured: PipelineDebugTop = null

  // 发射 SystemVerilog
  ChiselStage.emitSystemVerilogFile(
    {
      val t = new PipelineDebugTop
      topCaptured = t
      t
    },
    buildArgs
  )

  // 4. 导出符号表 (hwos.symbols)
  if (topCaptured != null) {
    // 确保目录存在
    new java.io.File("generated").mkdirs()
    // 导出线程和 Step 的符号信息，供 HwOSgdb 加载
    topCaptured.kernel.dumpSymbolTable("generated/hwos.symbols")
    println("[Success] Symbol table dumped to generated/hwos.symbols")
  }

  println("Done. You can now re-compile your C++ testbench.")
}