package HwOS.kernel.system

import chisel3._
import chisel3.util._
import scala.collection.mutable.{ArrayBuffer, HashMap}
import java.io._
import HwOS.kernel.debug.CallStack
import HwOS.kernel.context.HwContextEntity
import HwOS.kernel.process.HwProcess
import HwOS.kernel.thread.HardwareThread
import HwOS.kernel.thread.backend.ThreadBackendDebugApi

class Kernel {
  sealed trait AddressKind {
    def tag: String
  }

  object AddressKind {
    case object Code extends AddressKind {
      override val tag: String = "code"
    }

    case object State extends AddressKind {
      override val tag: String = "state"
    }
  }

  final class AddressObject(
      val kind: AddressKind,
      val ownerName: String,
      val objectName: String,
      val startAddress: Int,
      val span: Int,
  ) {
    def endAddressExclusive: Int = startAddress + span
    def width: Int = log2Ceil(endAddressExclusive max 2)
  }

  final class StateTableEntry(val addressObject: AddressObject, val ownerName: String, val signal: Option[Data])
  final class CodeTableEntry(val segment: GlobalCodeSegment)
  final class BindingTableEntry(
      val bindingName: String,
      val ownerName: String,
      val cursorObject: AddressObject,
      val lifecycleObject: AddressObject,
      val entityTagObject: AddressObject,
      val codeSegment: GlobalCodeSegment,
  )

  object RuntimeLifecycle {
    val Idle: Int = 0
    val Running: Int = 1
    val Done: Int = 2
  }

  val secure_mode :Boolean = true
  private var booted = false
  private var booting = false
  private val monitorEnabled =
    sys.env.get("HWOS_ENABLE_MONITOR").contains("1") || java.lang.Boolean.getBoolean("hwos.enableMonitor")

  private val threads = ArrayBuffer[HardwareThread]()
  private val threadNameMap = new HashMap[String, Int]()
  def registerThread(name: String, t: HardwareThread): Int = {
    if (threadNameMap.contains(name)) {
      throw new Exception(s"[Kernel] Duplicate thread name detected: $name")
    }
    
    val tid = threads.length
    threads += t
    threadNameMap(name) = tid
    
    println(s"[Kernel] Registered Thread: $name @ TID=$tid") 
    tid
  }
  def getTID(name: String): Option[Int] = threadNameMap.get(name)

  final class GlobalCodeSegment(
      val ownerName: String,
      val objectName: String,
      val addressObject: AddressObject,
      val labels: Seq[String],
      val addresses: Map[String, Int],
  ) {
    def startAddress: Int = addressObject.startAddress
    def entryAddress: Int = addressObject.startAddress
    def addressOf(label: String): Int =
      addresses.getOrElse(label, throw new Exception(s"[Kernel] Unknown label '$label' in code segment '$ownerName'."))
    def width: Int = addressObject.width
  }

  final class VirtualCursor(val reg: UInt, val segment: GlobalCodeSegment, val addressObject: AddressObject) {
    def entryAddress: UInt = segment.entryAddress.U(reg.getWidth.W)
    def addressOf(label: String): UInt = segment.addressOf(label).U(reg.getWidth.W)
  }

  final class RuntimeContext(
      val binding: BindingTableEntry,
      val cursor: VirtualCursor,
      val stateReg: UInt,
      val entityTagReg: UInt,
  )

  final class VirtualStepRecord(
      val name: String,
      val block: () => Unit,
      val threadCallStack: Seq[String],
      val invokedCalls: ArrayBuffer[Seq[String]] = ArrayBuffer.empty[Seq[String]],
  ) {
    var allocatedAddress: Int = -1
    var loweredStandalone: Boolean = true
  }

  final class VirtualProgram(val ownerName: String) {
    private val records = ArrayBuffer[VirtualStepRecord]()

    def appendStep(name: String, block: () => Unit): VirtualStepRecord = {
      val record = new VirtualStepRecord(name, block, CallStack.getSnapshot)
      records += record
      record
    }

    def steps: Seq[VirtualStepRecord] = records.toSeq
    def labels: Seq[String] = records.map(_.name).toSeq
  }

  private val codeSegments = ArrayBuffer[GlobalCodeSegment]()
  private val addressObjects = ArrayBuffer[AddressObject]()
  private val stateTable = ArrayBuffer[StateTableEntry]()
  private val codeTable = ArrayBuffer[CodeTableEntry]()
  private val bindingTable = ArrayBuffer[BindingTableEntry]()
  private val codeLabelMap = new HashMap[String, Int]()
  private val addressObjectMap = new HashMap[String, AddressObject]()
  private val dataAddressMap = new HashMap[Int, AddressObject]()
  private val stateObjectCounters = new HashMap[String, Int]()
  private var nextGlobalAddress = 0

  private def qualifyAddressObject(ownerName: String, objectName: String): String = s"$ownerName::${objectName}"

