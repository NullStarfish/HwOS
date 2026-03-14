package HwOS.prototype.cpu

import HwOS.kernel.function.HwFunction
import HwOS.kernel.lang.HwOSLanguage._
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{Kernel, SysCall}
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

object ModuleWrapperExample {
  class WrappedAdderProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
    override def entry(): Unit = {}

    def Invoke(lhs: UInt, rhs: UInt, dst: UInt): HwFunction[Unit] = HwFunction.thread(s"${name}_Invoke") { t =>
      val sumReg = t.own(RegInit(0.U(32.W)))
      t.Step("Add") {
        sumReg <== lhs + rhs
      }
      t.Step("Commit") {
        dst <== sumReg
      }
      ()
    }
  }

  class ArithmeticServiceWrapperModule extends Module {
    val io = IO(new Bundle {
      val start = Input(Bool())
      val lhs = Input(UInt(32.W))
      val rhs = Input(UInt(32.W))
      val busy = Output(Bool())
      val done = Output(Bool())
      val result = Output(UInt(32.W))
    })

    io.busy := DontCare
    io.done := DontCare
    io.result := DontCare

    implicit val kernel: Kernel = new Kernel()

    object Init extends HwProcess("Init") {
      this.own(io.busy)
      this.own(io.done)
      this.own(io.result)

      val adder = spawn(new WrappedAdderProcess("Adder"))
      val worker = createThread("Worker")
      val daemon = createLogic("Daemon")

      private val lhsReg = this.own(RegInit(0.U(32.W)))
      private val rhsReg = this.own(RegInit(0.U(32.W)))
      private val resultReg = this.own(RegInit(0.U(32.W)))

      override def entry(): Unit = {
        this.grant(resultReg, worker)
        worker.entry {
          SysCall.Call(adder.Invoke(lhsReg, rhsReg, resultReg))
          worker.Step("Finish") {
            worker.exit()
          }
        }

        this.grantLifecycle(worker, daemon)
        this.grant(lhsReg, daemon)
        this.grant(rhsReg, daemon)
        this.grant(io.busy, daemon)
        this.grant(io.done, daemon)
        this.grant(io.result, daemon)

        daemon.run {
          when(io.start && !worker.active) {
            lhsReg <== io.lhs
            rhsReg <== io.rhs
            SysCall.Call(SysCall.start(worker))
          }

          io.busy <== worker.active
          io.done <== worker.done
          io.result <== resultReg
        }
      }
    }

    Init.build()
  }

  class ArithmeticCallerModule extends Module {
    val io = IO(new Bundle {
      val kick = Input(Bool())
      val lhs = Input(UInt(32.W))
      val rhs = Input(UInt(32.W))
      val calleeBusy = Input(Bool())
      val calleeDone = Input(Bool())
      val calleeResult = Input(UInt(32.W))
      val calleeStart = Output(Bool())
      val calleeLhs = Output(UInt(32.W))
      val calleeRhs = Output(UInt(32.W))
      val finished = Output(Bool())
      val result = Output(UInt(32.W))
    })

    val sIdle :: sLaunch :: sWait :: sCapture :: sHold :: Nil = Enum(5)
    val state = RegInit(sIdle)
    val lhsReg = RegInit(0.U(32.W))
    val rhsReg = RegInit(0.U(32.W))
    val resultReg = RegInit(0.U(32.W))

    io.calleeStart := false.B
    io.calleeLhs := lhsReg
    io.calleeRhs := rhsReg
    io.finished := state === sHold
    io.result := resultReg

    switch(state) {
      is(sIdle) {
        when(io.kick && !io.calleeBusy) {
          lhsReg := io.lhs
          rhsReg := io.rhs
          state := sLaunch
        }
      }
      is(sLaunch) {
        when(!io.calleeBusy) {
          io.calleeStart := true.B
          state := sWait
        }
      }
      is(sWait) {
        when(io.calleeDone) {
          state := sCapture
        }
      }
      is(sCapture) {
        resultReg := io.calleeResult
        state := sHold
      }
      is(sHold) {
        when(!io.kick) {
          state := sIdle
        }
      }
    }
  }

  class ArithmeticWrapperLinkTop extends Module {
    val io = IO(new Bundle {
      val kick = Input(Bool())
      val lhs = Input(UInt(32.W))
      val rhs = Input(UInt(32.W))
      val busy = Output(Bool())
      val done = Output(Bool())
      val result = Output(UInt(32.W))
    })

    val caller = Module(new ArithmeticCallerModule)
    val callee = Module(new ArithmeticServiceWrapperModule)

    caller.io.kick := io.kick
    caller.io.lhs := io.lhs
    caller.io.rhs := io.rhs
    caller.io.calleeBusy := callee.io.busy
    caller.io.calleeDone := callee.io.done
    caller.io.calleeResult := callee.io.result

    callee.io.start := caller.io.calleeStart
    callee.io.lhs := caller.io.calleeLhs
    callee.io.rhs := caller.io.calleeRhs

    io.busy := callee.io.busy
    io.done := caller.io.finished
    io.result := callee.io.result
  }
}

object ExportArithmeticWrapperLinkTop extends App {
  ChiselStage.emitSystemVerilogFile(
    new ModuleWrapperExample.ArithmeticWrapperLinkTop(),
    Array("--target-dir", "generated/module_wrapper_example"),
    firtoolOpts = Array(
      "--lowering-options=disallowLocalVariables,disallowPackedArrays,locationInfoStyle=none,disallowPortDeclSharing"
    ),
  )
}
