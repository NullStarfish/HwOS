package HwOS.lib.regfile

import chisel3._
import chisel3.util._
import HwOS.kernel._
import HwOS.kernel.HwOSLanguage._
import HwOS.stdlib.sync._

object RegfileLib {

  // ==========================================
  // Layer 1: 基础寄存器堆 (纯物理数据通路)
  // 提供多端口的并发读写，无任何调度与阻塞逻辑
  // ==========================================
  class BaseRegfileProcess(val depth: Int, val width: Int, val maxWriters: Int, val zeroReg: Boolean, localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
    
    // 1. 物理核心资产 (整体 own)
    private val regs = this.own(RegInit(VecInit(Seq.fill(depth)(0.U(width.W)))))

    // 2. C/S 架构写入端口线缆 (独立 own，供外部并发连线)
    val wAddrs   = WireInit(VecInit(Seq.fill(maxWriters)(0.U(log2Ceil(depth).W))))
    val wDatas   = WireInit(VecInit(Seq.fill(maxWriters)(0.U(width.W))))
    val wEnables = WireInit(VecInit(Seq.fill(maxWriters)(false.B)))

    for (i <- 0 until maxWriters) {
      this.own(wAddrs(i))
      this.own(wDatas(i))
      this.own(wEnables(i))
    }

    // 3. 核心守护进程：统一管理物理资源的写入
    override def entry(): Unit = {
      val daemon = createLogic("WriteDaemon")
      this.grant(regs, daemon)
      
      daemon.run {
        for (i <- 0 until maxWriters) {
          when(wEnables(i)) {
            // 如果开启了 zeroReg 特性，强制保护 0 号寄存器不被修改 (RISC-V 必备)
            if (zeroReg) {
              when(wAddrs(i) =/= 0.U) { regs.at(wAddrs(i)) <== wDatas(i) }
            } else {
              regs(wAddrs(i)) := wDatas(i)
            }
          }
        }
      }
    }

    // --- L1 暴露的 HwFunction 接口 ---
    
    // 纯组合逻辑读取，不需要权限拦截
    def Read(addr: UInt): HwFunction[UInt] = HwFunction.stateless("Base_Read") { _ =>
      if (zeroReg) Mux(addr === 0.U, 0.U, regs(addr)) else regs(addr)
    }

    // 写入请求：自动鉴权对应的物理端口
    def Write(portIdx: Int, addr: UInt, data: UInt): HwFunction[Unit] = HwFunction.stateless(s"Base_Write_$portIdx") { agent =>
      this.grant(wAddrs(portIdx), agent)
      this.grant(wDatas(portIdx), agent)
      this.grant(wEnables(portIdx), agent)
      
      wAddrs(portIdx)   <== addr
      wDatas(portIdx)   <== data
      wEnables(portIdx) <== true.B
    }
  }

 // ==========================================
  // Layer 2: 记分板寄存器堆 (带调度拦截器) - 重构版
  // 组装 BaseRegfile 和 Stdlib.Scoreboard
  // ==========================================
  class ScoreboardRegfileProcess(val depth: Int, val width: Int, val maxWriters: Int, val zeroReg: Boolean, localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {

    // 1. 孵化 L1 数据通路 (BaseRegfile)
    val baseReg = spawn(new BaseRegfileProcess(depth, width, maxWriters, zeroReg, "Base"))
    
    // 2. 孵化 L2 操作系统控制通路 (Stdlib.Scoreboard)
    val scoreboard = spawn(new ScoreboardProcess(resourceCount=depth, maxConcurrentPorts = maxWriters, zeroAlwaysFree = zeroReg, "Control"))

    override def entry(): Unit = {}

    // ==========================================
    // 消费者接口：安全读取 (RAW 护盾)
    // ==========================================
    def GuardedRead(addr: UInt): HwFunction[UInt] = HwFunction.atomic("GuardedRead") { t =>
      // 1. 获取 Stdlib 记分板的就绪信号 (如果冲突，当前线程 pc 会在此挂起)
      val ready = SysCall.Call(scoreboard.Guard(addr))
      
      val rdata = this.own(WireInit(0.U(width.W)))
      grant(rdata, t)
      
      // 2. 只有当 ready (无 RAW 冲突) 时，才执行物理读取
      when(ready) {
        rdata <== SysCall.Call(baseReg.Read(addr))
      }
      rdata
    }

    // ==========================================
    // 生产者接口：写端口智能句柄 (RegWritePort)
    // ==========================================
    class RegWritePort(val portIdx: Int) {
      // 预约占位：底层的 ScoreboardLease 在 Reserve 时，会自动把契约挂载到 t.ctx (PCB) 中！
      def Reserve(addr: UInt): HwFunction[Unit] = HwFunction.atomic(s"Reserve_$portIdx") { t =>
        val sbLease = SysCall.Call(scoreboard.RequestLease(portIdx))
        SysCall.Call(sbLease.Reserve(addr))
      }

      // 写回并释放资源：将 BaseReg 的数据写入与 Lease 的释放绑定在一起
      def WritebackAndClear(addr: UInt, data: UInt): HwFunction[Unit] = HwFunction.stateless(s"WB_$portIdx") { agent =>
        val sbLease = SysCall.Call(scoreboard.RequestLease(portIdx))
        SysCall.Call(baseReg.Write(portIdx, addr, data))
        SysCall.Call(sbLease.Release())
      }
    }

    private val writePorts = Array.tabulate(maxWriters)(i => new RegWritePort(i))

    // 显式接口：向 Regfile 申请一个写端口的控制权
    def RequestWritePort(portIdx: Int): HwFunction[RegWritePort] = HwFunction.bindings(s"ReqRegPort_$portIdx") { _ =>
      writePorts(portIdx)
    }
  }
}