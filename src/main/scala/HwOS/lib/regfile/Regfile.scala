package HwOS.lib.regfile

import chisel3._
import chisel3.util._
import HwOS.kernel._
import HwOS.kernel.HwOSLanguage._

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

    // 1. 孵化 L1 数据通路 
    val baseReg = spawn(new BaseRegfileProcess(depth, width, maxWriters, zeroReg, "Base"))
    
    // 2. 孵化 Stdlib 通用记分板控制器
    val scoreboard = spawn(new HwOS.stdlib.sync.ScoreboardProcess(resourceCount = depth, maxConcurrentPorts = maxWriters, zeroAlwaysFree = zeroReg, "Control"))

    // Entry 现在不需要 Daemon 了，业务全部交由子 Process 处理
    override def entry(): Unit = {}

    // --- L2 暴露的被护盾包裹的 HwFunction 接口 ---

    // 操作 1：安全读取 (组合 Stdlib 的 Guard 和 Base 的 Read)
    def GuardedRead(addr: UInt): HwFunction[UInt] = HwFunction.atomic("GuardedRead") { t =>
      // 先让 Stdlib 帮我们挂起等待 (RAW冲突自动阻塞)
      SysCall.Call(scoreboard.Guard(addr))
      
      // 不拥堵了，调用基础寄存器的纯组合逻辑读
      SysCall.Call(baseReg.Read(addr))
    }

    // 操作 2：指令发射时预约寄存器 
    def Reserve(portIdx: Int, addr: UInt): HwFunction[Unit] = HwFunction.atomic(s"Reserve_$portIdx") { t =>
      // 直接委托给 Stdlib
      SysCall.Call(scoreboard.Reserve(portIdx, addr))
    }

    // 操作 3：写回并解锁 
    def WritebackAndClear(portIdx: Int, addr: UInt, data: UInt): HwFunction[Unit] = HwFunction.stateless(s"WB_$portIdx") { agent =>
      // 1. 写入物理数据
      SysCall.Call(baseReg.Write(portIdx, addr, data))
      // 2. 释放 Stdlib 的控制锁
      SysCall.Call(scoreboard.Release(portIdx, addr))
    }
  } 
}