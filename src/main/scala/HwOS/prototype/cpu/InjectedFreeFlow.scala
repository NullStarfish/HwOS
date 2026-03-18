package HwOS.prototype.cpu

import HwOS.kernel.control.StructuredControl
import HwOS.kernel.function.HwInline
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

    def Execute(lhs: UInt, rhs: UInt): HwInline[UInt] = HwInline.stateless(s"${name}_Execute") { _ =>
      lhs + rhs
    }

    def Available(): HwInline[Bool] = service.Available()

    def WithPort(clientId: Int, entryLabel: String)(body: HardwareThread => Unit): HwInline[Unit] =
      HwInline.thread(s"${name}_WithPort_$clientId") { t =>
        val lease = SysCall.Call(service.RequestLease(clientId))
        t.Step(entryLabel) {
          SysCall.Call(lease.Acquire())
        }
        body(t)
        t.Step(s"${entryLabel}_Release") {
          SysCall.Call(lease.Release())
        }
        ()
      }
  }

  class LoadProcess(val maxClients: Int, val ports: Int, val initData: Seq[Int], localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
    val service = spawn(new sync.SemaphoreProcess(maxClients, ports, "Ports"))
    private val memDepth = 16
    private val mem = (RegInit(VecInit((0 until memDepth).map(i => initData.lift(i).getOrElse(0).U(32.W)))))

    override def entry(): Unit = {}

    def Load(clientId: Int, delay: UInt, addr: UInt): HwInline[UInt] = HwInline.atomic(s"${name}_Load_$clientId") { t =>
      val lease = SysCall.Call(service.RequestLease(clientId))
      when(!lease.isActive) {
        SysCall.Call(lease.Acquire())
      }
      delay  :=  delay + 1.U
      val done = delay >= 2.U
      t.waitCondition(done)
      when(done) {
        SysCall.Call(lease.Release())
      }
      mem(addr(log2Ceil(memDepth) - 1, 0))
    }

    def Available(): HwInline[Bool] = service.Available()

    def WithPort(clientId: Int, entryLabel: String)(body: HardwareThread => Unit): HwInline[Unit] =
      HwInline.thread(s"${name}_WithPort_$clientId") { t =>
        val lease = SysCall.Call(service.RequestLease(clientId))
        t.Step(entryLabel) {
          SysCall.Call(lease.Acquire())
        }
        body(t)
        t.Step(s"${entryLabel}_Release") {
          SysCall.Call(lease.Release())
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

    def install(slotId: Int, instBits: UInt): HwInline[Unit] = HwInline.thread(s"${name}_Install_$slotId") { t =>
      val decodedSrc = (RegInit(0.U(32.W)))
      val loadedValue = (RegInit(0.U(32.W)))
      val result = (RegInit(0.U(32.W)))
      val loadDelay = (RegInit(0.U(2.W)))
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

      val addiPath = HwInline.thread(s"${name}_AddiPath_$slotId") { tx =>
        tx.Step(s"ArithWait_$slotId") {
          tx.waitCondition(SysCall.Call(arith.Available()))
        }
        withReservedWrite(s"ArithReserve_$slotId") { rx =>
          rx.Step(s"ArithRead_$slotId") {
            decodedSrc  :=  SysCall.Call(regFile.Read(rs1))
          }
          SysCall.Call(arith.WithPort(slotId, s"ArithAcquire_$slotId") { ax =>
            ax.Step(s"ArithExec_$slotId") {
              result  :=  SysCall.Call(arith.Execute(decodedSrc, imm))
            }
          })
          rx.Step(s"ArithAfterExec_$slotId") {
            rx.jump(s"RouteWriteback_$slotId")
          }
        }
        ()
      }

      val loadPath = HwInline.thread(s"${name}_LoadPath_$slotId") { tx =>
        tx.Step(s"LoadWait_$slotId") {
          tx.waitCondition(SysCall.Call(load.Available()))
        }
        withReservedWrite(s"LoadReserve_$slotId") { rx =>
          rx.Step(s"LoadPrepare_$slotId") {
            loadDelay  :=  0.U
          }
          SysCall.Call(load.WithPort(slotId, s"LoadAcquire_$slotId") { lx =>
            lx.Step(s"LoadExec_$slotId") {
              result  :=  SysCall.Call(load.Load(slotId, loadDelay, imm))
            }
          })
          rx.Step(s"LoadAfterExec_$slotId") {
            rx.jump(s"RouteWriteback_$slotId")
          }
        }
        ()
      }

      val loadAddPath = HwInline.thread(s"${name}_LoadAddPath_$slotId") { tx =>
        tx.Step(s"LoadAddLoadWait_$slotId") {
          tx.waitCondition(SysCall.Call(load.Available()))
        }
        withReservedWrite(s"LoadAddLoadReserve_$slotId") { rx =>
          rx.Step(s"LoadAddPrepare_$slotId") {
            loadDelay  :=  0.U
          }
          SysCall.Call(load.WithPort(slotId, s"LoadAddLoadAcquire_$slotId") { lx =>
            lx.Step(s"LoadAddLoadExec_$slotId") {
              loadedValue  :=  SysCall.Call(load.Load(slotId, loadDelay, imm))
            }
          })
          rx.Step(s"LoadAddArithWait_$slotId") {
            rx.waitCondition(SysCall.Call(arith.Available()))
          }
          rx.Step(s"LoadAddArithRead_$slotId") {
            decodedSrc  :=  SysCall.Call(regFile.Read(rs1))
          }
          SysCall.Call(arith.WithPort(slotId, s"LoadAddArithAcquire_$slotId") { ax =>
            ax.Step(s"LoadAddArithExec_$slotId") {
              result  :=  SysCall.Call(arith.Execute(loadedValue, decodedSrc))
            }
          })
          rx.Step(s"LoadAddAfterArith_$slotId") {
            rx.jump(s"RouteWriteback_$slotId")
          }
        }
        ()
      }

      val invalidPath = HwInline.thread(s"${name}_InvalidPath_$slotId") { tx =>
        tx.Step(s"UnsupportedOpcode_$slotId") {
          tx.jump(s"ThreadExit_$slotId")
        }
        ()
      }

      StructuredControl
        .If(t, "DecodeIsAddi", opcode === ISA.OP_ADDI)(addiPath)
        .ElseIf(opcode === ISA.OP_LOAD)(loadPath)
        .ElseIf(opcode === ISA.OP_LOADADD)(loadAddPath)
        .Else(invalidPath)

      t.Step(s"RouteWriteback_$slotId") {
        SysCall.Call(writePort.WritebackAndClear(rd, result))
      }

      t.Step(s"ThreadExit_$slotId") {
      }
      SysCall.Call(SysCall.Return())
      ()
    }
  }

  final class Slot(val slotId: Int, val thread: HardwareThread, val instArg: UInt)

  class FetchProcess(program: Seq[ISA.Instr], initData: Seq[Int], localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
    private val issueWidth = 2
    val decode = spawn(new DecodeProcess(program.length max 1, initData, "Decode"))
    private val launcher = createLogic("Launcher")

    private val fetchPtr = (RegInit(0.U(log2Ceil(program.length + 1).W)))
    private val programRom = VecInit(program.map(ISA.encode))

    val slots = program.indices.map { i =>
      val thread = createThread(s"Slot${i}_inst")
      val instArg = (RegInit(0.U(ISA.instWidth.W)))
      new Slot(i, thread, instArg)
    }

    override def entry(): Unit = {
      require(issueWidth == 2, "Current MVP keeps only 1-bit intra-bundle order")

      for (slot <- slots) {
        slot.thread.entry {
          SysCall.Call(decode.install(slot.slotId, slot.instArg))
        }
      }
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
      (io.x1); (io.x2); (io.x3); (io.activeThreads)
      val fetch = spawn(new FetchProcess(program, initData, "Fetch"))
      val daemon = createLogic("Daemon")

      override def entry(): Unit = {
        daemon.run {
          io.x1  :=  SysCall.Call(fetch.decode.regFile.ReadCommitted(1.U))
          io.x2  :=  SysCall.Call(fetch.decode.regFile.ReadCommitted(2.U))
          io.x3  :=  SysCall.Call(fetch.decode.regFile.ReadCommitted(3.U))
          io.activeThreads  :=  PopCount(fetch.slots.map(_.thread.active))
        }
      }
    }

    Init.build()
  }

}
