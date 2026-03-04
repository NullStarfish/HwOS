package HwOS.prototype.cpu

import HwOS.kernel.function.HwFunction
import HwOS.kernel.lang.HwOSLanguage._
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{Kernel, SysCall}
import HwOS.kernel.thread.HardwareThread
import HwOS.lib.regfile.RegfileLib._
import HwOS.stdlib.sync
import chisel3._
import chisel3.util._

object InjectedFreeFlow {
  object ISA {
    val opWidth = 2
    val regWidth = 3
    val immWidth = 8
    val instWidth = opWidth + regWidth + regWidth + immWidth

    val OP_ADDI = 0.U(opWidth.W)
    val OP_LOAD = 1.U(opWidth.W)
    val OP_LOADADD = 2.U(opWidth.W)

    case class Instr(op: Int, rd: Int, rs1: Int, imm: Int)

    def encode(inst: Instr): UInt = Cat(
      inst.op.U(opWidth.W),
      inst.rd.U(regWidth.W),
      inst.rs1.U(regWidth.W),
      inst.imm.U(immWidth.W),
    )

    def opcode(inst: UInt): UInt = inst(instWidth - 1, instWidth - opWidth)
    def rd(inst: UInt): UInt = inst(instWidth - opWidth - 1, immWidth + regWidth)
    def rs1(inst: UInt): UInt = inst(immWidth + regWidth - 1, immWidth)
    def imm(inst: UInt): UInt = inst(immWidth - 1, 0)
  }

  class ArithmeticProcess(val maxClients: Int, val ports: Int, localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
    val service = spawn(new sync.SemaphoreProcess(maxClients, ports, "Ports"))
    override def entry(): Unit = {}

    def Acquire(clientId: Int): HwFunction[Unit] = service.Acquire(clientId)

    def Execute(lhs: UInt, rhs: UInt): HwFunction[UInt] = HwFunction.stateless(s"${name}_Execute") { _ =>
      lhs + rhs
    }

    def Release(clientId: Int): HwFunction[Unit] = service.Release(clientId)

    def Available(): HwFunction[Bool] = service.Available()

    def WithPort(clientId: Int, entryLabel: String)(body: HardwareThread => Unit): HwFunction[Unit] =
      HwFunction.thread(s"${name}_WithPort_$clientId") { t =>
        t.Step(entryLabel) {
          SysCall.Call(Acquire(clientId))
        }
        body(t)
        t.Step(s"${entryLabel}_Release") {
          SysCall.Call(Release(clientId))
        }
        ()
      }
  }

  class LoadProcess(val maxClients: Int, val ports: Int, val initData: Seq[Int], localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
    val service = spawn(new sync.SemaphoreProcess(maxClients, ports, "Ports"))
    private val memDepth = 16
    private val mem = this.own(RegInit(VecInit((0 until memDepth).map(i => initData.lift(i).getOrElse(0).U(32.W)))))

    override def entry(): Unit = {}

    def Load(clientId: Int, delay: UInt, addr: UInt): HwFunction[UInt] = HwFunction.atomic(s"${name}_Load_$clientId") { t =>
      val lease = SysCall.Call(service.RequestLease(clientId))
      when(!lease.isActive) {
        SysCall.Call(service.Acquire(clientId))
      }
      delay <== delay + 1.U
      val done = delay >= 2.U
      t.waitCondition(done)
      when(done) {
        SysCall.Call(service.Release(clientId))
      }
      mem(addr(log2Ceil(memDepth) - 1, 0))
    }

    def Available(): HwFunction[Bool] = service.Available()

    def WithPort(clientId: Int, entryLabel: String)(body: HardwareThread => Unit): HwFunction[Unit] =
      HwFunction.thread(s"${name}_WithPort_$clientId") { t =>
        t.Step(entryLabel) {
          SysCall.Call(service.Acquire(clientId))
        }
        body(t)
        t.Step(s"${entryLabel}_Release") {
          SysCall.Call(service.Release(clientId))
        }
        ()
      }
  }

