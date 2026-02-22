package HwOS.kernel

import chisel3._
import chisel3.util._
import scala.collection.mutable.ArrayBuffer

//命名永远让上一级来命名
abstract class HwProcess(val name: String, val debugEnable: Boolean = true, val parent: Option[HwProcess])(kernel: Kernel) extends HwOwner {
  
  val threads = ArrayBuffer[HardwareThread]()
  val logics  = ArrayBuffer[HardwareLogic]()
  val children = ArrayBuffer[HwProcess]()


  def createThread(name: String = "Main"): HardwareThread = {
    val t = new HardwareThread(s"${this.name}/${name}_thread", this, debugEnable)
    kernel.registerThread(s"${this.name}/${name}_thread", t)
    threads += t
    t.grantLifecycle(this) 
    t
  }
  
  protected def createLogic(name: String = "Daemon"): HardwareLogic = {
    val l = new HardwareLogic(s"${this.name}/${name}_logic", this, debugEnable)
    logics += l
    l.grantLifecycle(this)
    l
  }


  def spawn[T <: HwProcess](name: String)(constructor: (String, Boolean, Option[HwProcess], Kernel) => T): T = {
    val childFullName = if (this.name.isEmpty) name else s"${this.name}/$name"
    val child = constructor(childFullName, this.debugEnable, Some(this), kernel)
    
    kernel.registerProcess(childFullName, child)
    this.children += child
    
    // 展开子进程的内部逻辑
    child.build()
    
    child.getAllOwnedSignals().foreach { sig =>
      child.grant(sig, this)
    }

    // 同样，允许父进程跨越生命周期杀死/控制子进程
    child.grantLifecycle(this)
    child.threads.foreach(_.grantLifecycle(this))
    child.logics.foreach(_.grantLifecycle(this))
    child.children.foreach(_.grantLifecycle(this))

    child
  }
  

  def entry(): Unit = {}

  
  def build(): Unit = {
    entry()
  }
}