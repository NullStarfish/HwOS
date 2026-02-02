package HwOS.kernel.drivers 

import chisel3._
import chisel3.util._
import HwOS.kernel._
import mycpu.common.KERNEL_DATA_WIDTH

// PipeDriver: 封装一个 Queue，支持阻塞读写
class PipeDriver(val name: String, depth: Int, kernel: Kernel) extends PhysicalDriver(
  DriverMeta(name, ScalarResource, 1, 1, 4, ConflictPolicies.Full_Duplex)
) {
  // 在这里实例化 Queue，它是 Kernel 模块的一部分
  val queue = Module(new Queue(UInt(KERNEL_DATA_WIDTH.W), depth)) 

  // 默认信号：如果没有 Thread 驱动，保持低电平
  queue.io.enq.valid := false.B
  queue.io.enq.bits  := 0.U
  queue.io.deq.ready := false.B

  def read(id: UInt): UInt = {
    ContextScope.current match {
      case ThreadCtx(t) => {
        val latch = RegInit(0.U(KERNEL_DATA_WIDTH.W))
        t.Step("PipeRead") {
          // 1. 只有当队列有数据(valid)时才继续
          t.waitCondition(queue.io.deq.valid)
          
          // 2. 读数据并断言 ready
          latch := queue.io.deq.bits
          queue.io.deq.ready := true.B
          
          // 3. 提交
          kernel.secure_done(meta.name, 0.U, ConflictPolicies.OP_READ, id)
        }
        latch
      }
      case _ => {
        // 非线程环境：非阻塞读取（如果需要）
        queue.io.deq.ready := true.B
        kernel.secure_done(meta.name, 0.U, ConflictPolicies.OP_READ, id)
        queue.io.deq.bits
      }
    }
  }

  def write(data: UInt, id: UInt): Unit = {
    ContextScope.current match {
      case ThreadCtx(t) => {
        t.Step("PipeWrite") {
          // 1. 等待队列不满(ready)
          t.waitCondition(queue.io.enq.ready)
          
          // 2. 写入
          queue.io.enq.bits  := data
          queue.io.enq.valid := true.B
          
          // 3. 提交
          kernel.secure_done(meta.name, 0.U, ConflictPolicies.OP_WRITE, id)
        }
      }
      case _ => {
        queue.io.enq.valid := true.B
        queue.io.enq.bits  := data
        kernel.secure_done(meta.name, 0.U, ConflictPolicies.OP_WRITE, id)
      }
    }
  }
}

class TerminalDriver(val name: String, kernel: Kernel) extends PhysicalDriver(
  DriverMeta(name, ScalarResource, 1, 1, 2, ConflictPolicies.Full_Mutex)
) {
  def write(data: UInt, id: UInt): Unit = {
    ContextScope.current match {
      case ThreadCtx(t) => {
        t.Step("TermWrite") {
          // 仿真环境下打印字符
          printf("%c", data(7,0))
          kernel.secure_done(meta.name, 0.U, ConflictPolicies.OP_WRITE, id)
        }
      }
      case _ => {
        printf("%c", data(7,0))
        kernel.secure_done(meta.name, 0.U, ConflictPolicies.OP_WRITE, id)
      }
    }
  }
}