package HwOS.kernel

import HwOS.kernel.control.ThreadStepDemo
import HwOS.kernel.system.RuntimeLifecycle
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class ThreadStepDemoModule extends Module {
  val io = IO(new Bundle {
    val mark = Output(UInt(8.W))
    val pc = Output(UInt(8.W))
    val active = Output(Bool())
    val done = Output(Bool())
    val step1Addr = Output(UInt(8.W))
    val step2Standalone = Output(Bool())
    val stateTableCount = Output(UInt(8.W))
    val codeTableCount = Output(UInt(8.W))
    val bindingCount = Output(UInt(8.W))
    val runtimeStateBindingOk = Output(Bool())
    val cursorBindingOk = Output(Bool())
    val step1HijackDetected = Output(Bool())
  })

  io.mark := DontCare
  io.pc := DontCare
  io.active := DontCare
  io.done := DontCare
  io.step1Addr := DontCare
  io.step2Standalone := DontCare
  io.stateTableCount := DontCare
  io.codeTableCount := DontCare
  io.bindingCount := DontCare
  io.runtimeStateBindingOk := DontCare
  io.cursorBindingOk := DontCare
  io.step1HijackDetected := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    private val markReg = (RegInit(0.U(8.W)))

    override def entry(): Unit = {
      val prog = new ThreadStepDemo.Program("ThreadStepDemo")
      val action = prog.hijack("step2")

      prog.Step("step1") {
        markReg := 1.U
        when(true.B) {
          action()
        }
      }

      prog.Step("step2") {
        markReg := 2.U
      }

      val runtime = prog.build(this)
      val step1 = prog.layout.find(_.name == "step1").get
      val step2 = prog.layout.find(_.name == "step2").get
      val runtimeStateObject = kernel.addressSpace.getAddressObject(runtime.stateReg).get

      io.mark := markReg
      io.pc := runtime.cursor.reg
      io.active := runtime.stateReg === RuntimeLifecycle.Running.U(runtime.stateReg.getWidth.W)
      io.done := runtime.stateReg === RuntimeLifecycle.Done.U(runtime.stateReg.getWidth.W)
      io.step1Addr := step1.address.U
      io.step2Standalone := step2.standalone.B
      io.stateTableCount := kernel.addressSpace.stateTableEntries.length.U
      io.codeTableCount := kernel.addressSpace.codeTableEntries.length.U
      io.bindingCount := kernel.addressSpace.bindingTableEntries.length.U
      io.runtimeStateBindingOk := (runtime.binding.runtimeStateObject eq runtimeStateObject).B
      io.cursorBindingOk := (runtime.binding.cursorObject eq runtime.cursor.addressObject).B
      io.step1HijackDetected := prog.stepEffects("step1").contains("hijack").B
    }
  }

  Init.build()
}

class ThreadStepDemoWaitModule extends Module {
  val io = IO(new Bundle {
    val allow = Input(Bool())
    val mark = Output(UInt(8.W))
    val pc = Output(UInt(8.W))
    val active = Output(Bool())
    val step1Addr = Output(UInt(8.W))
    val step2Standalone = Output(Bool())
    val step1HijackDetected = Output(Bool())
    val step2WaitDetected = Output(Bool())
  })

  io.mark := DontCare
  io.pc := DontCare
  io.active := DontCare
  io.step1Addr := DontCare
  io.step2Standalone := DontCare
  io.step1HijackDetected := DontCare
  io.step2WaitDetected := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    private val markReg = (RegInit(0.U(8.W)))

    override def entry(): Unit = {
      val prog = new ThreadStepDemo.Program("ThreadStepDemoWait")
      val action = prog.hijack("step2")

      prog.Step("step1") {
        markReg := 1.U
        action()
      }

      prog.Step("step2") {
        prog.waitCondition(io.allow)
        when(io.allow) {
          markReg := 3.U
        }
      }

      val runtime = prog.build(this)
      val step1 = prog.layout.find(_.name == "step1").get
      val step2 = prog.layout.find(_.name == "step2").get

      io.mark := markReg
      io.pc := runtime.cursor.reg
      io.active := runtime.stateReg === RuntimeLifecycle.Running.U(runtime.stateReg.getWidth.W)
      io.step1Addr := step1.address.U
      io.step2Standalone := step2.standalone.B
      io.step1HijackDetected := prog.stepEffects("step1").contains("hijack").B
      io.step2WaitDetected := prog.stepEffects("step2").contains("wait").B
    }
  }

  Init.build()
}

class ThreadStepDemoSpec extends AnyFlatSpec {
  "ThreadStepDemo.Program" should "derive hijack suppression directly from pre-analyzed step effects" in {
    class ThreadStepDemoPlanningModule extends Module {
      implicit val kernel: Kernel = new Kernel()

      object Init extends HwProcess("Init") {
        override def entry(): Unit = {
          val prog = new ThreadStepDemo.Program("PlanningDemo")
          val action = prog.hijack("step2")

          prog.Step("step1") {
            action()
          }

          prog.Step("step2") {}
          prog.Step("step3") {}

          prog.preAnalyzeOnly()
          assert(prog.stepEffects("step1").contains("hijack"))
          assert(prog.plannedStandaloneLabels == Seq("step1", "step3"))
        }
      }

      Init.build()
    }

    _root_.circt.stage.ChiselStage.emitCHIRRTL(new ThreadStepDemoPlanningModule)
  }

  "ThreadStepDemo" should "let hijack(label) return an action that inlines the target step body and removes its standalone slot" in {
    simulate(new ThreadStepDemoModule) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      c.io.step2Standalone.expect(false.B)
      c.io.active.expect(true.B)
      c.io.done.expect(false.B)
      c.io.codeTableCount.expect(1.U)
      c.io.bindingCount.expect(1.U)
      c.io.runtimeStateBindingOk.expect(true.B)
      c.io.cursorBindingOk.expect(true.B)
      c.io.step1HijackDetected.expect(true.B)

      c.clock.step()
      c.io.mark.expect(2.U)
      c.io.pc.expect(c.io.step1Addr.peek())
    }
  }

  it should "stall on the outer standalone step when waitCondition appears inside a hijacked action" in {
    simulate(new ThreadStepDemoWaitModule) { c =>
      c.io.allow.poke(false.B)
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      c.io.step2Standalone.expect(false.B)
      c.io.active.expect(true.B)
      c.io.step1HijackDetected.expect(true.B)
      c.io.step2WaitDetected.expect(true.B)

      c.clock.step()
      c.io.mark.expect(1.U)
      c.io.pc.expect(c.io.step1Addr.peek())

      c.io.allow.poke(true.B)
      c.clock.step()
      c.io.mark.expect(3.U)
    }
  }
}
