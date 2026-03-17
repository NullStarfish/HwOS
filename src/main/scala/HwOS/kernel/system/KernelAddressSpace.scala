package HwOS.kernel.system

import chisel3._
import chisel3.reflect.DataMirror
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
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
  private var nextStateAddress = 0
  private var nextCodeAddress = 0

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

    val startAddress = kind match {
      case AddressKind.State => nextStateAddress
      case AddressKind.Code  => nextCodeAddress
    }

    val obj = new AddressObject(
      kind = kind,
      ownerName = ownerName,
      objectName = objectName,
      startAddress = startAddress,
      span = span,
    )
    addressObjects += obj
    addressObjectMap(qualified) = obj
    kind match {
      case AddressKind.State => nextStateAddress = obj.endAddressExclusive
      case AddressKind.Code  => nextCodeAddress = obj.endAddressExclusive
    }
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

  /** Render the three kernel address tables as a stable human-readable text dump. */
  def renderAddressTables(): String = {
    val stateRows = stateTableEntries.map { entry =>
      Seq(
        entry.ownerName,
        entry.addressObject.objectName,
        entry.addressObject.spaceTag,
        entry.addressObject.kind.tag,
        entry.addressObject.startAddress.toString,
        entry.addressObject.span.toString,
        entry.addressObject.endAddressExclusive.toString,
        entry.signal.map(_.toString).getOrElse("None"),
      )
    }

    val codeRows = codeTableEntries.map { entry =>
      Seq(
        entry.segment.ownerName,
        entry.segment.objectName,
        entry.segment.addressObject.spaceTag,
        entry.segment.startAddress.toString,
        entry.segment.addressObject.span.toString,
        entry.segment.entryAddress.toString,
        entry.segment.labels.mkString("[", ", ", "]"),
        entry.segment.labels.map(label => s"$label=${entry.segment.addressOf(label)}").mkString("{", ", ", "}"),
      )
    }

    val bindingRows = bindingTableEntries.map { entry =>
      Seq(
        entry.bindingName,
        entry.ownerName,
        entry.cursorObject.objectName,
        entry.cursorObject.spaceTag,
        entry.cursorObject.startAddress.toString,
        entry.runtimeStateObject.objectName,
        entry.runtimeStateObject.spaceTag,
        entry.runtimeStateObject.startAddress.toString,
        entry.codeSegment.objectName,
        entry.codeSegment.addressObject.spaceTag,
        entry.codeSegment.startAddress.toString,
        entry.codeSegment.ownerName,
        entry.codeSegment.entryAddress.toString,
      )
    }

    val grantRows = grantTableEntries.map { entry =>
      Seq(
        entry.ownerName,
        entry.targetName,
        entry.signalObject.objectName,
        entry.signalObject.startAddress.toString,
        entry.signalObject.span.toString,
        entry.abi.name,
      )
    }

    Seq(
      renderSection(
        title = "State Table",
        headers = Seq("owner", "object", "space", "kind", "start", "span", "end_exclusive", "signal"),
        rows = stateRows,
      ),
      renderSection(
        title = "Code Table",
        headers = Seq("owner", "segment", "space", "code_start", "span", "code_entry", "labels", "addresses"),
        rows = codeRows,
      ),
      renderSection(
        title = "Binding Table",
        headers = Seq(
          "binding",
          "owner",
          "cursor_object",
          "cursor_space",
          "cursor_start",
          "runtime_state_object",
          "runtime_state_space",
          "runtime_state_start",
          "code_segment",
          "code_space",
          "code_start",
          "code_owner",
          "code_entry",
        ),
        rows = bindingRows,
      ),
      renderSection(
        title = "Grant Table",
        headers = Seq("owner", "target", "signal_object", "signal_start", "signal_span", "abi"),
        rows = grantRows,
      ),
    ).mkString("\n\n")
  }

  /** Export the three kernel address tables as structured JSON. */
  def exportAddressTablesJson(path: String): Unit = {
    writeFile(path, renderAddressTablesJson())
  }

  /** Export the three kernel address tables as a human-readable text dump. */
  def exportAddressTablesText(path: String): Unit = {
    writeFile(path, renderAddressTables())
  }

  /** Export both JSON and text snapshots of the three kernel address tables. */
  def exportAddressTables(baseDir: String = "generated"): Unit = {
    val dir = Paths.get(baseDir)
    Files.createDirectories(dir)
    exportAddressTablesJson(dir.resolve("address_tables.json").toString)
    exportAddressTablesText(dir.resolve("address_tables.txt").toString)
  }

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
    *
    * A binding-table entry is created so the runtime state can be related back
    * to the code segment it interprets.
    */
  def allocateRuntimeContext(
      owner: HwContextEntity,
      bindingName: String,
      segment: GlobalCodeSegment,
      initialState: Int = RuntimeLifecycle.Idle,
  ): RuntimeContext = {
    val cursor = allocateVirtualCursor(owner, s"${bindingName}_cursor", segment)
    val stateReg = owner.own(RegInit(initialState.U(2.W)))

    val runtimeStateObject = getAddressObject(stateReg).getOrElse(
      throw new Exception(s"[Kernel] Runtime state register for '$bindingName' was not registered."),
    )

    val binding = new BindingTableEntry(
      bindingName = bindingName,
      ownerName = owner.name,
      cursorObject = cursor.addressObject,
      runtimeStateObject = runtimeStateObject,
      codeSegment = segment,
    )
    bindingTable += binding
    new RuntimeContext(binding, cursor, stateReg)
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

  private def renderAddressTablesJson(): String = {
    val stateJson = stateTableEntries.map { entry =>
      s"""{"owner_name":${json(entry.ownerName)},"object_name":${json(entry.addressObject.objectName)},"space":${json(entry.addressObject.spaceTag)},"kind":${json(entry.addressObject.kind.tag)},"start_address":${entry.addressObject.startAddress},"span":${entry.addressObject.span},"end_address_exclusive":${entry.addressObject.endAddressExclusive},"signal_repr":${json(entry.signal.map(_.toString).getOrElse("None"))}}"""
    }.mkString("[", ",", "]")

    val codeJson = codeTableEntries.map { entry =>
      val labels = entry.segment.labels.map(json).mkString("[", ",", "]")
      val addresses = entry.segment.labels
        .map(label => s"${json(label)}:${entry.segment.addressOf(label)}")
        .mkString("{", ",", "}")
      s"""{"owner_name":${json(entry.segment.ownerName)},"segment_name":${json(entry.segment.objectName)},"space":${json(entry.segment.addressObject.spaceTag)},"code_start":${entry.segment.startAddress},"span":${entry.segment.addressObject.span},"code_entry":${entry.segment.entryAddress},"labels":$labels,"addresses":$addresses}"""
    }.mkString("[", ",", "]")

    val bindingJson = bindingTableEntries.map { entry =>
      s"""{"binding_name":${json(entry.bindingName)},"owner_name":${json(entry.ownerName)},"cursor_object_name":${json(entry.cursorObject.objectName)},"cursor_space":${json(entry.cursorObject.spaceTag)},"cursor_start_address":${entry.cursorObject.startAddress},"runtime_state_object_name":${json(entry.runtimeStateObject.objectName)},"runtime_state_space":${json(entry.runtimeStateObject.spaceTag)},"runtime_state_start_address":${entry.runtimeStateObject.startAddress},"code_segment_name":${json(entry.codeSegment.objectName)},"code_space":${json(entry.codeSegment.addressObject.spaceTag)},"code_start":${entry.codeSegment.startAddress},"code_segment_owner":${json(entry.codeSegment.ownerName)},"code_entry":${entry.codeSegment.entryAddress}}"""
    }.mkString("[", ",", "]")

    val grantJson = grantTableEntries.map { entry =>
      s"""{"owner_name":${json(entry.ownerName)},"target_name":${json(entry.targetName)},"signal_object_name":${json(entry.signalObject.objectName)},"signal_space":${json(entry.signalObject.spaceTag)},"signal_start_address":${entry.signalObject.startAddress},"signal_span":${entry.signalObject.span},"abi":${json(entry.abi.name)}}"""
    }.mkString("[", ",", "]")

    s"""{"state_table":$stateJson,"code_table":$codeJson,"binding_table":$bindingJson,"grant_table":$grantJson}"""
  }

  private def renderSection(title: String, headers: Seq[String], rows: Seq[Seq[String]]): String = {
    val widths = headers.indices.map { idx =>
      val rowWidths = rows.map(row => if (idx < row.length) row(idx).length else 0)
      (headers(idx).length +: rowWidths).max
    }

    val headerLine = headers.zip(widths).map { case (value, width) => pad(value, width) }.mkString(" | ")
    val separator = widths.map(width => "-" * width).mkString("-+-")
    val rowLines =
      if (rows.isEmpty) Seq("0 entries")
      else rows.map(row => row.zip(widths).map { case (value, width) => pad(value, width) }.mkString(" | "))

    (Seq(title, headerLine, separator) ++ rowLines).mkString("\n")
  }

  private def pad(value: String, width: Int): String = value.padTo(width, ' ').mkString

  private def json(value: String): String = {
    val escaped = value.flatMap {
      case '"'  => "\\\""
      case '\\' => "\\\\"
      case '\n' => "\\n"
      case '\r' => "\\r"
      case '\t' => "\\t"
      case c    => c.toString
    }
    s""""$escaped""""
  }

  private def writeFile(path: String, content: String): Unit = {
    val output = Paths.get(path)
    Option(output.getParent).foreach(parent => Files.createDirectories(parent))
    Files.write(output, content.getBytes(StandardCharsets.UTF_8))
  }
}
