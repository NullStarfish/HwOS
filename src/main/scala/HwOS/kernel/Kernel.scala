package HwOS.kernel

import chisel3._
import chisel3.util._
import scala.collection.mutable.{ArrayBuffer, HashMap}
import java.io._

class Kernel {
  val secure_mode :Boolean = true
  private var booted = false
  private var booting = false
  private val monitorEnabled =
    sys.env.get("HWOS_ENABLE_MONITOR").contains("1") || java.lang.Boolean.getBoolean("hwos.enableMonitor")

  private val threads = ArrayBuffer[HardwareThread]()
  private val threadNameMap = new HashMap[String, Int]()
  def registerThread(name: String, t: HardwareThread): Int = {
    if (threadNameMap.contains(name)) {
      throw new Exception(s"[Kernel] Duplicate thread name detected: $name")
    }
    
    val tid = threads.length
    threads += t
    threadNameMap(name) = tid
    
    println(s"[Kernel] Registered Thread: $name @ TID=$tid") 
    tid
  }
  def getTID(name: String): Option[Int] = threadNameMap.get(name)

  private val contexts = ArrayBuffer[HardwareThread]()
  def registerContext(thread: HardwareThread): Unit = {
    contexts += thread
  }




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

  def boot(): Unit = {
    if (booted || booting) return

    booting = true
    try {
      implicit val selfKernel: Kernel = this

      object SystemKernel extends HwProcess("Kernel", overrideDebug = Some(false)) {
        val reaper = spawn(new OSReaperProcess(contexts.toSeq, "OSReaper")(Kernel.this))
        override def entry(): Unit = {}
      }

      SystemKernel.build()
      booted = true
    } finally {
      booting = false
    }
  }

















  

  def dumpSymbolTable(filename: String): Unit = {
    boot()
    val file = new java.io.File(filename)
    val bw = new java.io.BufferedWriter(new java.io.FileWriter(file))
    
    for (t <- threads) {
      for (node <- t.threadNodes) {
        if (!node.isHijacked && node.allocatedPC != -1) {
          val pc = node.allocatedPC
          val stepName = node.name

          // 1. 格式化宏观时序栈 (Thread Level)
          val threadStackStr = if (node.threadCallStack.isEmpty) "None" else node.threadCallStack.mkString(",")
          val threadStackDepth = node.threadCallStack.length

          // 2. 格式化微观组合调用树 (Atomic Level)
          val atomicTreeStr = if (node.invokedCalls.isEmpty) "None" else node.invokedCalls.map(_.mkString(",")).mkString(";")
          val atomicCallCount = node.invokedCalls.length

          // 新格式: <ThreadName> <PC> <StepName> <T_Depth> <T_Stack> <A_Count> <A_Tree>
          bw.write(s"${t.name} $pc $stepName $threadStackDepth $threadStackStr $atomicCallCount $atomicTreeStr\n")
        }
      }
    }
    bw.close()
    println(s"[Kernel] Symbol table dumped to $filename")
  }
  
  def attachMonitor(): Unit = {
    boot()
    if (!monitorEnabled) {
      println("[Kernel] Monitor disabled. Set HWOS_ENABLE_MONITOR=1 or -Dhwos.enableMonitor=true to attach DPI monitor.")
      return
    }
    val nThreads = threads.length
    if (nThreads == 0) return

    // 将所有的 PC 强制对齐到 32 bit 供 C++ 读取
    val pc32Seq = threads.map { t =>
      val w = WireInit(0.U(32.W))
      w := t.pc
      w
    }.toSeq

    val pcVec     = VecInit(pc32Seq).asUInt
    val activeVec = VecInit(threads.map(_.active).toSeq).asUInt 
    val doneVec   = VecInit(threads.map(_.done).toSeq).asUInt      

    // 实例化 DPI BlackBox
    val monitor = Module(new KernelStateMonitorDPI(nThreads))
    
    monitor.io.clock   := Module.clock
    monitor.io.reset   := Module.reset
    monitor.io.pcs     := pcVec
    monitor.io.actives := activeVec
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
    val dones   = Input(UInt(nThreads.W))
  })

  // 生成 SystemVerilog 代码
  setInline("KernelStateMonitorDPI.sv",
    s"""
       |`ifndef SYNTHESIS  
       |module KernelStateMonitorDPI(
       |  input clock,
       |  input reset,
       |  input [${nThreads * 32 - 1}:0] pcs,
       |  input [${nThreads - 1}:0]      actives,
       |  input [${nThreads - 1}:0]      dones
       |);
       |
       |  // 声明 DPI-C 函数
       |  import "DPI-C" function void kernel_monitor_tick(
       |    input int n_threads,
       |    input bit [${nThreads * 32 - 1}:0] pcs,
       |    input bit [${nThreads - 1}:0]      actives,
       |    input bit [${nThreads - 1}:0]      dones
       |  );
       |
       |  always @(posedge clock) begin
       |    if (!reset) begin
       |      kernel_monitor_tick(
       |        ${nThreads},
       |        pcs,
       |        actives,
       |        dones
       |      );
       |    end
       |  end
       |
       |endmodule
       |`endif // SYNTHESIS
       |""".stripMargin)
}
