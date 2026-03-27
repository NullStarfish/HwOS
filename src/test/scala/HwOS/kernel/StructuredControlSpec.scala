package HwOS.kernel

import HwOS.kernel.HwOSLanguage._
import HwOS.kernel.control.StructuredControl
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.SysCall
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class StructuredControlProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  val worker = createThread("Worker")
  val branchOut = (RegInit(0.U(8.W)))
  val elifOut = (RegInit(0.U(8.W)))
  val acc = (RegInit(0.U(8.W)))
  val loopOut = (RegInit(0.U(8.W)))
  val returnOut = (RegInit(0.U(8.W)))
  val actionOut = (RegInit(0.U(8.W)))

  override def entry(): Unit = {
    worker.entry {
      StructuredControl
        .If(worker, "Branch", 3.U > 1.U) {
          worker.Step("BranchThenWrite") {
            branchOut  :=  7.U
          }
        }
        .Else {
          worker.Step("BranchElseWrite") {
            branchOut  :=  9.U
          }
        }

      StructuredControl
        .If(worker, "ElifChain", 0.U === 1.U) {
          worker.Step("ElifThenWrite") {
            elifOut  :=  1.U
          }
        }
        .ElseIf(2.U === 2.U) {
          worker.Step("ElifMidWrite") {
            elifOut  :=  2.U
          }
        }
        .Else {
          worker.Step("ElifElseWrite") {
            elifOut  :=  3.U
          }
        }

      StructuredControl
        .If(worker, "ActionBranch", 0.U === 1.U) {
          worker.Step("ActionThenWrite") {
            actionOut := 1.U
          }
        }
        .ElseIf(2.U === 2.U) {
          worker.Step("ActionElifWrite") {
            actionOut := 3.U
          }
        }
        .Else {
          worker.Step("ActionElseWrite") {
            actionOut := 9.U
          }
        }

      StructuredControl.ForRange(worker, "Loop", start = 0, endExclusive = 6, width = 8) { (i, loop) =>
        worker.Step("LoopSkipEven") {
          when(i(0) === 0.U) {
            loop.Continue()
          }
        }
        worker.Step("LoopAccumulate") {
          acc  :=  acc + i
          when(i === 5.U) {
            loop.Break()
          }
        }
      }

      StructuredControl.While(worker, "WhileAcc", acc < 12.U) { loop =>
        worker.Step("WhileBump") {
          acc  :=  acc + 2.U
          when(acc >= 10.U) {
            loop.Break()
          }
        }
      }

      worker.Step("CaptureLoopOut") {
        loopOut  :=  acc
      }

      StructuredControl
        .If(worker, "ReturnProbe", 1.U === 1.U) {
          worker.Step("ReturnArm") {
            returnOut  :=  5.U
          }
          SysCall.Return()
          worker.Step("ReturnDead") {
            returnOut  :=  99.U
          }
        }
        .End()

      worker.Step("Finish") {}
      SysCall.Return()
    }
  }
}

class StructuredControlModule extends Module {
  val io = IO(new Bundle {
    val branchOut = Output(UInt(8.W))
    val elifOut = Output(UInt(8.W))
    val acc = Output(UInt(8.W))
    val loopOut = Output(UInt(8.W))
    val returnOut = Output(UInt(8.W))
    val actionOut = Output(UInt(8.W))
    val done = Output(Bool())
  })

  io.branchOut := DontCare
  io.elifOut := DontCare
  io.acc := DontCare
  io.loopOut := DontCare
  io.returnOut := DontCare
  io.actionOut := DontCare
  io.done := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    val proc = spawn(new StructuredControlProcess("Structured"))
    val daemon = createLogic("Daemon")

    override def entry(): Unit = {
      daemon.run {
        when(!proc.worker.active && !proc.worker.done) {
          SysCall.Inline(SysCall.start(proc.worker))
        }
        io.branchOut  :=  proc.branchOut
        io.elifOut  :=  proc.elifOut
        io.acc  :=  proc.acc
        io.loopOut  :=  proc.loopOut
        io.returnOut  :=  proc.returnOut
        io.actionOut  :=  proc.actionOut
        io.done  :=  proc.worker.done
      }
    }
  }

  Init.build()
}

class StructuredControlSpec extends AnyFlatSpec {
  "StructuredControl" should "lower if/else, for-range, while, return, break, and continue into working step control flow" in {
    simulate(new StructuredControlModule) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      var cycles = 0
      while (c.io.done.peek().litValue == 0 && cycles < 80) {
        c.clock.step()
        cycles += 1
      }

      c.io.done.expect(true.B)
      c.io.branchOut.expect(7.U)
      c.io.elifOut.expect(2.U)
      c.io.acc.expect(13.U)
      c.io.loopOut.expect(13.U)
      c.io.returnOut.expect(5.U)
      c.io.actionOut.expect(3.U)
    }
  }
}
