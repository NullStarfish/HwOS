package HwOS.stdlib

import chisel3._
import chisel3.util._
import HwOS.kernel._
import HwOS.kernel.HwOSLanguage._

object sync {

  def Lock(locked: Bool): HwFunction[Unit] = HwFunction[Unit]("Mutex_Lock") { agent =>
    ContextScope.current match {
      case AtomicCtx(t) =>
        t.waitCondition(!locked)
        when(!locked) {
          locked <== true.B
          t.Next.hijack()
        }
        () // [修复] 显式返回 Unit，压制 when 产生的 WhenContext

      case _ => throw new Exception(s"[stdlib.sync] Lock() 必须在 Step (AtomicCtx) 中调用。Caller: ${agent.name}")
    }
  }

  def Unlock(locked: Bool): HwFunction[Unit] = HwFunction[Unit]("Mutex_Unlock") { agent =>
    ContextScope.current match {
      case AtomicCtx(t) =>
        locked <== false.B
        t.Next.hijack()
        () // [修复] 显式返回 Unit
        
      case LogicCtx(l) =>
        locked <== false.B
        () // [修复] 显式返回 Unit
        
      case _ => throw new Exception(s"[stdlib.sync] Unlock() 上下文错误。Caller: ${agent.name}")
    }
  }

  def Acquire(count: UInt): HwFunction[Unit] = HwFunction[Unit]("Sem_Acquire") { agent =>
    ContextScope.current match {
      case AtomicCtx(t) =>
        t.waitCondition(count > 0.U)
        when(count > 0.U) {
          count <== count - 1.U
          t.Next.hijack()
        }
        () // [修复] 显式返回 Unit
        
      case _ => throw new Exception(s"[stdlib.sync] Acquire() 必须在 Step 中调用。Caller: ${agent.name}")
    }
  }

  def Release(count: UInt): HwFunction[Unit] = HwFunction[Unit]("Sem_Release") { agent =>
    ContextScope.current match {
      case AtomicCtx(t) =>
        count <== count + 1.U
        t.Next.hijack()
        () // [修复] 显式返回 Unit
        
      case LogicCtx(l) =>
        count <== count + 1.U
        () // [修复] 显式返回 Unit
        
      case _ => throw new Exception(s"[stdlib.sync] Release() 上下文错误。Caller: ${agent.name}")
    }
  }

  // Add, Done, Wait 等逻辑也是同样的修改模式，在末尾加上 () 即可
  def Add(wgCounter: UInt, delta: UInt): HwFunction[Unit] = HwFunction[Unit]("WG_Add") { agent =>
    ContextScope.current match {
      case AtomicCtx(t) =>
        wgCounter <== wgCounter + delta
        t.Next.hijack()
        ()
      case LogicCtx(l) =>
        wgCounter <== wgCounter + delta
        ()
      case _ => throw new Exception("...")
    }
  }

  // 对于带有明确返回值的 HwFunction (比如 Select 返回 UInt)，则不需要加 ()
  def Select(readySignals: Seq[Bool]): HwFunction[UInt] = HwFunction[UInt]("Select") { agent =>
    val selectedIdx = WireInit(0.U(log2Ceil(readySignals.length max 2).W))

    ContextScope.current match {
      case AtomicCtx(t) =>
        val anyReady = readySignals.reduce(_ || _)
        t.waitCondition(anyReady)
        when(anyReady) {
          selectedIdx := PriorityEncoder(readySignals)
          t.Next.hijack()
        }
        // 这里不需要 ()，因为最后一行要返回 selectedIdx

      case LogicCtx(l) =>
        selectedIdx := PriorityEncoder(readySignals)

      case _ => throw new Exception("...")
    }
    
    selectedIdx // 最后一行为返回值 UInt，类型匹配，编译通过
  }
}