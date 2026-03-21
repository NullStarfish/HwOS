package HwOS.kernel

import HwOS.kernel.HwOSLanguage._
import HwOS.kernel.thread.step.EdgeAction
import HwOS.kernel.thread.ThreadDebugApi
import HwOS.kernel.function.HwFunction
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

  it should "reject Call(HwInline) segments that omit explicit Return()" in {
    val ex = intercept[Exception] {
      class MissingInlineReturnModule extends Module {
        implicit val kernel: Kernel = new Kernel()

        object Init extends HwProcess("Init") {
          val worker = createThread("Worker")

          private def badSegment: HwInline[Unit] = HwInline.thread("BadCallSegment") { t =>
            t.Step("Body") {}
            ()
          }

          override def entry(): Unit = {
            worker.entry {
              SysCall.Call(badSegment, "AfterBadCall")
              worker.Step("AfterBadCall") {}
              SysCall.Return()
            }
          }
        }

        Init.build()
      }

      _root_.circt.stage.ChiselStage.emitCHIRRTL(new MissingInlineReturnModule)
    }

    assert(ex.getMessage.contains("BadCallSegment"))
    assert(ex.getMessage.contains("no explicit SysCall.Return()"))
  }

  it should "reject Call(HwFunction) bodies that omit explicit Return()" in {
    val ex = intercept[Exception] {
      class MissingFunctionReturnModule extends Module {
        implicit val kernel: Kernel = new Kernel()

        object Init extends HwProcess("Init") {
          val worker = createThread("Worker")

          val badFn = HwFunction.thread("BadFn") { t =>
            t.Step("Body") {}
            ()
          }

          override def entry(): Unit = {
            worker.entry {
              SysCall.Inline(badFn.Invoke("AfterBadFn"))
              worker.Step("AfterBadFn") {}
              SysCall.Return()
            }
          }
        }

        Init.build()
      }

      _root_.circt.stage.ChiselStage.emitCHIRRTL(new MissingFunctionReturnModule)
    }

    assert(ex.getMessage.contains("BadFn"))
    assert(ex.getMessage.contains("must contain an explicit SysCall.Return()"))
  }

  it should "route multiple explicit Return sites in one Call to the same continuation" in {
    class MultiReturnCallModule extends Module {
      val io = IO(new Bundle {
        val chooseAlt = Input(Bool())
        val out = Output(UInt(8.W))
        val done = Output(Bool())
      })

      io.out := DontCare
      io.done := DontCare

      implicit val kernel: Kernel = new Kernel()

      object Init extends HwProcess("Init") {
        val worker = createThread("Worker")
        val outReg = RegInit(0.U(8.W))

        private def multiExit: HwInline[Unit] = HwInline.thread("MultiExit") { t =>
          t.Step("ChooseExit") {
            when(io.chooseAlt) {
              outReg := 2.U
              t.jump("ExitB")
            }.otherwise {
              outReg := 1.U
              t.jump("ExitA")
            }
          }
          t.Step("ExitA") {
            SysCall.Return()
          }
          t.Step("ExitB") {
            SysCall.Return()
          }
          ()
        }

        override def entry(): Unit = {
          worker.entry {
            SysCall.Call(multiExit, "AfterCall")
            worker.Step("AfterCall") {
              outReg := outReg + 10.U
            }
            SysCall.Return()
          }

          val daemon = createLogic("Daemon")
          daemon.run {
            when(!worker.active && !worker.done) {
              SysCall.Inline(SysCall.start(worker))
            }
            io.out := outReg
            io.done := worker.done
          }
        }
      }

      Init.build()
    }

    simulate(new MultiReturnCallModule) { c =>
      c.reset.poke(true.B)
      c.io.chooseAlt.poke(false.B)
      c.clock.step()
      c.reset.poke(false.B)

      var cycles = 0
      while (c.io.done.peek().litValue == 0 && cycles < 20) {
        c.clock.step()
        cycles += 1
      }

      c.io.done.expect(true.B)
      c.io.out.expect(11.U)
    }

    simulate(new MultiReturnCallModule) { c =>
      c.reset.poke(true.B)
      c.io.chooseAlt.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      var cycles = 0
      while (c.io.done.peek().litValue == 0 && cycles < 20) {
        c.clock.step()
        cycles += 1
      }

      c.io.done.expect(true.B)
      c.io.out.expect(12.U)
    }
  }

  it should "keep allowing natural fallthrough in SysCall.Inline thread segments" in {
    class FallthroughInlineModule extends Module {
      val io = IO(new Bundle {
        val out = Output(UInt(8.W))
        val done = Output(Bool())
      })

      io.out := DontCare
      io.done := DontCare

      implicit val kernel: Kernel = new Kernel()

      object Init extends HwProcess("Init") {
        val worker = createThread("Worker")
        val outReg = RegInit(0.U(8.W))

        private def helper: HwInline[Unit] = HwInline.thread("NaturalInline") { t =>
          t.Step("WriteOne") {
            outReg := 1.U
          }
          t.Step("WriteTwo") {
            outReg := outReg + 1.U
          }
          ()
        }

        override def entry(): Unit = {
          worker.entry {
            SysCall.Inline(helper)
            worker.Step("Finish") {
              outReg := outReg + 10.U
            }
            SysCall.Return()
          }

          val daemon = createLogic("Daemon")
          daemon.run {
            when(!worker.active && !worker.done) {
              SysCall.Inline(SysCall.start(worker))
            }
            io.out := outReg
            io.done := worker.done
          }
        }
      }

      Init.build()
    }

    simulate(new FallthroughInlineModule) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      var cycles = 0
      while (c.io.done.peek().litValue == 0 && cycles < 20) {
        c.clock.step()
        cycles += 1
      }

      c.io.done.expect(true.B)
      c.io.out.expect(12.U)
    }
  }

  it should "execute call-site return edge blocks before the continuation step" in {
    class CallSiteEdgeModule extends Module {
      val io = IO(new Bundle {
        val out = Output(UInt(8.W))
        val done = Output(Bool())
      })

      io.out := DontCare
      io.done := DontCare

      implicit val kernel: Kernel = new Kernel()

      object Init extends HwProcess("Init") {
        val worker = createThread("Worker")
        val outReg = RegInit(0.U(8.W))

        private def callee: HwInline[Unit] = HwInline.thread("CallSiteEdgeCallee") { t =>
          t.Step("Produce") {
            outReg := 1.U
          }
          SysCall.Return()
          ()
        }

        override def entry(): Unit = {
          worker.entry {
            val call = SysCall.CallSite(callee, "AfterCall")
            call.edge.add {
              outReg := outReg + 10.U
            }
            SysCall.Call(call)
            worker.Step("AfterCall") {
              outReg := outReg + 1.U
            }
            SysCall.Return()
          }

          val daemon = createLogic("Daemon")
          daemon.run {
            when(!worker.active && !worker.done) {
              SysCall.Inline(SysCall.start(worker))
            }
            io.out := outReg
            io.done := worker.done
          }
        }
      }

      Init.build()
    }

    simulate(new CallSiteEdgeModule) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      var cycles = 0
      while (c.io.done.peek().litValue == 0 && cycles < 20) {
        c.clock.step()
        cycles += 1
      }

      c.io.done.expect(true.B)
      c.io.out.expect(12.U)
    }
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

  it should "attach call-site continuation and return-edge patch to Return during pre-analysis" in {
    class ReturnCallSiteSummaryModule extends Module {
      implicit val kernel: Kernel = new Kernel()

      object Init extends HwProcess("Init") {
        val worker = createThread("Worker")

        override def entry(): Unit = {
          val callee = HwInline.thread("CalleeWithReturn") { t =>
            t.Step("InnerStep") {
              SysCall.Return()
            }
            ()
          }

          worker.entry {
            SysCall.Call(callee, "AfterCall")
            worker.Step("AfterCall") {
              SysCall.Return()
            }
          }

          val debugWorker = worker.asInstanceOf[ThreadDebugApi]
          val returns = debugWorker.debugStepActions.getOrElse("InnerStep", Seq.empty).collect { case ret: EdgeAction.Return => ret }
          assert(returns.length == 1)
          assert(returns.head.returnTarget.contains("AfterCall"))
          assert(returns.head.returnEdgePatch.nonEmpty)
          assert(returns.head.returnEdgePatch.flatMap(_.continuationTarget).contains("AfterCall"))
        }
      }

      Init.build()
    }

    _root_.circt.stage.ChiselStage.emitCHIRRTL(new ReturnCallSiteSummaryModule)
  }

  it should "keep nested call-site return-edge patches isolated during pre-analysis" in {
    class NestedReturnCallSiteModule extends Module {
      implicit val kernel: Kernel = new Kernel()

      object Init extends HwProcess("Init") {
        val worker = createThread("Worker")

        override def entry(): Unit = {
          val inner = HwInline.thread("InnerNested") { t =>
            t.Step("InnerReturnStep") {
              SysCall.Return()
            }
            ()
          }

          val outer = HwInline.thread("OuterNestedCall") { t =>
            SysCall.Call(inner, "OuterResume")
            t.Step("OuterResume") {
              SysCall.Return()
            }
            ()
          }

          worker.entry {
            SysCall.Call(outer, "AfterOuter")
            worker.Step("AfterOuter") {
              SysCall.Return()
            }
          }
          val debugWorker = worker.asInstanceOf[ThreadDebugApi]
          val innerReturns = debugWorker.debugStepActions.getOrElse("InnerReturnStep", Seq.empty).collect { case ret: EdgeAction.Return => ret }
          val outerReturns = debugWorker.debugStepActions.getOrElse("OuterResume", Seq.empty).collect { case ret: EdgeAction.Return => ret }

          assert(innerReturns.length == 1)
          assert(outerReturns.length == 1)
          assert(innerReturns.head.returnTarget.contains("OuterResume"))
          assert(innerReturns.head.returnEdgePatch.flatMap(_.continuationTarget).contains("OuterResume"))
          assert(outerReturns.head.returnTarget.contains("AfterOuter"))
          assert(outerReturns.head.returnEdgePatch.flatMap(_.continuationTarget).contains("AfterOuter"))
        }
      }

      Init.build()
    }

    _root_.circt.stage.ChiselStage.emitCHIRRTL(new NestedReturnCallSiteModule)
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
