package HwOS.kernel.debug

import scala.collection.mutable.Stack
import java.util.concurrent.atomic.AtomicInteger

/**
 * CallStack is primarily a debug-oriented call-frame tracker.
 * It keeps stack snapshots and return targets, while continuation naming
 * is delegated to dedicated helpers.
 */
object CallStack {
  private val nextCallSiteId = new AtomicInteger(1)

  final case class ReturnEdgePatch(
      continuationTarget: Option[String],
      emitThunk: () => Unit = () => (),
  )

  final case class CallSiteSnapshot(
      id: Int,
      name: String,
      continuationTarget: Option[String],
      requiresExplicitReturn: Boolean,
      returnEdgePatch: Option[ReturnEdgePatch],
  )

  final class CallSite(
      val snapshot: CallSiteSnapshot,
  ) {
    var returned: Boolean = false
  }

  final class Frame(
      val name: String,
      val returnTarget: Option[String],
      val callSiteSnapshot: Option[CallSiteSnapshot] = None,
      val liveCallSite: Option[CallSite] = None,
  ) {
  }

  // 使用 ThreadLocal 确保并行编译时的安全性
  private val stack = new ThreadLocal[Stack[Frame]] {
    override def initialValue(): Stack[Frame] = Stack[Frame]()
  }

  private def pushFrame(frame: Frame): Unit = stack.get().push(frame)

  def withFrame[T](name: String, returnTarget: Option[String])(block: => T): T = {
    withFrame(name, returnTarget, currentCallSiteSnapshot)(block)
  }

  def withFrame[T](name: String, returnTarget: Option[String], callSiteSnapshot: Option[CallSiteSnapshot])(block: => T): T = {
    pushFrame(new Frame(name, returnTarget, callSiteSnapshot = callSiteSnapshot))
    try {
      block
    } finally {
      pop()
    }
  }

  def pushCall(
      name: String,
      returnTarget: Option[String],
      requiresExplicitReturn: Boolean = false,
      returnEdgePatch: Option[ReturnEdgePatch] = None,
  ): CallSite = {
    val site = new CallSite(
      CallSiteSnapshot(
        id = nextCallSiteId.getAndIncrement(),
        name = name,
        continuationTarget = returnTarget,
        requiresExplicitReturn = requiresExplicitReturn,
        returnEdgePatch = returnEdgePatch,
      ),
    )
    pushFrame(new Frame(name, returnTarget, callSiteSnapshot = Some(site.snapshot), liveCallSite = Some(site)))
    site
  }
  
  def pop(): Option[Frame] = Option.when(stack.get().nonEmpty)(stack.get().pop())

  def currentFrame: Option[Frame] = stack.get().headOption

  def currentCallSite: Option[CallSite] =
    currentFrame.flatMap(_.liveCallSite)

  def currentCallSiteSnapshot: Option[CallSiteSnapshot] =
    currentFrame.flatMap { frame =>
      frame.liveCallSite.map(_.snapshot).orElse(frame.callSiteSnapshot)
    }

  def currentReturnTarget: Option[String] = {
    currentCallSiteSnapshot.flatMap(_.continuationTarget).orElse {
      stack.get().iterator.collectFirst { case frame if frame.returnTarget.nonEmpty => frame.returnTarget.get }
    }
  }

  def markReturned(): Unit = {
    currentCallSite.foreach(_.returned = true)
  }

  /**
   * 获取当前命名前缀
   * 例如栈为: ["Main", "FPU", "Add"]
   * 返回: "Main_FPU_Add_"
   */
  def getCurrentPrefix: String = {
    val s = stack.get()
    if (s.isEmpty) "" else s.reverse.map(_.name).mkString("_") + "_"
  }

  def getSnapshot: Seq[String] = stack.get().toSeq.reverse.map(_.name)

  def withIsolatedStack[T](block: => T): T = {
    val saved = stack.get().clone()
    stack.set(Stack.empty[Frame])
    try {
      block
    } finally {
      stack.set(saved)
    }
  }
}
