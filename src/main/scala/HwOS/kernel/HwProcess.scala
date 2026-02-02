package HwOS.kernel

import chisel3._
import chisel3.util._
import scala.collection.mutable.ArrayBuffer

//命名永远让上一级来命名
abstract class HwProcess(val name: String, val debugEnable: Boolean = true, val parent: Option[HwProcess])(kernel: Kernel) {
  
  private val threads = ArrayBuffer[HardwareThread]()
  private val logics  = ArrayBuffer[HardwareLogic]()
  private val children = ArrayBuffer[HwProcess]()

  protected def createThread(name: String = "Main"): HardwareThread = {
    val t = new HardwareThread(s"${this.name}/${name}_thread", this, debugEnable)
    kernel.registerThread(s"${this.name}/${name}_thread", t)
    threads += t
    t
  }
  
  protected def createLogic(name: String = "Daemon"): HardwareLogic = {
    val l = new HardwareLogic(s"${this.name}/${name}_logic", this, debugEnable)
    logics += l
    l
  }


  def spawn[T <: HwProcess](name: String)(constructor: (String, Boolean, Option[HwProcess], Kernel) => T): T = {
    val childFullName = if (this.name.isEmpty) name else s"${this.name}/$name"
    val child = constructor(childFullName, this.debugEnable, Some(this), kernel)
    kernel.registerProcess(childFullName, child)
    this.children += child
    child.build()
    child
  }
  

  def entry(): Unit

  
  def build(): Unit = {
    entry()
  }
}