package HwOS

package object kernel {
  // Stable kernel facade: core context, thread, function, process, system, and language APIs.
  type HwLease = context.HwLease
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
  type ThreadStepNode = thread.ThreadStepNode
  type ThreadNextApi = thread.ThreadNextApi
  type ThreadControlApi = thread.ThreadControlApi
  type ThreadRuntimeApi = thread.ThreadRuntimeApi
  type ThreadBackendKind = thread.ThreadBackendKind
  val ThreadBackendKind = thread.ThreadBackendKind
  type ThreadLifecycleLease = thread.ThreadLifecycleLease
  type DefaultThreadLifecycleLease = thread.DefaultThreadLifecycleLease
  type InlineThreadLifecycleLease = thread.InlineThreadLifecycleLease

  type HwFunction[T] = function.HwFunction[T]
  val HwFunction = function.HwFunction

  val HwOSLanguage = lang.HwOSLanguage

  type HwProcess = process.HwProcess
  val ProcessBuilder = process.ProcessBuilder
  type ProcEnv = process.ProcEnv
  val ProcEnv = process.ProcEnv

  type Kernel = system.Kernel
  type OSReaperProcess = system.OSReaperProcess
  val SysCall = system.SysCall

  val CallStack = debug.CallStack
}
