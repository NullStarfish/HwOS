package HwOS.lib.regfile

import HwOS.kernel._
import HwOS.kernel.HwOSLanguage._
import HwOS.lib.regfile.RegfileLib._
import HwOS.stdlib.sync
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class AgeOrderedRegfileModule extends Module {
  val io = IO(new Bundle {
    val committedX1 = Output(UInt(32.W))
    val committedX2 = Output(UInt(32.W))
    val forwardedX2 = Output(UInt(32.W))
  })

  io.committedX1 := DontCare
  io.committedX2 := DontCare
  io.forwardedX2 := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    val regFile = spawn(new AgeOrderedScoreboardRegfileProcess(8, 32, 4, 8, zeroReg = true, "RegFile"))
    val ingress = spawn(new sync.SemaphoreProcess(4, initialCount = 1, "Ingress"))
    val starter = createLogic("Starter")
    val monitor = createLogic("Monitor")
    val forwardedValue = (RegInit(0.U(32.W)))
    val launchDelay = (RegInit(0.U(3.W)))

    val oldWriter = createThread("OldWriter")
    val youngWriter = createThread("YoungWriter")
    val reader = createThread("Reader")

    override def entry(): Unit = {
      oldWriter.entry {
        val writePort = SysCall.Inline(regFile.RequestWritePort(0))
        val ingressPermit = SysCall.Inline(ingress.RequestLease(0))
        val delay = (RegInit(0.U(3.W)))

        oldWriter.Step("ReserveOld") {
          SysCall.Inline(ingressPermit.Acquire())
          SysCall.Inline(writePort.Reserve(1.U))
          SysCall.Inline(ingressPermit.Release())
        }

        oldWriter.Step("DelayOld") {
          delay  :=  delay + 1.U
          oldWriter.waitCondition(delay >= 3.U)
        }

        oldWriter.Step("CompleteOld") {
          SysCall.Inline(writePort.WritebackAndClear(1.U, 11.U))
        }

        oldWriter.Step("ExitOld") {
        }
        SysCall.Return()
      }

      youngWriter.entry {
        val writePort = SysCall.Inline(regFile.RequestWritePort(1))
        val ingressPermit = SysCall.Inline(ingress.RequestLease(1))

        youngWriter.Step("ReserveYoung") {
          SysCall.Inline(ingressPermit.Acquire())
          SysCall.Inline(writePort.Reserve(2.U))
          SysCall.Inline(ingressPermit.Release())
        }

        youngWriter.Step("CompleteYoung") {
          SysCall.Inline(writePort.WritebackAndClear(2.U, 22.U))
        }

        youngWriter.Step("ExitYoung") {
        }
        SysCall.Return()
      }

      reader.entry {
        val seen = (RegInit(0.U(32.W)))
        val delay = (RegInit(0.U(2.W)))
        reader.Step("DelayReader") {
          delay  :=  delay + 1.U
          reader.waitCondition(delay >= 1.U)
        }

        reader.Step("ReadForwarded") {
          seen  :=  SysCall.Inline(regFile.Read(2.U))
        }

        reader.Step("Publish") {
          forwardedValue  :=  seen
        }

        reader.Step("ExitReader") {
        }
        SysCall.Return()
      }

      starter.run {
        launchDelay  :=  launchDelay + 1.U
        when(!oldWriter.active && !oldWriter.done) {
          SysCall.Inline(SysCall.start(oldWriter))
        }
        when(launchDelay >= 1.U && !youngWriter.active && !youngWriter.done) {
          SysCall.Inline(SysCall.start(youngWriter))
        }
        when(launchDelay >= 2.U && !reader.active && !reader.done) {
          SysCall.Inline(SysCall.start(reader))
        }
      }

      monitor.run {
        io.committedX1  :=  SysCall.Inline(regFile.ReadCommitted(1.U))
        io.committedX2  :=  SysCall.Inline(regFile.ReadCommitted(2.U))
        io.forwardedX2  :=  forwardedValue
      }
    }
  }

  Init.build()
}

class AgeOrderedRegfileSpec extends AnyFlatSpec {
  "AgeOrderedScoreboardRegfileProcess" should "forward speculative values before global commit order reaches them" in {
    simulate(new AgeOrderedRegfileModule) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      c.clock.step(8)
      c.io.forwardedX2.expect(22.U)
      c.io.committedX2.expect(0.U)

      c.clock.step(6)
      c.io.committedX1.expect(11.U)
      c.io.committedX2.expect(22.U)
    }
  }
}
