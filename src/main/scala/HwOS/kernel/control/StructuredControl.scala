package HwOS.kernel.control

import HwOS.kernel.thread.HardwareThread
import chisel3._

object StructuredControl {
  private val counters = scala.collection.mutable.HashMap.empty[Int, Int]

  private def freshBase(thread: HardwareThread, prefix: String): String = {
    val key = System.identityHashCode(thread)
    val nextId = counters.getOrElse(key, 0)
    counters.update(key, nextId + 1)
    s"${prefix}_$nextId"
  }

  final class IfBuilder private[control] (
      thread: HardwareThread,
      base: String,
      cond: => Bool,
      thenBlock: => Unit,
  ) {
    def Else(elseBlock: => Unit): Unit = {
      AutoControl.Chain(thread, base)
        .Anchor("Cond") { ac =>
        when(cond) {
            ac.Jump(s"${base}_Then")
        }.otherwise {
            ac.Jump(s"${base}_Else")
        }
        }
        .Anchor("Then") { _ =>
          thenBlock
        }
        .AutoStep("ThenExit") { ac =>
          ac.Jump(s"${base}_End")
        }
        .Anchor("Else") { _ =>
          elseBlock
        }
        .AutoStep("ElseExit") { ac =>
          ac.Jump(s"${base}_End")
        }
        .Anchor("End") { _ => }
        .emit()
    }

    def End(): Unit = {
      AutoControl.Chain(thread, base)
        .Anchor("Cond") { ac =>
        when(cond) {
            ac.Jump(s"${base}_Then")
        }.otherwise {
            ac.Jump(s"${base}_End")
        }
        }
        .Anchor("Then") { _ =>
          thenBlock
        }
        .AutoStep("ThenExit") { ac =>
          ac.Jump(s"${base}_End")
        }
        .Anchor("End") { _ => }
        .emit()
    }
  }

  def If(thread: HardwareThread, prefix: String, cond: => Bool)(thenBlock: => Unit): IfBuilder =
    new IfBuilder(thread, freshBase(thread, prefix), cond, thenBlock)

  def While(thread: HardwareThread, prefix: String, cond: => Bool)(body: => Unit): Unit = {
    val base = freshBase(thread, prefix)
    AutoControl.Chain(thread, base)
      .Anchor("Cond") { ac =>
        when(cond) {
          ac.Jump(s"${base}_Body")
        }.otherwise {
          ac.Jump(s"${base}_End")
        }
      }
      .Anchor("Body") { _ =>
        body
      }
      .AutoStep("BodyExit") { ac =>
        ac.Jump(s"${base}_Cond")
      }
      .Anchor("End") { _ => }
      .emit()
  }

  def ForRange(
      thread: HardwareThread,
      prefix: String,
      start: Int,
      endExclusive: Int,
      width: Int = 32,
  )(body: UInt => Unit): UInt = {
    require(endExclusive >= start, s"ForRange endExclusive ($endExclusive) must be >= start ($start)")

    val base = freshBase(thread, prefix)
    val idx = thread.own(RegInit(start.U(width.W)))
    AutoControl.Chain(thread, base)
      .Anchor("Init") { _ =>
        idx := start.U(width.W)
      }
      .Anchor("Cond") { ac =>
        when(idx < endExclusive.U(width.W)) {
          ac.Jump(s"${base}_Body")
        }.otherwise {
          ac.Jump(s"${base}_End")
        }
      }
      .Anchor("Body") { _ =>
        body(idx)
      }
      .AutoStep("Inc") { _ =>
        idx := idx + 1.U
      }
      .AutoStep("BackEdge") { ac =>
        ac.Jump(s"${base}_Cond")
      }
      .Anchor("End") { _ => }
      .emit()

    idx
  }
}
