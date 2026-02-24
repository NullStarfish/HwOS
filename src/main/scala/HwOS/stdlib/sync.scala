package HwOS.stdlib

import chisel3._
import chisel3.util._
import HwOS.kernel._
import HwOS.kernel.HwOSLanguage._

object sync {

  // ==========================================
  // 1. 硬件互斥锁 (Mutex Process)
  // 内建基于优先级编码器的分布式仲裁器
  // ==========================================
  class MutexProcess(val maxClients: Int, localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
    
    private val locked = this.own(RegInit(false.B))
    
    // 分布式请求线与释放线
    private val reqs = WireInit(VecInit(Seq.fill(maxClients)(false.B)))
    private val unlocks = WireInit(VecInit(Seq.fill(maxClients)(false.B)))

    // 1. 进程在实例化时对自己内部的线缆宣誓主权
    for (i <- 0 until maxClients) {
      this.own(reqs(i))
      this.own(unlocks(i))
    }

    // 仲裁结算中心 (纯组合逻辑)
    private val anyUnlock = unlocks.reduce(_ || _)
    private val anyReq    = reqs.reduce(_ || _)
    private val winnerIdx = PriorityEncoder(reqs)

    // 状态机流转
    override def entry(): Unit = {
      val main = createLogic("Main")
      this.grant(locked, main)
      main.run {
        when(anyUnlock) {
          locked <== false.B
        } .elsewhen(anyReq && !locked) {
          locked <== true.B
        }
      }
    }


    // --- 高阶 HwFunction 接口 ---
    def Lock(id: Int): HwFunction[Unit] = HwFunction.atomic(s"Lock_$id") { t =>
      this.grant(reqs(id), t) 
      reqs(id) <== true.B // 安全写入
      val canAcquire = !locked && (winnerIdx === id.U)
      t.waitCondition(canAcquire)
      when(canAcquire) {
        t.Next.hijack() // 时序坍缩
      }
      ()
    }

    def Unlock(id: Int): HwFunction[Unit] = HwFunction.stateless(s"Unlock_$id") { agent =>
      this.grant(unlocks(id), agent) 
      unlocks(id) <== true.B
    }
  }

  // ==========================================
  // 2. 硬件信号量 (Semaphore Process)
  // 支持多 Client 并发 Release，但单拍只允许 1 个 Client Acquire 成功
  // ==========================================
  class SemaphoreProcess(val maxClients: Int, val initialCount: Int,  localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
    
    private val count = this.own(RegInit(initialCount.U(32.W)))
    
    private val acquires = WireInit(VecInit(Seq.fill(maxClients)(false.B)))
    private val releases = WireInit(VecInit(Seq.fill(maxClients)(false.B)))

    for (i <- 0 until maxClients) {
      this.own(acquires(i))
      this.own(releases(i))
    }

    private val anyAcquire = acquires.reduce(_ || _)
    private val winnerAcqIdx = PriorityEncoder(acquires)
    
    // 允许多个 Client 在同一拍释放资源，使用 PopCount 累加
    private val totalRelease = PopCount(releases)
    
    // 结算下一拍的 Count
    override def entry(): Unit = {
      val main = createLogic("Main")
      this.grant(count, main)
      main.run{
        when (anyAcquire && (count > 0.U)) {
          count <== count + totalRelease - 1.U
        } .otherwise {
          count <== count + totalRelease
        }
      }
    }

    // --- 高阶 HwFunction 接口 ---
    def Acquire(id: Int): HwFunction[Unit] = HwFunction.atomic(s"Sem_Acq_$id") { t =>
      this.grant(acquires(id), t)
      acquires(id) <== true.B
      
      val canAcquire = (count > 0.U) && (winnerAcqIdx === id.U)
      t.waitCondition(canAcquire)
      when(canAcquire) { t.Next.hijack() }
      ()
    }

    def Release(id: Int): HwFunction[Unit] = HwFunction.stateless(s"Sem_Rel_$id") { agent =>
      this.grant(releases(id), agent)
      releases(id) <== true.B
    }
  }

  // ==========================================
  // 3. 硬件等待组 (WaitGroup Process)
  // 内建并发加法树，完美解决多 Worker 同拍 Done 的数据践踏问题
  // ==========================================
  class WaitGroupProcess(val maxClients: Int, localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
    
    private val count = this.own(RegInit(0.U(32.W)))
    
    private val adds  = WireInit(VecInit(Seq.fill(maxClients)(0.U(32.W))))
    private val dones = WireInit(VecInit(Seq.fill(maxClients)(false.B)))

    for (i <- 0 until maxClients) {
      this.own(adds(i))
      this.own(dones(i))
    }

    // 组合逻辑加法树与 PopCount
    private val totalAdd  = adds.reduce(_ + _)
    private val totalDone = PopCount(dones)
    
    // 窥探下一拍的值，保证当拍归零时能立刻解锁主线程
    private val nextCount = count + totalAdd - totalDone

    override def entry(): Unit = {
      val main = createLogic("Main") 
      this.grant(count, main)
      main.run {
        count <== nextCount
      }
    }


    // --- 高阶 HwFunction 接口 ---
    def Add(id: Int, delta: UInt): HwFunction[Unit] = HwFunction.stateless(s"WG_Add_$id") { agent =>
      this.grant(adds(id), agent)
      adds(id) <== delta
    }

    def Done(id: Int): HwFunction[Unit] = HwFunction.stateless(s"WG_Done_$id") { agent =>
      this.grant(dones(id), agent)
      dones(id) <== true.B
    }

    def Wait(): HwFunction[Unit] = HwFunction.atomic("WG_Wait") { t =>
      // Wait 操作仅仅是纯组合逻辑读取 nextCount，不修改数据通路，因此不需要 grant 和 <==
      t.waitCondition(nextCount === 0.U)
      when(nextCount === 0.U) {
        t.Next.hijack()
      }
      ()
    }
  }

