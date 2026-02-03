package HwOS.kernel

import chisel3._
import chisel3.util._
import scala.collection.mutable.{ArrayBuffer, HashMap}


class Kernel {
  val secure_mode :Boolean = true

  private val threads = ArrayBuffer[HardwareThread]()
  private val threadNameMap = new HashMap[String, Int]()
  def registerThread(name: String, t: HardwareThread): Int = {
    if (threadNameMap.contains(name)) {
      throw new Exception(s"[Kernel] Duplicate thread name detected: $name")
    }
    
    val tid = threads.length
    threads += t
    threadNameMap(name) = tid
    
    println(s"[Kernel] Registered Thread: $name @ TID=$tid") // 编译时打印，方便看
    tid
  }
  def getTID(name: String): Option[Int] = threadNameMap.get(name)




  private val processes = ArrayBuffer[HwProcess]()
  private val processNameMap = new HashMap[String, Int]()

  def registerProcess(name: String, p: HwProcess): Int = {
    if (processNameMap.contains(name)) {
      throw new Exception(s"[Kernel] Duplicate process name detected: $name")
    }

    val pid = processes.length
    processes += p
    processNameMap(name) = pid

    println(s"[Kernel] Registered Process: $name @ PID=$pid")
    pid
  }


  def getPID(name: String): Option[Int] = processNameMap.get(name) 







  val systemProcess = new HwProcess("KernelSystem", debugEnable=false, parent=None)(this) {
    override def entry(): Unit = { /* 系统进程不需要跑普通指令，它是逻辑容器 */ }
  }

  private val drivers = new HashMap[String, PhysicalDriver]()





  def mount(driver: PhysicalDriver): Unit = {
    drivers(driver.meta.name) = driver


    val depth = driver.meta.model match {
      case ScalarResource => 1
      case VectorResource(d) => d
    }
    


  }

  def hasDriver(name: String): Boolean = drivers.contains(name)

  


  
}