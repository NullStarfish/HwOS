package HwOS.kernel

import chisel3._
import chisel3.util._
import HwOS.kernel.HwOSLanguage._

import scala.collection.mutable.{ArrayBuffer, LinkedHashMap}

trait DefaultThreadControlBackend extends ThreadControlApi { self: HardwareThread with DefaultThreadRuntimeBackend =>
  override val nodes = ArrayBuffer[ThreadStepNode]()
  private val nodeByName = LinkedHashMap[String, ThreadStepNode]()
  private val pendingJumpTargets = scala.collection.mutable.Set[String]()
  private var jumpPcByName = Map.empty[String, UInt]
  override private[kernel] var currentGeneratingNode: ThreadStepNode = _
  private val globals = ArrayBuffer[() => Unit]()

  override def pc: UInt = runtimePc

  override val Next: ThreadNextApi = new ThreadNextApi {
    override def hijack(): Unit = {
      self.hijack()
    }
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

    runtimeEntity = chooseRuntime()

    for (i <- 0 until nodes.length) {
      if (i > 0) nodes(i).prev = nodes(i - 1)
      if (i < nodes.length - 1) nodes(i).next = nodes(i + 1)
    }

    var pcCounter = 0
    val maxSteps = nodes.length
    val pcReg = runtime.allocatePc(maxSteps)
    bindRuntimeViews()
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

    if (runtime.supportsLifecycleGrant) {
      runtime.grantLifecycle(this.owner)
    }

    if (debugEnable) {
      val wasActive = RegNext(active)
      val lastPc = RegNext(pcReg)
      val watchDog = RegInit(0.U(32.W))
      when(!wasActive && active) { agentPrint("--- ONLINE ---") }
      when(wasActive && !active) { agentPrint("--- OFFLINE ---") }
      val justStarted = active && !wasActive
      when((active && pcReg =/= lastPc) || justStarted) {
        for (node <- nodes if node.allocatedPC >= 0) {
          when(pcReg === node.allocatedPC.U) { agentPrint(s"EXEC [PC ${node.allocatedPC}] ${node.name}") }
        }
      }
      when(active && (pc === lastPc)) {
        watchDog := watchDog + 1.U
      }.otherwise {
        watchDog := 0.U
      }
    }

    verifyExitPath()
    maybePrintCapabilitySummary()

    if (debugEnable) {
      when(this.freeze) {
        this.pc := this.pc
      }
    }
  }

  override def waitCondition(cond: Bool): Unit = {
    ContextScope.current match {
      case AtomicCtx(_) =>
      case _ =>
        agentPrint(p"Do not use waitCondition outside entry!!!")
        throw new Exception("waitCondition outside entry")
    }

    when(!cond) {
      this.pc := this.pc
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
      this.pc := this.pc
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
