package HwOS.prototype.cpu

import HwOS.kernel.function.HwInline
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{Kernel, SysCall}
import HwOS.lib.regfile.RegfileLib._
import chisel3._
import chisel3.util._

final class CommitServiceProcess(
    regFile: AgeOrderedScoreboardRegfileProcess,
    val maxPorts: Int,
    val depth: Int,
    localName: String,
)(implicit kernel: Kernel)
    extends HwProcess(localName) {
  require(maxPorts > 0, "CommitServiceProcess requires at least one write port")
  require(depth > 0, "CommitServiceProcess requires positive buffer depth")

  private case class CommitReq(valid: Bool, writePortId: UInt, rd: UInt, result: UInt)
  private case class CommitSlot(
      thread: HwOS.kernel.thread.HardwareThread,
      valid: Bool,
      writePortId: UInt,
      rd: UInt,
      result: UInt,
  )

  private val slots = Array.tabulate(depth) { _ =>
    CommitReq(
      valid = RegInit(false.B),
      writePortId = RegInit(0.U(log2Ceil((maxPorts max 1) max 2).W)),
      rd = RegInit(0.U(ISA.regWidth.W)),
      result = RegInit(0.U(32.W)),
    )
  }

  private val servers = Array.tabulate(maxPorts) { portIdx =>
    CommitSlot(
      thread = createThread(s"CommitServer$portIdx"),
      valid = RegInit(false.B),
      writePortId = RegInit(0.U(log2Ceil((maxPorts max 1) max 2).W)),
      rd = RegInit(0.U(ISA.regWidth.W)),
      result = RegInit(0.U(32.W)),
    )
  }

  override def entry(): Unit = {
    val dispatcher = createLogic("Dispatcher")

    for ((server, portIdx) <- servers.zipWithIndex) {
      server.thread.entry {
        server.thread.Step(s"CommitWriteback_$portIdx") {
          val writePort = SysCall.Inline(regFile.RequestWritePort(portIdx))
          SysCall.Inline(writePort.WritebackAndClear(server.rd, server.result))
        }
        SysCall.Return()
      }
    }

    dispatcher.run {
      for ((server, serverIdx) <- servers.zipWithIndex) {
        val matchingOH = PriorityEncoderOH(VecInit(slots.map(req => req.valid && req.writePortId === serverIdx.U)).asUInt)
        when(matchingOH.orR && !server.valid && !server.thread.active) {
          for ((req, reqIdx) <- slots.zipWithIndex) {
            when(matchingOH(reqIdx)) {
              server.valid := true.B
              server.writePortId := req.writePortId
              server.rd := req.rd
              server.result := req.result
              req.valid := false.B
              SysCall.Inline(SysCall.start(server.thread))
            }
          }
        }
      }

      for (server <- servers) {
        when(server.valid && server.thread.done) {
          server.valid := false.B
        }
      }
    }
  }

  def RequestCommit(writePortId: Int, rd: UInt, result: UInt): HwInline[Unit] = HwInline.atomic(s"${name}_RequestCommit_$writePortId") { t =>
    val freeOH = PriorityEncoderOH(VecInit(slots.map(!_.valid)).asUInt)
    t.waitCondition(freeOH.orR)
    when(freeOH.orR) {
      for ((req, idx) <- slots.zipWithIndex) {
        when(freeOH(idx)) {
          req.valid := true.B
          req.writePortId := writePortId.U
          req.rd := rd
          req.result := result
        }
      }
    }
    ()
  }

  def ActiveServerCount(): HwInline[UInt] = HwInline.stateless(s"${name}_ActiveServerCount") { _ =>
    PopCount(servers.map(_.thread.active))
  }
}

