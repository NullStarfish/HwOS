package HwOS.kernel.control

import HwOS.kernel.function.HwInline
import HwOS.kernel.lang.HwOSLanguage._
import HwOS.kernel.system.SysCall
import HwOS.kernel.thread.HardwareThread
import chisel3._

object StructuredControl {
  private val counters = scala.collection.mutable.HashMap.empty[Int, Int]
  private final case class Branch(label: String, cond: () => Bool, body: HwInline[Unit])

  final class LoopControl private[control] (
      thread: HardwareThread,
      val breakTarget: String,
      val continueTarget: String,
  ) {
    def Break(): Unit = {
      thread.jump(breakTarget)
    }

    def Continue(): Unit = {
      thread.jump(continueTarget)
    }
  }

  private def freshBase(thread: HardwareThread, prefix: String): String = {
    val key = System.identityHashCode(thread)
    val nextId = counters.getOrElse(key, 0)
    counters.update(key, nextId + 1)
    s"${prefix}_$nextId"
  }

  final class IfBuilder private[control] (
      thread: HardwareThread,
      base: String,
      branches: Vector[Branch],
  ) {
    private def condStepName(index: Int): String =
      if (index == 0) s"${base}_Cond" else s"${base}_${branches(index).label}_Cond"

    private def enterStepName(index: Int): String = s"${base}_${branches(index).label}_Enter"

    private def exitStepName(index: Int): String = s"${base}_${branches(index).label}_Exit"

    private def lower(elseBody: Option[HwInline[Unit]]): Unit = {
      for ((branch, index) <- branches.zipWithIndex) {
        val nextFailTarget =
          if (index + 1 < branches.length) condStepName(index + 1)
          else if (elseBody.isDefined) s"${base}_ElseEnter"
          else s"${base}_End"

        thread.Step(condStepName(index)) {
          when(branch.cond()) {
            thread.jump(enterStepName(index))
          }.otherwise {
            thread.jump(nextFailTarget)
          }
        }

        thread.Step(enterStepName(index)) {
          thread.Next.hijack()
        }

        SysCall.Call(branch.body, exitStepName(index))

        thread.Step(exitStepName(index)) {
          thread.jump(s"${base}_End")
        }
      }

      elseBody.foreach { body =>
        thread.Step(s"${base}_ElseEnter") {
          thread.Next.hijack()
        }

        SysCall.Call(body, s"${base}_ElseExit")

        thread.Step(s"${base}_ElseExit") {
          thread.jump(s"${base}_End")
        }
      }

      thread.Step(s"${base}_End") {}
    }

    def ElseIf(cond: => Bool)(body: HwInline[Unit]): IfBuilder = {
      new IfBuilder(
        thread = thread,
        base = base,
        branches = branches :+ Branch(s"ElseIf${branches.length}", () => cond, body),
      )
    }

    def Else(elseBody: HwInline[Unit]): Unit = {
      lower(Some(elseBody))
    }

    def End(): Unit = {
      lower(None)
    }
  }

  def If(thread: HardwareThread, prefix: String, cond: => Bool)(thenBody: HwInline[Unit]): IfBuilder =
    new IfBuilder(thread, freshBase(thread, prefix), Vector(Branch("Then", () => cond, thenBody)))

  def While(thread: HardwareThread, prefix: String, cond: => Bool)(body: LoopControl => HwInline[Unit]): Unit = {
    val base = freshBase(thread, prefix)
    val loop = new LoopControl(thread, breakTarget = s"${base}_End", continueTarget = s"${base}_Cond")

    thread.Step(s"${base}_Cond") {
      when(cond) {
        thread.jump(s"${base}_BodyEnter")
      }.otherwise {
        thread.jump(s"${base}_End")
      }
    }

    thread.Step(s"${base}_BodyEnter") {
      thread.Next.hijack()
    }

    SysCall.Call(body(loop), s"${base}_BodyExit")

    thread.Step(s"${base}_BodyExit") {
      thread.jump(s"${base}_Cond")
    }

    thread.Step(s"${base}_End") {}
  }

  def ForRange(
      thread: HardwareThread,
      prefix: String,
      start: Int,
      endExclusive: Int,
      width: Int = 32,
  )(body: (UInt, LoopControl) => HwInline[Unit]): UInt = {
    require(endExclusive >= start, s"ForRange endExclusive ($endExclusive) must be >= start ($start)")

    val base = freshBase(thread, prefix)
    val idx = thread.own(RegInit(start.U(width.W)))
    val loop = new LoopControl(thread, breakTarget = s"${base}_End", continueTarget = s"${base}_Inc")

    thread.Step(s"${base}_Init") {
      idx <== start.U(width.W)
    }

    thread.Step(s"${base}_Cond") {
      when(idx < endExclusive.U(width.W)) {
        thread.jump(s"${base}_BodyEnter")
      }.otherwise {
        thread.jump(s"${base}_End")
      }
    }

    thread.Step(s"${base}_BodyEnter") {
      thread.Next.hijack()
    }

    SysCall.Call(body(idx, loop), s"${base}_Inc")

    thread.Step(s"${base}_Inc") {
      idx <== idx + 1.U
      thread.Next.hijack()
    }

    thread.Step(s"${base}_BackEdge") {
      thread.jump(s"${base}_Cond")
    }

    thread.Step(s"${base}_End") {}

    idx
  }
}
