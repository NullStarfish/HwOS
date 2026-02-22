package HwOS.stdlib

import chisel3._
import chisel3.util._
import HwOS.kernel._

object sync {

  // ==========================================
  // 1. 硬件 Mutex (内建优先级仲裁器)
  // ==========================================
  class Mutex(val maxClients: Int) {
    private val locked = RegInit(false.B)
    
    // 分布式请求线与释放线
    private val reqs = WireInit(VecInit(Seq.fill(maxClients)(false.B)))
    private val unlocks = WireInit(VecInit(Seq.fill(maxClients)(false.B)))

    // 组合逻辑结算中心 (Arbiter)
    private val anyUnlock = unlocks.reduce(_ || _)
    private val anyReq    = reqs.reduce(_ || _)
    private val winnerIdx = PriorityEncoder(reqs) // 优先级仲裁

    // 状态机流转
    when(anyUnlock) {
      locked := false.B
    } .elsewhen(anyReq && !locked) {
      locked := true.B
    }

    // Client 调用的 HwFunction：传入自己唯一的 ID 以接入仲裁线
    def Lock(id: Int): HwFunction[Unit] = HwFunction.atomic(s"Mutex_Lock_$id") { t =>
      reqs(id) := true.B
      // 核心：锁不仅要空闲，我还必须是仲裁胜出者！
      val canAcquire = !locked && (winnerIdx === id.U)
      
      t.waitCondition(canAcquire)
      when(canAcquire) {
        t.Next.hijack()
      }
      ()
    }

    def Unlock(id: Int): HwFunction[Unit] = HwFunction.stateless(s"Mutex_Unlock_$id") { _ =>
      unlocks(id) := true.B
    }
  }

  // ==========================================
  // 2. 硬件 WaitGroup (内建当拍加法树)
  // ==========================================
  class WaitGroup(val maxClients: Int) {
    private val count = RegInit(0.U(32.W))
    
    // 每条线程有独立的加减通道，彻底解决 lastConnect 数据践踏
    private val adds  = WireInit(VecInit(Seq.fill(maxClients)(0.U(32.W))))
    private val dones = WireInit(VecInit(Seq.fill(maxClients)(false.B)))

    // 当拍结算中心 (加法树与 PopCount)
    private val totalAdd  = adds.reduce(_ + _)
    private val totalDone = PopCount(dones)
    
    // 窥探下一拍的值，保证当拍归零时能立刻解锁
    private val nextCount = count + totalAdd - totalDone
    count := nextCount

    def Add(id: Int, delta: UInt): HwFunction[Unit] = HwFunction.stateless(s"WG_Add_$id") { _ =>
      adds(id) := delta
    }

    def Done(id: Int): HwFunction[Unit] = HwFunction.stateless(s"WG_Done_$id") { _ =>
      dones(id) := true.B
    }

    def Wait(): HwFunction[Unit] = HwFunction.atomic("WG_Wait") { t =>
      t.waitCondition(nextCount === 0.U)
      when(nextCount === 0.U) {
        t.Next.hijack()
      }
      ()
    }
  }
}