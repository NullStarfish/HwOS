package HwOS.kernel.examples
import chisel3._
import HwOS.kernel.GrantAbi
import HwOS.kernel.function.HwInline
import HwOS.kernel.lang.HwOSLanguage._
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{Kernel, SysCall}
import _root_.circt.stage.ChiselStage

class CallStackDemoProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  val main = createThread("Main")
  val dataReg = this.own(RegInit(0.U(32.W)))

  // ==========================================
  // [A] 微观组合逻辑层 (Atomic / Stateless)
  // 它们会在单个 Step 内部展开，生成 invokedCalls 树
  // ==========================================
  def HashData(in: UInt): HwInline[UInt] = HwInline.stateless("HashData") { _ =>
    // 假装做了一些哈希计算
    in ^ "hDEADBEEF".U
  }

  def StoreData(v: UInt): HwInline[Unit] = HwInline.atomic("StoreData") { t =>
    this.grant(dataReg, t)
    dataReg  :=  v
  }

  // ==========================================
  // [B] 宏观时序逻辑层 (Thread)
  // 它们会劫持线程，注入多个 Step，生成 threadCallStack
  // ==========================================
  def SendPayload(): HwInline[Unit] = HwInline.thread("SendPayload") { t =>
    t.Step("PrepareHeader") {
      // 这里的 SysCall.Call 将会被记录在当前 Step 的 invokedCalls 中
      val hashed = SysCall.Call(HashData(100.U))
      SysCall.Call(StoreData(hashed))
    }
    t.Step("Transmit") {
      // 模拟传输等待
    }
  }

  def NetworkTX(): HwInline[Unit] = HwInline.thread("NetworkTX") { t =>
    t.Step("WaitLink") {
      // 模拟等待网卡就绪
    }
    
    // 时序层面的深层嵌套调用！
    // SendPayload 内部的 Step 将继承 "NetworkTX,SendPayload" 的 threadCallStack
    SysCall.Call(SendPayload()) 
    
    t.Step("Ack") {
      // 模拟确认逻辑
    }
  }

  override def entry(): Unit = {
    main.entry {
      main.Step("Init") {
        SysCall.Call(StoreData(0.U))
      }
      
      // 主线程发起时序调用
      SysCall.Call(NetworkTX())
      
      main.Step("Finish") {
      }
      SysCall.Call(SysCall.Return())
    }
  }
}

// ---------------------------------------------------------
// 2. 顶层测试模块包装
// ---------------------------------------------------------
class CallStackIntegrationModule extends Module {
  val io = IO(new Bundle {
    val start = Input(Bool())
    val done  = Output(Bool())
  })
  io.done := DontCare
  
  // 显式声明为 public val，方便在 ScalaTest 中获取以导出符号表
  val osKernel = new Kernel() 
  implicit val kernel: Kernel = osKernel

  object Init extends HwProcess("Init") {
    this.own(io.done)
    val demo = spawn(new CallStackDemoProcess("Demo"))
    val daemon = createLogic("Daemon")

    override def entry(): Unit = {
      this.grant(io.done, daemon, GrantAbi.LevelDrivenWire)
      this.grantLifecycle(demo.main, daemon)
      daemon.run {
        when(io.start) { SysCall.Call(SysCall.start(demo.main)) }
        io.done  :=  demo.main.done
      }
    }
  }
  Init.build()
  kernel.dumpSymbolTable("generated/hwos.symbols")
  kernel.attachMonitor()
}



class TopModule extends CallStackIntegrationModule {}

object Example extends App {
  println("🚀 正在将 HwOS 顶层模块编译为 Verilog...")
  
  // 导出 Verilog 到 generated 文件夹
  ChiselStage.emitSystemVerilogFile(
    new TopModule(),
    Array("--target-dir", "generated"),
    firtoolOpts = Array(
      "--lowering-options=disallowLocalVariables,disallowPackedArrays,locationInfoStyle=none,disallowPortDeclSharing"
    ) 
  )

  
  println("✅ Verilog 导出完成！请查看 generated/TopModule.v")
}
