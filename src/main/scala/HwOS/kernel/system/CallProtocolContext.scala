package HwOS.kernel.system

import HwOS.kernel.debug.CallStack
import java.util.concurrent.atomic.AtomicInteger

object CallProtocolContext {
  private val nextCallSiteId = new AtomicInteger(1)

  final case class ReturnEdgePatch(
      continuationTarget: Option[String] = None,
      emitThunk: () => Unit = () => (),
  )

  final case class ContinuationSnapshot(
      targetLabel: Option[String],
      requiresExplicitReturn: Boolean,
      returnEdgePatch: Option[ReturnEdgePatch],
  )

  final class ContinuationBinding(
      val snapshot: ContinuationSnapshot,
  )

  final case class CallSiteSnapshot(
      id: Int,
      name: String,
      continuation: ContinuationSnapshot,
  ) {
    def continuationTarget: Option[String] = continuation.targetLabel
    def requiresExplicitReturn: Boolean = continuation.requiresExplicitReturn
    def returnEdgePatch: Option[ReturnEdgePatch] = continuation.returnEdgePatch
  }

  final class CallSiteBinding(
      val id: Int,
      val name: String,
      val continuation: ContinuationBinding,
  ) {
    var returned: Boolean = false

    def snapshot: CallSiteSnapshot =
      CallSiteSnapshot(
        id = id,
        name = name,
        continuation = continuation.snapshot,
      )
  }

  private val continuationStack = new ThreadLocal[List[ContinuationBinding]] {
    override def initialValue(): List[ContinuationBinding] = Nil
  }

  private val callSiteStack = new ThreadLocal[List[CallSiteBinding]] {
    override def initialValue(): List[CallSiteBinding] = Nil
  }

  def bindContinuation(
      targetLabel: Option[String],
      requiresExplicitReturn: Boolean = false,
      returnEdgePatch: Option[ReturnEdgePatch] = None,
  ): ContinuationBinding =
    new ContinuationBinding(
      ContinuationSnapshot(
        targetLabel = targetLabel,
        requiresExplicitReturn = requiresExplicitReturn,
        returnEdgePatch = returnEdgePatch,
      ),
    )

  def currentContinuationBinding: Option[ContinuationBinding] =
    continuationStack.get().headOption

  def currentContinuationSnapshot: Option[ContinuationSnapshot] =
    currentContinuationBinding.map(_.snapshot)

  def currentCallSiteBinding: Option[CallSiteBinding] =
    callSiteStack.get().headOption

  def currentCallSiteSnapshot: Option[CallSiteSnapshot] =
    currentCallSiteBinding.map(_.snapshot)

  def currentReturnTarget: Option[String] =
    currentContinuationSnapshot.flatMap(_.targetLabel)

  def withContinuation[T](binding: ContinuationBinding)(block: => T): T = {
    val saved = continuationStack.get()
    continuationStack.set(binding :: saved)
    try block
    finally {
      continuationStack.set(saved)
    }
  }

  def withCallSite[T](name: String, continuation: ContinuationBinding)(block: CallSiteBinding => T): T = {
    val site = new CallSiteBinding(
      id = nextCallSiteId.getAndIncrement(),
      name = name,
      continuation = continuation,
    )
    val savedContinuations = continuationStack.get()
    val savedCallSites = callSiteStack.get()
    continuationStack.set(continuation :: savedContinuations)
    callSiteStack.set(site :: savedCallSites)
    try {
      CallStack.withFrame(name, Some(site.id)) {
        block(site)
      }
    } finally {
      callSiteStack.set(savedCallSites)
      continuationStack.set(savedContinuations)
    }
  }

  def withCallSiteSnapshot[T](callSiteSnapshot: Option[CallSiteSnapshot])(block: => T): T =
    callSiteSnapshot match {
      case Some(snapshot) =>
        val continuation = new ContinuationBinding(snapshot.continuation)
        val site = new CallSiteBinding(snapshot.id, snapshot.name, continuation)
        val savedContinuations = continuationStack.get()
        val savedCallSites = callSiteStack.get()
        continuationStack.set(continuation :: savedContinuations)
        callSiteStack.set(site :: savedCallSites)
        try block
        finally {
          callSiteStack.set(savedCallSites)
          continuationStack.set(savedContinuations)
        }
      case None =>
        block
    }

  def markReturned(): Option[Int] =
    currentCallSiteBinding.map { site =>
      site.returned = true
      site.id
    }

  def withIsolatedProtocol[T](block: => T): T = {
    val savedContinuations = continuationStack.get()
    val savedCallSites = callSiteStack.get()
    continuationStack.set(Nil)
    callSiteStack.set(Nil)
    try block
    finally {
      continuationStack.set(savedContinuations)
      callSiteStack.set(savedCallSites)
    }
  }
}
