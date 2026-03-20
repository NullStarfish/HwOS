package HwOS.quick_start

import chisel3._
import HwOS.kernel.lang.HwOSLanguage._ // 引入 HwOS 独有的安全赋值操作符  := 
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{Kernel, SysCall}


class TopModule extends Module {
  val io = IO(new Bundle {
    val start  = Input(Bool())
    val result = Output(UInt(32.W))
    val done   = Output(Bool())
  })
  io.result := DontCare; io.done := DontCare

  // 初始化全局内核
  implicit val kernel: Kernel = new Kernel()

  // 顶层容器进程
  object Init extends HwProcess("Init") {
    (io.result); (io.done)
    
    // 孵化 (Spawn) 我们的计数器子进程
    val counterProc = spawn(new CounterProcess("CounterApp"))
    val daemon = createLogic("DaemonLogic")

    override def entry(): Unit = {
      // 允许 daemon 逻辑控制子线程的生命周期
      // 守护逻辑：处理外部 IO 并启动线程
      daemon.run {
        when(io.start) {
          SysCall.Inline(SysCall.start(counterProc.mainThread)) // 唤醒目标线程
        }
        io.result  :=  counterProc.counter
        io.done    :=  counterProc.isDone
      }
    }
  }
  
  // 触发全局构建
  Init.build()
  kernel.dumpSymbolTable("generated/hwos.symbols")
  kernel.attachMonitor()
}
