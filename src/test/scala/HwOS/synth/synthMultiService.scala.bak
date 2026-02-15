package HwOS.synth
import HwOS.kernel._
import _root_.circt.stage.{ChiselStage, FirtoolOption} // 1. 引入 FirtoolOption

object synthMultiService extends App {
  val buildArgs = Array("--target-dir", "generated", "--full-stacktrace") 
  
  println("Generating Verilog for PipelineDebugTop (as SimpleTop)...")

  ChiselStage.emitSystemVerilogFile(
    {
      val t = new PipelineModule
      t
    },
    buildArgs,
    firtoolOpts = Array(
      "--lowering-options=disallowLocalVariables,disallowPackedArrays,locationInfoStyle=none,disallowPortDeclSharing"
    )
  )
}