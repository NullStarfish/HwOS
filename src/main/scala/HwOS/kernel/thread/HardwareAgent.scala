package HwOS.kernel.thread

import chisel3._
import HwOS.kernel.context.{ContextScope, HwContext, HwContextEntity, LogicCtx}
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{Kernel, RuntimeContext}

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
  ctx.bindIsActive(true.B)

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
  private[kernel] val lifecycleAcl = scala.collection.mutable.Set[HwContext]()

  private[kernel] def grantLifecycleAccess(target: HwContext): Unit = {
    lifecycleAcl += target
  }

  private[kernel] def requireLifecycleAccess(actor: HwContext, op: String): Unit = {
    if (!lifecycleAcl.contains(actor)) {
      throw new Exception(
        s"[HwOS Lifecycle Error] '${actor.name}' is not allowed to $op thread '${name}'. Use grantLifecycle() first.",
      )
    }
  }

  private[kernel] def runtimeStart(): Unit
  private[kernel] def runtimeExit(): Unit
  private[kernel] def reset(): Unit
  private[kernel] def runtimeHandle: RuntimeContext
}

private[kernel] final class KernelStepHardwareThread(
    name: String,
    owner: HwProcess,
    debugEnable: Boolean = true,
) extends HardwareThread(name, owner, debugEnable)
    with ThreadCore
