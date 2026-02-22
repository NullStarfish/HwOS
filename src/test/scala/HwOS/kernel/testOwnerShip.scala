package HwOS.kernel

import chisel3._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import HwOS.kernel.HwOSLanguage._ // 引入 <== 操作符

class OwnershipSpec extends AnyFlatSpec with Matchers {

  "HwOwnerShip" should "throw SegFault when an unauthorized thread writes to a protected signal" in {
    // 使用 intercept 捕获编译期抛出的异常
    val thrown = intercept[Exception] {
      chisel3.emitVerilog(new Module {
        val io = IO(new Bundle{})
        val kernel = new Kernel()

        class TestProc(k: Kernel) extends HwProcess("Proc", debugEnable = false, parent = None)(k) {
          val ownerThread = createThread("OwnerThread")
          val hackerThread = createThread("HackerThread")
          
          val secureReg = RegInit(0.U(32.W))
          
          // 1. OwnerThread 声明对 secureReg 的所有权
          ownerThread.own(secureReg)

          override def entry(): Unit = {
            ownerThread.entry {
              // 合法写入：因为它是 Owner
              secureReg <== 1.U 
            }

            hackerThread.entry {
              // 非法写入：HackerThread 既不是 Owner，也没被 Grant
              // 这行代码在执行时会抛出异常
              secureReg <== 2.U 
            }
          }
        }
        val proc = new TestProc(kernel)
        proc.build() // 触发 entry 逻辑的展开
      })
    }
    
    // 验证异常信息是否符合预期
    println(s"[Expected Error Caught] ${thrown.getMessage}")
    thrown.getMessage should include("SegFault")
    thrown.getMessage should include("cannot write to resource owned by")
  }

  it should "allow a thread to write to a signal if explicitly granted" in {
    // 这个测试应该能够顺利生成 Verilog，不抛出任何异常
    chisel3.emitVerilog(new Module {
      val io = IO(new Bundle{})
      val kernel = new Kernel()

      class TestProc(k: Kernel) extends HwProcess("Proc", debugEnable = false, parent = None)(k) {
        val ownerThread = createThread("OwnerThread")
        val workerThread = createThread("WorkerThread")
        
        val sharedReg = RegInit(0.U(32.W))
        
        ownerThread.own(sharedReg)
        // 核心：Owner 显式授权给 Worker
        ownerThread.grant(sharedReg, workerThread)

        override def entry(): Unit = {
          workerThread.entry {
            // 合法写入：因为获得了 grant
            sharedReg <== 10.U
          }
        }
      }
      val proc = new TestProc(kernel)
      proc.build()
    })
  }

  it should "throw SegFault when a thread attempts to kill a target without lifecycle permission" in {
    val thrown = intercept[Exception] {
      chisel3.emitVerilog(new Module {
        val io = IO(new Bundle{})
        val kernel = new Kernel()

        class TestProc(k: Kernel) extends HwProcess("Proc", debugEnable = false, parent = None)(k) {
          val victimThread = createThread("VictimThread")
          val rogueThread = createThread("RogueThread")

          override def entry(): Unit = {
            victimThread.entry {
               victimThread.Step("Working") { 
                 // 修复：添加合法的退出路径
                 victimThread.exit() 
               }
            }

            rogueThread.entry {
               rogueThread.Step("Attack") {
                 // 非法控制：试图跨越权限界限杀死 VictimThread
                 // 这行代码执行时就会抛出带有 [HwOS SegFault] 的 Exception
                 SysCall.kill(victimThread)
                 
                 // 修复：为了通过编译期的 exit 检查，补充出口
                 rogueThread.exit()
               }
            }
          }
        }
        val proc = new TestProc(kernel)
        proc.build()
      })
    }

    println(s"[Expected Error Caught] ${thrown.getMessage}")
    thrown.getMessage should not be null
    thrown.getMessage should include("SegFault")
    thrown.getMessage should include("lacks lifecycle permission to kill")
  }
}