  def reserveAddressObject(
      kind: AddressKind,
      ownerName: String,
      objectName: String,
      span: Int,
  ): AddressObject = {
    if (span <= 0) {
      throw new Exception(s"[Kernel] Address object '$ownerName::$objectName' must reserve a positive span, got $span.")
    }

    val qualified = qualifyAddressObject(ownerName, objectName)
    if (addressObjectMap.contains(qualified)) {
      throw new Exception(s"[Kernel] Duplicate address object detected: $qualified")
    }

    val obj = new AddressObject(
      kind = kind,
      ownerName = ownerName,
      objectName = objectName,
      startAddress = nextGlobalAddress,
      span = span,
    )
    addressObjects += obj
    addressObjectMap(qualified) = obj
    nextGlobalAddress = obj.endAddressExclusive
    obj
  }

  private def dataKey(signal: Data): Int = System.identityHashCode(signal)

  private def freshStateObjectName(ownerName: String): String = {
    val nextId = stateObjectCounters.getOrElse(ownerName, 0)
    stateObjectCounters(ownerName) = nextId + 1
    s"${ownerName}_state_$nextId"
  }

  private def estimateSignalSpan(signal: Data): Int = {
    val width = signal.getWidth
    if (width <= 0) 1 else width
  }

  def registerOwnedSignal(ownerName: String, signal: Data): AddressObject = {
    dataAddressMap.getOrElseUpdate(dataKey(signal), {
      val obj = reserveAddressObject(
        kind = AddressKind.State,
        ownerName = ownerName,
        objectName = freshStateObjectName(ownerName),
        span = estimateSignalSpan(signal),
      )
      stateTable += new StateTableEntry(obj, ownerName, Some(signal))
      obj
    })
  }

  def getAddressObject(signal: Data): Option[AddressObject] = dataAddressMap.get(dataKey(signal))

  def stateTableEntries: Seq[StateTableEntry] = stateTable.toSeq
  def codeTableEntries: Seq[CodeTableEntry] = codeTable.toSeq
  def bindingTableEntries: Seq[BindingTableEntry] = bindingTable.toSeq

  def reserveCodeSegment(ownerName: String, labels: Seq[String]): GlobalCodeSegment = {
    if (labels.isEmpty) {
      throw new Exception(s"[Kernel] Cannot reserve an empty code segment for '$ownerName'.")
    }

    val duplicateLocalLabels = labels.groupBy(identity).collect { case (label, xs) if xs.length > 1 => label }
    if (duplicateLocalLabels.nonEmpty) {
      throw new Exception(
        s"[Kernel] Duplicate labels inside code segment '$ownerName': ${duplicateLocalLabels.mkString(", ")}",
      )
    }

    val addressObject = reserveAddressObject(
      kind = AddressKind.Code,
      ownerName = ownerName,
      objectName = s"${ownerName}_segment",
      span = labels.length,
    )
    val start = addressObject.startAddress
    val addresses = labels.zipWithIndex.map { case (label, idx) =>
      val qualified = s"$ownerName::$label"
      if (codeLabelMap.contains(qualified)) {
        throw new Exception(s"[Kernel] Duplicate global code label detected: $qualified")
      }
      val addr = start + idx
      codeLabelMap(qualified) = addr
      label -> addr
    }.toMap

    val segment = new GlobalCodeSegment(ownerName, s"${ownerName}_segment", addressObject, labels, addresses)
    codeSegments += segment
    codeTable += new CodeTableEntry(segment)
    segment
  }

  def allocateVirtualCursor(owner: HwContextEntity, cursorName: String, segment: GlobalCodeSegment): VirtualCursor = {
    val cursorReg = owner.own(RegInit(segment.entryAddress.U(segment.width.W)))
    val cursorAddressObject = getAddressObject(cursorReg).getOrElse(
      throw new Exception(s"[Kernel] Virtual cursor '$cursorName' for '${owner.name}' was not registered as an owned signal."),
    )
    new VirtualCursor(cursorReg, segment, cursorAddressObject)
  }

  def allocateRuntimeContext(
      owner: HwContextEntity,
      bindingName: String,
      segment: GlobalCodeSegment,
      entityTagWidth: Int = 8,
      initialState: Int = RuntimeLifecycle.Idle,
      initialEntityTag: Int = 0,
  ): RuntimeContext = {
    val cursor = allocateVirtualCursor(owner, s"${bindingName}_cursor", segment)
    val stateReg = owner.own(RegInit(initialState.U(2.W)))
    val entityTagReg = owner.own(RegInit(initialEntityTag.U(entityTagWidth.W)))

    val lifecycleObject = getAddressObject(stateReg).getOrElse(
      throw new Exception(s"[Kernel] Runtime state register for '$bindingName' was not registered."),
    )
    val entityTagObject = getAddressObject(entityTagReg).getOrElse(
      throw new Exception(s"[Kernel] Runtime entity-tag register for '$bindingName' was not registered."),
    )

    val binding = new BindingTableEntry(
      bindingName = bindingName,
      ownerName = owner.name,
      cursorObject = cursor.addressObject,
      lifecycleObject = lifecycleObject,
      entityTagObject = entityTagObject,
      codeSegment = segment,
    )
    bindingTable += binding
    new RuntimeContext(binding, cursor, stateReg, entityTagReg)
  }

  def createVirtualProgram(ownerName: String): VirtualProgram = new VirtualProgram(ownerName)

