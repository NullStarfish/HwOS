# HwOS: 面向可组合与可观测硬件设计的线程级 RTL 抽象框架

[![Scala](https://img.shields.io/badge/Language-Scala%2FChisel-red)](https://www.chisel-lang.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

<p align="center">
  <a href="README.md">🇺🇸 English</a> | <strong>🇨🇳 中文</strong>
</p>


**HwOS** 是一个基于 Chisel 的硬件构建框架，旨在将操作系统的抽象引入 RTL 设计领域 。其核心理念是：“**硬件即操作系统，万物皆线程**” (Hardware is an Operating System, Everything is a Thread) 。

通过定义 **线程级 RTL (TL-RTL)**，HwOS 将原本破碎的有限状态机（FSM）和底层的握手协议封装为具有独立生命周期的 `HardwareThread` 对象 。配合 **HwOSgdb** 调试器，开发者可以在源码语义层面上对硬件状态进行实时监控和调试 。

---

## ✨ 核心特性 (Features)

* **线程级 RTL (TL-RTL) 抽象**
* 采用命令式风格描述时序行为，使用 `Step`、`Wait`、`Fork` 等原语替代传统的 FSM 状态跳转 。


* 支持隐式上下文捕获 (Context Capture)，子线程可直接访问父线程变量，实现零连线开销的通信 。




* **驱动即控制流代理 (Driver as Control Flow Proxy)**
* 物理资源（如寄存器堆、总线）被封装在 `Driver` 中。
* 
**指令注入机制**：Driver 通过向调用线程注入阻塞逻辑（如 `waitCondition`）来自动处理资源仲裁和流水线停顿，无需手动编写 Ready-Valid 握手信号 。




* **基于服务的时间缝合流水线 (Service-Based Pipeline)**
* 摒弃“指令在流水级间流动”的传统视图，转为“指令作为活跃线程主动寻求服务” 。


* 支持乱序执行和动态依赖解析，无需构建集中的发射队列 。




* **分形架构 (Fractal Architecture)**
* 采用递归的 `Kernel` - `Process` - `Thread` 结构，支持模块化复用和系统级层级管理 。




* **原生可观测性与 HwOSgdb**
* 利用 SystemVerilog DPI-C 接口将硬件仿真状态映射回源码符号 。


* 提供基于 `ncurses` 的终端调试器，支持断点 (`break Thread.pc==2`)、单步执行、时光倒流 (Time Travel) 和状态可视化 。





---

## 📂 项目结构 (Project Structure)

```text
HwOS/
├── src/main/scala/HwOS/kernel/    # 核心内核实现
│   ├── Kernel.scala               # 全局管理器，ID分配与DPI监控接口 
│   ├── HwProcess.scala            # 逻辑容器，支持递归 spawn 
│   ├── HardwareAgent.scala        # HardwareThread (线程) 与 HardwareLogic 定义 
│   ├── ContextScope.scala         # 用于元编程的上下文栈管理 (ThreadCtx, AtomicCtx) 
│   ├── PhysicalDriver.scala       # 驱动基类与 DriverMeta
│   └── drivers/                   # 标准驱动库
│       ├── ScoreboardRegfileDriver.scala  # 支持记分板机制的寄存器堆驱动 
│       ├── PipelinedScoreboardRegfileDriver.scala
│       └── ...
├── src/test/scala/HwOS/           # 测试用例与示例
│   ├── example/                   # 综合示例
│   │   ├── SimpleTopforDPIDebug.scala # 用于 DPI 调试的顶层封装
│   │   └── PipelineDebugGen.scala
│   └── kernel/                    # 单元测试 (Fork, Abort, MultiCore 等)
├── HwOSgdb.cpp                    # C++ 编写的终端调试器源码 
├── compile_HwOSgdb.sh             # 调试器编译脚本
└── build.sbt                      # Scala/Chisel 构建配置

```

---

## 🛠️ 依赖工具 (Prerequisites)

在开始之前，请确保你的环境已安装以下工具：

1. **Scala & sbt**: 用于构建 Chisel 项目。
* JDK 17 或更高版本。


2. 
**Verilator**: 开源 Verilog 仿真器，用于生成 DPI 接口和进行高速仿真 。


3. **C++ 编译器 (g++)**: 用于编译 HwOSgdb。
4. **ncurses 库**: HwOSgdb 的 UI 依赖。
* Ubuntu/Debian: `sudo apt-get install libncurses5-dev libncursesw5-dev`



---

## 🚀 使用说明 (Usage)

### 1. 定义硬件线程

在 HwOS 中，你不需要编写 `always` 块或状态机 `case`。只需在 `entry` 块中定义时序逻辑：

```scala
// 示例：定义一个简单的累加器线程
val t = createThread("Accumulator")
t.entry {
  // 定义步骤 Step，自动映射为状态机状态
  t.Step("Load") {
    accReg := 10.U
  }
  t.Step("Compute") {
    accReg := accReg * 2.U
  }
  // 使用 Driver 进行原子操作，自动处理握手
  t.Step("Store") {
    driver.writeAtomic(addr, accReg) {
      t.exit() // 任务完成，退出线程
    }
  }
}

```

*[参考代码: HardwareAgent.scala, ScoreboardRegfileDriver.scala]* 

### 2. 编译与运行调试器 (HwOSgdb)

HwOS 提供了一个强大的终端调试器，通过 DPI 连接到仿真器。

**步骤 1: 编译调试器前端**

```bash
./compile_HwOSgdb.sh

```

这会生成 `HwOSgdb` 可执行文件。该程序依赖 `verilated.o` 等对象文件，通常需要先运行一次 Chisel 测试生成 Verilog 和 C++ 仿真模型。

**步骤 2: 生成带 DPI 接口的仿真核**
运行 `SimpleTopforDPIDebug` 或其他测试生成包含 `KernelStateMonitorDPI` 的 SystemVerilog 代码。

```bash
sbt "testOnly HwOS.example.SimpleTopforDPIDebug"

```

内核会自动生成符号表文件 `hwos.symbols` 。

**步骤 3: 启动调试**
运行编译好的仿真程序（通常由 Verilator 生成在 `test_run_dir` 或类似目录），配合 `HwOSgdb`：

```bash
./HwOSgdb

```

**调试器快捷键**:

* `[Space]`: 单步执行一个时钟周期。
* `[r]`: 连续运行 (Run)。
* 
`[b]`: 设置断点 (例如 `Thread_0.pc == 3`) 。


* `[s]`: 运行直到当前关注的线程发生状态变化 (Step Thread)。
* `[TAB]`: 在侧边栏（线程列表）和主视图（波形/状态）之间切换焦点。

### 3. 使用 Driver 管理资源

不要直接读写寄存器堆的信号，而是使用 Driver 提供的 API：

```scala
// 阻塞式读取：如果资源冲突，当前线程会自动 Stall
val data = regfileDriver.read(addr)

// 原子写操作：只有当获得资源所有权时才执行回调
regfileDriver.writeAtomic(addr, data) {
  printf("Write completed!\n")
}

```

*[参考代码: ScoreboardRegfileDriver.scala]* 

---

## 📝 引用 (Citation)

如果您在研究中使用了 HwOS，请引用以下论文：

**HwOS: A Thread-Level RTL Abstraction for Composable and Observable Hardware Design** 

> Kaixin Chen, Department of Electrical Engineering, Zhejiang University.

---

*本项目为个人研究项目，基于 Chisel 3 构建。*