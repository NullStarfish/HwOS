package HwOS.kernel.process

import chisel3._
import chisel3.util._
import scala.collection.mutable.ArrayBuffer
import HwOS.kernel.context.HwContextEntity
import HwOS.kernel.system.{Kernel, OSReaperManaged, OSReaperManagedLogic}
import HwOS.kernel.thread._

//命名永远让上一级来命名

object ProcessBuilder {
  private val stack = new ThreadLocal[scala.collection.mutable.Stack[HwProcess]] {
    override def initialValue(): scala.collection.mutable.Stack[HwProcess] =
      scala.collection.mutable.Stack.empty[HwProcess]
  }

  def push(p: HwProcess): Unit = stack.get().push(p)
  def pop(): Unit = stack.get().pop()
  def currentParent: Option[HwProcess] = stack.get().headOption
}





case class ProcEnv(kernel: Kernel, parent: Option[HwProcess], debugEnable: Boolean)
abstract class HwProcess(val localName: String, overrideDebug: Option[Boolean] = None )(implicit val kernel: Kernel) extends HwContextEntity {
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


  def createThread(
      name: String = "Main",
  ): HardwareThread = {
    val threadName = s"${this.name}/${name}_thread"
    val t = new KernelStepHardwareThread(threadName, this, debugEnable)
    kernel.registerThread(threadName, t)
    threads += t
    t
  }

  def install(threadDef: ThreadDef, name: String = "Main"): HardwareThread =
    threadDef.install(this, name)
  
  protected def createLogic(name: String = "Daemon"): HardwareLogic = {
    val l = new HardwareLogic(s"${this.name}/${name}_logic", this, debugEnable)
    logics += l
    l
  }

  private[HwOS] def createReaperManagedLogic(name: String = "Reaper"): OSReaperManagedLogic = {
    val l = new OSReaperManagedLogic(s"${this.name}/${name}_logic", this, debugEnable)
    logics += l
    l
  }


  def spawn[T <: HwProcess](child: => T): T = {

    ProcessBuilder.push(this)


    val c = child

    ProcessBuilder.pop()
    
    this.children += c
    c.build()
    
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
