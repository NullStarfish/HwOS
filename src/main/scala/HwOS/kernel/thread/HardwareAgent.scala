package HwOS.kernel.thread

import chisel3._
import HwOS.kernel.context.{ContextScope, HwContext, HwContextEntity, LogicCtx}
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.RuntimeContext
import HwOS.kernel.system.Kernel

trait HardwareAgent extends HwContextEntity {
  val owner: HwProcess
  val name: String
  val debugEnable: Boolean
  override def kernel: Kernel = owner.kernel


  def agentPrint(p:Printable): Unit = {
    if (debugEnable) {
      printf(p"[$name] " + p + p"\n")
    }
  }
  def agentPrint(msg: String): Unit = {
    if (debugEnable) {
      printf(s"[$name] $msg\n")
    }
  }


}

class HardwareLogic(val name: String, val owner: HwProcess, val debugEnable: Boolean = true) extends HardwareAgent {
  def run(block: => Unit): Unit = {
    ContextScope.withContext(LogicCtx(this)) {
      block
    }
  }
}

abstract class HardwareThread(
    val name: String,
    val owner: HwProcess,
    val debugEnable: Boolean = true,
) extends HardwareAgent
    with ThreadControlApi
    with ThreadRuntimeApi {
  val tls = scala.collection.mutable.Map[String, HwContext]() //used for visibility

  private[kernel] def runtimeStart(): Unit
  private[kernel] def runtimeExit(): Unit
  def reset(): Unit
  private[kernel] def runtimeHandle: RuntimeContext
}

private[kernel] final class KernelStepHardwareThread(
    name: String,
    owner: HwProcess,
    debugEnable: Boolean = true,
) extends HardwareThread(name, owner, debugEnable)
    with ThreadCore
