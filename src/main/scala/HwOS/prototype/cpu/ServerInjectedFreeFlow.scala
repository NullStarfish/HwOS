package HwOS.prototype.cpu

import HwOS.kernel.function.HwInline
import HwOS.kernel.lang.HwOSLanguage._
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{Kernel, SysCall}
import HwOS.kernel.thread.HardwareThread
import HwOS.prototype.cpu.InjectedFreeFlow.{DecodeProcess, ISA, Slot}
import HwOS.stdlib.sync
import chisel3._
import chisel3.util._
import _root_.circt.stage.ChiselStage

object ServerInjectedFreeFlow {
  class ServerDecodeProcess(
      val maxClients: Int,
      val maxServers: Int,
      val initData: Seq[Int],
      localName: String,
  )(implicit kernel: Kernel)
      extends HwProcess(localName) {
    private val core = spawn(new DecodeProcess(maxServers max 1, initData, "Core"))
    private val serverSlots = spawn(new sync.SemaphoreProcess(maxClients max 1, maxServers max 1, "ServerSlots"))

    private case class ClientReq(pending: Bool, completed: Bool, instBits: UInt)
    private case class ServerSlot(thread: HardwareThread, instArg: UInt, ownerValid: Bool, ownerClient: UInt)

    private val clientReqs = Array.tabulate(maxClients max 1) { _ =>
      ClientReq(
        this.own(RegInit(false.B)),
        this.own(RegInit(false.B)),
        this.own(RegInit(0.U(ISA.instWidth.W))),
      )
    }

    private val servers = Array.tabulate(maxServers max 1) { i =>
      val thread = createThread(s"Server$i")
      val instArg = thread.own(RegInit(0.U(ISA.instWidth.W)))
      ServerSlot(
        thread,
        instArg,
        this.own(RegInit(false.B)),
        this.own(RegInit(0.U(log2Ceil((maxClients max 1) max 2).W))),
      )
    }

    val regFile = core.regFile

    override def entry(): Unit = {
      val dispatcher = createLogic("Dispatcher")

      for ((slot, serverId) <- servers.zipWithIndex) {
        slot.thread.grant(slot.instArg, this)
        slot.thread.entry {
          SysCall.Call(core.install(serverId, slot.instArg))
        }
        this.grant(slot.instArg, dispatcher)
        this.grantLifecycle(slot.thread, dispatcher)
        this.grant(slot.ownerValid, dispatcher)
        this.grant(slot.ownerClient, dispatcher)
      }

      clientReqs.foreach { req =>
        this.grant(req.pending, dispatcher)
        this.grant(req.completed, dispatcher)
        this.grant(req.instBits, dispatcher)
      }

      dispatcher.run {
        val pendingOH = PriorityEncoderOH(VecInit(clientReqs.toIndexedSeq.map(_.pending)).asUInt)
        val freeOH = PriorityEncoderOH(VecInit(servers.toIndexedSeq.map(s => !s.ownerValid && !s.thread.active)).asUInt)
        when(pendingOH.orR && freeOH.orR) {
          for (((req, _), clientIdx) <- clientReqs.zipWithIndex.zipWithIndex.map { case ((r, id), idx) => ((r, id), idx) }) {
            when(pendingOH(clientIdx)) {
              for (((slot, _), freeIdx) <- servers.zipWithIndex.zipWithIndex.map { case ((s, id), idx) => ((s, id), idx) }) {
                when(freeOH(freeIdx)) {
                  slot.instArg  :=  req.instBits
                  slot.ownerValid  :=  true.B
                  slot.ownerClient  :=  clientIdx.U
                  req.pending  :=  false.B
                  SysCall.Call(SysCall.start(slot.thread))
                }
              }
            }
          }
        }

        for ((slot, _) <- servers.zipWithIndex) {
          when(slot.ownerValid && slot.thread.done) {
            val owner = slot.ownerClient
            slot.ownerValid  :=  false.B
            for ((req, clientIdx) <- clientReqs.zipWithIndex) {
              when(owner === clientIdx.U) {
                req.completed  :=  true.B
              }
            }
          }
        }
      }
    }

    def ActiveServerCount(): HwInline[UInt] = HwInline.stateless(s"${name}_ActiveServerCount") { _ =>
      PopCount(servers.map(_.thread.active))
    }

    def RequestDecode(clientId: Int, instBits: UInt): HwInline[Unit] = HwInline.atomic(s"${name}_RequestDecode_$clientId") { t =>
      val req = clientReqs(clientId)
      val slotLease = SysCall.Call(serverSlots.RequestLease(clientId))
      this.grant(req.pending, t)
      this.grant(req.completed, t)
      this.grant(req.instBits, t)
      t.waitCondition(!req.pending)
      when(!req.pending) {
        SysCall.Call(slotLease.Acquire())
        req.instBits  :=  instBits
        req.completed  :=  false.B
        req.pending  :=  true.B
      }
      t.waitCondition(req.completed)
      when(req.completed) {
        SysCall.Call(slotLease.Release())
        t.hijack(t.Next)
      }
      ()
    }
  }

