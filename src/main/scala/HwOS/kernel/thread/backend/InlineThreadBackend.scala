package HwOS.kernel.thread.backend

import chisel3._
import chisel3.util._
import scala.collection.mutable.{ArrayBuffer, LinkedHashMap}
import HwOS.kernel.context.{AtomicCtx, ContextScope, ThreadCtx}
import HwOS.kernel.lang.HwOSLanguage._
import HwOS.kernel.thread._

trait InlineThreadBackend
    extends ThreadControlApi
    with ThreadRuntimeApi
    with ThreadBackendDebugApi { self: HardwareThread =>
  private var pcEntity: UInt = _
  private var terminalPc: UInt = _
  private lazy val lifecycleLease = new InlineThreadLifecycleLease(
    thread = this,
    pcAccessor = () => pcEntity,
    terminalPcAccessor = () => terminalPc,
  )
  private var lifecycleLeaseRegistered = false

  private[kernel] var generatedEntry: Boolean = false
  private[kernel] var hasExitPath: Boolean = false
  private[kernel] val freeze: Bool = WireInit(false.B)

  private val nodes = ArrayBuffer[ThreadStepNode]()
  private val nodeByName = LinkedHashMap[String, ThreadStepNode]()
  private val pendingJumpTargets = scala.collection.mutable.Set[String]()
  private var jumpPcByName = Map.empty[String, UInt]
  private[kernel] var currentGeneratingNode: ThreadStepNode = _
  private val globals = ArrayBuffer[() => Unit]()

  override def active: Bool = {
    if (pcEntity == null || terminalPc == null) {
      throw new Exception(s"[HwOS] Inline backend for '$name' is not initialized")
    }
    lifecycleLease.active
  }

  override def done: Bool = {
    if (pcEntity == null || terminalPc == null) {
      throw new Exception(s"[HwOS] Inline backend for '$name' is not initialized")
    }
    lifecycleLease.done
  }

  override def pc: UInt = {
    if (pcEntity == null) {
      agentPrint("Cannot access thread.pc outside of entry!!!")
      throw new Exception("pc not set")
    }
    pcEntity
  }

  override def start(): Unit = {
    lifecycleLease.startLifecycle()
  }

  override def exit(): Unit = {
    hasExitPath = true
    lifecycleLease.exitLifecycle()
  }

  private def allocatePc(maxSteps: Int): UInt = {
    if (pcEntity != null) {
      throw new Exception(s"[HwOS] PC allocated twice for thread '$name'")
    }
    val pcWidth = log2Ceil(maxSteps + 1)
    pcEntity = this.own(RegInit(0.U(pcWidth.W)))
    terminalPc = maxSteps.U(pcWidth.W)
    pcEntity
  }

  private def bindContext(): Unit = {
    if (!lifecycleLeaseRegistered) {
      ctx.registerLease(lifecycleLease)
      lifecycleLeaseRegistered = true
    }
    ctx.bindIsActive(lifecycleLease.active)
  }

  private def verifyExitPath(): Unit = {}
  private def maybePrintCapabilitySummary(): Unit = ()

  override def threadNodes: Seq[ThreadStepNode] = nodes.toSeq

  override def recordAtomicCallSnapshot(snapshot: Seq[String]): Unit = {
    if (currentGeneratingNode != null) {
      currentGeneratingNode.invokedCalls += snapshot
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

    val me = currentGeneratingNode
    val victim = me.next
    if (victim == null) {
      throw new Exception(s"[HwOS] Step '${me.name}' tried to hijack non-existent next step!")
    }

    victim.isHijacked = true
    ContextScope.withContext(AtomicCtx(self)) {
      val save = currentGeneratingNode
      currentGeneratingNode = victim
      victim.block()
      currentGeneratingNode = save
    }
  }

  override def Step(name: String)(block: => Unit): Unit = {
    if (nodeByName.contains(name)) {
      throw new Exception(s"[HwOS] Duplicate step name '$name' in thread '$this.name'.")
    }
    val node = new ThreadStepNode(name, () => {
      ContextScope.withContext(AtomicCtx(this)) {
        block
      }
    })
    nodes += node
    nodeByName(name) = node
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
    pendingJumpTargets += target
    pc <== targetPc
  }

  override def entry(block: => Unit): Unit = {
    if (generatedEntry) {
      agentPrint("generated twice!!!")
      throw new Exception("generate thread twice")
    }
    generatedEntry = true

    ContextScope.withContext(ThreadCtx(this)) { block }
    if (nodes.isEmpty) { return }

    for (i <- 0 until nodes.length) {
      if (i > 0) nodes(i).prev = nodes(i - 1)
      if (i < nodes.length - 1) nodes(i).next = nodes(i + 1)
    }

    var pcCounter = 0
    val maxSteps = nodes.length
    val pcReg = allocatePc(maxSteps)
    bindContext()
    jumpPcByName = nodes.iterator.map(node => node.name -> WireInit(0.U(log2Ceil(maxSteps max 2).W))).toMap

    for (node <- nodes) {
      if (!node.isHijacked) {
        node.allocatedPC = pcCounter
        currentGeneratingNode = node
        when(pc === pcCounter.U) {
          ContextScope.withContext(AtomicCtx(this)) {
            pc <== pc + 1.U
          }
          node.block()
        }
        pcCounter += 1
      }
    }

    for (node <- nodes if !node.isHijacked) {
      jumpPcByName(node.name) := node.allocatedPC.U
    }

    for (target <- pendingJumpTargets) {
      val targetNode = nodeByName(target)
      if (targetNode.isHijacked || targetNode.allocatedPC < 0) {
        throw new Exception(s"[HwOS] jump target '$target' in thread '$name' has no standalone PC. Consider jumping to a non-hijacked step.")
      }
    }

    if (debugEnable) {
      val lastPc = RegNext(pcReg)
      when(pcReg =/= lastPc) {
        for (node <- nodes if node.allocatedPC >= 0) {
          when(pcReg === node.allocatedPC.U) { agentPrint(s"EXEC [PC ${node.allocatedPC}] ${node.name}") }
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
      case _ =>
        agentPrint("Do not use waitCondition outside entry!!!")
        throw new Exception("waitCondition outside entry")
    }

    when(!cond) {
      this.pc <== this.pc
    }
  }

  override def waitAndAct(cond: Bool)(block: => Unit): Unit = {
    ContextScope.current match {
      case AtomicCtx(_) =>
      case _ =>
        agentPrint("Do not use waitCondition outside entry!!!")
        throw new Exception("waitCondition outside entry")
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
      case _ =>
        agentPrint("Do not use Global outside entry!!!")
        throw new Exception("global outside entry")
    }
    globals += { () => block }
  }
}
