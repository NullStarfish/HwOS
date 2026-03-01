package HwOS.kernel

import chisel3._

trait DefaultThreadRuntimeBackend extends ThreadRuntimeApi { self: HardwareThread =>
  private[kernel] var runtimeEntity: ThreadRuntime = _
  private[kernel] var generatedEntry: Boolean = false
  private[kernel] var hasExitPath: Boolean = false
  private[kernel] val freeze: Bool = WireInit(false.B)

  override private[kernel] def runtime: ThreadRuntime = {
    if (runtimeEntity == null) {
      throw new Exception(s"[HwOS] Thread '$name' runtime is not initialized")
    }
    runtimeEntity
  }

  protected[kernel] def runtimePc: UInt = runtime.pc

  override def active: Bool = {
    markActiveObserved()
    runtime.active
  }

  override def done: Bool = {
    markDoneObserved()
    runtime.done
  }

  override def exit(): Unit = {
    hasExitPath = true
    runtime.exit()
  }

  override def lifecycleReady: Boolean = runtimeEntity != null && runtime.lifecycleReady

  def bindRuntimeViews(): Unit = {
    runtime.bindContext()
  }

  def verifyExitPath(): Unit = {}

  def maybePrintCapabilitySummary(): Unit = {
    ()
  }

  def chooseRuntime(): ThreadRuntime = {
    new PersistentThreadRuntime(this)
  }

  override def markExternalStart(): Unit = {
    ()
  }

  override def markExternalKill(): Unit = {
    ()
  }

  override def markDoneObserved(): Unit = {
    ()
  }

  override def markActiveObserved(): Unit = {
    ()
  }

  override def markLifecycleGranted(): Unit = {
    ()
  }

  override def markLeaseTracking(): Unit = {
    ()
  }

  override def markFork(): Unit = {
    ()
  }
}
