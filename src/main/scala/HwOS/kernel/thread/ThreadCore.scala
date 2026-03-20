package HwOS.kernel.thread

import chisel3._
import HwOS.kernel.context.{ContextScope, ThreadCtx}
import HwOS.kernel.system.{RuntimeContext, RuntimeLifecycle}
import HwOS.kernel.thread.step.SystemEffect
import HwOS.kernel.thread.step.{ThreadIR, ThreadLayout, ThreadRuntimeLogic}

trait ThreadCore
    extends ThreadControlApi
    with ThreadRuntimeApi
    with ThreadDebugApi { self: HardwareThread =>
  private var runtimeContext: Option[RuntimeContext] = None
  private[kernel] var generatedEntry: Boolean = false
  private[kernel] var hasExitPath: Boolean = false
  private[kernel] val freeze: Bool = WireInit(false.B)

  private var irStateOpt: Option[ThreadIR.IRState] = None
  private val layoutState = new ThreadLayout.LayoutState()

  private def irState: ThreadIR.IRState =
    irStateOpt.getOrElse(throw new Exception(s"[HwOS] Thread IR is not initialized for thread '$name'."))

  private def runtime: RuntimeContext =
    runtimeContext.getOrElse(throw new Exception(s"[HwOS] Runtime context is not allocated for thread '$name'."))

  override private[kernel] def runtimeHandle: RuntimeContext = runtime

  override def active: Bool =
    runtimeContext.map(ThreadRuntimeLogic.isRunning).getOrElse(false.B)

  override def done: Bool =
    runtimeContext.map(ThreadRuntimeLogic.isDone).getOrElse(false.B)

  override def pc: UInt = runtime.cursor.reg

  override private[kernel] def runtimeStart(): Unit = {
    ThreadRuntimeLogic.start(runtime)
  }

  override private[kernel] def runtimeExit(): Unit = {
    hasExitPath = true
    if (HwOS.kernel.thread.step.PreLoweringAnalysis.isActive) {
      return
    }
    ThreadRuntimeLogic.exit(runtime)
  }

  override def reset(): Unit = {
    ThreadRuntimeLogic.resetToIdle(runtime)
  }

  private def verifyExitPath(): Unit = {}
  private def maybePrintCapabilitySummary(): Unit = ()

  override def debugSteps: Seq[DebugStepRecord] =
    irStateOpt.toSeq.flatMap(_.program.steps).map { step =>
      new DebugStepRecord(
        step.name,
        step.allocatedAddress,
        step.loweredStandalone,
        step.threadCallStack,
        step.invokedCalls,
        step.effects.map {
          case SystemEffect.ReturnEffect   => "return"
          case SystemEffect.JumpEffect(_)  => "jump"
          case SystemEffect.HijackEffect(_) => "hijack"
          case SystemEffect.WaitEffect     => "wait"
        }.toSeq,
      )
    }

  override def debugStepEffects: Map[String, Seq[String]] =
    irStateOpt
      .map(_.program.steps.map(step => step.name -> step.effects.map {
        case SystemEffect.ReturnEffect    => "return"
        case SystemEffect.JumpEffect(_)   => "jump"
        case SystemEffect.HijackEffect(_) => "hijack"
        case SystemEffect.WaitEffect      => "wait"
      }.toSeq).toMap)
      .getOrElse(Map.empty)

  override def recordAtomicCallSnapshot(snapshot: Seq[String]): Unit = {
    layoutState.currentDebugRecord.foreach(_.invokedCalls += snapshot)
  }

  override val Next: StepRef = StepRef.NextStepRef

  override def hijack(target: StepRef): Unit = {
    if (ContextScope.getCurrentThread() != this) {
      throw new Exception("Cannot hijack another thread!")
    }
    ThreadRuntimeLogic.emitHijack(irState, layoutState, runtime, target)
  }

  override def Step(stepName: String)(block: => Unit): Unit = {
    ThreadIR.defineStep(irState, stepName) {
      ContextScope.withContext(HwOS.kernel.context.AtomicCtx(this)) {
        block
      }
    }
  }

  override def jump(target: StepRef): Unit = {
    if (ContextScope.getCurrentThread() != this) {
      throw new Exception("Cannot jump another thread!")
    }
    ThreadRuntimeLogic.emitJump(irState, layoutState, runtime, target)
  }

  override def entry(block: => Unit): Unit = {
    if (generatedEntry) {
      throw new Exception("generate thread twice")
    }
    generatedEntry = true

    irStateOpt = Some(new ThreadIR.IRState(name, owner.kernel.addressSpace.createVirtualProgram(name)))
    ContextScope.withContext(ThreadCtx(this)) { block }
    ThreadIR.runGlobals(irState)
    if (irState.program.steps.isEmpty) { return }
    val layoutPlan = ThreadRuntimeLogic.analyzeControl(irState, layoutState)

    val allocatedRuntime = ThreadRuntimeLogic.materializeRuntime(
      owner = this,
      irState = irState,
      layoutState = layoutState,
      plan = layoutPlan,
      initialState = RuntimeLifecycle.Idle,
    )
    runtimeContext = Some(allocatedRuntime)
    ThreadRuntimeLogic.lowerRuntime(
      irState = irState,
      layoutState = layoutState,
      plan = layoutPlan,
      runtime = allocatedRuntime,
    )

    if (debugEnable) {
      val wasActive = RegNext(active)
      val lastPc = RegNext(pc)
      when(!wasActive && active) { agentPrint("--- ONLINE ---") }
      when(wasActive && !active) { agentPrint("--- OFFLINE ---") }
      val justStarted = active && !wasActive
      when((active && pc =/= lastPc) || justStarted) {
        for (step <- irState.program.steps if step.allocatedAddress >= 0 && step.loweredStandalone) {
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

  override def waitCondition(cond: Bool): Unit = {
    if (ContextScope.getCurrentThread() != this) {
      throw new Exception("waitCondition outside entry")
    }
    ThreadRuntimeLogic.emitWaitCondition(irState, layoutState, runtime, cond)
  }

  override def waitAndAct(cond: Bool)(block: => Unit): Unit = {
    if (ContextScope.getCurrentThread() != this) {
      throw new Exception("waitCondition outside entry")
    }
    when(!cond) {
      ThreadRuntimeLogic.emitWaitCondition(irState, layoutState, runtime, false.B)
    }.otherwise {
      block
    }
  }

  override def Global(block: => Unit): Unit = {
    ContextScope.current match {
      case ThreadCtx(_) =>
      case _ => throw new Exception("global outside entry")
    }
    ThreadIR.defineGlobal(irState) {
      ContextScope.withContext(ThreadCtx(this)) {
        block
      }
    }
  }
}
