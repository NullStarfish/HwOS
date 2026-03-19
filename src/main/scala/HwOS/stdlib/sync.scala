package HwOS.stdlib

import chisel3._
import HwOS.kernel.context.{AtomicCtx, ContextScope, LogicCtx}
import HwOS.kernel.function.HwInline
import HwOS.kernel.thread.HardwareAgent

package object sync {

  // 无状态多路选择 utility；不是 process/service。
  def Select(readySignals: Seq[Bool]): HwInline[UInt] = HwInline("Select") { agent: HardwareAgent =>
    val selectedIdx = WireInit(0.U(chisel3.util.log2Ceil(readySignals.length max 2).W))

    ContextScope.current match {
      case AtomicCtx(t) =>
        val anyReady = readySignals.reduce(_ || _)
        t.waitCondition(anyReady)
        when(anyReady) {
          selectedIdx := chisel3.util.PriorityEncoder(readySignals)
          t.hijack(t.Next)
        }
      case LogicCtx(_) =>
        selectedIdx := chisel3.util.PriorityEncoder(readySignals)
      case _ =>
        throw new Exception(s"[stdlib.sync] Select 上下文错误。调用者: ${agent.name}")
    }
    selectedIdx
  }
}
