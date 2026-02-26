package HwOS.quick_start
import chisel3._
import _root_.circt.stage.ChiselStage

object QuickStart extends App {
  println("🚀 正在将 HwOS 顶层模块编译为 Verilog...")
  
  // 导出 Verilog 到 generated 文件夹
  ChiselStage.emitSystemVerilogFile(
    new TopModule(),
    Array("--target-dir", "generated"),
    firtoolOpts = Array(
      "--lowering-options=disallowLocalVariables,disallowPackedArrays,locationInfoStyle=none,disallowPortDeclSharing"
    ) 
  )

  
  println("✅ Verilog 导出完成！请查看 generated/TopModule.v")
}