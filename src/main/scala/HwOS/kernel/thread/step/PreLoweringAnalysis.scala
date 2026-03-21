package HwOS.kernel.thread.step

import chisel3.{Bool, Module => ChiselModule, RawModule}
import chisel3.experimental.UnlocatableSourceInfo
import HwOS.kernel.debug.CallStack
import HwOS.kernel.system.RecordedEffect
import HwOS.kernel.system.CallProtocolContext
import HwOS.kernel.system.VirtualStepRecord

private[kernel] object PreLoweringAnalysis {
  private val activeRecord = new ThreadLocal[Option[VirtualStepRecord]] {
    override def initialValue(): Option[VirtualStepRecord] = None
  }

  private val edgeGuardStack = new ThreadLocal[List[Bool]] {
    override def initialValue(): List[Bool] = Nil
  }

  def isActive: Boolean = activeRecord.get().nonEmpty

  def currentRecord: VirtualStepRecord =
    activeRecord.get().getOrElse(
      throw new Exception("[HwOS] No active pre-lowering step analysis record."),
    )

  def record(effect: EdgeAction): Unit = {
    val record = currentRecord
    record.edgeActions += effect
    record.capturedEffects += RecordedEffect(effect, edgeGuardStack.get())
  }

  def pushEdgeGuard(cond: Bool): Unit = {
    edgeGuardStack.set(cond :: edgeGuardStack.get())
  }

  def currentEdgeGuards: List[Bool] = edgeGuardStack.get()

  def withScopedEdgeGuards[T](block: => T): T = {
    val saved = edgeGuardStack.get()
    edgeGuardStack.set(Nil)
    try block
    finally {
      edgeGuardStack.set(saved)
    }
  }

  def analyzeStep(record: VirtualStepRecord)(block: => Unit): Unit = {
    val saveRecord = activeRecord.get()
    val saveGuards = edgeGuardStack.get()
    val module = ChiselModule.currentModule.getOrElse(
      throw new Exception("[HwOS] Pre-lowering analysis requires an active Chisel module context."),
    ).asInstanceOf[RawModule]
    val tempRegion = createTempBlock()
    val ids = moduleIds(module)
    val idsBefore = ids.length
    val portsBefore = modulePortsSize(module)

    record.edgeActions.clear()
    record.capturedEffects.clear()
    activeRecord.set(Some(record))
    edgeGuardStack.set(Nil)
    try {
      CallProtocolContext.withCallSiteSnapshot(record.implicitCallSite) {
        CallStack.withFrame(record.name, None, record.implicitCallSite) {
          withTempRegion(module, tempRegion) { block }
        }
      }
    } finally {
      val addedIds = ids.length - idsBefore
      if (addedIds > 0) {
        ids.dropRightInPlace(addedIds)
      }
      if (modulePortsSize(module) != portsBefore) {
        throw new Exception(
          s"[HwOS] Pre-lowering analysis of step '${record.name}' created IO ports. " +
            s"Step bodies must not create IO during dry-run analysis.",
        )
      }
      edgeGuardStack.set(saveGuards)
      activeRecord.set(saveRecord)
    }
  }

  private def createTempBlock(): AnyRef = {
    val blockClass = Class.forName("chisel3.internal.firrtl.ir$Block")
    val ctor = blockClass.getConstructors.head
    ctor.newInstance(UnlocatableSourceInfo).asInstanceOf[AnyRef]
  }

  private def withTempRegion[A](module: RawModule, tempRegion: AnyRef)(block: => A): A = {
    val method = module.getClass.getMethods.find(_.getName == "withRegion").getOrElse(
      throw new Exception("[HwOS] Failed to find RawModule.withRegion for pre-lowering analysis."),
    )
    val thunk = new scala.runtime.AbstractFunction0[A] {
      override def apply(): A = block
    }
    method.invoke(module, tempRegion, thunk).asInstanceOf[A]
  }

  private def moduleIds(module: RawModule): scala.collection.mutable.ArrayBuffer[AnyRef] = {
    val method = module.getClass.getMethods.find(_.getName == "_ids").getOrElse(
      throw new Exception("[HwOS] Failed to access BaseModule._ids for pre-lowering analysis."),
    )
    method.invoke(module).asInstanceOf[scala.collection.mutable.ArrayBuffer[AnyRef]]
  }

  private def modulePortsSize(module: RawModule): Int = {
    val method = module.getClass.getMethods.find(_.getName == "portsSize").getOrElse(
      throw new Exception("[HwOS] Failed to access BaseModule.portsSize for pre-lowering analysis."),
    )
    method.invoke(module).asInstanceOf[Int]
  }
}
