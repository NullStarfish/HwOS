package HwOS.kernel

import chisel3._
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import HwOS.kernel.HwOSLanguage._

class OwnershipHierarchySpec extends AnyFlatSpec with Matchers {

  "HwOwnerShip" should "throw SegFault when a sibling process writes to another sibling's resource" in {
    val thrown = intercept[Exception] {
      chisel3.emitVerilog(new Module {
        val io = IO(new Bundle{})
        val kernel = new Kernel()

        class ServiceA(n: String, d: Boolean, p: Option[HwProcess], k: Kernel) extends HwProcess(n, d, p)(k) {
          val secureDataA = RegInit(0.U(32.W))
          this.own(secureDataA)
          
          val workerA = createThread("WorkerA")
          workerA.entry {
             workerA.Step("Idle") { workerA.exit() } // [修复]：增加 exit()
          }
        }

        class ServiceB(val siblingA: ServiceA, n: String, d: Boolean, p: Option[HwProcess], k: Kernel) extends HwProcess(n, d, p)(k) {
          val workerB = createThread("WorkerB")
          
          override def entry(): Unit = {
            workerB.entry {
              workerB.Step("HackAttempt") {
                siblingA.secureDataA <== "hDEADBEEF".U
                workerB.exit() // [修复]：增加 exit()
              }
            }
          }
        }

        class TopProcess(k: Kernel) extends HwProcess("Top", parent = None)(k) {
          val procA = spawn("InstA") { (n, d, p, kr) => new ServiceA(n, d, p, kr) }
          val procB = spawn("InstB") { (n, d, p, kr) => new ServiceB(procA, n, d, p, kr) }
        }

        val top = new TopProcess(kernel)
        top.build()
      })
    }
    
    val msg = Option(thrown.getMessage).getOrElse(thrown.toString)
    println(s"[Expected Data SegFault Caught] $msg")
    msg should include("SegFault")
  }

  it should "throw SegFault when a sibling attempts to kill another sibling's thread" in {
    val thrown = intercept[Exception] {
      chisel3.emitVerilog(new Module {
        val io = IO(new Bundle{})
        val kernel = new Kernel()

        class ServiceA(n: String, d: Boolean, p: Option[HwProcess], k: Kernel) extends HwProcess(n, d, p)(k) {
          val victimThread = createThread("VictimThread")
          victimThread.entry {
             victimThread.Step("Working") { victimThread.exit() } // [修复]：增加 exit()
          }
        }

        class ServiceB(val siblingA: ServiceA, n: String, d: Boolean, p: Option[HwProcess], k: Kernel) extends HwProcess(n, d, p)(k) {
          val assassinThread = createThread("AssassinThread")
          override def entry(): Unit = {
            assassinThread.entry {
              assassinThread.Step("Assassinate") {
                SysCall.kill(siblingA.victimThread)
                assassinThread.exit() // [修复]：增加 exit()
              }
            }
          }
        }

        class TopProcess(k: Kernel) extends HwProcess("Top", parent = None)(k) {
          val procA = spawn("InstA") { (n, d, p, kr) => new ServiceA(n, d, p, kr) }
          val procB = spawn("InstB") { (n, d, p, kr) => new ServiceB(procA, n, d, p, kr) }
        }

        val top = new TopProcess(kernel)
        top.build()
      })
    }
    
    val msg = Option(thrown.getMessage).getOrElse(thrown.toString)
    println(s"[Expected Lifecycle SegFault Caught] $msg")
    msg should include("SegFault")
  }

  it should "allow Parent process to safely manipulate Child resources due to spawn auto-granting" in {
    chisel3.emitVerilog(new Module {
      val io = IO(new Bundle{})
      val kernel = new Kernel()

      class ChildService(n: String, d: Boolean, p: Option[HwProcess], k: Kernel) extends HwProcess(n, d, p)(k) {
        val childReg = RegInit(0.U(32.W))
        this.own(childReg)
      }

      class TopProcess(k: Kernel) extends HwProcess("Top", parent = None)(k) {
        val childProc = spawn("InstChild") { (n, d, p, kr) => new ChildService(n, d, p, kr) }
        val masterThread = createThread("Master")
        
        childProc.grant(childProc.childReg, masterThread)

        override def entry(): Unit = {
          masterThread.entry {
            masterThread.Step("ConfigChild") {
              childProc.childReg <== 100.U
              masterThread.exit() // [修复]：增加 exit()
            }
          }
        }
      }

      val top = new TopProcess(kernel)
      top.build()
    })
    println("[Pass] Parent to Child access successful via spawn auto-grant.")
  }
}