package HwOS.synth

import chisel3._
import chisel3.util._
import HwOS.kernel._
import HwOS.kernel.drivers._
import _root_.circt.stage.ChiselStage // 或者 import chisel3.stage.ChiselStage，取决于你的 Chisel 版本
import _root_.HwOS.kernel.ForkNonBlockingModule

object synthFork extends App {
  val buildArgs = Array("--target-dir", "generated", "--full-stacktrace") 
  
  println("Generating Verilog for PipelineDebugTop (as SimpleTop)...")

  ChiselStage.emitSystemVerilogFile(
    {
      val t = new ForkNonBlockingModule
      t
    },
    buildArgs,
    firtoolOpts = Array(
      "--lowering-options=disallowLocalVariables,disallowPackedArrays,locationInfoStyle=none,disallowPortDeclSharing"
    )
  )
}