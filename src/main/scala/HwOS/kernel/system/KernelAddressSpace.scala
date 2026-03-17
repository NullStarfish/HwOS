package HwOS.kernel.system

import chisel3._
import chisel3.reflect.DataMirror
import scala.collection.mutable.{ArrayBuffer, HashMap}
import HwOS.kernel.context.HwContextEntity

final class KernelAddressSpace {
  private val codeSegments = ArrayBuffer[GlobalCodeSegment]()
  private val addressObjects = ArrayBuffer[AddressObject]()
  private val stateTable = ArrayBuffer[StateTableEntry]()
  private val codeTable = ArrayBuffer[CodeTableEntry]()
  private val bindingTable = ArrayBuffer[BindingTableEntry]()
  private val grantTable = ArrayBuffer[GrantTableEntry]()
  private val codeLabelMap = new HashMap[String, Int]()
  private val addressObjectMap = new HashMap[String, AddressObject]()
  private val dataAddressMap = new HashMap[Int, AddressObject]()
  private val stateObjectCounters = new HashMap[String, Int]()
  private var nextGlobalAddress = 0

  /** Reserve a typed object in the global address space.
    *
    * This is the lowest-level allocator used by both state-space objects
    * (regs, cursor, runtime state) and code-space objects (step encodings).
    * Higher-level helpers such as `reserveCodeSegment()` and
    * `allocateRuntimeContext()` are thin wrappers around this primitive.
    */
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

  /** Register an owned hardware state object into the state table.
    *
    * `own(...)` ultimately calls this method. It gives every stateful resource
    * a stable address-space entry without forcing a bus/MMIO design yet.
    */
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

  /** Look up the state-table entry for an already-registered hardware signal. */
  def getAddressObject(signal: Data): Option[AddressObject] = dataAddressMap.get(dataKey(signal))

  /** Snapshot accessors for the three kernel tables.
    *
    * These are primarily for inspection/debug today. They give the caller the
    * current state-space table, code-space table, and cursor/code binding table.
    */
  def stateTableEntries: Seq[StateTableEntry] = stateTable.toSeq
  def codeTableEntries: Seq[CodeTableEntry] = codeTable.toSeq
  def bindingTableEntries: Seq[BindingTableEntry] = bindingTable.toSeq
  def grantTableEntries: Seq[GrantTableEntry] = grantTable.toSeq

  /** Register an ABI-facing grant.
    *
    * Grants do not allocate new state objects; they attach an interaction ABI
    * to a previously owned signal so later interface generation/runtime logic
    * can tell whether the exported interaction is register-write, level-driven,
    * or pulse-like.
    */
  def registerGrant(
      ownerName: String,
      targetName: String,
      signal: Data,
      abi: GrantAbi,
  ): GrantTableEntry = {
    val signalObject = getAddressObject(signal).getOrElse(registerOwnedSignal(ownerName, signal))
    val entry = new GrantTableEntry(ownerName, targetName, signalObject, abi)
    grantTable += entry
    entry
  }

  /** Default ABI inference for legacy `grant(signal, target)` call sites.
    *
    * Registers may default to `RegisterWrite`. Non-register signals must use
    * the explicit ABI overload so wire-like protocols are never guessed.
    */
  def inferGrantAbi(signal: Data): GrantAbi = {
    if (DataMirror.isReg(signal)) {
      GrantAbi.RegisterWrite
    } else {
      throw new Exception(
        s"[Kernel] grant(signal, target) requires an explicit ABI for non-register signal '$signal'. " +
          s"Use grant(signal, target, GrantAbi.LevelDrivenWire) or GrantAbi.PulseWire.",
      )
    }
  }

  /** Allocate a global code-table slice for a program after control-flow normalization.
    *
    * Call this only after the caller has decided which steps remain standalone.
    * The resulting segment belongs to the code table, not the state table.
    */
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

  /** Allocate a cursor register bound to a code segment.
    *
    * The cursor is state-space data, but its legal values are interpreted
    * through the supplied code segment.
    */
  def allocateVirtualCursor(owner: HwContextEntity, cursorName: String, segment: GlobalCodeSegment): VirtualCursor = {
    val cursorReg = owner.own(RegInit(segment.entryAddress.U(segment.width.W)))
    val cursorAddressObject = getAddressObject(cursorReg).getOrElse(
      throw new Exception(s"[Kernel] Virtual cursor '$cursorName' for '${owner.name}' was not registered as an owned signal."),
    )
    new VirtualCursor(cursorReg, segment, cursorAddressObject)
  }

  /** Allocate the minimal thread runtime context for one executable cursor.
    *
    * The runtime context bundles:
    * - a cursor register (execution position)
    * - a state register (Idle/Running/Done)
    * - an entity-tag register kept as a compatibility bridge
    *
    * A binding-table entry is created so the runtime state can be related back
    * to the code segment it interprets.
    */
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

    val runtimeStateObject = getAddressObject(stateReg).getOrElse(
      throw new Exception(s"[Kernel] Runtime state register for '$bindingName' was not registered."),
    )
    val entityTagObject = getAddressObject(entityTagReg).getOrElse(
      throw new Exception(s"[Kernel] Runtime entity-tag register for '$bindingName' was not registered."),
    )

    val binding = new BindingTableEntry(
      bindingName = bindingName,
      ownerName = owner.name,
      cursorObject = cursor.addressObject,
      runtimeStateObject = runtimeStateObject,
      entityTagObject = entityTagObject,
      codeSegment = segment,
    )
    bindingTable += binding
    new RuntimeContext(binding, cursor, stateReg, entityTagReg)
  }

  /** Create the mutable IR container used to collect step-level control flow. */
  def createVirtualProgram(ownerName: String): VirtualProgram = new VirtualProgram(ownerName)

  private def qualifyAddressObject(ownerName: String, objectName: String): String = s"$ownerName::${objectName}"

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
}
