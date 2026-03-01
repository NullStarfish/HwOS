package HwOS.kernel

trait InlineThreadRuntimeBackend extends DefaultThreadRuntimeBackend { self: HardwareThread =>
  override def chooseRuntime(): ThreadRuntime = {
    new InlineThreadRuntime(this)
  }
}
