package HwOS.kernel.thread

import chisel3._
import HwOS.kernel.context.{ContextScope, ThreadCtx}
import HwOS.kernel.system.CallProtocolContext.CallSiteSnapshot
import HwOS.kernel.system.{RuntimeContext, RuntimeLifecycle}
import HwOS.kernel.thread.step.EdgeAction
import HwOS.kernel.thread.step.{ControlProgram, ControlProgramBuilder, CurrentProgramContext, ThreadRuntimeLogic}
import scala.collection.mutable.ArrayBuffer

trait ThreadCore
    extends ThreadControlApi
    with ThreadRuntimeApi
    with ThreadDebugApi
    with StepEdgeRecorder { self: HardwareThread =>
  private[kernel] var generatedEntry: Boolean = false
  private[kernel] var hasExitPath: Boolean = false
  private[kernel] val freeze: Bool = WireInit(false.B)

  private var programBuilderOpt: Option[ControlProgramBuilder] = None
  private var compiledProgramOpt: Option[ControlProgram.CompiledControlProgram] = None
  private var threadHostOpt: Option[ThreadHost] = None
  private val resetHooks = ArrayBuffer.empty[() => Unit]
  private val debugValidation = new ThreadDebugValidation

  private def programBuilder: ControlProgramBuilder =
    programBuilderOpt.getOrElse(throw new Exception(s"[HwOS] Thread program is not initialized for thread '$name'."))

  private def programFacade: ThreadProgramFacade =
    new ThreadProgramFacade(programBuilder)

  private def compiledProgram: ControlProgram.CompiledControlProgram =
    compiledProgramOpt.getOrElse(throw new Exception(s"[HwOS] Compiled program is not initialized for thread '$name'."))

  private def runtime: RuntimeContext =
    threadHost.runtimeStateHandle

  private def threadHost: ThreadHost =
    threadHostOpt.getOrElse(throw new Exception(s"[HwOS] Thread host is not allocated for thread '$name'."))

  override private[kernel] def runtimeHandle: RuntimeContext = runtime

  override def active: Bool =
    threadHostOpt.map(_.isActive).getOrElse(false.B)

  override def done: Bool =
    threadHostOpt.map(_.isDone).getOrElse(false.B)

  override def pc: UInt = threadHostOpt.map(_.pc).getOrElse(0.U)

  override private[kernel] def runtimeStart(): Unit = {
    threadHost.start()
  }

  override private[kernel] def runtimeExit(): Unit = {
    hasExitPath = true
    if (HwOS.kernel.thread.step.PreLoweringAnalysis.isActive) {
      return
    }
    threadHost.exit()
  }

  override def reset(): Unit = {
    threadHost.reset()
    resetHooks.foreach(_.apply())
  }

  override def registerReset(block: => Unit): Unit = {
    resetHooks += (() => block)
  }

  private def verifyExitPath(): Unit = {}
  private def maybePrintCapabilitySummary(): Unit = ()

  override def debugSteps: Seq[DebugStepRecord] =
    compiledProgramOpt.toSeq.flatMap(_.steps).map { step =>
      new DebugStepRecord(
        step.name,
        step.allocatedAddress,
        step.loweredStandalone,
        step.threadCallStack,
        step.invokedCalls,
        edgeActionNames(step.edgeActions.toSeq).toSeq,
      )
    }

  override def hasReturningStep: Boolean =
    debugValidation.hasReturningStep(compiledProgramOpt)

  override def debugStepActions: Map[String, Seq[EdgeAction]] =
    compiledProgramOpt
      .map(_.steps.map(step => step.name -> step.edgeActions.toSeq).toMap)
      .getOrElse(Map.empty)

  override def debugStepEffects: Map[String, Seq[String]] =
    compiledProgramOpt
      .map(_.steps.map(step => step.name -> edgeActionNames(step.edgeActions.toSeq)).toMap)
      .getOrElse(Map.empty)

  private def edgeActionNames(actions: Seq[EdgeAction]): Seq[String] =
    actions.map(_.kindName)

  override def recordAtomicCallSnapshot(snapshot: Seq[String]): Unit = {
    debugValidation.recordAtomicCallSnapshot(CurrentProgramContext.currentDebugRecord, snapshot)
  }

  override def markExplicitReturnEncountered(): Unit = {
    debugValidation.markExplicitReturnEncountered()
  }

  override def registerCallSiteReturnRequirement(callSite: CallSiteSnapshot): Unit = {
    debugValidation.registerCallSiteReturnRequirement(callSite)
  }

  override def markCallSiteReturned(callSiteId: Int): Unit = {
    debugValidation.markCallSiteReturned(callSiteId)
  }

  override val Next: StepRef = StepRef.NextStepRef()

  override def Prev: StepRef = {
    programFacade.prevRef
  }

  override def hijack(target: StepRef): Unit = {
    if (ContextScope.getCurrentThread() != this) {
      throw new Exception("Cannot hijack another thread!")
    }
    ThreadRuntimeLogic.emitHijack(programBuilder, compiledProgram, threadHost, target)
  }

  override def Step(stepName: String)(block: => Unit): Unit = {
    programBuilder.defineStep(stepName) {
      ContextScope.withContext(HwOS.kernel.context.AtomicCtx(this)) {
        block
      }
    }
  }

  override def jump(target: StepRef): Unit = {
    if (ContextScope.getCurrentThread() != this) {
      throw new Exception("Cannot jump another thread!")
    }
    ThreadRuntimeLogic.emitJump(programBuilder, compiledProgram, threadHost, target)
  }

  override private[kernel] def recordEdgePatch(target: StepRef)(block: => Unit): Unit = {
    ThreadRuntimeLogic.recordEdgePatch(programBuilder, target, block)
  }

  override def entry(block: => Unit): Unit = {
    if (generatedEntry) {
      throw new Exception("generate thread twice")
    }
    generatedEntry = true

    val builder = new ControlProgramBuilder(name, owner.kernel.addressSpace.createVirtualProgram(name))
    programBuilderOpt = Some(builder)
    ContextScope.withContext(ThreadCtx(this)) { block }
    builder.runGlobals()
    if (builder.steps.isEmpty) { return }
    val plan = ThreadRuntimeLogic.compileProgram(builder)
    compiledProgramOpt = Some(plan)
    debugValidation.validateRequiredReturningCallSites(plan)

    val host = new ThreadHost(this)
    threadHostOpt = Some(host)
    val loweredRuntime = host.materializeProgram(
      builder = builder,
      compiledProgram = plan,
      initialState = RuntimeLifecycle.Idle,
    )
    compiledProgramOpt = Some(loweredRuntime.compiledProgram)
    ThreadRuntimeLogic.lowerRuntime(
      builder = builder,
      compiledProgram = loweredRuntime.compiledProgram,
      host = threadHost,
    )

    if (debugEnable) {
      val wasActive = RegNext(active)
      val lastPc = RegNext(pc)
      when(!wasActive && active) { agentPrint("--- ONLINE ---") }
      when(wasActive && !active) { agentPrint("--- OFFLINE ---") }
      val justStarted = active && !wasActive
      when((active && pc =/= lastPc) || justStarted) {
        for (step <- compiledProgram.steps if step.allocatedAddress >= 0 && step.loweredStandalone) {
          when(pc === step.allocatedAddress.U(pc.getWidth.W)) { agentPrint(s"EXEC [SK ${step.allocatedAddress}] ${step.name}") }
        }
      }
      when(this.freeze) {
        this.pc := this.pc
      }
    }

    verifyExitPath()
    maybePrintCapabilitySummary()
  }

  override def waitCondition(cond: => Bool): Unit = {
    if (ContextScope.getCurrentThread() != this) {
      throw new Exception("waitCondition outside entry")
    }
    ThreadRuntimeLogic.emitWaitCondition(programBuilder, threadHost, cond)
  }

  override def waitAndAct(cond: Bool)(block: => Unit): Unit = {
    if (ContextScope.getCurrentThread() != this) {
      throw new Exception("waitCondition outside entry")
    }
    waitCondition(cond)
    when(cond) {
      block
    }
  }

  override def Global(block: => Unit): Unit = {
    ContextScope.current match {
      case ThreadCtx(_) =>
      case _ => throw new Exception("global outside entry")
    }
    programBuilder.defineGlobal {
      ContextScope.withContext(ThreadCtx(this)) {
        block
      }
    }
  }
}
