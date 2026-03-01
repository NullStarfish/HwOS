
package HwOS.kernel

import chisel3._
import scala.collection.mutable.ArrayBuffer

trait HardwareAgent extends HwOwner {
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

  val nodes: ArrayBuffer[ThreadStepNode]
  private[kernel] var currentGeneratingNode: ThreadStepNode
  private[kernel] def runtime: ThreadRuntime
  def lifecycleReady: Boolean

  def markExternalStart(): Unit
  def markExternalKill(): Unit
  def markDoneObserved(): Unit
  def markActiveObserved(): Unit
  def markLifecycleGranted(): Unit
  def markLeaseTracking(): Unit
  def markFork(): Unit
}

private[kernel] final class DefaultHardwareThread(
    name: String,
    owner: HwProcess,
    debugEnable: Boolean = true,
    backend: ThreadBackendKind = ThreadBackendKind.Default,
) extends HardwareThread(name, owner, debugEnable, backend)
    with DefaultThreadRuntimeBackend
    with DefaultThreadControlBackend

private[kernel] final class InlineHardwareThread(
    name: String,
    owner: HwProcess,
    debugEnable: Boolean = true,
    backend: ThreadBackendKind = ThreadBackendKind.Inline,
) extends HardwareThread(name, owner, debugEnable, backend)
    with InlineThreadRuntimeBackend
    with DefaultThreadControlBackend
