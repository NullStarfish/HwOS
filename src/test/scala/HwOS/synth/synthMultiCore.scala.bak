package HwOS.synth

import chisel3._
import chisel3.util._
import HwOS.kernel._
import HwOS.kernel.drivers._
import _root_.circt.stage.ChiselStage 

object synthMultiCore extends App {
  val buildArgs = Array("--target-dir", "generated", "--full-stacktrace") 
  
  println("Generating Verilog for PipelineDebugTop (as SimpleTop)...")

  ChiselStage.emitSystemVerilogFile(
    {
      val t = new MultiCoreGpuModule(8)
      t
    },
    buildArgs,
    firtoolOpts = Array(
      "--lowering-options=disallowLocalVariables,disallowPackedArrays,locationInfoStyle=none,disallowPortDeclSharing"

    )
  )
}