package HwOS.kernel

import HwOS.kernel.HwOSLanguage._
import HwOS.kernel.function.HwInline
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.SysCall
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class ReturnProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  val worker = createThread("Worker")
  val out = (RegInit(0.U(8.W)))

  private def inner: HwInline[Unit] = HwInline.thread("Inner") { t =>
    t.Step("InnerWrite") {
      out  :=  7.U
    }
    SysCall.Return()
    t.Step("InnerDead") {
      out  :=  99.U
    }
    ()
  }

  private def innerMost: HwInline[Unit] = HwInline.thread("InnerMost") { t =>
    t.Step("InnerMostWrite") {
      out  :=  10.U
    }
    SysCall.Return()
    t.Step("InnerMostDead") {
      out  :=  77.U
    }
    ()
  }

  private def middle: HwInline[Unit] = HwInline.thread("Middle") { t =>
    t.Step("MiddleWrite") {
      out  :=  5.U
    }
    SysCall.Call(innerMost, "MiddleResume")
    t.Step("MiddleResume") {
      out  :=  66.U
    }
    SysCall.Return()
    ()
  }

  private def outer: HwInline[Unit] = HwInline.thread("Outer") { t =>
    t.Step("OuterInit") {
      out  :=  1.U
    }
    SysCall.Call(inner, "OuterResume")
    t.Step("OuterResume") {
      out  :=  out + 1.U
    }
    ()
  }

  private def outerNested: HwInline[Unit] = HwInline.thread("OuterNested") { t =>
    t.Step("OuterNestedInit") {
      out  :=  2.U
    }
    SysCall.Call(middle, "OuterNestedResume")
    t.Step("OuterNestedResume") {
      out  :=  out + 1.U
    }
    ()
  }

  override def entry(): Unit = {
    worker.entry {
      SysCall.Inline(outer)
      SysCall.Inline(outerNested)
      SysCall.Return()
    }
  }
}

class ReturnModule extends Module {
  val io = IO(new Bundle {
    val out = Output(UInt(8.W))
    val done = Output(Bool())
  })

  io.out := DontCare
  io.done := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    (io.out)
    (io.done)

    val proc = spawn(new ReturnProcess("ReturnProc"))
    val daemon = createLogic("Daemon")

    override def entry(): Unit = {
      daemon.run {
        when(!proc.worker.active && !proc.worker.done) {
          SysCall.Inline(SysCall.start(proc.worker))
        }
        io.out  :=  proc.out
        io.done  :=  proc.worker.done
      }
    }
  }

  Init.build()
}

class EdgePatchedReturnModule extends Module {
  val io = IO(new Bundle {
    val allow = Input(Bool())
    val out = Output(UInt(8.W))
    val done = Output(Bool())
    val pc = Output(UInt(8.W))
  })

  io.out := DontCare
  io.done := DontCare
  io.pc := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    val worker = createThread("Worker")
    val outReg = RegInit(0.U(8.W))

    override def entry(): Unit = {
      worker.entry {
        worker.Step("WaitThenReturn") {
          outReg := 1.U
          worker.waitCondition(io.allow)
          worker.Prev.edge.add {
            SysCall.Return()
          }
          when(io.allow) {
            outReg := 2.U
          }
        }
        worker.Step("Dead") {
          outReg := 9.U
        }
      }

      val daemon = createLogic("Daemon")
      daemon.run {
        when(!worker.active && !worker.done) {
          SysCall.Inline(SysCall.start(worker))
        }
        io.out := outReg
        io.done := worker.done
        io.pc := worker.pc
      }
    }
  }

  Init.build()
}

class ReturnSpec extends AnyFlatSpec {
  "SysCall.Return" should "jump to the explicit caller continuation when Call binds a return target" in {
    simulate(new ReturnModule) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      var cycles = 0
      while (c.io.done.peek().litValue == 0 && cycles < 40) {
        c.clock.step()
        cycles += 1
      }

      c.io.done.expect(true.B)
      c.io.out.expect(67.U)
    }
  }

  it should "reject Return() inside SysCall.Inline(...)" in {
    val ex = intercept[Exception] {
      class IllegalInlineReturnModule extends Module {
        implicit val kernel: Kernel = new Kernel()

        object Init extends HwProcess("Init") {
          val worker = createThread("Worker")

          override def entry(): Unit = {
            worker.entry {
              SysCall.Inline(HwInline.thread("BadInline") { _ =>
                SysCall.Return()
                ()
              })
            }
          }
        }

        Init.build()
      }

      _root_.circt.stage.ChiselStage.emitCHIRRTL(new IllegalInlineReturnModule)
    }

    assert(ex.getMessage.contains("illegal inside Inline"))
  }

  it should "mark returning-step information in pre-analysis before lowering runs" in {
    class ReturnEffectSummaryModule extends Module {
      implicit val kernel: Kernel = new Kernel()

      object Init extends HwProcess("Init") {
        override def entry(): Unit = {
          val prog = new HwOS.kernel.control.ThreadStepDemo.Program("ReturnSummary")
          prog.Step("Arm") {
            SysCall.Return()
          }

          prog.preAnalyzeOnly()
          assert(prog.stepEffects("Arm").contains("return"))
          assert(prog.hasReturningStep)
        }
      }

      Init.build()
    }

    _root_.circt.stage.ChiselStage.emitCHIRRTL(new ReturnEffectSummaryModule)
  }

  it should "allow Prev.edge.add(Return()) to patch the pass edge created after waitCondition" in {
    simulate(new EdgePatchedReturnModule) { c =>
      c.reset.poke(true.B)
      c.io.allow.poke(false.B)
      c.clock.step()
      c.reset.poke(false.B)

      c.clock.step()
      c.clock.step()
      c.io.out.expect(1.U)
      c.io.done.expect(false.B)

      c.io.allow.poke(true.B)
      c.clock.step()
      c.io.out.expect(2.U)
      c.io.done.expect(true.B)
    }
  }
}
