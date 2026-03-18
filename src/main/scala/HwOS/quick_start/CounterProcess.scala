package HwOS.quick_start

import chisel3._
import HwOS.kernel.function.HwInline
import HwOS.kernel.lang.HwOSLanguage._ // 引入 HwOS 独有的安全赋值操作符  := 
import HwOS.kernel.process.HwProcess
import HwOS.kernel.system.{GrantAbi, Kernel, SysCall}
import chisel3.util.log2Ceil

    // 必须隐式传入 Kernel 以注册全局资源
class CounterProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {

  // 1. 声明物理资源，并宣誓主权 (Ownership)
  val counter  = this.own(RegInit(0.U(32.W)))
  val isDone   = this.own(WireInit(false.B))

  // 2. 创建一个硬件线程 (HardwareThread)
  val mainThread = createThread("MainThread")

  override def entry(): Unit = {
    // 3. 授权 (Grant)：赋予 mainThread 修改 counter 和 isDone 的权限
    this.grant(counter, mainThread)
    this.grant(isDone, mainThread, GrantAbi.LevelDrivenWire)

    // 4. 定义线程的时序逻辑 (Step-by-Step)
    mainThread.entry {
      
      mainThread.Step("Init") {
        // 使用  :=  进行安全赋值，受线程 isActive 状态的物理保护
        counter  :=  0.U 
        isDone   :=  false.B
      }

      mainThread.Step("CountUp") {
        counter  :=  counter + 1.U
        
        // 硬件级阻塞：如果条件不满足，PC 寄存器将在此挂起
        
        mainThread.waitAndAct(counter === 10.U) {
          mainThread.hijack(mainThread.Next) // 零气泡 (Zero-Bubble) 抢占下一步逻辑
        }
      }

      mainThread.Step("Finish") {
        isDone  :=  true.B
      }
      SysCall.Call(SysCall.Return())
    }
  }

  def DoNTimes(n: Int): HwInline[Unit] = HwInline.thread("do n times") {t =>
    this.grantLifecycle(mainThread, t)
    val cnt = this.own(RegInit(0.U(log2Ceil(n + 1).W)))
    t.Step("Start") {
      SysCall.Call(SysCall.start(mainThread))
      when (mainThread.done) {
        cnt  :=  cnt + 1.U
      }
      t.waitAndAct(cnt >= n.U) {
        t.jump(t.stepRef("Done"))
      }
    }
    t.Step("Done") {}
    SysCall.Call(SysCall.Return())
    ()
  }
}
