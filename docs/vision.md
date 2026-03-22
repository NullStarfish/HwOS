# HwOS 愿景与定义

这份文档面向研究者和接手开发的工程师，目标是用**论文级别的语言**回答一个更根本的问题：

**HwOS 到底在做什么？**

它不是实现文档，也不是 API 手册。  
如果你想看当前代码怎么组织，请读 [architecture.md](/Users/nullstarfish/HwOS_personal/docs/architecture.md)。  
如果你想看具体接口怎么用，请读 [docs/api/README.md](/Users/nullstarfish/HwOS_personal/docs/api/README.md)。

## 摘要

HwOS 是一个面向 RTL 的系统化抽象框架。  
它当前最核心的主张不是“让硬件更像软件”，而是：

**把控制流本身提升为硬件设计的一等对象，并把它组织成可移植、可组合、可观测的代码段。**

在传统 RTL 中，复杂控制往往被编码为：

- 局部 FSM
- 分散的 enable/valid/ready 协议
- 紧耦合的对象引用与层级穿透
- 散落在模块内部的 kill/abort/reclaim 逻辑

这些技术都能工作，但它们通常缺少一个统一的语义中心。  
HwOS 当前的工作，是把这些分散机制收敛成一条更清楚的主线：

- `thread` 作为统一执行宿主
- `Step` / `StepRef` 作为编译期控制对象
- `HwInline` 作为正式控制代码段
- `Process` 作为 service / environment / physical component
- `export / declare` 作为 lightweight symbolic v0 的跨边界接口
- `KernelAddressSpace` 作为 state/code/binding/exported/dependency 元数据平面
- `OSReaper` 作为可选的系统级神力，而不是基础模型的一部分

因此，HwOS 不是一个“更漂亮的 Chisel DSL”，而是一个关于**控制流本位硬件描述**的提案。

## 1. 问题陈述

HwOS 面对的问题不是单一模块的 RTL 书写，而是**复杂控制系统如何被组织**。

当一个硬件系统开始同时拥有这些特征时，传统 RTL 会迅速暴露出结构性压力：

1. 控制流跨越多个状态机和模块边界。
2. 共享资源围绕某些物理对象集中，但控制逻辑却散落在不同模块中。
3. 阻塞、等待、取消、重启、回收等生命周期语义很难统一表达。
4. 代码的组织方式已经很现代，但空间拓扑和资源拓扑仍主要靠连线和对象引用表达。
5. 调试时只能看到波形，却很难恢复“哪段控制代码在执行、为什么被阻塞、它依赖了什么环境”。

这意味着，复杂硬件设计中真正困难的部分，并不只是“写一个状态机”，而是：

- 如何把多个控制流、多个共享对象、多个资源边界组织成**可组合的系统**

HwOS 当前的研究问题可以表述为：

> 是否可以为 RTL 提供一种控制流本位的系统级语义，使控制代码、执行宿主、共享环境、跨边界依赖与系统级收尾能够在同一模型下被描述？

## 2. 核心命题

HwOS 当前的核心命题可以压成两句：

> Hardware is an Operating System.  
> Control Flow is a First-Class Hardware Object.

前一句并不意味着“硬件里真的跑了一个软件 OS”，而是说：

- 复杂硬件系统客观地拥有执行体、环境、共享资源、生命周期和回收问题
- 这些问题不应继续被局部 FSM 与临时协议分散吞掉

后一句更接近当前主线的核心：

- 现代 RTL 更擅长复用结构与数据通路
- HwOS 要探索的是：**控制流本身能否成为正式的、可组合的、可复用的硬件设计对象**

在这个命题下，当前主线做出的最重要定义是：

