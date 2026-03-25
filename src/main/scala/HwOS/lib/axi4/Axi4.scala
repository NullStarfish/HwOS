package HwOS.lib.axi4

import HwOS.kernel.function.HwInline
import HwOS.kernel.system.SysCall
import chisel3._

object Axi4Lib {
  class Axi4ReadAddressChannel(val addrWidth: Int) extends Bundle {
    val valid = Output(Bool())
    val ready = Input(Bool())
    val addr = Output(UInt(addrWidth.W))
  }

  class Axi4ReadDataChannel(val dataWidth: Int) extends Bundle {
    val valid = Input(Bool())
    val ready = Output(Bool())
    val data = Input(UInt(dataWidth.W))
    val resp = Input(UInt(2.W))
  }

  class Axi4ReadOnly(val addrWidth: Int, val dataWidth: Int) extends Bundle {
    val ar = new Axi4ReadAddressChannel(addrWidth)
    val r = new Axi4ReadDataChannel(dataWidth)
  }

  def tieOff(bus: Axi4ReadOnly): Unit = {
    bus.ar.valid := false.B
    bus.ar.addr := 0.U
    bus.r.ready := false.B
  }

  def axi_read(bus: Axi4ReadOnly, addr: UInt): HwInline[UInt] = HwInline.thread("axi_read") { t =>
    val stepTag = s"axi_read_${System.identityHashCode(new Object())}"
    val responseData = WireInit(0.U(bus.dataWidth.W))

    t.Step(s"${stepTag}_IssueAddr") {
      bus.ar.valid := true.B
      bus.ar.addr := addr
      t.waitCondition(bus.ar.ready)
    }

    t.Step(s"${stepTag}_WaitData") {
      bus.r.ready := true.B
      t.waitCondition(bus.r.valid)
      when(bus.r.valid) {
        chisel3.assert(
          bus.r.resp === 0.U || bus.r.resp === 1.U,
          "axi_read only accepts AXI OKAY/EXOKAY responses in this demo",
        )
        responseData := bus.r.data
        SysCall.Return()
        printf(p"axi read data: $responseData\n")
      }
    }

    responseData
  }
}
