error id: 7D2234C46B5FF9D085E15F4267A1FFAA
file://<WORKSPACE>/src/main/scala/HwOS/kernel/drivers/StandardDrivers.scala
### java.lang.AssertionError: assertion failed: bad position: [104:102]

occurred in the presentation compiler.



action parameters:
uri: file://<WORKSPACE>/src/main/scala/HwOS/kernel/drivers/StandardDrivers.scala
text:
```scala
package HwOS.kernel.drivers

import chisel3._
import chisel3.util._
import HwOS.kernel._

import mycpu

class PCDriver(pcReg: UInt) extends PhysicalDriver(
  DriverMeta("PC", DriverTiming.Combinational, DriverTiming.Sequential)
) {
  override def combRead(addr: UInt, size: UInt): UInt = pcReg

  override def seqWrite(addr: UInt, data: UInt, size: UInt): (UInt, Bool) = {
    // [修复] data 是 64位，PC 是 32位，显式截断
    pcReg := data(XLEN-1, 0)
    (Errno.ESUCCESS, true.B)
  }
}

class RegFileDriver(regs: Vec[UInt]) extends PhysicalDriver(
  DriverMeta("RF", DriverTiming.Combinational, DriverTiming.Sequential)
) {
  override def combRead(addr: UInt, size: UInt): UInt = {
    Mux(addr(4,0) === 0.U, 0.U, regs(addr))
  }

  override def seqWrite(addr: UInt, data: UInt, size: UInt): (UInt, Bool) = {
    when(addr =/= 0.U) {
      // [修复] 显式截断数据
      regs(addr) := data(XLEN-1, 0)
    }
    (Errno.ESUCCESS, true.B)
  }
}

class CSRDriver(regs: Vec[UInt]) extends PhysicalDriver(
  DriverMeta("CSR", DriverTiming.Combinational, DriverTiming.Sequential)
) {
  override def combRead(addr: UInt, size: UInt): UInt = {
    Mux(addr < regs.length.U, regs(addr), 0.U)
  }

  override def seqWrite(cmdOrAddr: UInt, data: UInt, size: UInt): (UInt, Bool) = {
    val cmd = cmdOrAddr(1, 0).asTypeOf(CSROp())
    val addr = data(31, 20) 
    val wdata = data(19, 0) // 这里逻辑可能需要根据你实际 CSR 协议调整，暂时保留原样
    // 注意：这里的 CSR 写逻辑比较特殊，如果之前有特定实现请保留
    (Errno.ESUCCESS, true.B)
  }
}
```


presentation compiler configuration:
Scala version: 2.13.16
Classpath:
<WORKSPACE>/.bloop/root/bloop-bsp-clients-classes/classes-Metals-JyAsqsgXS9uLBw_Hk5rDNg== [exists ], <HOME>/.cache/bloop/semanticdb/com.sourcegraph.semanticdb-javac.0.11.2/semanticdb-javac-0.11.2.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/chipsalliance/chisel_2.13/7.0.0/chisel_2.13-7.0.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/github/scopt/scopt_2.13/4.1.0/scopt_2.13-4.1.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/apache/commons/commons-text/1.13.1/commons-text-1.13.1.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/os-lib_2.13/0.10.7/os-lib_2.13-0.10.7.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/json4s/json4s-native_2.13/4.0.7/json4s-native_2.13-4.0.7.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/io/github/alexarchambault/data-class_2.13/0.2.7/data-class_2.13-0.2.7.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/scala-reflect/2.13.16/scala-reflect-2.13.16.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/upickle_2.13/3.3.1/upickle_2.13-3.3.1.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/chipsalliance/firtool-resolver_2.13/2.0.1/firtool-resolver_2.13-2.0.1.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/apache/commons/commons-lang3/3.17.0/commons-lang3-3.17.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/geny_2.13/1.1.1/geny_2.13-1.1.1.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/json4s/json4s-core_2.13/4.0.7/json4s-core_2.13-4.0.7.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/json4s/json4s-native-core_2.13/4.0.7/json4s-native-core_2.13-4.0.7.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/ujson_2.13/3.3.1/ujson_2.13-3.3.1.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/upack_2.13/3.3.1/upack_2.13-3.3.1.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/upickle-implicits_2.13/3.3.1/upickle-implicits_2.13-3.3.1.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/modules/scala-xml_2.13/2.2.0/scala-xml_2.13-2.2.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/scala-lang/modules/scala-collection-compat_2.13/2.11.0/scala-collection-compat_2.13-2.11.0.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/json4s/json4s-ast_2.13/4.0.7/json4s-ast_2.13-4.0.7.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/org/json4s/json4s-scalap_2.13/4.0.7/json4s-scalap_2.13-4.0.7.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/thoughtworks/paranamer/paranamer/2.8/paranamer-2.8.jar [exists ], <HOME>/.cache/coursier/v1/https/repo1.maven.org/maven2/com/lihaoyi/upickle-core_2.13/3.3.1/upickle-core_2.13-3.3.1.jar [exists ]
Options:
-language:reflectiveCalls -deprecation -feature -Xcheckinit -Ymacro-annotations -Yrangepos -Xplugin-require:semanticdb




