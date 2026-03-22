package HwOS.kernel

import HwOS.kernel.control.ThreadStepDemo
import HwOS.kernel.thread.RuntimeControlHostAdapter
import HwOS.kernel.thread.step.ControlProgramBuilder
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class ThreadRuntimeStructureModule extends Module {
  val io = IO(new Bundle {
    val compiledWithoutThread = Output(Bool())
    val runtimeBuilt = Output(Bool())
    val done = Output(Bool())
  })

  io.compiledWithoutThread := false.B
  io.runtimeBuilt := false.B
  io.done := false.B

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    override def entry(): Unit = {
      val builder = new ControlProgramBuilder("StandaloneProgram", kernel.addressSpace.createVirtualProgram("StandaloneProgram"))
      builder.defineStep("S0") {}
      builder.defineStep("S1") {}

      val compiled = builder.compiled()
      io.compiledWithoutThread := (compiled.standaloneIndices == Vector(0, 1)).B

      val host = new RuntimeControlHostAdapter(this, "StandaloneProgram")
      val runtime = host.materializeProgram(builder, compiled, HwOS.kernel.system.RuntimeLifecycle.Running)
      HwOS.kernel.thread.step.ThreadRuntimeLogic.lowerRuntime(builder, runtime.compiledProgram, host)
      io.runtimeBuilt := true.B
      io.done := host.canExecute
    }
  }

  Init.build()
}

class ThreadRuntimeStructureSpec extends AnyFlatSpec {
  "ControlProgramBuilder and ControlRuntimeCore" should "support build and lowering without HardwareThread" in {
    simulate(new ThreadRuntimeStructureModule) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      c.io.compiledWithoutThread.expect(true.B)
      c.io.runtimeBuilt.expect(true.B)
      c.io.done.expect(true.B)
    }
  }
}