  class ServerFetchProcess(
      program: Seq[ISA.Instr],
      initData: Seq[Int],
      decodeServers: Int,
      localName: String,
  )(implicit kernel: Kernel)
      extends HwProcess(localName) {
    private val issueWidth = 2
    val decode = spawn(new ServerDecodeProcess(program.length max 1, decodeServers, initData, "Decode"))
    private val launcher = createLogic("Launcher")

    private val fetchPtr = this.own(RegInit(0.U(log2Ceil(program.length + 1).W)))
    private val programRom = VecInit(program.map(ISA.encode))

    val slots = program.indices.map { i =>
      val thread = createThread(s"Client${i}_req")
      val instArg = thread.own(RegInit(0.U(ISA.instWidth.W)))
      new Slot(i, thread, instArg)
    }

    override def entry(): Unit = {
      require(issueWidth == 2, "Current MVP keeps only 1-bit intra-bundle order")

      for (slot <- slots) {
        slot.thread.grant(slot.instArg, this)
        slot.thread.entry {
          slot.thread.Step(s"SubmitDecode_${slot.slotId}") {
            SysCall.Call(decode.RequestDecode(slot.slotId, slot.instArg))
          }
          slot.thread.Step(s"Retire_${slot.slotId}") {
          }
          SysCall.Call(SysCall.Return())
        }
        this.grantLifecycle(slot.thread, this)
      }

      this.grant(fetchPtr, launcher)
      slots.foreach(slot => this.grantLifecycle(slot.thread, launcher))
      slots.foreach(slot => this.grant(slot.instArg, launcher))

      launcher.run {
        val freeVec = VecInit(slots.map(slot => !slot.thread.active))
        val launchPlan = Seq(
          freeVec,
          VecInit(slots.indices.map(idx => freeVec(idx) && !PriorityEncoderOH(freeVec.asUInt)(idx)))
        )
        val fires = launchPlan.zipWithIndex.map { case (candidates, laneIdx) =>
          val slotOH = PriorityEncoderOH(candidates.asUInt)
          val slotFire = (fetchPtr + laneIdx.U) < program.length.U && slotOH.orR
          val inst = programRom((fetchPtr + laneIdx.U)(log2Ceil(program.length max 2) - 1, 0))
          when(slotFire) {
            for ((slot, idx) <- slots.zipWithIndex) {
              when(slotOH(idx)) {
                slot.instArg  :=  inst
                SysCall.Call(SysCall.start(slot.thread))
              }
            }
          }
          Mux(slotFire, 1.U, 0.U)
        }

        val issueCount = fires.reduce(_ + _)
        when(issueCount =/= 0.U) {
          fetchPtr  :=  fetchPtr + issueCount
        }
      }
    }

    def ActiveThreadCount(): HwInline[UInt] = HwInline.stateless(s"${name}_ActiveThreadCount") { _ =>
      PopCount(slots.map(_.thread.active)) + SysCall.Call(decode.ActiveServerCount())
    }
  }

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
      this.own(io.x1); this.own(io.x2); this.own(io.x3); this.own(io.activeThreads)
      val fetch = spawn(new ServerFetchProcess(program, initData, decodeServers, "Fetch"))
      val daemon = createLogic("Daemon")

