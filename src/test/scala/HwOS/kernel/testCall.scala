package HwOS.kernel

import chisel3._
import chisel3.simulator.EphemeralSimulator._
import org.scalatest.flatspec.AnyFlatSpec

class FunctionReturnTestModule extends Module {
  val io = IO(new Bundle {
    val start  = Input(Bool())
    val result = Output(UInt(32.W))
    val done   = Output(Bool())
  })

  val kernel = new Kernel()

  class TestProcess(k: Kernel) extends HwProcess("Proc", debugEnable = true, parent = None)(k) {
    
    val main = createThread("Main")

    // --- 定义一个带返回值的 HwFunction ---
    // 输入：n (计算第几项)
    // 输出：HwFunction[UInt] (返回一个 32位 UInt，指向结果寄存器)
    def Fibonacci(n: Int): HwFunction[UInt] = HwFunction.thread(s"Fib$n") { t =>
      
      // 在函数内部定义局部硬件资源
      // 这些资源只会在 Call 的时候被实例化


      val a = RegInit(0.U(32.W))
      val b = RegInit(1.U(32.W))
      val count = RegInit(0.U(32.W))
      val result = RegInit(0.U(32.W))

      t.Step("Init") {
        a := 0.U
        b := 1.U
        count := 0.U
      }

      // 循环计算 (展开 n 次 Step，或者用计数器循环)
      // 这里演示用 Step 链式展开 (Unrolled Loop)
      for (i <- 0 until n) {
        t.Step(s"Calc_$i") {
          val next = a + b
          a := b
          b := next
        }
      }

      t.Step("Finish") {
        result := b
      }

      // *** 这里的最后一行就是返回值 ***
      // 我们返回 result 寄存器，以便调用者可以读取它
      result 
    }

    override def entry(): Unit = {
      main.entry {
        
        // --- 调用函数并捕获返回值 ---
        // fibRes 就是 Fibonacci 函数内部定义的 `result` 寄存器
        // 这一步是 "编译期" 链接，直接拿到了那个寄存器的引用
        val fibRes: UInt = SysCall.Call(Fibonacci(5))

        // 在后续的 Step 中使用返回值
        main.Step("ReadResult") {
          // 将函数内部的寄存器连接到 IO
          // 此时 Fibonacci 已经跑完了 (Step 顺序保证)
          // fibRes 保持着最终值
          // 这里的赋值会生成组合逻辑连线
        }
        
        // 为了让外面能观测，我们在这里做一个持久连接
        // 注意：Chisel 中可以在任何地方连线。
        // 这里利用 Scala 的闭包特性，fibRes 在 entry 作用域内可见
        io.result := fibRes 
        
        main.Step("Done") {
          main.exit()
        }
      }
    }
    
    when(io.start) { main.start() }
  }

  val proc = new TestProcess(kernel)
  proc.build()
  io.done := proc.main.done
}

class FunctionReturnTest extends AnyFlatSpec {
  "HwFunction" should "return hardware objects (Reg/Wire)" in {
    simulate(new FunctionReturnTestModule) { c =>
      println("\n=== HwFunction Return Value Test ===")
      
      c.reset.poke(true.B)
      c.clock.step()
      c.reset.poke(false.B)
      c.io.start.poke(true.B)
      c.clock.step()
      c.io.start.poke(false.B)

      var cycles = 0
      while (c.io.done.peek().litValue == 0 && cycles < 20) {
        c.clock.step()
        cycles += 1
      }

      // Fib(5): 0, 1, 1, 2, 3, [5] (Sequence: 0,1,1,2,3,5,8...)
      // Loop logic: 
      // i=0: next=1, a=1, b=1
      // i=1: next=2, a=1, b=2
      // i=2: next=3, a=2, b=3
      // i=3: next=5, a=3, b=5
      // i=4: next=8, a=5, b=8 
      // result := b (8)
      
      val res = c.io.result.peek().litValue
      println(s"Fib(5) Result: $res")
      
      // 根据上面的逻辑，Fib(5) 实际上跑了 5 次迭代，应该是 8
      c.io.result.expect(8.U) 
      c.io.done.expect(true.B)
      
      println("=== Test Passed ===")
    }
  }
}