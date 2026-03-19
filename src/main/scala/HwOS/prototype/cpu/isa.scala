package HwOS.prototype.cpu

import HwOS.kernel.thread.HardwareThread
import chisel3._
import chisel3.util.Cat

object ISA {
  val opWidth = 2
  val regWidth = 3
  val immWidth = 8
  val instWidth = opWidth + regWidth + regWidth + immWidth

  val OP_ADDI = 0.U(opWidth.W)
  val OP_LOAD = 1.U(opWidth.W)
  val OP_LOADADD = 2.U(opWidth.W)

  case class Instr(op: Int, rd: Int, rs1: Int, imm: Int)

  def encode(inst: Instr): UInt = Cat(
    inst.op.U(opWidth.W),
    inst.rd.U(regWidth.W),
    inst.rs1.U(regWidth.W),
    inst.imm.U(immWidth.W),
  )

  def opcode(inst: UInt): UInt = inst(instWidth - 1, instWidth - opWidth)
  def rd(inst: UInt): UInt = inst(instWidth - opWidth - 1, immWidth + regWidth)
  def rs1(inst: UInt): UInt = inst(immWidth + regWidth - 1, immWidth)
  def imm(inst: UInt): UInt = inst(immWidth - 1, 0)
}

final class Slot(val slotId: Int, val thread: HardwareThread, val instArg: UInt)
