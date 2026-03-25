package HwOS.lib.axi4

import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{Kernel, SysCall}
import HwOS.lib.axi4.Axi4Lib._
import chisel3._

class Axi4ReadDemoModule(addrWidth: Int = 32, dataWidth: Int = 32) extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    val addr = Input(UInt(addrWidth.W))
    val busy = Output(Bool())
    val done = Output(Bool())
    val data = Output(UInt(dataWidth.W))
    val axi = new Axi4ReadOnly(addrWidth, dataWidth)
  })

  io.busy := DontCare
  io.done := DontCare
  io.data := DontCare
  tieOff(io.axi)

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    private val worker = createThread("Reader")
    private val daemon = createLogic("Daemon")
    private val addrReg = RegInit(0.U(addrWidth.W))
    private val dataReg = RegInit(0.U(dataWidth.W))


    private val dataReg_prev = RegNext(dataReg)
    when (dataReg =/= dataReg_prev) {
      printf(p"dataReg differs: new value: $dataReg\n")
    }

    override def entry(): Unit = {
      val readTxn = axi_read(io.axi, addrReg)

      worker.entry {
        val value = SysCall.Call(readTxn)
        worker.Prev.edge.add {
          dataReg := value
          io.done := true.B
        }
      }

      daemon.run {
        when(io.start && !worker.active) {
          addrReg := io.addr
          SysCall.Inline(SysCall.start(worker))
        }

        io.busy := worker.active
        io.data := dataReg
      }
    }
  }

  Init.build()
}
