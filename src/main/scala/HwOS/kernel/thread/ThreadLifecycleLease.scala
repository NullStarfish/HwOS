package HwOS.kernel.thread

import chisel3._
import HwOS.kernel.context.{AtomicCtx, ContextScope, HwLease}
import HwOS.kernel.lang.HwOSLanguage._

trait ThreadLifecycleLease extends HwLease {
  def active: Bool
  def done: Bool

  def startLifecycle(): Unit
  def exitLifecycle(): Unit
}

final class DefaultThreadLifecycleLease(
    thread: HardwareThread,
    activeReg: Bool,
    doneReg: Bool,
    pcAccessor: () => UInt,
    entryPcAccessor: () => UInt = () => 0.U,
) extends ThreadLifecycleLease {
  override def isActive: Bool = activeReg

  override def active: Bool = activeReg && !thread.ctx.kernelKillSignal
  override def done: Bool = doneReg && !thread.ctx.kernelKillSignal

  override def startLifecycle(): Unit = {
    ContextScope.withContext(AtomicCtx(thread)) {
      activeReg <==! true.B
      pcAccessor() <==! entryPcAccessor()
      doneReg <==! false.B
    }
  }

  override def exitLifecycle(): Unit = {
    val pc = pcAccessor()
    pc <== entryPcAccessor()
    doneReg <== true.B
    activeReg <== false.B
  }

  override private[kernel] def forceReclaim(agent: HardwareAgent): Unit = {
    thread.grant(activeReg, agent)
    thread.grant(doneReg, agent)
    thread.grant(pcAccessor(), agent)

    activeReg <==! false.B
    doneReg <==! false.B
    pcAccessor() <==! entryPcAccessor()
  }
}

final class InlineThreadLifecycleLease(
    thread: HardwareThread,
    pcAccessor: () => UInt,
    terminalPcAccessor: () => UInt,
) extends ThreadLifecycleLease {
  override def isActive: Bool = pcAccessor() =/= terminalPcAccessor()

  override def active: Bool = (pcAccessor() =/= terminalPcAccessor()) && !thread.ctx.kernelKillSignal
  override def done: Bool = (pcAccessor() === terminalPcAccessor()) && !thread.ctx.kernelKillSignal

  override def startLifecycle(): Unit = {
    throw new Exception(s"[HwOS] Thread '${thread.name}' uses Inline backend and cannot be started explicitly.")
  }

  override def exitLifecycle(): Unit = {
    pcAccessor() <== terminalPcAccessor()
  }

  override private[kernel] def forceReclaim(agent: HardwareAgent): Unit = {
    thread.grant(pcAccessor(), agent)
    pcAccessor() <==! terminalPcAccessor()
  }
}