1. **thread 是正式执行宿主**
2. **Step / StepRef 是编译期控制对象**
3. **`HwInline` 是控制代码段，而不是普通软件 function**
4. **`Process` 是环境、服务与物理组件壳，而不是软件意义上的 OS process**
5. **`export / declare` 只负责跨边界可见性与依赖记录**
6. **state/code 是两套不同的空间**
7. **`reset` 是 thread 自己的基础原语；`OSReaper` 的即时截断是可选系统神力**

也就是说，HwOS 当前想建立的不是另一套“语法糖”，而是一套：

- 执行模型
- 控制模型
- 环境模型
- 跨边界依赖模型
- 系统服务模型

## 3. HwOS 的工作性定义

### 定义 1：执行宿主

在 HwOS 中，**thread 是正式执行宿主**。

它至少具备：

- 一份运行期控制状态
- 一条编译期控制流
- 一个可被系统观察和复位的执行上下文

在当前实现中，这体现为：

- `HardwareThread`
- `ThreadCore`
- `RuntimeContext(cursor + stateReg + binding)`

### 定义 2：控制流

在 HwOS 中，**控制流不是原始 FSM 边，而是由 `Step` 与 `StepRef` 构成的编译期结构**。

其中：

- `Step` 是控制点
- `jump` 是运行期控制转移
- `hijack` 是编译期 splice
- `waitCondition` 是入口级 stall

因此，HwOS 的控制流模型天然区分了：

- 编译期控制结构
- 运行期执行位置

### 定义 3：控制代码段

在 HwOS 中，**真正可移植、可组合的控制复用对象是代码段，而不是 thread 壳本身**。

当前主要体现为：

- `HwInline`
  - 局部或可调用控制段（通过 `SysCall.Call(HwInline, ...)`）

这一定义的关键意义在于：

- thread 更像 runtime engine / CPU host
- `HwInline` 更像运行在其上的控制程序片段

### 定义 4：环境对象

在 HwOS 中，**`Process` 当前更接近 service / environment / physical component**。

它负责：

- 持有本地状态
- 承载多个 thread / logic
- 作为共享资源和仲裁的物理边界
- 作为安装 thread code 的装配壳

因此：

- `Process` 不是软件意义上的 OS process
- 它也不只是传统 OOP 里的单线程 object
- 它更像一个并发原生的物理服务对象

### 定义 5：跨边界接口

在 HwOS 中，**当前的 symbolic 主线是 lightweight symbolic v0**。

它只承担两件事：

1. **可见性**
2. **依赖记录**

因此：

- 同一 process 内部的局部 thread/logic 可以继续直接 Scala/Chisel 交互
- 只有跨边界复用、provider/consumer 解耦或正式接口导出时，才使用：
  - `export(symbol, signal, caps)`
  - `declare(symbol, caps)`

### 定义 6：系统神力

在 HwOS 中，**`OSReaper` 是系统级附加能力，而不是基础语言默认语义**。

当前主线中，这体现为：

- 普通 thread 的基础原语是 `reset()`
- `kill(thread)` 默认最终落到 `reset()`
- 只有显式接入 `OSReaperManaged` 的对象，才拥有额外的即时截断、强制收尾与 reclaim 能力

这一定义的关键意义在于：

- 基础执行模型保持轻量
- 系统级强制收尾保持显式

## 4. 与传统 RTL 方法的区别

HwOS 并不否认 FSM、握手线和组合逻辑优化本身的价值。  
它的不同之处在于：**它试图把这些机制放在一个更高阶的控制流组织模型之中。**

### 4.1 与局部 FSM 的区别

局部 FSM 的问题不是不能表达控制流，而是它天然偏向：

- 局部
- 单模块
- 手工组合

HwOS 当前要解决的是：

- 如何让控制流成为系统级对象，而不是局部编码产物

换句话说：

- 传统 FSM 更像 lowering 结果
- HwOS 试图先定义控制语义，再让 FSM 成为实现结果

### 4.2 与“像软件一样写硬件”的区别

很多硬件 DSL 会强调“像软件一样写硬件”。  
HwOS 的重点不在于语法像不像软件，而在于：

