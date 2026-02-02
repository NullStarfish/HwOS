package HwOS.kernel

import chisel3._
import chisel3.util._
import scala.collection.mutable.{ArrayBuffer, HashMap}

import mycpu.common.KERNEL_DATA_WIDTH

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
  private val trackers = new HashMap[String, ResourceTracker]()
  private val lockTables = new HashMap[String, Vec[Bool]]()





  def mount(driver: PhysicalDriver): Unit = {
    drivers(driver.meta.name) = driver
    val tracker = new ResourceTracker(driver.meta)
    trackers(driver.meta.name) = tracker

    val depth = driver.meta.model match {
      case ScalarResource => 1
      case VectorResource(d) => d
    }
    

    lockTables(driver.meta.name) = RegInit(VecInit(Seq.fill(depth)(false.B)))


  }

  def hasDriver(name: String): Boolean = drivers.contains(name)

  


  def sys_intent(name: String, addr: UInt, op: UInt, id: UInt): Bool = {
    // Decode 阶段调用。
    // 如果返回 false，CPU 应该 Stall，或者重放。
    if (trackers.contains(name)) {
        trackers(name).sys_intent(addr, op, id)
    } else {
        false.B // 驱动不存在，Intent 失败
    }
  }

  def secure_done(name: String, addr: UInt, op: UInt, id: UInt): Unit = {
    // Driver 完成物理操作后调用
    if (trackers.contains(name)) {
        trackers(name).secure_done(addr, op, id)
    }
  }

  // 用户手动锁
  def sys_lock(name: String, addr: UInt, op: UInt, id: UInt): Unit = {
    if (trackers.contains(name)) trackers(name).sys_lock(addr, id)
  }

  // 用户手动解锁
  def sys_done(name: String, addr: UInt, op: UInt, id: UInt): Unit = {
    if (trackers.contains(name)) trackers(name).sys_unlock(addr, id)
  }

  
}