#### Error stacktrace:

```
scala.reflect.internal.util.Position$.validate(Position.scala:42)
	scala.reflect.internal.util.Position$.range(Position.scala:61)
	scala.reflect.internal.util.InternalPositionImpl.withStart(Position.scala:237)
	scala.reflect.internal.util.InternalPositionImpl.withStart$(Position.scala:138)
	scala.reflect.internal.util.Position.withStart(Position.scala:19)
	scala.reflect.internal.Trees$Import.posOf(Trees.scala:548)
	scala.tools.nsc.typechecker.ContextErrors$TyperContextErrors$TyperErrorGen$.NotAMemberError(ContextErrors.scala:523)
	scala.tools.nsc.typechecker.Namers$Namer.checkSelector$1(Namers.scala:560)
	scala.tools.nsc.typechecker.Namers$Namer.$anonfun$checkSelectors$4(Namers.scala:576)
	scala.tools.nsc.typechecker.Namers$Namer.checkSelectors(Namers.scala:576)
	scala.tools.nsc.typechecker.Namers$Namer.scala$tools$nsc$typechecker$Namers$Namer$$importSig(Namers.scala:1836)
	scala.tools.nsc.typechecker.Namers$Namer$ImportTypeCompleter.completeImpl(Namers.scala:864)
	scala.tools.nsc.typechecker.Namers$LockingTypeCompleter.complete(Namers.scala:2077)
	scala.tools.nsc.typechecker.Namers$LockingTypeCompleter.complete$(Namers.scala:2075)
	scala.tools.nsc.typechecker.Namers$TypeCompleterBase.complete(Namers.scala:2070)
	scala.reflect.internal.Symbols$Symbol.completeInfo(Symbols.scala:1583)
	scala.reflect.internal.Symbols$Symbol.info(Symbols.scala:1548)
	scala.reflect.internal.Symbols$Symbol.initialize(Symbols.scala:1747)
	scala.tools.nsc.typechecker.Typers$Typer.typedStat$1(Typers.scala:3375)
	scala.tools.nsc.typechecker.Typers$Typer.$anonfun$typedStats$10(Typers.scala:3547)
	scala.tools.nsc.typechecker.Typers$Typer.typedStats(Typers.scala:3547)
	scala.tools.nsc.typechecker.Typers$Typer.typedPackageDef$1(Typers.scala:5925)
	scala.tools.nsc.typechecker.Typers$Typer.typed1(Typers.scala:6254)
	scala.tools.nsc.typechecker.Typers$Typer.typed(Typers.scala:6344)
	scala.tools.nsc.typechecker.Analyzer$typerFactory$TyperPhase.apply(Analyzer.scala:126)
	scala.tools.nsc.Global$GlobalPhase.applyPhase(Global.scala:483)
	scala.tools.nsc.interactive.Global$TyperRun.applyPhase(Global.scala:1370)
	scala.tools.nsc.interactive.Global$TyperRun.typeCheck(Global.scala:1363)
	scala.tools.nsc.interactive.Global.typeCheck(Global.scala:681)
	scala.meta.internal.pc.Compat.$anonfun$runOutline$1(Compat.scala:74)
	scala.collection.IterableOnceOps.foreach(IterableOnce.scala:619)
	scala.collection.IterableOnceOps.foreach$(IterableOnce.scala:617)
	scala.collection.AbstractIterable.foreach(Iterable.scala:935)
	scala.meta.internal.pc.Compat.runOutline(Compat.scala:66)
	scala.meta.internal.pc.Compat.runOutline(Compat.scala:35)
	scala.meta.internal.pc.Compat.runOutline$(Compat.scala:33)
	scala.meta.internal.pc.MetalsGlobal.runOutline(MetalsGlobal.scala:39)
	scala.meta.internal.pc.ScalaCompilerWrapper.compiler(ScalaCompilerAccess.scala:18)
	scala.meta.internal.pc.ScalaCompilerWrapper.compiler(ScalaCompilerAccess.scala:13)
	scala.meta.internal.pc.ScalaPresentationCompiler.$anonfun$semanticTokens$1(ScalaPresentationCompiler.scala:206)
	scala.meta.internal.pc.CompilerAccess.retryWithCleanCompiler(CompilerAccess.scala:182)
	scala.meta.internal.pc.CompilerAccess.$anonfun$withSharedCompiler$1(CompilerAccess.scala:155)
	scala.Option.map(Option.scala:242)
	scala.meta.internal.pc.CompilerAccess.withSharedCompiler(CompilerAccess.scala:154)
	scala.meta.internal.pc.CompilerAccess.$anonfun$withInterruptableCompiler$1(CompilerAccess.scala:92)
	scala.meta.internal.pc.CompilerAccess.$anonfun$onCompilerJobQueue$1(CompilerAccess.scala:209)
	scala.meta.internal.pc.CompilerJobQueue$Job.run(CompilerJobQueue.scala:152)
	java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1144)
	java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:642)
	java.base/java.lang.Thread.run(Thread.java:1583)
```
#### Short summary: 

java.lang.AssertionError: assertion failed: bad position: [104:102]