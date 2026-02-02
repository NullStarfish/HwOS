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
    val tracker = new ResourceTracker(driver.meta, systemProcess)
    tracker.run{} //现在没有逻辑
    trackers(driver.meta.name) = tracker

    val depth = driver.meta.model match {
      case ScalarResource => 1
      case VectorResource(d) => d
    }
    

    lockTables(driver.meta.name) = RegInit(VecInit(Seq.fill(depth)(false.B)))


  }

  def hasDriver(name: String): Boolean = drivers.contains(name)

  


  def sys_intent(name: String, addr: UInt, op: UInt, id: UInt): Bool = {
    if (!this.hasDriver(name)) {
      throw new Exception(s"[Kernel] doesn't have dirver named $name")
    }

    val tracker = trackers(name)
    tracker.send_intent(addr, op, id)
  }


  def sys_inquiry(name: String, addr: UInt, op: UInt, id: UInt): Bool = {
    if (!this.hasDriver(name)) {
      throw new Exception(s"[Kernel] doesn't have dirver named $name")
    }

    val tracker = trackers(name)
    tracker.access_check(addr, op, id)
  }


  def secure_check(name: String, addr: UInt, op: UInt, id: UInt): Unit = {
    if (secure_mode) {
      val canAccess = sys_inquiry(name, addr, op, id)
      when(!canAccess) {
        printf(p"[Kernel Violation] Illegal Access! Res:$name Addr:$addr Op:$op ID:$id\n")
        assert(false.B)
      }
    }
  }

  //单纯返回句柄
  def sys_open(name: String, addr: UInt, op: UInt, id: UInt): PhysicalDriver = {
    if (!this.hasDriver(name)) {
      throw new Exception(s"[Kernel] doesn't have dirver named $name")
    }
    drivers(name)
  }

  def sys_done(name: String, addr: UInt, op: UInt, id: UInt): Unit = {
    if (!this.hasDriver(name)) {
      throw new Exception(s"[Kernel] doesn't have dirver named $name")
    }
    val tracker = trackers(name)
    val locks = lockTables(name)
    locks(addr) := false.B

    tracker.commit_done(addr, op, id)
    
  }

  def sys_lock(name: String, addr: UInt, op: UInt, id: UInt): Unit = {
    // 简单地把对应地址的锁置为 1
    // 注意：这里没有复杂的握手，因为 lock 通常紧跟 intent 或在 exec 期间调用
    if (lockTables.contains(name)) {
       lockTables(name)(addr) := true.B
    } else {
      throw new Exception(s"[Kernel] doesn't have dirver named $name")
    }
  }

  def secure_done(name: String, addr: UInt, op: UInt, id: UInt): Unit = {
    val tracker = trackers(name)
    val locks   = lockTables(name)

    // [逻辑拦截]
    // 只有当锁表对应位为 false 时，才允许 Driver 触发 Tracker 的 Commit
    val isLocked = locks(addr)
    
    when (!isLocked) {
      // 正常流程：Driver 说做完了，且没锁，Tracker 释放资源
      val isEarly = tracker.commit_done(addr, op, id)
      
      // Driver 触发的不可能是 Early Commit (保持之前的断言)
      if (secure_mode) {
        when(isEarly) {
            printf(p"[Kernel Violation] Driver triggered Early Commit! Res:$name ID:$id\n")
            assert(false.B)
        }
      }
    } .otherwise {
      // [被锁定]
      // Driver 虽然调了 secure_done，但 Kernel 假装没听见。
      // ResourceTracker 的 Head 指针不会移动，资源依然显示为 Busy。
      // 这样后续指令如果尝试 Access，依然会阻塞。
    }

    // 安全检查 (保持不变，检查 Driver 是否有权操作)
    secure_check(name, addr, op, id)
  }



  
}