package HwOS.kernel

import HwOS.kernel.HwOSLanguage._
import HwOS.kernel.control.StructuredControl
import HwOS.kernel.function.HwFunction
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.SysCall
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class StructuredControlProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  val worker = createThread("Worker")
  val branchOut = this.own(RegInit(0.U(8.W)))
  val acc = this.own(RegInit(0.U(8.W)))
  val loopOut = this.own(RegInit(0.U(8.W)))
  val returnOut = this.own(RegInit(0.U(8.W)))

  override def entry(): Unit = {
    this.grant(branchOut, worker)
    this.grant(acc, worker)
    this.grant(loopOut, worker)
    this.grant(returnOut, worker)

    worker.entry {
      StructuredControl
        .If(worker, "Branch", 3.U > 1.U)(
          HwFunction.thread("BranchThen") { t =>
            t.Step("BranchThenWrite") {
              branchOut <== 7.U
            }
            SysCall.Call(SysCall.Return())
            t.Step("BranchThenUnreachable") {
              branchOut <== 99.U
            }
            ()
          },
        )
        .Else(
          HwFunction.thread("BranchElse") { t =>
            t.Step("BranchElseWrite") {
              branchOut <== 9.U
            }
            ()
          },
        )

      StructuredControl
        .If(worker, "ReturnProbe", 1.U === 1.U)(
          HwFunction.thread("ReturnThen") { t =>
            t.Step("ReturnArm") {
              returnOut <== 5.U
            }
            SysCall.Call(SysCall.Return())
            t.Step("ReturnDead") {
              returnOut <== 99.U
            }
            ()
          },
        )
        .End()

      StructuredControl.ForRange(worker, "Loop", start = 0, endExclusive = 6, width = 8) { (i, loop) =>
        HwFunction.thread("LoopBody") { t =>
          t.Step("LoopSkipEven") {
            when(i(0) === 0.U) {
              loop.Continue()
            }
          }
          t.Step("LoopAccumulate") {
            acc <== acc + i
            when(i === 5.U) {
              loop.Break()
            }
          }
          ()
        }
      }

      StructuredControl.While(worker, "WhileAcc", acc < 12.U) { loop =>
        HwFunction.thread("WhileBody") { t =>
          t.Step("WhileBump") {
            acc <== acc + 2.U
            when(acc >= 10.U) {
              loop.Break()
            }
          }
          ()
        }
      }

      worker.Step("CaptureLoopOut") {
        loopOut <== acc
      }

      worker.Step("Finish") {
        worker.exit()
      }
    }
  }
}

class StructuredControlModule extends Module {
  val io = IO(new Bundle {
    val branchOut = Output(UInt(8.W))
    val acc = Output(UInt(8.W))
    val loopOut = Output(UInt(8.W))
    val returnOut = Output(UInt(8.W))
    val done = Output(Bool())
  })

  io.branchOut := DontCare
  io.acc := DontCare
  io.loopOut := DontCare
  io.returnOut := DontCare
  io.done := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    this.own(io.branchOut)
    this.own(io.acc)
    this.own(io.loopOut)
    this.own(io.returnOut)
    this.own(io.done)

    val proc = spawn(new StructuredControlProcess("Structured"))
    val daemon = createLogic("Daemon")

    override def entry(): Unit = {
      this.grant(io.branchOut, daemon)
      this.grant(io.acc, daemon)
      this.grant(io.loopOut, daemon)
      this.grant(io.returnOut, daemon)
      this.grant(io.done, daemon)
      this.grantLifecycle(proc.worker, daemon)

      daemon.run {
        when(!proc.worker.active && !proc.worker.done) {
          SysCall.Call(SysCall.start(proc.worker))
        }
        io.branchOut <== proc.branchOut
        io.acc <== proc.acc
        io.loopOut <== proc.loopOut
        io.returnOut <== proc.returnOut
        io.done <== proc.worker.done
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
      c.io.acc.expect(13.U)
      c.io.loopOut.expect(13.U)
      c.io.returnOut.expect(5.U)
    }
  }
}
