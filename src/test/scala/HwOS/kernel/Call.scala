package HwOS.kernel


import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec
import HwOS.kernel._
import HwOS.kernel.HwOSLanguage._

// ---------------------------------------------------------
// 1. 业务逻辑进程 (展示双轨调用栈)
// ---------------------------------------------------------
class CallStackDemoProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  val main = createThread("Main")
  val dataReg = this.own(RegInit(0.U(32.W)))

  // ==========================================
  // [A] 微观组合逻辑层 (Atomic / Stateless)
  // 它们会在单个 Step 内部展开，生成 invokedCalls 树
  // ==========================================
  def HashData(in: UInt): HwFunction[UInt] = HwFunction.stateless("HashData") { _ =>
    // 假装做了一些哈希计算
    in ^ "hDEADBEEF".U
  }

  def StoreData(v: UInt): HwFunction[Unit] = HwFunction.atomic("StoreData") { t =>
    this.grant(dataReg, t)
    dataReg <== v
  }

  // ==========================================
  // [B] 宏观时序逻辑层 (Thread)
  // 它们会劫持线程，注入多个 Step，生成 threadCallStack
  // ==========================================
  def SendPayload(): HwFunction[Unit] = HwFunction.thread("SendPayload") { t =>
    t.Step("PrepareHeader") {
      // 这里的 SysCall.Call 将会被记录在当前 Step 的 invokedCalls 中
      val hashed = SysCall.Call(HashData(100.U))
      SysCall.Call(StoreData(hashed))
    }
    t.Step("Transmit") {
      // 模拟传输等待
    }
  }

  def NetworkTX(): HwFunction[Unit] = HwFunction.thread("NetworkTX") { t =>
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
        main.exit()
      }
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
      this.grant(io.done, daemon)
      this.grantLifecycle(demo.main, daemon)
      daemon.run {
        when(io.start) { SysCall.Call(SysCall.start(demo.main)) }
        io.done <== demo.main.done
      }
    }
  }
  Init.build()
  kernel.dumpSymbolTable("test")
}

// ---------------------------------------------------------
// 3. ScalaTest 驱动程序
// ---------------------------------------------------------
class CallStackSpec extends AnyFlatSpec {
  "HwOS Dual-Track CallStack" should "generate correct temporal and combinational traces" in {
    simulate(new CallStackIntegrationModule) { c =>
      println("\n=== HwOS Dual-Track CallStack Test ===")
      
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      c.io.start.poke(true.B)
      c.clock.step()
      c.io.start.poke(false.B)

      var cycles = 0
      while (c.io.done.peek().litValue == 0 && cycles < 20) {
        c.clock.step()
        cycles += 1
      }
      c.clock.step()

      // 显式触发符号表导出 (假设你在 Kernel 中添加了 dumpSymbolTable)
      c.osKernel.dumpSymbolTable("test_dual_stack.symbols")
      println("=== Simulation Done! Please check test_dual_stack.symbols ===\n")
    }
  }
}