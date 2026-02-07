package HwOS.synth

import chisel3._
import chisel3.util._
import HwOS.kernel._
import HwOS.kernel.drivers._
import _root_.circt.stage.ChiselStage // 或者 import chisel3.stage.ChiselStage，取决于你的 Chisel 版本

object synthMultiCore extends App {
  // 2. 在 buildArgs 中移除那个报错的参数
  val buildArgs = Array("--target-dir", "generated", "--full-stacktrace") 
  
  println("Generating Verilog for PipelineDebugTop (as SimpleTop)...")

  ChiselStage.emitSystemVerilogFile(
    {
      val t = new MultiCoreGpuModule(8)
      t
    },
    buildArgs,
    // 3. 通过 annotations 传递 firtool 参数
    firtoolOpts = Array(
      "--lowering-options=disallowLocalVariables,disallowPackedArrays,locationInfoStyle=none,disallowPortDeclSharing"
      // 如果你的工具链非常老，可以加上这个强制不输出 SV 关键字的开关
      //"--disable-all-randomization"
    )
  )
}