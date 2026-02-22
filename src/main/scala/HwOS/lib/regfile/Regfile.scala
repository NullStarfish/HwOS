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
  class BaseRegfileProcess(val depth: Int, val width: Int, val maxWriters: Int, val zeroReg: Boolean = true, n: String, d: Boolean, p: Option[HwProcess], k: Kernel) extends HwProcess(n, d, p)(k) {
    
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
              when(wAddrs(i) =/= 0.U) { regs(wAddrs(i)) := wDatas(i) }
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
  // Layer 2: 记分板寄存器堆 (带调度拦截器)
  // 组装 BaseRegfile，提供 RAW/WAW/WAR 冲突拦截
  // ==========================================
  class ScoreboardRegfileProcess(val depth: Int, val width: Int, val maxWriters: Int, val zeroReg: Boolean = true, n: String, d: Boolean, p: Option[HwProcess], k: Kernel) extends HwProcess(n, d, p)(k) {

    // 1. 孵化 L1 数据通路 (权限会自动 grant 给当前进程)
    val baseReg = spawn("Base") { (cn, cd, cp, ck) => new BaseRegfileProcess(depth, width, maxWriters, zeroReg, cn, cd, cp, ck) }

    // 2. Scoreboard 资产：Busy 表
    private val busyTable = this.own(RegInit(VecInit(Seq.fill(depth)(false.B))))

    // 拦截器交互线缆
    val setBusyAddr = WireInit(VecInit(Seq.fill(maxWriters)(0.U(log2Ceil(depth).W))))
    val setBusyEn   = WireInit(VecInit(Seq.fill(maxWriters)(false.B)))
    
    val clearBusyAddr = WireInit(VecInit(Seq.fill(maxWriters)(0.U(log2Ceil(depth).W))))
    val clearBusyEn   = WireInit(VecInit(Seq.fill(maxWriters)(false.B)))

    for(i <- 0 until maxWriters) {
      this.own(setBusyAddr(i));   this.own(setBusyEn(i))
      this.own(clearBusyAddr(i)); this.own(clearBusyEn(i))
    }

    // 3. 记分板守护进程：处理 Busy 表的状态翻转
    override def entry(): Unit = {
      val sbDaemon = createLogic("ScoreboardDaemon")
      this.grant(busyTable, sbDaemon)
      
      sbDaemon.run {
        // 解锁优先级高于加锁 (支持单拍内的 Forwarding / Bypassing 逻辑)
        for(i <- 0 until maxWriters) {
          when(clearBusyEn(i)) { busyTable(clearBusyAddr(i)) := false.B }
        }
        for(i <- 0 until maxWriters) {
          when(setBusyEn(i)) { busyTable(setBusyAddr(i)) := true.B }
        }
        // 如果是 0 号寄存器，永远保持非 Busy
        if (zeroReg) { busyTable(0) := false.B }
      }
    }

    // --- L2 暴露的被护盾包裹的 HwFunction 接口 ---

    // 操作 1：安全读取 (发生 RAW 冲突时自动阻塞)
    def GuardedRead(addr: UInt): HwFunction[UInt] = HwFunction.atomic("GuardedRead") { t =>
      val isBusy = busyTable(addr)
      t.waitCondition(!isBusy)
      
      val rdata = WireInit(0.U(width.W))
      when(!isBusy) {
        // 内联调用 L1 的读取逻辑
        rdata := SysCall.Call(baseReg.Read(addr))
        t.Next.hijack() // 读到的当拍放行
      }
      rdata
    }

    // 操作 2：指令发射时预约寄存器 (发生 WAW 冲突时自动阻塞)
    def Reserve(portIdx: Int, addr: UInt): HwFunction[Unit] = HwFunction.atomic(s"Reserve_$portIdx") { t =>
      val isBusy = busyTable(addr)
      t.waitCondition(!isBusy)
      
      when(!isBusy) {
        this.grant(setBusyAddr(portIdx), t)
        this.grant(setBusyEn(portIdx), t)
        setBusyAddr(portIdx) <== addr
        setBusyEn(portIdx)   <== true.B
        t.Next.hijack()
      }
      ()
    }

    // 操作 3：写回并解锁 (流水线 Commit 阶段调用，属于无状态的一拍到底)
    def WritebackAndClear(portIdx: Int, addr: UInt, data: UInt): HwFunction[Unit] = HwFunction.stateless(s"WB_$portIdx") { agent =>
      // 同时驱动 L1 的写端口和 L2 的清零端口
      SysCall.Call(baseReg.Write(portIdx, addr, data))

      this.grant(clearBusyAddr(portIdx), agent)
      this.grant(clearBusyEn(portIdx), agent)
      clearBusyAddr(portIdx) <== addr
      clearBusyEn(portIdx)   <== true.B
    }
  }
}