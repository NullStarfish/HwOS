package HwOS.kernel.function

import HwOS.kernel.debug.CallStack
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{OSReaper, OSReaperManagedLogic}
import HwOS.kernel.thread.{HardwareAgent, HardwareThread, ThreadDebugApi}
import chisel3._

private[kernel] final class FunctionCallBindingState(
    val activation: HardwareThread,
    val callActive: Bool,
    val activeBindingId: UInt,
)

private[kernel] final class FunctionCallLease(
    val bindingId: Int,
    val caller: HardwareThread,
    val activation: HardwareThread,
    val binding: FunctionCallBindingState,
    val callPending: Bool,
) {
  private val bindingIdValue = bindingId.U(binding.activeBindingId.getWidth.W)

  def isActive: Bool =
    binding.callActive && binding.activeBindingId === bindingIdValue

  private[kernel] def forceReclaim(agent: HardwareAgent): Unit = {
    when(isActive) {
      OSReaper.reclaimThread(activation, agent.kernel.managedReaperEntities, agent)
      OSReaper.forceAssign(binding.callActive, false.B)
      OSReaper.forceAssign(binding.activeBindingId, 0.U(binding.activeBindingId.getWidth.W))
    }

    OSReaper.forceAssign(callPending, false.B)
  }
}

private[kernel] final class FunctionRuntimeHost[T](
    functionName: String,
    body: HardwareThread => T,
    owner: HwProcess,
) {
  private var resultHandle: Option[T] = None
  private var callBindingState: Option[FunctionCallBindingState] = None
  private var reaperHolder: Option[OSReaperManagedLogic] = None
  private var nextBindingId: Int = 1

  val activation: HardwareThread = {
    val thread = owner.createThread(name = s"${functionName}_activation")
    CallStack.withIsolatedStack {
      thread.entry {
        resultHandle = Some(body(thread))
      }
    }
    thread match {
      case debugThread: ThreadDebugApi if !debugThread.hasReturningStep =>
        throw new Exception(
          s"[HwOS] HwFunction '$functionName' is call-terminated and must contain an explicit SysCall.Return().",
        )
      case _ =>
    }
    thread
  }

  def result: T =
    resultHandle.getOrElse(throw new Exception(s"[HwOS] Function '$functionName' result handle was not initialized."))

  def bindingState: FunctionCallBindingState =
    callBindingState.getOrElse {
      val binding = new FunctionCallBindingState(
        activation = activation,
        callActive = RegInit(false.B),
        activeBindingId = RegInit(0.U(32.W)),
      )
      callBindingState = Some(binding)
      binding
    }

  def allocateCallLease(caller: HardwareThread): FunctionCallLease = {
    val binding = bindingState
    val holder = reaperHolder.getOrElse {
      val created = owner.createReaperManagedLogic(s"${functionName}_reaper")
      reaperHolder = Some(created)
      created
    }
    val lease = new FunctionCallLease(
      bindingId = nextBindingId,
      caller = caller,
      activation = binding.activation,
      binding = binding,
      callPending = RegInit(false.B),
    )
    nextBindingId += 1
    holder.registerReclaimEntry(caller, lease.isActive) { agent =>
      lease.forceReclaim(agent)
    }
    lease
  }

  def reaperLogic: Option[OSReaperManagedLogic] = reaperHolder
}
