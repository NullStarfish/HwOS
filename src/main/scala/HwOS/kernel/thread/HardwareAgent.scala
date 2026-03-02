package HwOS.kernel.thread

import chisel3._
import HwOS.kernel.context.{ContextScope, HwContext, HwContextEntity, LogicCtx}
import HwOS.kernel.process.HwProcess
import HwOS.kernel.thread.backend.{DefaultThreadBackend, InlineThreadBackend}

trait HardwareAgent extends HwContextEntity {
  val owner: HwProcess
  val name: String
  val debugEnable: Boolean


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
    val backend: ThreadBackendKind = ThreadBackendKind.Default,
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
}

private[kernel] final class DefaultHardwareThread(
    name: String,
    owner: HwProcess,
    debugEnable: Boolean = true,
    backend: ThreadBackendKind = ThreadBackendKind.Default,
) extends HardwareThread(name, owner, debugEnable, backend)
    with DefaultThreadBackend

private[kernel] final class InlineHardwareThread(
    name: String,
    owner: HwProcess,
    debugEnable: Boolean = true,
    backend: ThreadBackendKind = ThreadBackendKind.Inline,
) extends HardwareThread(name, owner, debugEnable, backend)
    with InlineThreadBackend
