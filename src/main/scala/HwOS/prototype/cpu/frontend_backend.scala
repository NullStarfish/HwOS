package HwOS.prototype.cpu

import HwOS.kernel.function.HwInline
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{Kernel, SysCall}
import HwOS.lib.regfile.RegfileLib._
import chisel3._

final class BackendProcess(
    initData: Seq[Int],
    decodeServers: Int,
    localName: String,
)(implicit kernel: Kernel)
    extends HwProcess(localName) {
  private val pathBufferDepth = 3

  val regFile = spawn(new AgeOrderedScoreboardRegfileProcess(8, 32, decodeServers max 3, decodeServers max 3, zeroReg = true, "RegFile"))
  val arith = spawn(new ArithmeticServiceProcess(decodeServers max 2, 1, "Arithmetic"))
  val load = spawn(new LoadServiceProcess(decodeServers max 2, 1, initData, "Load"))
  val commit = spawn(new CommitServiceProcess(regFile, maxPorts = 3, depth = pathBufferDepth, "Commit"))
  val addiPath = spawn(new AddiPathProcess(regFile, arith, commit, pathBufferDepth, arithClientId = 0, writePortId = 0, "AddiPath"))
  val loadPath = spawn(new LoadPathProcess(load, commit, pathBufferDepth, loadClientId = 0, writePortId = 1, "LoadPath"))
  val loadAddPath =
    spawn(new LoadAddPathProcess(regFile, load, arith, commit, pathBufferDepth, loadClientId = 1, arithClientId = 1, writePortId = 2, "LoadAddPath"))

  override def entry(): Unit = {}

  def ActiveThreadCount(): HwInline[UInt] = HwInline.stateless(s"${name}_ActiveThreadCount") { _ =>
    SysCall.Inline(addiPath.ActiveServerCount()) +
    SysCall.Inline(loadPath.ActiveServerCount()) +
    SysCall.Inline(loadAddPath.ActiveServerCount()) +
    SysCall.Inline(commit.ActiveServerCount()) +
    SysCall.Inline(arith.ActiveServerCount()) +
    SysCall.Inline(load.ActiveServerCount())
  }
}

final class FrontendProcess(
    program: Seq[ISA.Instr],
    decodeServers: Int,
    backend: BackendProcess,
    localName: String,
)(implicit kernel: Kernel)
    extends HwProcess(localName) {
  val decode = spawn(new ServerDecodeProcess(program.length max 1, decodeServers, backend, "Decode"))
  val fetch = spawn(new ServerFetchProcess(program, decode, "Fetch"))

  override def entry(): Unit = {}

  def ActiveThreadCount(): HwInline[UInt] = HwInline.stateless(s"${name}_ActiveThreadCount") { _ =>
    SysCall.Inline(fetch.ActiveThreadCount())
  }
}
