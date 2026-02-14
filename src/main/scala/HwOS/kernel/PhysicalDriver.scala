package HwOS.kernel

import chisel3._
import chisel3.util._



sealed trait DependencyModel
// 模型 A: 标量资源 (Scalar)
// 这种资源被视为一个整体，没有内部地址区分。比如 AXI总线、UART、FIFO管道。
// 只需要管理端口占用 (Structural Hazard)。
case object ScalarResource extends DependencyModel

// 模型 B: 向量资源 (Vector)
// 这种资源内部有多个独立的存储单元，需要按地址查重 (RAW Hazard)。
// 比如 RegFile (depth=32), SRAM (depth=1024)。
case class VectorResource(depth: Int) extends DependencyModel


object ConflictPolicies {
  // 定义常量
  val OP_READ  = 0.U
  val OP_WRITE = 1.U

  val RW_Lock = (activeOp: UInt, newOp: UInt) => {
    (activeOp === OP_WRITE) || (newOp === OP_WRITE)
  }

  val Full_Mutex = (activeOp: UInt, newOp: UInt) => {
    true.B
  }


  val Full_Duplex = (activeOp: UInt, newOp: UInt) => {
    activeOp === newOp 
  }
}



case class DriverMeta(
  name: String,
  model: DependencyModel,
  read_clients: Int,
  write_clients: Int, 
  fifo_depth: Int,
  conflict_policy: (UInt, UInt) => Bool = (_, _) => true.B,

)



abstract class PhysicalDriver(val meta: DriverMeta) {
  var _driverId: Int = 0
  protected def DriverStep(name: String, t: HardwareThread = null)(block: => Unit): Unit = {
    // 1. 优先使用显式传递的线程
    // 2. 否则尝试从 ContextScope 获取
    val thread = if (t != null) t else ContextScope.current match {
      case ThreadCtx(current) => current
      case _ => throw new Exception(s"[Driver Error] DriverStep '$name' called without explicit thread and outside of Thread context!")
    }

    // 关键：将 this._driverId 传给 Step，这样调试工具就知道这一步归属于当前 Driver
    thread.Step(name )(block)
  }
}


object DriverUtils {

  def genWStrobe(addr: UInt, size: UInt): UInt = {
    val baseMask = MuxLookup(size, 0.U)(Seq(
      0.U -> "b0001".U(4.W),
      1.U -> "b0011".U(4.W),
      2.U -> "b1111".U(4.W)
    ))
    baseMask << addr(1, 0)
  }

  def alignWData(data: UInt, addr: UInt): UInt = {
    val offsetBits = addr(1, 0) ## 0.U(3.W) // addr[1:0] * 8
    data << offsetBits
  }
}