package HwOS.kernel

import HwOS.kernel.HwOSLanguage._
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class HwFunctionProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  val worker = createThread("Worker")
  val out = this.own(RegInit(0.U(8.W)))
  val callCount = this.own(RegInit(0.U(8.W)))

  private val addOne = HwFunction.thread("AddOne") { t =>
    this.grant(out, t)
    this.grant(callCount, t)
    val localTmp = t.own(RegInit(0.U(8.W)))

    t.Step("LoadTmp") {
      localTmp <== out + 1.U
    }
    t.Step("Commit") {
      out <== localTmp
      callCount <== callCount + 1.U
    }
    SysCall.Call(SysCall.Return())
    ()
  }

  def functionActivationThread: Option[HardwareThread] = addOne.debugActivationThread

  override def entry(): Unit = {
    this.grant(out, worker)
    this.grant(callCount, worker)

    worker.entry {
      worker.Step("Init") {
        out <== 0.U
      }

      SysCall.Call(addOne.Invoke("AfterCall"))

      worker.Step("AfterCall") {
        out <== out + 10.U
      }

      SysCall.Call(addOne.Invoke("AfterSecondCall"))

      worker.Step("AfterSecondCall") {
        out <== out + 20.U
      }

      SysCall.Call(SysCall.Return())
    }
  }
}

class HwFunctionModule extends Module {
  val io = IO(new Bundle {
    val out = Output(UInt(8.W))
    val callCount = Output(UInt(8.W))
    val done = Output(Bool())
    val functionCodeRegistered = Output(Bool())
    val activationOwnedStateCount = Output(UInt(8.W))
  })

  io.out := DontCare
  io.callCount := DontCare
  io.done := DontCare
  io.functionCodeRegistered := DontCare
  io.activationOwnedStateCount := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    this.own(io.out)
    this.own(io.callCount)
    this.own(io.done)
    this.own(io.functionCodeRegistered)
    this.own(io.activationOwnedStateCount)

    val proc = spawn(new HwFunctionProcess("FnProc"))
    val daemon = createLogic("Daemon")

    override def entry(): Unit = {
      this.grant(io.out, daemon, GrantAbi.LevelDrivenWire)
      this.grant(io.callCount, daemon, GrantAbi.LevelDrivenWire)
      this.grant(io.done, daemon, GrantAbi.LevelDrivenWire)
      this.grant(io.functionCodeRegistered, daemon, GrantAbi.LevelDrivenWire)
      this.grant(io.activationOwnedStateCount, daemon, GrantAbi.LevelDrivenWire)
      this.grantLifecycle(proc.worker, daemon)

      daemon.run {
        val activation = proc.functionActivationThread.getOrElse(
          throw new Exception("[HwOS Test] Function activation thread was not initialized."),
        )
        when(!proc.worker.active && !proc.worker.done) {
          SysCall.Call(SysCall.start(proc.worker))
        }
        io.out <== proc.out
        io.callCount <== proc.callCount
        io.done <== proc.worker.done
        io.functionCodeRegistered <== kernel.addressSpace.codeTableEntries.exists(_.segment.ownerName == activation.name).B
        io.activationOwnedStateCount <== kernel.addressSpace.stateTableEntries.count(_.ownerName == activation.name).U
      }
    }
  }

  Init.build()
}

class HwFunctionSpec extends AnyFlatSpec {
  "HwFunction v1" should "run as a real blocking activation with its own code segment and local slot state" in {
    simulate(new HwFunctionModule) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      var cycles = 0
      while (c.io.done.peek().litValue == 0 && cycles < 30) {
        c.clock.step()
        cycles += 1
      }

      c.io.done.expect(true.B)
      c.io.out.expect(32.U)
      c.io.callCount.expect(2.U)
      c.io.functionCodeRegistered.expect(true.B)
      assert(c.io.activationOwnedStateCount.peek().litValue >= 4, "function activation should own local slot state")
    }
  }
}