final class AddiPathProcess(
    regFile: AgeOrderedScoreboardRegfileProcess,
    arith: ArithmeticServiceProcess,
    commit: CommitServiceProcess,
    val depth: Int,
    val arithClientId: Int,
    val writePortId: Int,
    localName: String,
)(implicit kernel: Kernel)
    extends HwProcess(localName) {
  require(depth > 0, "AddiPathProcess requires positive buffer depth")

  private case class AddiReq(valid: Bool, clientId: UInt, rd: UInt, rs1: UInt, imm: UInt)
  private case class ServerState(
      thread: HwOS.kernel.thread.HardwareThread,
      valid: Bool,
      clientId: UInt,
      rd: UInt,
      rs1: UInt,
      imm: UInt,
      decodedSrc: UInt,
      result: UInt,
  )

  private val slots = Array.tabulate(depth) { _ =>
    AddiReq(
      valid = RegInit(false.B),
      clientId = RegInit(0.U(log2Ceil((depth max 1) max 2).W)),
      rd = RegInit(0.U(ISA.regWidth.W)),
      rs1 = RegInit(0.U(ISA.regWidth.W)),
      imm = RegInit(0.U(32.W)),
    )
  }

  private val server = ServerState(
    thread = createThread("Server"),
    valid = RegInit(false.B),
    clientId = RegInit(0.U(log2Ceil((depth max 1) max 2).W)),
    rd = RegInit(0.U(ISA.regWidth.W)),
    rs1 = RegInit(0.U(ISA.regWidth.W)),
    imm = RegInit(0.U(32.W)),
    decodedSrc = RegInit(0.U(32.W)),
    result = RegInit(0.U(32.W)),
  )

  override def entry(): Unit = {
    val dispatcher = createLogic("Dispatcher")

    server.thread.entry {
      server.thread.Step("Read") {
        server.decodedSrc := SysCall.Inline(regFile.Read(server.rs1))
      }
      server.thread.Step("Exec") {
        server.result := SysCall.Call(arith.RequestExecute(arithClientId, server.decodedSrc, server.imm), "Commit")
      }
      server.thread.Step("Commit") {
        SysCall.Inline(commit.RequestCommit(writePortId, server.rd, server.result))
      }
      SysCall.Return()
    }

    dispatcher.run {
      val pendingOH = PriorityEncoderOH(VecInit(slots.map(_.valid)).asUInt)
      when(pendingOH.orR && !server.valid && !server.thread.active) {
        for ((req, idx) <- slots.zipWithIndex) {
          when(pendingOH(idx)) {
            server.valid := true.B
            server.clientId := req.clientId
            server.rd := req.rd
            server.rs1 := req.rs1
            server.imm := req.imm
            req.valid := false.B
            SysCall.Inline(SysCall.start(server.thread))
          }
        }
      }
      when(server.valid && server.thread.done) {
        server.valid := false.B
      }
    }
  }

  def RequestAddi(clientId: Int, rd: UInt, rs1: UInt, imm: UInt): HwInline[Unit] = HwInline.atomic(s"${name}_RequestAddi_$clientId") { t =>
    val freeOH = PriorityEncoderOH(VecInit(slots.map(!_.valid)).asUInt)
    t.waitCondition(freeOH.orR)
    when(freeOH.orR) {
      for ((req, idx) <- slots.zipWithIndex) {
        when(freeOH(idx)) {
          req.valid := true.B
          req.clientId := clientId.U
          req.rd := rd
          req.rs1 := rs1
          req.imm := imm
        }
      }
    }
    ()
  }

  def ActiveServerCount(): HwInline[UInt] = HwInline.stateless(s"${name}_ActiveServerCount") { _ =>
    server.thread.active.asUInt
  }
}

final class LoadPathProcess(
    load: LoadServiceProcess,
    commit: CommitServiceProcess,
    val depth: Int,
    val loadClientId: Int,
    val writePortId: Int,
    localName: String,
)(implicit kernel: Kernel)
    extends HwProcess(localName) {
  require(depth > 0, "LoadPathProcess requires positive buffer depth")

  private case class LoadReq(valid: Bool, clientId: UInt, rd: UInt, imm: UInt)
  private case class ServerState(
      thread: HwOS.kernel.thread.HardwareThread,
      valid: Bool,
      clientId: UInt,
      rd: UInt,
      imm: UInt,
      result: UInt,
  )

  private val slots = Array.tabulate(depth) { _ =>
    LoadReq(
      valid = RegInit(false.B),
      clientId = RegInit(0.U(log2Ceil((depth max 1) max 2).W)),
      rd = RegInit(0.U(ISA.regWidth.W)),
      imm = RegInit(0.U(32.W)),
    )
  }

  private val server = ServerState(
    thread = createThread("Server"),
    valid = RegInit(false.B),
    clientId = RegInit(0.U(log2Ceil((depth max 1) max 2).W)),
    rd = RegInit(0.U(ISA.regWidth.W)),
    imm = RegInit(0.U(32.W)),
    result = RegInit(0.U(32.W)),
  )

  override def entry(): Unit = {
    val dispatcher = createLogic("Dispatcher")

    server.thread.entry {
      server.thread.Step("Exec") {
        server.result := SysCall.Call(load.RequestLoad(loadClientId, server.imm), "Commit")
      }
      server.thread.Step("Commit") {
        SysCall.Inline(commit.RequestCommit(writePortId, server.rd, server.result))
      }
      SysCall.Return()
    }

    dispatcher.run {
      val pendingOH = PriorityEncoderOH(VecInit(slots.map(_.valid)).asUInt)
      when(pendingOH.orR && !server.valid && !server.thread.active) {
        for ((req, idx) <- slots.zipWithIndex) {
          when(pendingOH(idx)) {
            server.valid := true.B
            server.clientId := req.clientId
            server.rd := req.rd
            server.imm := req.imm
            req.valid := false.B
            SysCall.Inline(SysCall.start(server.thread))
          }
        }
      }
      when(server.valid && server.thread.done) {
        server.valid := false.B
      }
    }
  }

  def RequestLoadPath(clientId: Int, rd: UInt, imm: UInt): HwInline[Unit] = HwInline.atomic(s"${name}_RequestLoadPath_$clientId") { t =>
    val freeOH = PriorityEncoderOH(VecInit(slots.map(!_.valid)).asUInt)
    t.waitCondition(freeOH.orR)
    when(freeOH.orR) {
      for ((req, idx) <- slots.zipWithIndex) {
        when(freeOH(idx)) {
          req.valid := true.B
          req.clientId := clientId.U
          req.rd := rd
          req.imm := imm
        }
      }
    }
    ()
  }

  def ActiveServerCount(): HwInline[UInt] = HwInline.stateless(s"${name}_ActiveServerCount") { _ =>
    server.thread.active.asUInt
  }
}

