package HwOS.kernel

import chisel3._
import chisel3.util._
import scala.collection.mutable.{ArrayBuffer, HashMap}
import java.io._

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

  private var driverIdAlloc = 1 //0 for users
  private val drivers = new HashMap[String, PhysicalDriver]()





  def mount(driver: PhysicalDriver): Unit = {
    drivers(driver.meta.name) = driver


    driver._driverId = driverIdAlloc
    println(s"[Kernel] Mounted Driver: ${driver.meta.name} @ ID=${driver._driverId}")
    driverIdAlloc += 1
    


  }

  def hasDriver(name: String): Boolean = drivers.contains(name)

  
  // ==============================================================================
  // 新增：全局状态监控接口 (DPI)
  // ==============================================================================


  def dumpSymbolTable(filename: String): Unit = {
    val file = new File(filename)
    val bw = new BufferedWriter(new FileWriter(file))
    
    // 遍历所有注册的线程
    for (t <- threads) {
      // zip stepNames 和 stepOwners
      for (((stepName, ownerId), pc) <- t.stepNames.zip(t.stepOwners).zipWithIndex) {
        // 格式: <ThreadName> <PC> <StepName> <OwnerID>
        bw.write(s"${t.name} $pc $stepName $ownerId\n")
      }
    }
    bw.close()
    println(s"[Kernel] Symbol table dumped to $filename")
  }
  
 def attachMonitor(): Unit = {
    val nThreads = threads.length
    if (nThreads == 0) return

    // [关键修复] 显式将每个 PC 扩展/填充为 32 位宽，确保与 DPI 接口对齐
    val pc32Seq = threads.map { t =>
      // 如果 PC 位宽小于 32，则补零；如果大于 32（理论上不应发生），则截断
      // pad(32) 确保最小宽度，run-time width adjustment
      // 但最稳妥的方式是 Wire 初始化指定宽度
      val w = Wire(UInt(32.W))
      w := t.pc
      w
    }.toSeq

    val pcVec     = VecInit(pc32Seq).asUInt                // [32*N] packed
    val activeVec = VecInit(threads.map(_.isRunning).toSeq).asUInt 
    val startVec  = VecInit(threads.map(_.startWire).toSeq).asUInt 
    val abortVec  = VecInit(threads.map(_.abortWire).toSeq).asUInt 
    val doneVec   = VecInit(threads.map(_.done).toSeq).asUInt      

    // 实例化 DPI BlackBox
    val monitor = Module(new KernelStateMonitorDPI(nThreads))
    
    monitor.io.clock   := Module.clock
    monitor.io.reset   := Module.reset
    monitor.io.pcs     := pcVec
    monitor.io.actives := activeVec
    monitor.io.starts  := startVec
    monitor.io.aborts  := abortVec
    monitor.io.dones   := doneVec
    
    println(s"[Kernel] Attached DPI Monitor for $nThreads threads.")
  }
} 


class KernelStateMonitorDPI(val nThreads: Int) extends BlackBox with HasBlackBoxInline {
  val io = IO(new Bundle {
    val clock   = Input(Clock())
    val reset   = Input(Bool())
    val pcs     = Input(UInt((32 * nThreads).W))
    val actives = Input(UInt(nThreads.W))
    val starts  = Input(UInt(nThreads.W))
    val aborts  = Input(UInt(nThreads.W))
    val dones   = Input(UInt(nThreads.W))
  })

  // 生成 SystemVerilog 代码
  setInline("KernelStateMonitorDPI.sv",
    s"""
       |`ifndef SYNTHESIS  // <--- 添加这一行
       |module KernelStateMonitorDPI(
       |  input clock,
       |  input reset,
       |  input [${nThreads * 32 - 1}:0] pcs,
       |  input [${nThreads - 1}:0]      actives,
       |  input [${nThreads - 1}:0]      starts,
       |  input [${nThreads - 1}:0]      aborts,
       |  input [${nThreads - 1}:0]      dones
       |);
       |
       |  // 声明 DPI-C 函数
       |  // 注意：在 C++ 侧，bit 向量通常映射为 svBitVecVal* 数组
       |  import "DPI-C" function void kernel_monitor_tick(
       |    input int n_threads,
       |    input bit [${nThreads * 32 - 1}:0] pcs,
       |    input bit [${nThreads - 1}:0]      actives,
       |    input bit [${nThreads - 1}:0]      starts,
       |    input bit [${nThreads - 1}:0]      aborts,
       |    input bit [${nThreads - 1}:0]      dones
       |  );
       |
       |  always @(posedge clock) begin
       |    if (!reset) begin
       |      kernel_monitor_tick(
       |        ${nThreads},
       |        pcs,
       |        actives,
       |        starts,
       |        aborts,
       |        dones
       |      );
       |    end
       |  end
       |
       |endmodule
       |`endif // SYNTHESIS
       |""".stripMargin)
}