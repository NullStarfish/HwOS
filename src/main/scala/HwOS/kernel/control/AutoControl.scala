package HwOS.kernel.control

import HwOS.kernel.thread.HardwareThread
import chisel3._

object AutoControl {
  final class Scope private[control] (private val thread: HardwareThread) {
    private var jumped = false
    private var blocked = false

    def Jump(target: String): Unit = {
      jumped = true
      thread.jump(target)
    }

    def Await(cond: Bool): Unit = {
      blocked = true
      thread.waitCondition(cond)
    }

    private[control] def canAutoHijack: Boolean = !jumped && !blocked
  }

  private final case class Block(name: String, anchor: Boolean, body: Scope => Unit)

  final class Chain(private val thread: HardwareThread, private val base: String) {
    private val blocks = scala.collection.mutable.ArrayBuffer.empty[Block]

    def AutoStep(name: String)(body: Scope => Unit): Chain = {
      blocks += Block(name, anchor = false, body)
      this
    }

    private[control] def Anchor(name: String)(body: Scope => Unit): Chain = {
      blocks += Block(name, anchor = true, body)
      this
    }

    def emit(): Unit = {
      for ((block, idx) <- blocks.zipWithIndex) {
        val isLast = idx == blocks.length - 1
        val nextIsAnchored = !isLast && blocks(idx + 1).anchor
        thread.Step(s"${base}_${block.name}") {
          val scope = new Scope(thread)
          block.body(scope)
          if (!isLast && !nextIsAnchored && scope.canAutoHijack) {
            thread.Next.hijack()
          }
        }
      }
    }
  }

  def Chain(thread: HardwareThread, base: String): Chain = new Chain(thread, base)
}
