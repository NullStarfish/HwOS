package HwOS.lib.axi4

import HwOS.lib.axi4.Axi4Lib._
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class Axi4ReadHarness(addrWidth: Int = 32, dataWidth: Int = 32) extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    val addr = Input(UInt(addrWidth.W))
    val busy = Output(Bool())
    val done = Output(Bool())
    val data = Output(UInt(dataWidth.W))
  })

  val dut = Module(new Axi4ReadDemoModule(addrWidth, dataWidth))

  io.busy := dut.io.busy
  io.done := dut.io.done
  io.data := dut.io.data

  dut.io.start := io.start
  dut.io.addr := io.addr

  val readPending = RegInit(false.B)
  val readAddr = RegInit(0.U(addrWidth.W))
  val readDelay = RegInit(0.U(2.W))
  val memory = VecInit(Seq.tabulate(16)(i => (i * 7 + 3).U(dataWidth.W)))

  dut.io.axi.ar.ready := !readPending
  dut.io.axi.r.valid := false.B
  dut.io.axi.r.data := 0.U
  dut.io.axi.r.resp := 0.U

  when(dut.io.axi.ar.valid && dut.io.axi.ar.ready) {
    readPending := true.B
    readAddr := dut.io.axi.ar.addr
    readDelay := 0.U
  }

  when(readPending) {
    readDelay := readDelay + 1.U
    when(readDelay >= 1.U) {
      dut.io.axi.r.valid := true.B
      dut.io.axi.r.data := memory(readAddr(3, 0))
      dut.io.axi.r.resp := 0.U
      when(dut.io.axi.r.ready) {
        readPending := false.B
      }
    }
  }
}

class Axi4ReadApiSpec extends AnyFlatSpec {
  "axi_read" should "turn AXI4 read-channel handshakes into a blocking API" in {
    simulate(new Axi4ReadHarness()) { c =>
      c.reset.poke(true.B)
      c.io.start.poke(false.B)
      c.io.addr.poke(0.U)
      c.clock.step()

      c.reset.poke(false.B)
      c.io.addr.poke(5.U)
      c.io.start.poke(true.B)
      c.clock.step()
      c.io.start.poke(false.B)

      var cycles = 0
      while (c.io.done.peek().litValue == 0 && cycles < 20) {
        c.clock.step()
        cycles += 1
      }

      c.io.done.expect(true.B)
      c.io.data.expect(38.U)

      c.clock.step(3)
    }
  }
}