final class LoadAddPathProcess(
    regFile: AgeOrderedScoreboardRegfileProcess,
    load: LoadServiceProcess,
    arith: ArithmeticServiceProcess,
    commit: CommitServiceProcess,
    val depth: Int,
    val loadClientId: Int,
    val arithClientId: Int,
    val writePortId: Int,
    localName: String,
)(implicit kernel: Kernel)
    extends HwProcess(localName) {
  require(depth > 0, "LoadAddPathProcess requires positive buffer depth")

  private case class LoadAddReq(valid: Bool, clientId: UInt, rd: UInt, rs1: UInt, imm: UInt)
  private case class ServerState(
      thread: HwOS.kernel.thread.HardwareThread,
      valid: Bool,
      clientId: UInt,
      rd: UInt,
      rs1: UInt,
      imm: UInt,
      loadedValue: UInt,
      decodedSrc: UInt,
      result: UInt,
  )

  private val slots = Array.tabulate(depth) { _ =>
    LoadAddReq(
      valid = RegInit(false.B),
      clientId = RegInit(0.U(log2Ceil((depth max 1) max 2).W)),
      rd = RegInit(0.U(ISA.regWidth.W)),
      rs1 = RegInit(0.U(ISA.regWidth.W)),
      imm = RegInit(0.U(32.W)),
    )
  }

  private val server = ServerState(
    thread = createThread("Server"),
    valid = RegInit(false.B),
    clientId = RegInit(0.U(log2Ceil((depth max 1) max 2).W)),
    rd = RegInit(0.U(ISA.regWidth.W)),
    rs1 = RegInit(0.U(ISA.regWidth.W)),
    imm = RegInit(0.U(32.W)),
    loadedValue = RegInit(0.U(32.W)),
    decodedSrc = RegInit(0.U(32.W)),
    result = RegInit(0.U(32.W)),
  )

  override def entry(): Unit = {
    val dispatcher = createLogic("Dispatcher")

    server.thread.entry {
      server.thread.Step("LoadExec") {
        server.loadedValue := SysCall.Call(load.RequestLoad(loadClientId, server.imm), "ArithRead")
      }
      server.thread.Step("ArithRead") {
        server.decodedSrc := SysCall.Inline(regFile.Read(server.rs1))
      }
      server.thread.Step("ArithExec") {
        server.result := SysCall.Call(arith.RequestExecute(arithClientId, server.loadedValue, server.decodedSrc), "Commit")
      }
      server.thread.Step("Commit") {
        SysCall.Inline(commit.RequestCommit(writePortId, server.rd, server.result))
      }
      SysCall.Return()
    }

    dispatcher.run {
      val pendingOH = PriorityEncoderOH(VecInit(slots.map(_.valid)).asUInt)
      when(pendingOH.orR && !server.valid && !server.thread.active) {
        for ((req, idx) <- slots.zipWithIndex) {
          when(pendingOH(idx)) {
            server.valid := true.B
            server.clientId := req.clientId
            server.rd := req.rd
            server.rs1 := req.rs1
            server.imm := req.imm
            req.valid := false.B
            SysCall.Inline(SysCall.start(server.thread))
          }
        }
      }
      when(server.valid && server.thread.done) {
        server.valid := false.B
      }
    }
  }

  def RequestLoadAdd(clientId: Int, rd: UInt, rs1: UInt, imm: UInt): HwInline[Unit] = HwInline.atomic(s"${name}_RequestLoadAdd_$clientId") { t =>
    val freeOH = PriorityEncoderOH(VecInit(slots.map(!_.valid)).asUInt)
    t.waitCondition(freeOH.orR)
    when(freeOH.orR) {
      for ((req, idx) <- slots.zipWithIndex) {
        when(freeOH(idx)) {
          req.valid := true.B
          req.clientId := clientId.U
          req.rd := rd
          req.rs1 := rs1
          req.imm := imm
        }
      }
    }
    ()
  }

  def ActiveServerCount(): HwInline[UInt] = HwInline.stateless(s"${name}_ActiveServerCount") { _ =>
    server.thread.active.asUInt
  }
}