  class DecodeProcess(
      val maxClients: Int,
      val initData: Seq[Int],
      localName: String,
  )(implicit kernel: Kernel)
      extends HwProcess(localName) {
    val regFile = spawn(new ScoreboardRegfileProcess(8, 32, maxClients max 1, zeroReg = true, "RegFile"))
    val arith = spawn(new ArithmeticProcess(maxClients max 1, 1, "Arithmetic"))
    val load = spawn(new LoadProcess(maxClients max 1, 1, initData, "Load"))

    override def entry(): Unit = {}

    def install(slotId: Int, instBits: UInt): HwFunction[Unit] = HwFunction.thread(s"${name}_Install_$slotId") { t =>
      val decodedSrc = t.own(RegInit(0.U(32.W)))
      val loadedValue = t.own(RegInit(0.U(32.W)))
      val result = t.own(RegInit(0.U(32.W)))
      val loadDelay = t.own(RegInit(0.U(2.W)))
      val opcode = ISA.opcode(instBits)
      val rd = ISA.rd(instBits)
      val rs1 = ISA.rs1(instBits)
      val imm = ISA.imm(instBits)
      val writePort = SysCall.Call(regFile.RequestWritePort(slotId))

      def withReservedWrite(entryLabel: String)(body: HardwareThread => Unit): Unit = {
        t.Step(entryLabel) {
          SysCall.Call(writePort.Reserve(rd))
        }
        body(t)
      }

      t.Step(s"RouteDispatch_$slotId") {
        val canArith = opcode === ISA.OP_ADDI && SysCall.Call(arith.Available())
        val canLoad = opcode === ISA.OP_LOAD && SysCall.Call(load.Available())
        val canLoadAdd = opcode === ISA.OP_LOADADD && SysCall.Call(load.Available())
        t.waitCondition(canArith || canLoad || canLoadAdd)

        when(canArith) {
          t.jump(s"ArithReserve_$slotId")
        }
        when(canLoad) {
          t.jump(s"LoadReserve_$slotId")
        }
        when(canLoadAdd) {
          t.jump(s"LoadAddLoadReserve_$slotId")
        }
      }

      withReservedWrite(s"ArithReserve_$slotId") { tx =>
        tx.Step(s"ArithRead_$slotId") {
          decodedSrc <== SysCall.Call(regFile.Read(rs1))
        }
        SysCall.Call(arith.WithPort(slotId, s"ArithAcquire_$slotId") { ax =>
          ax.Step(s"ArithExec_$slotId") {
            result <== SysCall.Call(arith.Execute(decodedSrc, imm))
          }
        })
        tx.Step(s"ArithAfterExec_$slotId") {
          tx.jump(s"RouteWriteback_$slotId")
        }
      }

      withReservedWrite(s"LoadReserve_$slotId") { tx =>
        tx.Step(s"LoadPrepare_$slotId") {
          loadDelay <== 0.U
        }
        SysCall.Call(load.WithPort(slotId, s"LoadAcquire_$slotId") { lx =>
          lx.Step(s"LoadExec_$slotId") {
            result <== SysCall.Call(load.Load(slotId, loadDelay, imm))
          }
        })
        tx.Step(s"LoadAfterExec_$slotId") {
          tx.jump(s"RouteWriteback_$slotId")
        }
      }

      withReservedWrite(s"LoadAddLoadReserve_$slotId") { tx =>
        tx.Step(s"LoadAddPrepare_$slotId") {
          loadDelay <== 0.U
        }
        SysCall.Call(load.WithPort(slotId, s"LoadAddLoadAcquire_$slotId") { lx =>
          lx.Step(s"LoadAddLoadExec_$slotId") {
            loadedValue <== SysCall.Call(load.Load(slotId, loadDelay, imm))
          }
        })
        tx.Step(s"LoadAddAfterLoad_$slotId") {
          tx.jump(s"LoadAddArithDispatch_$slotId")
        }
      }

      t.Step(s"LoadAddArithDispatch_$slotId") {
        val canArith = SysCall.Call(arith.Available())
        t.waitCondition(canArith)
        when(canArith) {
          t.jump(s"LoadAddArithRead_$slotId")
        }
      }

      t.Step(s"LoadAddArithRead_$slotId") {
        decodedSrc <== SysCall.Call(regFile.Read(rs1))
      }

      SysCall.Call(arith.WithPort(slotId, s"LoadAddArithAcquire_$slotId") { ax =>
        ax.Step(s"LoadAddArithExec_$slotId") {
          result <== SysCall.Call(arith.Execute(loadedValue, decodedSrc))
        }
      })

      t.Step(s"LoadAddAfterArith_$slotId") {
        t.jump(s"RouteWriteback_$slotId")
      }

      t.Step(s"RouteWriteback_$slotId") {
        SysCall.Call(writePort.WritebackAndClear(rd, result))
      }

      t.Step(s"ThreadExit_$slotId") {
        t.exit()
      }
      ()
    }
  }

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
                  slot.instArg <== req.instBits
                  slot.ownerValid <== true.B
                  slot.ownerClient <== clientIdx.U
                  req.pending <== false.B
                  SysCall.Call(SysCall.start(slot.thread))
                }
              }
            }
          }
        }

        for ((slot, serverId) <- servers.zipWithIndex) {
          when(slot.ownerValid && slot.thread.done) {
            val owner = slot.ownerClient
            slot.ownerValid <== false.B
            for ((req, clientIdx) <- clientReqs.zipWithIndex) {
              when(owner === clientIdx.U) {
                req.completed <== true.B
              }
            }
          }
        }
      }
    }

    def ActiveServerCount(): HwFunction[UInt] = HwFunction.stateless(s"${name}_ActiveServerCount") { _ =>
      PopCount(servers.map(_.thread.active))
    }

    def RequestDecode(clientId: Int, instBits: UInt): HwFunction[Unit] = HwFunction.atomic(s"${name}_RequestDecode_$clientId") { t =>
      val req = clientReqs(clientId)
      val slotLease = SysCall.Call(serverSlots.RequestLease(clientId))
      this.grant(req.pending, t)
      this.grant(req.completed, t)
      this.grant(req.instBits, t)
      t.waitCondition(!req.pending)
      when(!req.pending) {
        SysCall.Call(slotLease.Acquire())
        req.instBits <== instBits
        req.completed <== false.B
        req.pending <== true.B
      }
      t.waitCondition(req.completed)
      when(req.completed) {
        SysCall.Call(slotLease.Release())
        t.Next.hijack()
      }
      ()
    }
  }

  final class Slot(val slotId: Int, val thread: HardwareThread, val instArg: UInt)

  class FetchProcess(program: Seq[ISA.Instr], initData: Seq[Int], localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
    private val issueWidth = 2
    val decode = spawn(new DecodeProcess(program.length max 1, initData, "Decode"))
    private val launcher = createLogic("Launcher")

    private val fetchPtr = this.own(RegInit(0.U(log2Ceil(program.length + 1).W)))
    private val programRom = VecInit(program.map(ISA.encode))

    val slots = program.indices.map { i =>
      val thread = createThread(s"Slot${i}_inst")
      val instArg = thread.own(RegInit(0.U(ISA.instWidth.W)))
      new Slot(i, thread, instArg)
    }

    override def entry(): Unit = {
      require(issueWidth == 2, "Current MVP keeps only 1-bit intra-bundle order")

      for (slot <- slots) {
        slot.thread.grant(slot.instArg, this)
        slot.thread.entry {
          SysCall.Call(decode.install(slot.slotId, slot.instArg))
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
                slot.instArg <== inst
                SysCall.Call(SysCall.start(slot.thread))
              }
            }
          }
          Mux(slotFire, 1.U, 0.U)
        }

        val issueCount = fires.reduce(_ + _)
        when(issueCount =/= 0.U) {
          fetchPtr <== fetchPtr + issueCount
        }
      }
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
            slot.thread.exit()
          }
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
                slot.instArg <== inst
                SysCall.Call(SysCall.start(slot.thread))
              }
            }
          }
          Mux(slotFire, 1.U, 0.U)
        }

        val issueCount = fires.reduce(_ + _)
        when(issueCount =/= 0.U) {
          fetchPtr <== fetchPtr + issueCount
        }
      }
    }

    def ActiveThreadCount(): HwFunction[UInt] = HwFunction.stateless(s"${name}_ActiveThreadCount") { _ =>
      PopCount(slots.map(_.thread.active)) + SysCall.Call(decode.ActiveServerCount())
    }
  }

  class InjectedCpuModule(program: Seq[ISA.Instr], initData: Seq[Int] = Seq.empty) extends Module {
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
      val fetch = spawn(new FetchProcess(program, initData, "Fetch"))
      val daemon = createLogic("Daemon")

      override def entry(): Unit = {
        this.grant(io.x1, daemon)
        this.grant(io.x2, daemon)
        this.grant(io.x3, daemon)
        this.grant(io.activeThreads, daemon)
        daemon.run {
          io.x1 <== SysCall.Call(fetch.decode.regFile.ReadCommitted(1.U))
          io.x2 <== SysCall.Call(fetch.decode.regFile.ReadCommitted(2.U))
          io.x3 <== SysCall.Call(fetch.decode.regFile.ReadCommitted(3.U))
          io.activeThreads <== PopCount(fetch.slots.map(_.thread.active))
        }
      }
    }

    Init.build()
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
        this.grant(io.x1, daemon)
        this.grant(io.x2, daemon)
        this.grant(io.x3, daemon)
        this.grant(io.activeThreads, daemon)
        daemon.run {
          io.x1 <== SysCall.Call(fetch.decode.regFile.ReadCommitted(1.U))
          io.x2 <== SysCall.Call(fetch.decode.regFile.ReadCommitted(2.U))
          io.x3 <== SysCall.Call(fetch.decode.regFile.ReadCommitted(3.U))
          io.activeThreads <== SysCall.Call(fetch.ActiveThreadCount())
        }
      }
    }

    Init.build()
  }
}