  // ==========================================
  // 4. 多路复用 (Select)
  // 作为纯无状态的 Utility 函数存在，不占有任何物理寄存器资源
  // ==========================================
  def Select(readySignals: Seq[Bool]): HwFunction[UInt] = HwFunction("Select") { agent =>
    val selectedIdx = WireInit(0.U(log2Ceil(readySignals.length max 2).W))

    ContextScope.current match {
      case AtomicCtx(t) =>
        val anyReady = readySignals.reduce(_ || _)
        t.waitCondition(anyReady)
        when(anyReady) {
          selectedIdx := PriorityEncoder(readySignals)
          t.Next.hijack()
        }
      case LogicCtx(l) =>
        selectedIdx := PriorityEncoder(readySignals)
      case _ => throw new Exception(s"[stdlib.sync] Select 上下文错误。调用者: ${agent.name}")
    }
    selectedIdx // 返回硬件线缆引用
  }




  // ==========================================
  // 5. 硬件通用记分板 (Generic Scoreboard Process)
  // 用于管理一组资源的预约与释放，支持 RAW/WAW 冲突拦截，适用于 Regfile、缓存行或多Bank外设
  // ==========================================
  class ScoreboardProcess(val resourceCount: Int, val maxConcurrentPorts: Int, val zeroAlwaysFree: Boolean = false, localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
    
    // 核心资产：Busy 状态表
    private val busyTable = this.own(RegInit(VecInit(Seq.fill(resourceCount)(false.B))))

    // 交互线缆：支持多端口并发 set/clear
    val setIdx = WireInit(VecInit(Seq.fill(maxConcurrentPorts)(0.U(log2Ceil(resourceCount).W))))
    val setEn  = WireInit(VecInit(Seq.fill(maxConcurrentPorts)(false.B)))
    
    val clrIdx = WireInit(VecInit(Seq.fill(maxConcurrentPorts)(0.U(log2Ceil(resourceCount).W))))
    val clrEn  = WireInit(VecInit(Seq.fill(maxConcurrentPorts)(false.B)))

    for(i <- 0 until maxConcurrentPorts) {
      this.own(setIdx(i)); this.own(setEn(i))
      this.own(clrIdx(i)); this.own(clrEn(i))
    }

    // 守护进程：处理 Busy 表翻转
    override def entry(): Unit = {
      val sbDaemon = createLogic("SBDaemon")
      this.grant(busyTable, sbDaemon)
      
      sbDaemon.run {
        // 先 clear 后 set，支持单拍内的 Bypassing
        for(i <- 0 until maxConcurrentPorts) {
          when(clrEn(i)) { busyTable.at(clrIdx(i)) <== false.B }
        }
        for(i <- 0 until maxConcurrentPorts) {
          when(setEn(i)) { busyTable.at(setIdx(i)) <== true.B }
        }
        // 若某些资源（如 RISC-V 0号寄存器）免预约
        if (zeroAlwaysFree) { busyTable.at(0) <== false.B }
      }
    }

    // --- 高阶 HwFunction 接口 ---

    // 纯阻塞守卫：只等不占 (适用于 RAW 冲突场景)
    def Guard(addr: UInt): HwFunction[Unit] = HwFunction.atomic("Guard") { t =>
      val isBusy = busyTable.at(addr)
      t.waitCondition(!isBusy)
      when(!isBusy) { t.Next.hijack() } // 资源空闲时当拍放行
      ()
    }

    // 预约占位：阻塞等待直至空闲，然后锁定 (适用于 WAW 冲突/指令发射场景)
    def Reserve(portIdx: Int, addr: UInt): HwFunction[Unit] = HwFunction.atomic(s"Reserve_$portIdx") { t =>
      val isBusy = busyTable.at(addr)
      t.waitCondition(!isBusy)
      
      when(!isBusy) {
        this.grant(setIdx(portIdx), t); this.grant(setEn(portIdx), t)
        setIdx(portIdx) <== addr
        setEn(portIdx)  <== true.B
        t.Next.hijack()
      }
      ()
    }

    // 释放资源：无状态，一拍到底 (适用于写回/Commit阶段)
    def Release(portIdx: Int, addr: UInt): HwFunction[Unit] = HwFunction.stateless(s"Release_$portIdx") { agent =>
      this.grant(clrIdx(portIdx), agent); this.grant(clrEn(portIdx), agent)
      clrIdx(portIdx) <== addr
      clrEn(portIdx)  <== true.B
    }
  }
}