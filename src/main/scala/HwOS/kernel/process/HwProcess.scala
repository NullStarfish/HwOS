package HwOS.kernel.process

import chisel3._
import chisel3.util._
import scala.collection.mutable.ArrayBuffer
import scala.reflect.ClassTag
import HwOS.kernel.context.HwContextEntity
import HwOS.kernel.system.Kernel
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

  def importService[T <: HwProcess: ClassTag](serviceName: String): T = {
    val child = children.find(_.localName == serviceName).getOrElse {
      throw new Exception(
        s"[HwOS] Process '$name' cannot import service '$serviceName' because no direct child service with that local name exists.",
      )
    }

    val expectedClass = implicitly[ClassTag[T]].runtimeClass
    if (!expectedClass.isAssignableFrom(child.getClass)) {
      throw new Exception(
        s"[HwOS] Service '$serviceName' under '$name' has type '${child.getClass.getSimpleName}', incompatible with requested '${expectedClass.getSimpleName}'.",
      )
    }

    child.asInstanceOf[T]
  }


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

  private[kernel] def createKernelLogic(name: String = "Daemon"): HardwareLogic =
    createLogic(name)
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
