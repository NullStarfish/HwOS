package HwOS.prototype.cpu

import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{Kernel, SysCall}
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

class ServerInjectedCpuModule(program: Seq[ISA.Instr], initData: Seq[Int] = Seq.empty, decodeServers: Int = 2) extends Module {
  val io = IO(new Bundle {
    val x1 = Output(UInt(32.W))
    val x2 = Output(UInt(32.W))
    val x3 = Output(UInt(32.W))
    val activeThreads = Output(UInt(8.W))
  })

  io.x1 := DontCare
  io.x2 := DontCare
  io.x3 := DontCare
  io.activeThreads := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    val backend = spawn(new BackendProcess(initData, decodeServers, "Backend"))
    val frontend = spawn(new FrontendProcess(program, decodeServers, backend, "Frontend"))
    val daemon = createLogic("Daemon")

    override def entry(): Unit = {
      daemon.run {
        io.x1 := SysCall.Inline(backend.regFile.ReadCommitted(1.U))
        io.x2 := SysCall.Inline(backend.regFile.ReadCommitted(2.U))
        io.x3 := SysCall.Inline(backend.regFile.ReadCommitted(3.U))
        io.activeThreads := SysCall.Inline(frontend.ActiveThreadCount())
      }
    }
  }

  Init.build()
}

class ServerDecodeWrapperModule(
    maxClients: Int = 2,
    decodeServers: Int = 2,
    initData: Seq[Int] = Seq.empty,
) extends Module {
  val io = IO(new Bundle {
    val reqValid = Input(Vec(maxClients, Bool()))
    val reqInst = Input(Vec(maxClients, UInt(ISA.instWidth.W)))
    val reqBusy = Output(Vec(maxClients, Bool()))
    val reqDone = Output(Vec(maxClients, Bool()))
    val x1 = Output(UInt(32.W))
    val x2 = Output(UInt(32.W))
    val x3 = Output(UInt(32.W))
    val activeThreads = Output(UInt(8.W))
  })

  io.reqBusy := DontCare
  io.reqDone := DontCare
  io.x1 := DontCare
  io.x2 := DontCare
  io.x3 := DontCare
  io.activeThreads := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {


    val backend = spawn(new BackendProcess(initData, decodeServers, "Backend"))
    val decode = spawn(new ServerDecodeProcess(maxClients, decodeServers, backend, "Decode"))
    val daemon = createLogic("Daemon")

    private val clientSlots = (0 until maxClients).map { clientId =>
      val thread = createThread(s"Client${clientId}_driver")
      val instArg = RegInit(0.U(ISA.instWidth.W))
      (thread, instArg)
    }

    override def entry(): Unit = {
      for (((thread, instArg), clientId) <- clientSlots.zipWithIndex) {
        thread.entry {
          thread.Step(s"SubmitDecode_$clientId") {
            SysCall.Call(decode.RequestDecode(clientId, instArg), s"Retire_$clientId")
          }
          thread.Step(s"Retire_$clientId") {}
          SysCall.Return()
        }
      }

      daemon.run {
        for ((((thread, instArg), reqValid), clientId) <- clientSlots.zip(io.reqValid).zipWithIndex) {
          io.reqBusy(clientId) := thread.active
          io.reqDone(clientId) := thread.done
          when(reqValid && !thread.active) {
            instArg := io.reqInst(clientId)
            SysCall.Inline(SysCall.start(thread))
          }
        }

        io.x1 := SysCall.Inline(backend.regFile.ReadCommitted(1.U))
        io.x2 := SysCall.Inline(backend.regFile.ReadCommitted(2.U))
        io.x3 := SysCall.Inline(backend.regFile.ReadCommitted(3.U))
        io.activeThreads := PopCount(clientSlots.map(_._1.active)) + SysCall.Inline(decode.ActiveServerCount())
      }
    }
  }

  Init.build()
}

object ExportServerDecodeWrapper extends App {
  ChiselStage.emitSystemVerilogFile(
    new ServerDecodeWrapperModule(),
    Array("--target-dir", "generated/server_decode_wrapper"),
    firtoolOpts = Array(
      "--lowering-options=disallowLocalVariables,disallowPackedArrays,locationInfoStyle=none,disallowPortDeclSharing"
    ),
  )
}