      override def entry(): Unit = {
        this.grant(io.x1, daemon, HwOS.kernel.GrantAbi.LevelDrivenWire)
        this.grant(io.x2, daemon, HwOS.kernel.GrantAbi.LevelDrivenWire)
        this.grant(io.x3, daemon, HwOS.kernel.GrantAbi.LevelDrivenWire)
        this.grant(io.activeThreads, daemon, HwOS.kernel.GrantAbi.LevelDrivenWire)
        daemon.run {
          io.x1  :=  SysCall.Call(fetch.decode.regFile.ReadCommitted(1.U))
          io.x2  :=  SysCall.Call(fetch.decode.regFile.ReadCommitted(2.U))
          io.x3  :=  SysCall.Call(fetch.decode.regFile.ReadCommitted(3.U))
          io.activeThreads  :=  SysCall.Call(fetch.ActiveThreadCount())
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
      this.own(io.reqBusy)
      this.own(io.reqDone)
      this.own(io.x1)
      this.own(io.x2)
      this.own(io.x3)
      this.own(io.activeThreads)

      val decode = spawn(new ServerDecodeProcess(maxClients, decodeServers, initData, "Decode"))
      val daemon = createLogic("Daemon")

      private val clientSlots = (0 until maxClients).map { clientId =>
        val thread = createThread(s"Client${clientId}_driver")
        val instArg = thread.own(RegInit(0.U(ISA.instWidth.W)))
        (thread, instArg)
      }

      override def entry(): Unit = {
        this.grant(io.reqBusy, daemon, HwOS.kernel.GrantAbi.LevelDrivenWire)
        this.grant(io.reqDone, daemon, HwOS.kernel.GrantAbi.LevelDrivenWire)
        this.grant(io.x1, daemon, HwOS.kernel.GrantAbi.LevelDrivenWire)
        this.grant(io.x2, daemon, HwOS.kernel.GrantAbi.LevelDrivenWire)
        this.grant(io.x3, daemon, HwOS.kernel.GrantAbi.LevelDrivenWire)
        this.grant(io.activeThreads, daemon, HwOS.kernel.GrantAbi.LevelDrivenWire)

        for (((thread, instArg), clientId) <- clientSlots.zipWithIndex) {
          thread.grant(instArg, this)
          thread.entry {
            thread.Step(s"SubmitDecode_$clientId") {
              SysCall.Call(decode.RequestDecode(clientId, instArg))
            }
            thread.Step(s"Retire_$clientId") {
            }
            SysCall.Call(SysCall.Return())
          }
          this.grantLifecycle(thread, daemon)
          this.grant(instArg, daemon)
        }

        daemon.run {
          for ((((thread, instArg), reqValid), clientId) <- clientSlots.zip(io.reqValid).zipWithIndex) {
            io.reqBusy.at(clientId)  :=  thread.active
            io.reqDone.at(clientId)  :=  thread.done
            when(reqValid && !thread.active) {
              instArg  :=  io.reqInst(clientId)
              SysCall.Call(SysCall.start(thread))
            }
          }

          io.x1  :=  SysCall.Call(decode.regFile.ReadCommitted(1.U))
          io.x2  :=  SysCall.Call(decode.regFile.ReadCommitted(2.U))
          io.x3  :=  SysCall.Call(decode.regFile.ReadCommitted(3.U))
          io.activeThreads  :=  PopCount(clientSlots.map(_._1.active)) + SysCall.Call(decode.ActiveServerCount())
        }
      }
    }

    Init.build()
  }
}

object ExportServerDecodeWrapper extends App {
  ChiselStage.emitSystemVerilogFile(
    new ServerInjectedFreeFlow.ServerDecodeWrapperModule(),
    Array("--target-dir", "generated/server_decode_wrapper"),
    firtoolOpts = Array(
      "--lowering-options=disallowLocalVariables,disallowPackedArrays,locationInfoStyle=none,disallowPortDeclSharing"
    ),
  )
}
