package HwOS.kernel.thread.backend

import chisel3._
import scala.collection.mutable.ArrayBuffer
import HwOS.kernel.context.{AtomicCtx, ContextScope, ThreadCtx}
import HwOS.kernel.lang.HwOSLanguage._
import HwOS.kernel.system.Kernel
import HwOS.kernel.thread._

trait VirtualCursorThreadBackend
    extends ThreadControlApi
    with ThreadRuntimeApi
    with ThreadBackendDebugApi { self: HardwareThread =>
  private val activeReg = this.own(RegInit(false.B))
  private val doneReg = this.own(RegInit(false.B))
  private var virtualCursor: Option[Kernel#VirtualCursor] = None
  private lazy val lifecycleLease = new DefaultThreadLifecycleLease(
    thread = this,
    activeReg = activeReg,
    doneReg = doneReg,
    pcAccessor = () => pc,
    entryPcAccessor = () => virtualCursor.get.entryAddress,
  )
  private var lifecycleLeaseRegistered = false

  private[kernel] var generatedEntry: Boolean = false
  private[kernel] var hasExitPath: Boolean = false
  private[kernel] val freeze: Bool = WireInit(false.B)

  private var program: Option[Kernel#VirtualProgram] = None
  private val stepNameSet = scala.collection.mutable.Set[String]()
  private var jumpPcByName = Map.empty[String, UInt]
  private var currentLoweringIndex: Int = -1
  private var loweredStandalone = scala.collection.mutable.Set[Int]()
  private var suppressedStandalone = scala.collection.mutable.Set[Int]()
  private var currentDebugRecord: Option[Kernel#VirtualStepRecord] = None
  private val globals = ArrayBuffer[() => Unit]()

  override def active: Bool = lifecycleLease.active
  override def done: Bool = lifecycleLease.done

  override def pc: UInt =
    virtualCursor.map(_.reg).getOrElse(throw new Exception(s"[HwOS] Virtual cursor is not allocated for thread '$name'."))

  override private[kernel] def runtimeStart(): Unit = {
    lifecycleLease.startLifecycle()
  }

  override private[kernel] def runtimeExit(): Unit = {
    hasExitPath = true
    lifecycleLease.exitLifecycle()
  }

  private def bindContext(): Unit = {
    if (!lifecycleLeaseRegistered) {
      ctx.registerLease(lifecycleLease)
      lifecycleLeaseRegistered = true
    }
    ctx.bindIsActive(lifecycleLease.active)
  }

  private def allocateVirtualCursor(): Kernel#VirtualCursor = {
    if (virtualCursor.isDefined) {
      throw new Exception(s"[HwOS] Virtual cursor allocated twice for thread '$name'")
    }
    val labels = program.map(_.labels).getOrElse(Seq.empty)
    val segment = owner.kernel.reserveCodeSegment(name, labels)
    val cursor = owner.kernel.allocateVirtualCursor(this, s"${name}_virtual_cursor", segment)
    virtualCursor = Some(cursor)
    cursor
  }

  private def verifyExitPath(): Unit = {}
  private def maybePrintCapabilitySummary(): Unit = ()

  override def debugSteps: Seq[DebugStepRecord] =
    program.toSeq.flatMap(_.steps).map { step =>
      new DebugStepRecord(
        step.name,
        step.allocatedAddress,
        step.loweredStandalone,
        step.threadCallStack,
        step.invokedCalls,
      )
    }

  override def recordAtomicCallSnapshot(snapshot: Seq[String]): Unit = {
    currentDebugRecord.foreach(_.invokedCalls += snapshot)
  }

  private def currentProgram: Kernel#VirtualProgram =
    program.getOrElse(throw new Exception(s"[HwOS] Virtual program not initialized for thread '$name'."))

  private def lowerStepAt(index: Int): Unit = {
    val steps = currentProgram.steps
    val step = steps(index)
    val saveIndex = currentLoweringIndex
    val saveRecord = currentDebugRecord
    currentLoweringIndex = index
    currentDebugRecord = Some(step)
    step.block()
    currentDebugRecord = saveRecord
    currentLoweringIndex = saveIndex
  }

  private def nextStandaloneIndex(after: Int): Option[Int] = {
    val steps = currentProgram.steps
    ((after + 1) until steps.length).find(idx => !suppressedStandalone.contains(idx))
  }

  private def lowerMainPass(cursor: Kernel#VirtualCursor): Unit = {
    val steps = currentProgram.steps
    for ((step, index) <- steps.zipWithIndex if !suppressedStandalone.contains(index)) {
      step.allocatedAddress = cursor.segment.addressOf(step.name)
      step.loweredStandalone = true
      loweredStandalone += index

      when(pc === step.allocatedAddress.U(pc.getWidth.W)) {
        ContextScope.withContext(AtomicCtx(this)) {
          val nextPc = nextStandaloneIndex(index)
            .map(nextIdx => cursor.segment.addressOf(steps(nextIdx).name).U(pc.getWidth.W))
            .getOrElse(cursor.entryAddress)
          pc <== nextPc
        }
        lowerStepAt(index)
      }
    }
  }

  override val Next: ThreadNextApi = new ThreadNextApi {
    override def hijack(): Unit = self.hijack()
  }

  override def hijack(): Unit = {
    ContextScope.current match {
      case AtomicCtx(t) if t == this =>
      case AtomicCtx(_) => throw new Exception("Cannot hijack another thread!")
      case _ => throw new Exception("hijack() must be called inside a Step!")
    }

    val victimIndex = currentLoweringIndex + 1
    val steps = currentProgram.steps
    if (currentLoweringIndex < 0 || victimIndex >= steps.length) {
      val currentName = if (currentLoweringIndex >= 0) steps(currentLoweringIndex).name else "<unknown>"
      throw new Exception(s"[HwOS] Step '$currentName' tried to hijack non-existent next step!")
    }

    val victim = steps(victimIndex)
    victim.loweredStandalone = false
    suppressedStandalone += victimIndex
    ContextScope.withContext(AtomicCtx(self)) {
      lowerStepAt(victimIndex)
    }
  }

  override def Step(name: String)(block: => Unit): Unit = {
    if (stepNameSet.contains(name)) {
      throw new Exception(s"[HwOS] Duplicate step name '$name' in thread '$this.name'.")
    }
    stepNameSet += name
    currentProgram.appendStep(name, () => {
      ContextScope.withContext(AtomicCtx(this)) {
        block
      }
    })
  }

  override def jump(target: String): Unit = {
    ContextScope.current match {
      case AtomicCtx(t) if t == this =>
      case AtomicCtx(_) => throw new Exception("Cannot jump another thread!")
      case _ => throw new Exception("jump() must be called inside a Step!")
    }

    val targetPc = jumpPcByName.getOrElse(
      target,
      throw new Exception(s"[HwOS] Unknown jump target '$target' in thread '$name'."),
    )
    pc <== targetPc
  }

  override def entry(block: => Unit): Unit = {
    if (generatedEntry) {
      throw new Exception("generate thread twice")
    }
    generatedEntry = true

    program = Some(owner.kernel.createVirtualProgram(name))
    ContextScope.withContext(ThreadCtx(this)) { block }
    if (currentProgram.steps.isEmpty) { return }

    val cursor = allocateVirtualCursor()
    bindContext()
    jumpPcByName = currentProgram.steps.iterator.map(step => step.name -> cursor.addressOf(step.name)).toMap
    lowerMainPass(cursor)

    if (debugEnable) {
      val wasActive = RegNext(active)
      val lastPc = RegNext(pc)
      when(!wasActive && active) { agentPrint("--- ONLINE ---") }
      when(wasActive && !active) { agentPrint("--- OFFLINE ---") }
      val justStarted = active && !wasActive
      when((active && pc =/= lastPc) || justStarted) {
        for (step <- currentProgram.steps if step.allocatedAddress >= 0 && step.loweredStandalone) {
          when(pc === step.allocatedAddress.U(pc.getWidth.W)) { agentPrint(s"EXEC [VPC ${step.allocatedAddress}] ${step.name}") }
        }
      }
      when(this.freeze) {
        ContextScope.withContext(AtomicCtx(this)) {
          this.pc <== this.pc
        }
      }
    }

    verifyExitPath()
    maybePrintCapabilitySummary()
  }

  override def waitCondition(cond: Bool): Unit = {
    ContextScope.current match {
      case AtomicCtx(_) =>
      case _ => throw new Exception("waitCondition outside entry")
    }

    when(!cond) {
      this.pc <== this.pc
    }
  }

  override def waitAndAct(cond: Bool)(block: => Unit): Unit = {
    ContextScope.current match {
      case AtomicCtx(_) =>
      case _ => throw new Exception("waitCondition outside entry")
    }

    when(!cond) {
      this.pc <== this.pc
    }.otherwise {
      block
    }
  }

  override def Global(block: => Unit): Unit = {
    ContextScope.current match {
      case ThreadCtx(_) =>
      case _ => throw new Exception("global outside entry")
    }
    globals += { () => block }
  }
}
