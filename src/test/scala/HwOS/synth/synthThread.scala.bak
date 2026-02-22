package HwOS.synth

import chisel3._
import chisel3.util._
import HwOS.kernel._
import HwOS.kernel.drivers._
import _root_.circt.stage.ChiselStage // 或者 import chisel3.stage.ChiselStage，取决于你的 Chisel 版本


object synthThread extends App {
    val buildArgs = Array("--target-dir", "synthtest", "--full-stacktrace")

    val verilog = _root_.circt.stage.ChiselStage.emitSystemVerilogFile(
        {
            val t = new ThreadTestModule
            t
        },
        buildArgs
    )
}