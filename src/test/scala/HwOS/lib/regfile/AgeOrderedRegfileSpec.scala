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
    (io.committedX1)
    (io.committedX2)
    (io.forwardedX2)

    val regFile = spawn(new AgeOrderedScoreboardRegfileProcess(8, 32, 4, 8, zeroReg = true, "RegFile"))
    val ingress = spawn(new sync.MutexProcess(4, "Ingress"))
    val starter = createLogic("Starter")
    val monitor = createLogic("Monitor")
    val forwardedValue = (RegInit(0.U(32.W)))
    val launchDelay = (RegInit(0.U(3.W)))

    val oldWriter = createThread("OldWriter")
    val youngWriter = createThread("YoungWriter")
    val reader = createThread("Reader")

    override def entry(): Unit = {
      oldWriter.entry {
        val writePort = SysCall.Call(regFile.RequestWritePort(0))
        val delay = (RegInit(0.U(3.W)))

        oldWriter.Step("ReserveOld") {
          SysCall.Call(ingress.Lock(0))
          SysCall.Call(writePort.Reserve(1.U))
          SysCall.Call(ingress.Unlock(0))
        }

        oldWriter.Step("DelayOld") {
          delay  :=  delay + 1.U
          oldWriter.waitCondition(delay >= 3.U)
        }

        oldWriter.Step("CompleteOld") {
          SysCall.Call(writePort.WritebackAndClear(1.U, 11.U))
        }

        oldWriter.Step("ExitOld") {
        }
        SysCall.Call(SysCall.Return())
      }

      youngWriter.entry {
        val writePort = SysCall.Call(regFile.RequestWritePort(1))

        youngWriter.Step("ReserveYoung") {
          SysCall.Call(ingress.Lock(1))
          SysCall.Call(writePort.Reserve(2.U))
          SysCall.Call(ingress.Unlock(1))
        }

        youngWriter.Step("CompleteYoung") {
          SysCall.Call(writePort.WritebackAndClear(2.U, 22.U))
        }

        youngWriter.Step("ExitYoung") {
        }
        SysCall.Call(SysCall.Return())
      }

      reader.entry {
        val seen = (RegInit(0.U(32.W)))
        val delay = (RegInit(0.U(2.W)))
        reader.Step("DelayReader") {
          delay  :=  delay + 1.U
          reader.waitCondition(delay >= 1.U)
        }

        reader.Step("ReadForwarded") {
          seen  :=  SysCall.Call(regFile.Read(2.U))
        }

        reader.Step("Publish") {
          forwardedValue  :=  seen
        }

        reader.Step("ExitReader") {
        }
        SysCall.Call(SysCall.Return())
      }

      starter.run {
        launchDelay  :=  launchDelay + 1.U
        when(!oldWriter.active && !oldWriter.done) {
          SysCall.Call(SysCall.start(oldWriter))
        }
        when(launchDelay >= 1.U && !youngWriter.active && !youngWriter.done) {
          SysCall.Call(SysCall.start(youngWriter))
        }
        when(launchDelay >= 2.U && !reader.active && !reader.done) {
          SysCall.Call(SysCall.start(reader))
        }
      }

      monitor.run {
        io.committedX1  :=  SysCall.Call(regFile.ReadCommitted(1.U))
        io.committedX2  :=  SysCall.Call(regFile.ReadCommitted(2.U))
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
