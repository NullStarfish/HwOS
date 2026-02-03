package HwOS.kernel.drivers

import chisel3._
import HwOS.kernel._
class RegfileDriver(regs: Vec[UInt],  kernel: Kernel) extends PhysicalDriver(
    DriverMeta (
      "RF", VectorResource(32), 2, 2, 2, ConflictPolicies.RW_Lock
    )
  ) {
  def read(addr: UInt) : UInt = {
    ContextScope.current match {
      case ThreadCtx(t) => {
        val latch = RegInit(0.U(32.W))
        t.Step("RegRead") {
          latch := regs(addr)
        }
        latch
      }
      case _ => {
        regs(addr)
      }
    }
  }

  def write(addr: UInt, data: UInt): Unit= {
    ContextScope.current match { 
      case ThreadCtx(t) => {
        t.Step("Reg write") {
          regs(addr) := data
        }
      }
      case _ => {
        regs(addr) := data
      }
    }
  }
}