- 是否把控制抽象的结构代价公开出来
- 是否让控制段成为正式硬件对象

因此，HwOS 不是在简单模拟软件函数调用栈。  
它是在 RTL 里重新定义：

- 执行宿主
- 控制代码段
- 共享环境
- 系统级收尾

### 4.3 与软件并发模型的区别

软件线程通信常常围绕 `channel`、共享对象或运行时库展开。  
HwOS 当前更接近：

- thread 围绕某个物理对象 / service 交互
- 该对象本身承担共享状态、仲裁和物理边界

因此：

- 软件 OOP 通常是单控制流对象模型
- HwOS 暴露的是一种**并发原生的物理对象模型**

## 5. 当前系统的研究贡献表达

如果用更论文式的语言，当前 HwOS 的工作可以概括为以下几点。

### 5.1 提出控制流本位的 RTL 主线

HwOS 不再把控制逻辑当成附着在数据通路上的辅助结构，而是把控制流设为正式主语义。

### 5.2 把 thread 收成统一执行宿主

`thread` 不再只是另一种写法，而是统一的执行宿主。  
这使控制流、生命周期与调试都能围绕同一主体组织。

### 5.3 让控制代码段成为正式可组合对象

`HwInline` 让控制代码本身能够以：

- 可组合
- 可移植
- 可链接

的形式存在，而不再只围绕局部 FSM 与对象引用展开。

### 5.4 把 `Process` 重新解释为并发原生的物理服务对象

HwOS 当前的 `Process` 不只是层级容器，而是：

- 共享状态壳
- 仲裁点
- 物理组件边界
- 控制流运行环境

### 5.5 引入 lightweight symbolic v0

HwOS 不是试图把所有硬件值都做成符号系统。  
它当前只把跨边界正式接口收成：

- `export`
- `declare`
- exported memory table
- dependency table

从而把可见性和依赖记录正式化。

## 6. 当前系统边界

这份愿景文档必须明确当前已经落地的边界，而不是把未来目标写成既成事实。

### 已经成立的边界

- thread 是唯一正式控制流执行宿主
- `StepRef` 是正式主语义
- `hijack` 是编译期 splice
- state/code 已分离为两套空间
- `export / declare` 已经构成 symbolic v0
- `reset()` 已经是 thread 自己的正式原语
- `OSReaper` 是可选系统服务，而不是基础模型默认组成部分

### 仍然是演进中的部分

- 历史 `HwFunction` v1 已移除，当前 callable segment 主线统一使用 `HwInline + SysCall.Call/Return`
- code segment 的“linkable”主线仍在继续收敛
- `Process` 到 object/service 的正式命名与叙事仍在继续演进

## 7. 这份工作的正确理解方式

如果要用一句更准确的话来理解 HwOS，现在最合适的表述不是：

- “它是一个更高级的 FSM DSL”
- “它是把硬件写得像软件”

而是：

> HwOS 是一个控制流本位的 RTL 抽象框架。  
> 它探索如何把硬件控制代码组织成正式的、可组合的代码段，并让这些代码段围绕共享物理对象运行。

它当前已经提出并部分实现了一套统一模型，用于描述：

- 执行宿主
- 控制代码段
- 共享环境
- 跨边界依赖
- 系统级收尾

因此，HwOS 的核心价值不在某一个 API，而在它试图建立的**统一解释框架**。

## 8. 相关文档

- [当前架构说明](/Users/nullstarfish/HwOS_personal/docs/architecture.md)
- [当前设计哲学](/Users/nullstarfish/HwOS_personal/docs/philosophy.md)
- [核心概念说明](/Users/nullstarfish/HwOS_personal/docs/concepts.md)
- [术语表](/Users/nullstarfish/HwOS_personal/docs/glossary.md)
- [Kernel API 指南](/Users/nullstarfish/HwOS_personal/docs/api/README.md)
