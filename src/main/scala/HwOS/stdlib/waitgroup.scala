package HwOS.stdlib.sync

import chisel3._
import chisel3.util._
import HwOS.kernel.function.HwInline
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.Kernel

class WaitGroupProcess(val maxClients: Int, localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {

  private val count = RegInit(0.U(32.W))
  private val adds = WireInit(VecInit(Seq.fill(maxClients)(0.U(32.W))))
  private val dones = WireInit(VecInit(Seq.fill(maxClients)(false.B)))

  for (i <- 0 until maxClients) {
    (adds(i))
    (dones(i))
  }

  private val totalAdd = adds.reduce(_ + _)
  private val totalDone = PopCount(dones)
  private val nextCount = count + totalAdd - totalDone

  override def entry(): Unit = {
    val main = createLogic("Main")
    main.run {
      count := nextCount
    }
  }

  def Add(id: Int, delta: UInt): HwInline[Unit] = HwInline.stateless(s"WG_Add_$id") { _ =>
    adds(id) := delta
  }

  def Done(id: Int): HwInline[Unit] = HwInline.stateless(s"WG_Done_$id") { _ =>
    dones(id) := true.B
  }

  def Wait(): HwInline[Unit] = HwInline.atomic("WG_Wait") { t =>
    t.waitCondition(nextCount === 0.U)
    when(nextCount === 0.U) {
      t.hijack(t.Next)
    }
    ()
  }
}
