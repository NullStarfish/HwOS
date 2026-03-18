package HwOS.kernel

import HwOS.kernel.HwOSLanguage._
import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class ThreadResetProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  val worker = createThread("Worker")
  val hits = this.own(RegInit(0.U(8.W)))
  val resetIssued = this.own(RegInit(false.B))
  val restarted = this.own(RegInit(false.B))

  override def entry(): Unit = {
    this.grant(hits, worker)

    worker.entry {
      worker.Step("Tick") {
        hits := hits + 1.U
      }
      worker.Step("Park") {
        worker.waitCondition(false.B)
      }
      SysCall.Call(SysCall.Return())
    }
  }
}

class ThreadResetModule extends Module {
  val io = IO(new Bundle {
    val hits = Output(UInt(8.W))
    val workerActive = Output(Bool())
    val workerDone = Output(Bool())
    val resetIssued = Output(Bool())
    val restarted = Output(Bool())
  })

  io.hits := DontCare
  io.workerActive := DontCare
  io.workerDone := DontCare
  io.resetIssued := DontCare
  io.restarted := DontCare

  implicit val kernel: Kernel = new Kernel()

  object Init extends HwProcess("Init") {
    this.own(io.hits)
    this.own(io.workerActive)
    this.own(io.workerDone)
    this.own(io.resetIssued)
    this.own(io.restarted)

    val proc = spawn(new ThreadResetProcess("ResetProc"))
    val daemon = createLogic("Daemon")

    override def entry(): Unit = {
      this.grant(io.hits, daemon, GrantAbi.LevelDrivenWire)
      this.grant(io.workerActive, daemon, GrantAbi.LevelDrivenWire)
      this.grant(io.workerDone, daemon, GrantAbi.LevelDrivenWire)
      this.grant(io.resetIssued, daemon, GrantAbi.LevelDrivenWire)
      this.grant(io.restarted, daemon, GrantAbi.LevelDrivenWire)
      this.grant(proc.resetIssued, daemon)
      this.grant(proc.restarted, daemon)
      this.grantLifecycle(proc.worker, daemon)

      daemon.run {
        when(!proc.worker.active && !proc.worker.done && !proc.resetIssued && !proc.restarted) {
          SysCall.Call(SysCall.start(proc.worker))
        }

        when(proc.hits === 1.U && !proc.resetIssued) {
          proc.worker.reset()
          proc.resetIssued := true.B
        }

        when(proc.resetIssued && !proc.worker.active && !proc.worker.done && !proc.restarted) {
          SysCall.Call(SysCall.start(proc.worker))
          proc.restarted := true.B
        }

        io.hits := proc.hits
        io.workerActive := proc.worker.active
        io.workerDone := proc.worker.done
        io.resetIssued := proc.resetIssued
        io.restarted := proc.restarted
      }
    }
  }

  Init.build()
}

class ThreadResetSpec extends AnyFlatSpec {
  "Thread reset" should "reset the runtime to Idle and allow a clean restart without reclaiming ordinary state" in {
    simulate(new ThreadResetModule) { c =>
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)

      var settled = false
      var guard = 0
      while (!settled && guard < 20) {
        c.clock.step()
        settled =
          c.io.resetIssued.peek().litValue == 1 &&
            c.io.restarted.peek().litValue == 1 &&
            c.io.hits.peek().litValue >= 2 &&
            c.io.workerActive.peek().litValue == 1 &&
            c.io.workerDone.peek().litValue == 0
        guard += 1
      }

      assert(settled, "thread.reset() did not reset the runtime and allow a clean restart")
      assert(c.io.hits.peek().litValue >= 2, "thread.reset() unexpectedly reclaimed ordinary state")
      c.io.workerDone.expect(false.B)
    }
  }
}
