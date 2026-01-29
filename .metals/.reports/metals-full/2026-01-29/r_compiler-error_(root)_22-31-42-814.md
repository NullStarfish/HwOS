error id: 7D2234C46B5FF9D085E15F4267A1FFAA
file://<WORKSPACE>/src/main/scala/HwOS/kernel/Kernel.scala
### java.lang.AssertionError: assertion failed: bad position: [130:128]

occurred in the presentation compiler.



action parameters:
uri: file://<WORKSPACE>/src/main/scala/HwOS/kernel/Kernel.scala
text:
```scala
package HwOS.kernel

import chisel3._
import chisel3.util._
import scala.collection.mutable.{ArrayBuffer, HashMap}

import mycpu

class Kernel {
  private val drivers = new HashMap[String, PhysicalDriver]()
  private val clients = new HashMap[String, ArrayBuffer[ClientChannel]]()

  /**
   * 挂载一个物理驱动到内核
   */
  def mount(driver: PhysicalDriver): Unit = {
    drivers(driver.meta.name) = driver
    clients(driver.meta.name) = ArrayBuffer[ClientChannel]()
  }

  def hasDriver(name: String): Boolean = drivers.contains(name)

  /**
   * 为进程创建一个访问特定驱动的虚拟连接
   */
  def createConnection(driverName: String): VirtualResourceHandle = {
    if (!drivers.contains(driverName)) throw new Exception(s"[Kernel] Unknown driver: $driverName")
    val drv = drivers(driverName)
    
    // 创建一个全局宽度的通道 (64位)，确保数据不被截断
    val channel = Wire(new ClientChannel(32, KERNEL_DATA_WIDTH))
    
    // 初始化默认值，防止 Chisel 报错
    channel.req.valid := false.B
    channel.req.addr  := 0.U
    channel.req.data  := 0.U
    channel.req.size  := 0.U
    channel.req.wen   := false.B
    channel.respData  := 0.U
    channel.ready     := false.B
    channel.error     := Errno.ESUCCESS
    
    clients(driverName) += channel
    new VirtualResourceHandle(drv.meta, channel)
  }

  /**
   * 启动内核：为每个驱动生成对应的资源仲裁逻辑
   */
  def boot(): Unit = {
    drivers.foreach { case (name, drv) =>
      val driverClients = clients(name)
      if (driverClients.nonEmpty) generateResourceManager(drv, driverClients)
    }
  }

  /**
   * 生成资源管理器：处理多客户端竞争同一物理驱动的情况
   */
  private def generateResourceManager(drv: PhysicalDriver, channels: ArrayBuffer[ClientChannel]): Unit = {
    val meta = drv.meta
    
    // 为该驱动创建一个独立的内核代理逻辑块
    val logic = new HardwareLogic(s"kArbiter_${meta.name}", debugEnable = false)
    
    // 物理层初始化
    drv.setup(logic)

    logic.run {
      // 1. 默认所有通道不响应
      for (ch <- channels) {
        ch.respData := 0.U
        ch.ready    := false.B
        ch.error    := Errno.ESUCCESS
      }

      // 2. 状态寄存器
      val rBusy = RegInit(false.B)
      val wBusy = RegInit(false.B)
      val rIdx  = Reg(UInt(log2Ceil(channels.length max 1).W))
      val wIdx  = Reg(UInt(log2Ceil(channels.length max 1).W))

      // 【关键修复】请求参数锁存器
      // 必须锁存地址和大小，防止 AXI 事务进行中时，由于客户端 Step 切换导致地址抖动
      val activeRAddr = Reg(UInt(32.W))
      val activeRSize = Reg(UInt(2.W))
      val activeWAddr = Reg(UInt(32.W))
      val activeWData = Reg(UInt(KERNEL_DATA_WIDTH.W))
      val activeWSize = Reg(UInt(2.W))

      // 3. 收集并仲裁读写请求
      val readReqSignals = channels.map(ch => ch.req.valid && !ch.req.wen && (meta.readTiming == DriverTiming.Sequential).B)
      val writeReqSignals = channels.map(ch => ch.req.valid && ch.req.wen)
      
      val readReqs  = VecInit(readReqSignals.toSeq).asUInt
      val writeReqs = VecInit(writeReqSignals.toSeq).asUInt
      
      val nextRIdx = PriorityEncoder(readReqs)
      val nextWIdx = PriorityEncoder(writeReqs)

      // --- 读事务逻辑 (Sequential) ---
      when(!rBusy) {
        when(readReqs.orR) {
          rBusy := true.B
          rIdx  := nextRIdx
          // 锁存当前请求的参数
          activeRAddr := VecInit(channels.map(_.req.addr).toSeq)(nextRIdx)
          activeRSize := VecInit(channels.map(_.req.size).toSeq)(nextRIdx)
        }
      } .otherwise {
        // 调用物理驱动的顺序读接口
        val res = drv.seqRead(activeRAddr, activeRSize)
        val data: UInt = res._1
        val err:  UInt = res._2
        val done: Bool = res._3

        // 将结果反馈给选中的通道
        for ((ch, i) <- channels.zipWithIndex) {
          when(rIdx === i.U) {
            ch.respData := data 
            ch.ready    := done
            ch.error    := err
          }
        }
        // 只有当物理驱动报告完成时，才释放 busy 锁
        when(done) { rBusy := false.B }
      }

      // --- 写事务逻辑 (Sequential) ---
      when(!wBusy) {
        when(writeReqs.orR) {
          wBusy := true.B
          wIdx  := nextWIdx
          // 锁存写操作参数
          activeWAddr := VecInit(channels.map(_.req.addr).toSeq)(nextWIdx)
          activeWData := VecInit(channels.map(_.req.data).toSeq)(nextWIdx)
          activeWSize := VecInit(channels.map(_.req.size).toSeq)(nextWIdx)
        }
      } .otherwise {
        // 调用物理驱动的顺序写接口
        val res = drv.seqWrite(activeWAddr, activeWData, activeWSize)
        val err:  UInt = res._1
        val done: Bool = res._2

        for ((ch, i) <- channels.zipWithIndex) {
          when(wIdx === i.U) {
            ch.ready := done
            ch.error := err
          }
        }
        when(done) { wBusy := false.B }
      }
      
      // --- 组合读逻辑 (Bypass/Combinational) ---
      // 适用于 RegFile, PC 等不需要握手的设备
      if (meta.readTiming == DriverTiming.Combinational) {
        for (ch <- channels) {
          // 组合设备不参与仲裁，只要 valid 就立即响应
          when(ch.req.valid && !ch.req.wen) {
            ch.respData := drv.combRead(ch.req.addr, ch.req.size)
            ch.ready    := true.B
            ch.error    := Errno.ESUCCESS
          }
        }
      }
    }
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

java.lang.AssertionError: assertion failed: bad position: [130:128]