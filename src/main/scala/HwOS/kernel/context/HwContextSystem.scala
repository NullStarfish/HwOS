package HwOS.kernel.context

import chisel3._
import scala.reflect.ClassTag
import HwOS.kernel.system.Kernel
import HwOS.kernel.memory.{ExportCapability, ExportedSymbol, VirtualHandle}

class HwContext(val self: HwContextEntity) {
  def name: String = self.name
}


object HwContext {
  def apply(self: HwContextEntity) : HwContext = new HwContext(self)
}

// ---------------------------------------------------------
// 任何持有上下文的实体
// ---------------------------------------------------------
trait HwContextEntity {
  def name: String 
  def kernel: Kernel

  // 每个实体都显式持有一个 context。
  // context 是受保护赋值、resource ACL、kernel kill cut-off 的中心；
  // entity 只是承载这个 context 的对象壳。
  val ctx = new HwContext(this)

  def export[T <: Data](symbolName: String, signal: T, caps: ExportCapability): ExportedSymbol[T] = {
    kernel.addressSpace.registerExport(name, symbolName, signal, caps)
  }

  def declare[T <: Data: ClassTag](symbolName: String, caps: ExportCapability): VirtualHandle[T] = {
    kernel.addressSpace.resolveExport[T](symbolName, name, caps)
  }
}
