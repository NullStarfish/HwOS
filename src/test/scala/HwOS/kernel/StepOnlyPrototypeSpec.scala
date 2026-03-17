package HwOS.kernel

import HwOS.kernel.control.StepOnlyPrototype
import HwOS.kernel.system.RuntimeLifecycle
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class StepOnlyPrototypeModule extends Module {
  val io = IO(new Bundle {
    val mark = Output(UInt(8.W))
    val pc = Output(UInt(8.W))
    val active = Output(Bool())
    val done = Output(Bool())
    val entityTag = Output(UInt(8.W))
    val step1Addr = Output(UInt(8.W))
    val step2Standalone = Output(Bool())
    val stateTableCount = Output(UInt(8.W))
    val codeTableCount = Output(UInt(8.W))
    val bindingCount = Output(UInt(8.W))
    val runtimeStateBindingOk = Output(Bool())
    val cursorBindingOk = Output(Bool())
  })

  io.mark := DontCare
  io.pc := DontCare
  io.active := DontCare
  io.done := DontCare
  io.entityTag := DontCare
  io.step1Addr := DontCare
  io.step2Standalone := DontCare
  io.stateTableCount := DontCare
  io.codeTableCount := DontCare
  io.bindingCount := DontCare
  io.runtimeStateBindingOk := DontCare
  io.cursorBindingOk := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    private val markReg = this.own(RegInit(0.U(8.W)))

    override def entry(): Unit = {
      val prog = new StepOnlyPrototype.Program("StepOnly")
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
      io.entityTag := runtime.entityTagReg
      io.step1Addr := step1.address.U
      io.step2Standalone := step2.standalone.B
      io.stateTableCount := kernel.addressSpace.stateTableEntries.length.U
      io.codeTableCount := kernel.addressSpace.codeTableEntries.length.U
      io.bindingCount := kernel.addressSpace.bindingTableEntries.length.U
      io.runtimeStateBindingOk := (runtime.binding.runtimeStateObject eq runtimeStateObject).B
      io.cursorBindingOk := (runtime.binding.cursorObject eq runtime.cursor.addressObject).B
    }
  }

  Init.build()
}

class StepOnlyWaitPrototypeModule extends Module {
  val io = IO(new Bundle {
    val allow = Input(Bool())
    val mark = Output(UInt(8.W))
    val pc = Output(UInt(8.W))
    val active = Output(Bool())
    val step1Addr = Output(UInt(8.W))
    val step2Standalone = Output(Bool())
  })

  io.mark := DontCare
  io.pc := DontCare
  io.active := DontCare
  io.step1Addr := DontCare
  io.step2Standalone := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    private val markReg = this.own(RegInit(0.U(8.W)))

    override def entry(): Unit = {
      val prog = new StepOnlyPrototype.Program("StepOnlyWait")
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
    }
  }

  Init.build()
}

class StepOnlyPrototypeSpec extends AnyFlatSpec {
  "StepOnlyPrototype" should "let hijack(label) return an action that inlines the target step body and removes its standalone slot" in {
    simulate(new StepOnlyPrototypeModule) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      c.io.step2Standalone.expect(false.B)
      c.io.active.expect(true.B)
      c.io.done.expect(false.B)
      c.io.entityTag.expect(0.U)
      c.io.codeTableCount.expect(1.U)
      c.io.bindingCount.expect(1.U)
      c.io.runtimeStateBindingOk.expect(true.B)
      c.io.cursorBindingOk.expect(true.B)

      c.clock.step()
      c.io.mark.expect(2.U)
      c.io.pc.expect(c.io.step1Addr.peek())
    }
  }

  it should "stall on the outer standalone step when waitCondition appears inside a hijacked action" in {
    simulate(new StepOnlyWaitPrototypeModule) { c =>
      c.io.allow.poke(false.B)
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      c.io.step2Standalone.expect(false.B)
      c.io.active.expect(true.B)

      c.clock.step()
      c.io.mark.expect(1.U)
      c.io.pc.expect(c.io.step1Addr.peek())

      c.io.allow.poke(true.B)
      c.clock.step()
      c.io.mark.expect(3.U)
    }
  }
}
