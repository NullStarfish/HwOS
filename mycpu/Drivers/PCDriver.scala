package mycpu.Drivers
import chisel3._
import HwOS.kernel._
import mycpu.common._

class PCDriver(pcReg: UInt, kernel: Kernel) extends PhysicalDriver(
  DriverMeta("PC", ScalarResource, 4, 1, 2, ConflictPolicies.RW_Lock)
) {
  // 读操作也放入 Step，虽然 PC 是寄存器，但为了统一的时序感知和 Hook 能力
  def read(id: UInt): UInt = {
    ContextScope.current match {
      case ThreadCtx(t) => {
        val latch = RegInit(0.U(XLEN.W))
        t.Step("PCRead") {
          latch := pcReg
          kernel.secure_done(meta.name, 0.U, ConflictPolicies.OP_READ, id)
        }
        latch
      }
      // 支持非线程环境（如调试器或系统逻辑）直接读取
      case _ => {
        kernel.secure_done(meta.name, 0.U, ConflictPolicies.OP_READ, id)
        pcReg
      }
    }
  }

  def write(target: UInt, id: UInt): Unit = {
    ContextScope.current match {
      case ThreadCtx(t) => {
        t.Step("PCWrite") {
          pcReg := target(XLEN-1, 0)
          kernel.secure_done(meta.name, 0.U, ConflictPolicies.OP_WRITE, id)
        }
      }
      case _ => {
        pcReg := target(XLEN-1, 0)
        kernel.secure_done(meta.name, 0.U, ConflictPolicies.OP_WRITE, id)
      }
    }
  }
}