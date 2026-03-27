package HwOS

package object kernel {
  // Stable kernel facade: core context, thread, function, process, system, and language APIs.
  type HwContext = context.HwContext
  val HwContext = context.HwContext
  type HwContextEntity = context.HwContextEntity
  type ExecutionContext = context.ExecutionContext
  type LogicCtx = context.LogicCtx
  val LogicCtx = context.LogicCtx
  type ThreadCtx = context.ThreadCtx
  val ThreadCtx = context.ThreadCtx
  type AtomicCtx = context.AtomicCtx
  val AtomicCtx = context.AtomicCtx
  val ContextScope = context.ContextScope

  type HardwareAgent = thread.HardwareAgent
  type HardwareLogic = thread.HardwareLogic
  type HardwareThread = thread.HardwareThread
  type StepRef = thread.StepRef
  val StepRef = thread.StepRef
  type ThreadControlApi = thread.ThreadControlApi
  type ThreadRuntimeApi = thread.ThreadRuntimeApi

  type HwInline[T] = function.HwInline[T]
  val HwInline = function.HwInline

  val HwOSLanguage = lang.HwOSLanguage

  type HwProcess = process.HwProcess
  val ProcessBuilder = process.ProcessBuilder
  type ProcEnv = process.ProcEnv
  val ProcEnv = process.ProcEnv

  type Kernel = system.Kernel
  type KernelAddressSpace = system.KernelAddressSpace
  type RuntimeContext = system.RuntimeContext
  val SysCall = system.SysCall

  type ExportCapability = memory.ExportCapability
  val ExportCapability = memory.ExportCapability
  type ExportedSymbol[T <: chisel3.Data] = memory.ExportedSymbol[T]
  type VirtualHandle[T <: chisel3.Data] = memory.VirtualHandle[T]

  val CallStack = debug.CallStack
}
