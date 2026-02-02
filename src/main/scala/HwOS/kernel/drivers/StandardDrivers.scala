package HwOS.kernel.drivers

import chisel3._
import chisel3.util._
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

class CSRDriver(regs: Vec[UInt], kernel: Kernel) extends PhysicalDriver(
  DriverMeta("CSR", VectorResource(regs.length), 2, 1, 2, ConflictPolicies.RW_Lock)
) {
  def read(addr: UInt, id: UInt): UInt = {
    ContextScope.current match {
      case ThreadCtx(t) => {
        val latch = RegInit(0.U(XLEN.W))
        t.Step("CSRRead") {
          val safeAddr = Mux(addr < regs.length.U, addr, 0.U)
          latch := regs(safeAddr)
          kernel.secure_done(meta.name, addr, ConflictPolicies.OP_READ, id)
        }
        latch
      }
      case _ => {
        kernel.secure_done(meta.name, addr, ConflictPolicies.OP_READ, id)
        Mux(addr < regs.length.U, regs(addr), 0.U)
      }
    }
  }

  def write(addr: UInt, data: UInt, id: UInt): Unit = {
    ContextScope.current match {
      case ThreadCtx(t) => {
        t.Step("CSRWrite") {
          when(addr < regs.length.U) {
            regs(addr) := data(XLEN-1, 0)
          }
          kernel.secure_done(meta.name, addr, ConflictPolicies.OP_WRITE, id)
        }
      }
      case _ => {
        when(addr < regs.length.U) {
           regs(addr) := data(XLEN-1, 0)
        }
        kernel.secure_done(meta.name, addr, ConflictPolicies.OP_WRITE, id)
      }
    }
  }
}