  private val contexts = ArrayBuffer[HardwareThread]()
  def registerContext(thread: HardwareThread): Unit = {
    contexts += thread
  }




  private val processes = ArrayBuffer[HwProcess]()
  private val processNameMap = new HashMap[String, Int]()

  def registerProcess(name: String, p: HwProcess): Int = {
    if (processNameMap.contains(name)) {
      throw new Exception(s"[Kernel] Duplicate process name detected: $name")
    }

    val pid = processes.length
    processes += p
    processNameMap(name) = pid

    println(s"[Kernel] Registered Process: $name @ PID=$pid")
    pid
  }


  def getPID(name: String): Option[Int] = processNameMap.get(name) 

  def boot(): Unit = {
    if (booted || booting) return

    booting = true
    try {
      implicit val selfKernel: Kernel = this

      object SystemKernel extends HwProcess("Kernel", overrideDebug = Some(false)) {
        val reaper = spawn(new OSReaperProcess(contexts.toSeq, "OSReaper")(Kernel.this))
        override def entry(): Unit = {}
      }

      SystemKernel.build()
      booted = true
    } finally {
      booting = false
    }
  }

















  

  def dumpSymbolTable(filename: String): Unit = {
    boot()
    val file = new java.io.File(filename)
    val bw = new java.io.BufferedWriter(new java.io.FileWriter(file))
    
    for (t <- threads) {
      t match {
        case debugT: ThreadBackendDebugApi =>
          for (node <- debugT.debugSteps) {
            if (node.isStandalone && node.allocatedPC != -1) {
              val pc = node.allocatedPC
              val stepName = node.name

              // 1. 格式化宏观时序栈 (Thread Level)
              val threadStackStr = if (node.threadCallStack.isEmpty) "None" else node.threadCallStack.mkString(",")
              val threadStackDepth = node.threadCallStack.length

              // 2. 格式化微观组合调用树 (Atomic Level)
              val atomicTreeStr = if (node.invokedCalls.isEmpty) "None" else node.invokedCalls.map(_.mkString(",")).mkString(";")
              val atomicCallCount = node.invokedCalls.length

              // 新格式: <ThreadName> <PC> <StepName> <T_Depth> <T_Stack> <A_Count> <A_Tree>
              bw.write(s"${t.name} $pc $stepName $threadStackDepth $threadStackStr $atomicCallCount $atomicTreeStr\n")
            }
          }
        case _ =>
      }
    }
    bw.close()
    println(s"[Kernel] Symbol table dumped to $filename")
  }
  
  def attachMonitor(): Unit = {
    boot()
    if (!monitorEnabled) {
      println("[Kernel] Monitor disabled. Set HWOS_ENABLE_MONITOR=1 or -Dhwos.enableMonitor=true to attach DPI monitor.")
      return
    }
    val nThreads = threads.length
    if (nThreads == 0) return

    // 将所有的 PC 强制对齐到 32 bit 供 C++ 读取
    val pc32Seq = threads.map { t =>
      val w = WireInit(0.U(32.W))
      w := t.pc
      w
    }.toSeq

    val pcVec     = VecInit(pc32Seq).asUInt
    val activeVec = VecInit(threads.map(_.active).toSeq).asUInt 
    val doneVec   = VecInit(threads.map(_.done).toSeq).asUInt      

    // 实例化 DPI BlackBox
    val monitor = Module(new KernelStateMonitorDPI(nThreads))
    
    monitor.io.clock   := Module.clock
    monitor.io.reset   := Module.reset
    monitor.io.pcs     := pcVec
    monitor.io.actives := activeVec
    monitor.io.dones   := doneVec
    
    println(s"[Kernel] Attached DPI Monitor for $nThreads threads.")
  }
} 


class KernelStateMonitorDPI(val nThreads: Int) extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val clock   = Input(Clock())
    val reset   = Input(Bool())
    val pcs     = Input(UInt((32 * nThreads).W))
    val actives = Input(UInt(nThreads.W))
    val dones   = Input(UInt(nThreads.W))
  })

  // 生成 SystemVerilog 代码
  setInline("KernelStateMonitorDPI.sv",
    s"""
       |`ifndef SYNTHESIS  
       |module KernelStateMonitorDPI(
       |  input clock,
       |  input reset,
       |  input [${nThreads * 32 - 1}:0] pcs,
       |  input [${nThreads - 1}:0]      actives,
       |  input [${nThreads - 1}:0]      dones
       |);
       |
       |  // 声明 DPI-C 函数
       |  import "DPI-C" function void kernel_monitor_tick(
       |    input int n_threads,
       |    input bit [${nThreads * 32 - 1}:0] pcs,
       |    input bit [${nThreads - 1}:0]      actives,
       |    input bit [${nThreads - 1}:0]      dones
       |  );
       |
       |  always @(posedge clock) begin
       |    if (!reset) begin
       |      kernel_monitor_tick(
       |        ${nThreads},
       |        pcs,
       |        actives,
       |        dones
       |      );
       |    end
       |  end
       |
       |endmodule
       |`endif // SYNTHESIS
       |""".stripMargin)
}
