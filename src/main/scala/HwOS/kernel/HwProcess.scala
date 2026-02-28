package HwOS.kernel

import chisel3._
import chisel3.util._
import scala.collection.mutable.ArrayBuffer

//命名永远让上一级来命名

object ProcessBuilder {
  private val stack = scala.collection.mutable.Stack[HwProcess]()
  def push(p: HwProcess): Unit = stack.push(p)
  def pop(): Unit = stack.pop()
  def currentParent: Option[HwProcess] = stack.headOption
}





case class ProcEnv(kernel: Kernel, parent: Option[HwProcess], debugEnable: Boolean)
abstract class HwProcess(val localName: String, overrideDebug: Option[Boolean] = None )(implicit val kernel: Kernel) extends HwOwner {



  val parent: Option[HwProcess] = ProcessBuilder.currentParent

  val debugEnable: Boolean = overrideDebug.getOrElse {
    parent.map(_.debugEnable).getOrElse(true)
  }

  // 2. 完美的递归名称生成
  val name: String = parent match {
    case Some(p) => if (p.name.isEmpty) localName else s"${p.name}/$localName"
    case None => localName
  }


  
  kernel.registerProcess(name, this)


  



  
  val threads = ArrayBuffer[HardwareThread]()
  val logics  = ArrayBuffer[HardwareLogic]()
  val children = ArrayBuffer[HwProcess]()


  def createThread(name: String = "Main", policy: ThreadPolicy = ThreadPolicy.Auto): HardwareThread = {
    val t = new HardwareThread(s"${this.name}/${name}_thread", this, debugEnable, policy)
    kernel.registerThread(s"${this.name}/${name}_thread", t)
    kernel.registerContext(t)
    threads += t
    t
  }
  
  protected def createLogic(name: String = "Daemon"): HardwareLogic = {
    val l = new HardwareLogic(s"${this.name}/${name}_logic", this, debugEnable)
    logics += l
    l
  }


  def spawn[T <: HwProcess](child: => T): T = {

    ProcessBuilder.push(this)


    val c = child

    ProcessBuilder.pop()
    
    this.children += c
    c.build()
    
    // 自动向上兼容的权限二次分发
    c.getAllOwnedSignals().foreach { sig =>
      c.grant(sig, this)
    }

    c.threads.foreach { t =>
      if (t.runtime.supportsLifecycleGrant) {
        t.grantLifecycle(t, this)
      }
    }

    c
  }
  

  def entry(): Unit = {}

  
  def build(): Unit = {
    entry()
    if (parent.isEmpty) {
      kernel.boot()
    }
  }
}
