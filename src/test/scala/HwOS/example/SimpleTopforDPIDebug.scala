package HwOS.example

import chisel3._
import chisel3.util._
import HwOS.kernel._
import HwOS.kernel.drivers._
import _root_.circt.stage.ChiselStage 

class SimpleTop extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    val done  = Output(Bool()) 
  })

  val kernel = new Kernel()

  class DemoProcess(k: Kernel) extends HwProcess("DemoProc", debugEnable = true, parent = None)(k) {
    val producer = createThread("Producer")
    val consumer = createThread("Consumer")
    val sharedCounter = RegInit(0.U(32.W))
    

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
      
    }
  }

  val proc = new DemoProcess(kernel)
  proc.build()

  io.done := proc.producer.done && proc.consumer.done

  kernel.attachMonitor()
}


object SimpleTopMain extends App {
  val buildArgs = Array("--target-dir", "generated", "--full-stacktrace")
  println("Generating Verilog for SimpleTop...")

  var topCaptured: SimpleTop = null

  val verilog = _root_.circt.stage.ChiselStage.emitSystemVerilogFile(
    {
      val t = new SimpleTop
      topCaptured = t 
      t 
    },
    buildArgs
  )

  println("Done. Files generated in ./generated/")

  if (topCaptured != null) {
    topCaptured.kernel.dumpSymbolTable("generated/hwos.symbols")
  }
}