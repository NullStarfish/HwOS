package HwOS.prototype.cpu

import HwOS.kernel._
import HwOS.kernel.HwOSLanguage._
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

    def Acquire(clientId: Int): HwFunction[Unit] = HwFunction.atomic(s"${name}_Acquire_$clientId") { t =>
      val lease = SysCall.Call(service.RequestLease(clientId))
      SysCall.Call(lease.Acquire())
      ()
    }

    def Execute(lhs: UInt, rhs: UInt): HwFunction[UInt] = HwFunction.stateless(s"${name}_Execute") { agent =>
      lhs + rhs
    }

    def Release(clientId: Int): HwFunction[Unit] = HwFunction.atomic(s"${name}_Release_$clientId") { t =>
      val lease = SysCall.Call(service.RequestLease(clientId))
      SysCall.Call(lease.Release())
      ()
    }
  }

  class LoadProcess(val maxClients: Int, val ports: Int, val initData: Seq[Int], localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
    val service = spawn(new sync.SemaphoreProcess(maxClients, ports, "Ports"))
    private val memDepth = 16
    private val mem = this.own(RegInit(VecInit((0 until memDepth).map(i => initData.lift(i).getOrElse(0).U(32.W)))))

    override def entry(): Unit = {}

    def Acquire(clientId: Int): HwFunction[Unit] = HwFunction.atomic(s"${name}_Acquire_$clientId") { t =>
      val lease = SysCall.Call(service.RequestLease(clientId))
      SysCall.Call(lease.Acquire())
      ()
    }

    def Wait(delay: UInt): HwFunction[Unit] = HwFunction.atomic(s"${name}_Wait") { t =>
      delay <== delay + 1.U
      t.waitCondition(delay >= 2.U)
      ()
    }

    def Read(addr: UInt): HwFunction[UInt] = HwFunction.stateless(s"${name}_Read") { agent =>
      mem(addr(log2Ceil(memDepth) - 1, 0))
    }

    def Release(clientId: Int): HwFunction[Unit] = HwFunction.atomic(s"${name}_Release_$clientId") { t =>
      val lease = SysCall.Call(service.RequestLease(clientId))
      SysCall.Call(lease.Release())
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

      t.Step(s"RouteDispatch_$slotId") {
        val canArith = ISA.opcode(instBits) === ISA.OP_ADDI && SysCall.Call(arith.service.Available())
        val canLoad = ISA.opcode(instBits) === ISA.OP_LOAD && SysCall.Call(load.service.Available())
        val canLoadAdd = ISA.opcode(instBits) === ISA.OP_LOADADD && SysCall.Call(load.service.Available())
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

      t.Step(s"ArithReserve_$slotId") {
        val writePort = SysCall.Call(regFile.RequestWritePort(slotId))
        SysCall.Call(writePort.Reserve(ISA.rd(instBits)))
      }

      t.Step(s"ArithGuard_$slotId") {
        SysCall.Call(regFile.scoreboard.Guard(ISA.rs1(instBits)))
      }

      t.Step(s"ArithRead_$slotId") {
        decodedSrc <== SysCall.Call(regFile.baseReg.Read(ISA.rs1(instBits)))
      }

      t.Step(s"ArithAcquire_$slotId") {
        SysCall.Call(arith.Acquire(slotId))
      }

      t.Step(s"ArithExec_$slotId") {
        result <== SysCall.Call(arith.Execute(decodedSrc, ISA.imm(instBits)))
        SysCall.Call(arith.Release(slotId))
        t.jump(s"RouteWriteback_$slotId")
      }

      t.Step(s"LoadReserve_$slotId") {
        val writePort = SysCall.Call(regFile.RequestWritePort(slotId))
        SysCall.Call(writePort.Reserve(ISA.rd(instBits)))
      }

      t.Step(s"LoadAcquire_$slotId") {
        SysCall.Call(load.Acquire(slotId))
        loadDelay <== 0.U
      }

      t.Step(s"LoadWait_$slotId") {
        SysCall.Call(load.Wait(loadDelay))
      }

      t.Step(s"LoadRead_$slotId") {
        result <== SysCall.Call(load.Read(ISA.imm(instBits)))
        SysCall.Call(load.Release(slotId))
        t.jump(s"RouteWriteback_$slotId")
      }

      t.Step(s"LoadAddLoadReserve_$slotId") {
        val writePort = SysCall.Call(regFile.RequestWritePort(slotId))
        SysCall.Call(writePort.Reserve(ISA.rd(instBits)))
      }

      t.Step(s"LoadAddLoadAcquire_$slotId") {
        SysCall.Call(load.Acquire(slotId))
        loadDelay <== 0.U
      }

      t.Step(s"LoadAddLoadWait_$slotId") {
        SysCall.Call(load.Wait(loadDelay))
      }

      t.Step(s"LoadAddLoadRead_$slotId") {
        loadedValue <== SysCall.Call(load.Read(ISA.imm(instBits)))
        SysCall.Call(load.Release(slotId))
        t.jump(s"LoadAddArithDispatch_$slotId")
      }

      t.Step(s"LoadAddArithDispatch_$slotId") {
        val canArith = SysCall.Call(arith.service.Available())
        t.waitCondition(canArith)
        when(canArith) {
          t.jump(s"LoadAddArithGuard_$slotId")
        }
      }

      t.Step(s"LoadAddArithGuard_$slotId") {
        SysCall.Call(regFile.scoreboard.Guard(ISA.rs1(instBits)))
      }

      t.Step(s"LoadAddArithRead_$slotId") {
        decodedSrc <== SysCall.Call(regFile.baseReg.Read(ISA.rs1(instBits)))
      }

      t.Step(s"LoadAddArithAcquire_$slotId") {
        SysCall.Call(arith.Acquire(slotId))
      }

      t.Step(s"LoadAddArithExec_$slotId") {
        result <== SysCall.Call(arith.Execute(loadedValue, decodedSrc))
        SysCall.Call(arith.Release(slotId))
        t.jump(s"RouteWriteback_$slotId")
      }

      t.Step(s"RouteWriteback_$slotId") {
        val writePort = SysCall.Call(regFile.RequestWritePort(slotId))
        SysCall.Call(writePort.WritebackAndClear(ISA.rd(instBits), result))
      }

      t.Step(s"ThreadExit_$slotId") {
        t.exit()
      }
      ()
    }
  }

  final class Slot(val slotId: Int, val thread: HardwareThread, val instArg: UInt)

  class FetchProcess(program: Seq[ISA.Instr], initData: Seq[Int], localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
    private val fetchWidth = 2
    val decode = spawn(new DecodeProcess(program.length max 1, initData, "Decode"))
    private val launcher = createLogic("Launcher")

    private val issuePtr = this.own(RegInit(0.U(log2Ceil(program.length + 1).W)))
    private val programRom = VecInit(program.map(ISA.encode))

    val slots = program.indices.map { i =>
      val thread = createThread(s"Slot${i}_inst")
      val instArg = thread.own(RegInit(0.U(ISA.instWidth.W)))
      new Slot(i, thread, instArg)
    }

    override def entry(): Unit = {
      for (slot <- slots) {
        slot.thread.grant(slot.instArg, this)
        slot.thread.entry {
          SysCall.Call(decode.install(slot.slotId, slot.instArg))
        }
        this.grantLifecycle(slot.thread, this)
      }
      this.grant(issuePtr, launcher)
      slots.foreach(slot => this.grantLifecycle(slot.thread, launcher))
      slots.foreach(slot => this.grant(slot.instArg, launcher))
      launcher.run {
        val freeVec = VecInit(slots.map(slot => !slot.thread.active))
        val firstFire = issuePtr < program.length.U && freeVec.asUInt.orR
        val firstIdx = PriorityEncoder(freeVec)
        val secondMask = Wire(Vec(slots.length, Bool()))
        for ((isFree, idx) <- freeVec.zipWithIndex) {
          secondMask(idx) := isFree && (!firstFire || firstIdx =/= idx.U)
        }
        val secondFire = issuePtr + 1.U < program.length.U && secondMask.asUInt.orR
        val secondIdx = PriorityEncoder(secondMask)

        when(firstFire) {
          val inst0 = programRom(issuePtr(log2Ceil(program.length max 2) - 1, 0))
          for ((slot, idx) <- slots.zipWithIndex) {
            when(firstIdx === idx.U) {
              slot.instArg <== inst0
              SysCall.Call(SysCall.start(slot.thread))
            }
          }
        }

        when(secondFire) {
          val inst1Idx = issuePtr + 1.U
          val inst1 = programRom(inst1Idx(log2Ceil(program.length max 2) - 1, 0))
          for ((slot, idx) <- slots.zipWithIndex) {
            when(secondIdx === idx.U) {
              slot.instArg <== inst1
              SysCall.Call(SysCall.start(slot.thread))
            }
          }
        }

        val issueCount = Mux(firstFire, 1.U, 0.U) + Mux(secondFire, 1.U, 0.U)
        when(issueCount =/= 0.U) {
          issuePtr <== issuePtr + issueCount
        }
      }
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
          io.x1 <== SysCall.Call(fetch.decode.regFile.baseReg.Read(1.U))
          io.x2 <== SysCall.Call(fetch.decode.regFile.baseReg.Read(2.U))
          io.x3 <== SysCall.Call(fetch.decode.regFile.baseReg.Read(3.U))
          io.activeThreads <== PopCount(fetch.slots.map(_.thread.active))
        }
      }
    }

    Init.build()
  }
}
