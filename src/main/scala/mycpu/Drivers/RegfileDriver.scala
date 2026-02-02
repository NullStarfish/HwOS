package mycpu.Drivers

import chisel3._
import HwOS.kernel._
class RegfileDriver(regs: Vec[UInt],  kernel: Kernel) extends PhysicalDriver(
    DriverMeta (
      "RF", VectorResource(32), 2, 2, 2, ConflictPolicies.RW_Lock
    )
  ) {
  def read(addr: UInt, id: UInt) : UInt = {
    ContextScope.current match {
      case ThreadCtx(t) => {
        val latch = RegInit(0.U(32.W))
        t.Step("RegRead") {
          latch := regs(addr)
          kernel.secure_done(meta.name, addr, ConflictPolicies.OP_READ, id)
        }
        latch
      }
      case _ => {
        kernel.secure_done(meta.name, addr, ConflictPolicies.OP_READ, id)
        regs(addr)
      }
    }
  }

  def write(addr: UInt, data: UInt, id: UInt): Unit= {
    ContextScope.current match { 
      case ThreadCtx(t) => {
        t.Step("Reg write") {
          regs(addr) := data
          kernel.secure_done(meta.name, addr, ConflictPolicies.OP_WRITE, id)
        }
      }
      case _ => {
        regs(addr) := data
        kernel.secure_done(meta.name, addr, ConflictPolicies.OP_WRITE, id)
      }
    }
  }
}