package mycpu.Drivers

import chisel3._
import chisel3.util._
import HwOS.kernel._
import mycpu.utils.AXI4Bundle

class SmartAXIDriver(bus: AXI4Bundle, kernel: Kernel) extends PhysicalDriver(
  DriverMeta("AXI_BUS", ScalarResource, 1, 1, 4, ConflictPolicies.Full_Mutex)
) {
  
  // 默认值：防止未被 Step 覆盖时的信号浮空
  // Chisel 的 Last Connect 机制会确保 Step 内部的赋值覆盖这些默认值
  bus.ar.valid := false.B
  bus.ar.bits  := DontCare
  bus.r.ready  := false.B
  
  bus.aw.valid := false.B
  bus.aw.bits  := DontCare
  bus.w.valid  := false.B
  bus.w.bits   := DontCare
  bus.b.ready  := false.B

  // --- 读操作 ---
  def read(addr: UInt, size: UInt, id: UInt): UInt = {
    ContextScope.current match {
      case ThreadCtx(t) => {
        val dataLatch = RegInit(0.U(32.W))
        
        // 步骤 1: 发送读地址
        t.Step("AXI_Read_Addr") {
          bus.ar.valid := true.B
          bus.ar.bits.addr := addr
          bus.ar.bits.size := size
          bus.ar.bits.len  := 0.U
          bus.ar.bits.burst := 1.U // INCR
          bus.ar.bits.id   := 0.U
          
          // 阻塞直到握手成功
          t.waitCondition(bus.ar.ready)
        }

        // 步骤 2: 接收读数据
        t.Step("AXI_Read_Data") {
          bus.r.ready := true.B
          
          t.waitCondition(bus.r.valid)
          
          // 锁存数据，因为 Step 结束后 bus.r.bits 可能变化
          dataLatch := bus.r.bits.data
          
          // 只有在最后一个 Step 调用 done
          kernel.secure_done(meta.name, 0.U, ConflictPolicies.OP_READ, id)
        }
        
        dataLatch
      }
      case _ => throw new Exception("AXI Read must be called from a Thread")
    }
  }

  // --- 写操作 ---
  def write(addr: UInt, data: UInt, size: UInt, id: UInt): Unit = {
    ContextScope.current match {
      case ThreadCtx(t) => {
        // 使用寄存器记录握手状态，因为我们在一个 Step 里同时发 AW 和 W
        val awDone = RegInit(false.B)
        val wDone  = RegInit(false.B)

        t.Step("AXI_Write_Issue") {
          // 发送 AW
          when(!awDone) {
            bus.aw.valid := true.B
            bus.aw.bits.addr := addr
            bus.aw.bits.size := size
            bus.aw.bits.len  := 0.U
            bus.aw.bits.burst := 1.U
            when(bus.aw.ready) { awDone := true.B }
          }

          // 发送 W
          when(!wDone) {
            bus.w.valid := true.B
            val offset = addr(1, 0)
            val shift  = Cat(offset, 0.U(3.W)) 
            bus.w.bits.data := data(31, 0) << shift
            // 简单的 Strobe 生成
            bus.w.bits.strb := MuxLookup(size, "b1111".U)(Seq(
              0.U -> "b0001".U, 1.U -> "b0011".U
            )) << offset
            bus.w.bits.last := true.B
            when(bus.w.ready) { wDone := true.B }
          }
          
          // 等待两者都完成（注意：检查当前周期的 fire 状态或已完成状态）
          val awFinished = awDone || (bus.aw.valid && bus.aw.ready)
          val wFinished  = wDone  || (bus.w.valid && bus.w.ready)
          
          t.waitCondition(awFinished && wFinished)
        }
        
        // 写响应阶段
        t.Step("AXI_Write_Resp") {
          // 此时进入新状态，必须重置握手标志，供下次调用使用
          // (虽然每次 Entry 都会重置 RegInit，但 Loop 模式下需手动重置)
          awDone := false.B
          wDone  := false.B
          
          bus.b.ready := true.B
          t.waitCondition(bus.b.valid)
          
          kernel.secure_done(meta.name, 0.U, ConflictPolicies.OP_WRITE, id)
        }
      }
      case _ => throw new Exception("AXI Write must be called from a Thread")
    }
  }
}