package HwOS.kernel

import HwOS.kernel.system.GrantAbi
import _root_.circt.stage.ChiselStage
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class GrantAbiModule extends Module {
  val io = IO(new Bundle {
    val regGrantOk = Output(Bool())
    val levelGrantOk = Output(Bool())
    val pulseGrantOk = Output(Bool())
    val grantCount = Output(UInt(8.W))
  })

  io.regGrantOk := DontCare
  io.levelGrantOk := DontCare
  io.pulseGrantOk := DontCare
  io.grantCount := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    private val ownedReg = this.own(RegInit(0.U(8.W)))
    private val ownedLevel = this.own(WireInit(false.B))
    private val ownedPulse = this.own(WireInit(false.B))
    private val daemon = createLogic("Daemon")

    override def entry(): Unit = {
      this.grant(ownedReg, daemon)
      this.grant(ownedLevel, daemon, GrantAbi.LevelDrivenWire)
      this.grant(ownedPulse, daemon, GrantAbi.PulseWire)

      val entries = kernel.addressSpace.grantTableEntries.filter(_.ownerName == this.name)
      io.regGrantOk := entries.exists(e => e.signalObject == kernel.addressSpace.getAddressObject(ownedReg).get && e.abi == GrantAbi.RegisterWrite).B
      io.levelGrantOk := entries.exists(e => e.signalObject == kernel.addressSpace.getAddressObject(ownedLevel).get && e.abi == GrantAbi.LevelDrivenWire).B
      io.pulseGrantOk := entries.exists(e => e.signalObject == kernel.addressSpace.getAddressObject(ownedPulse).get && e.abi == GrantAbi.PulseWire).B
      io.grantCount := entries.length.U
    }
  }

  Init.build()
}

class GrantAbiSpec extends AnyFlatSpec {
  "grant ABI metadata" should "record default register-write grants and explicit wire ABI grants" in {
    simulate(new GrantAbiModule) { c =>
      c.io.regGrantOk.expect(true.B)
      c.io.levelGrantOk.expect(true.B)
      c.io.pulseGrantOk.expect(true.B)
      c.io.grantCount.expect(3.U)
    }
  }

  it should "reject default grant inference for non-register signals" in {
    assertThrows[Exception] {
      ChiselStage.emitCHIRRTL(new WireGrantWithoutAbiModule)
    }
  }
}

class WireGrantWithoutAbiModule extends Module {
  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    private val ownedWire = this.own(WireInit(false.B))
    private val daemon = createLogic("Daemon")

    override def entry(): Unit = {
      this.grant(ownedWire, daemon)
    }
  }

  Init.build()
}
