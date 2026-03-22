# HwOS 当前设计哲学

这份文档解释的是 **为什么当前系统被设计成这样**，而不是代码实现细节。  
如果你想看当前代码怎么组织，请先读 [architecture.md](/Users/nullstarfish/HwOS_personal/docs/architecture.md)。

## 概览

当前 HwOS 的核心哲学可以压成几句话：

- HwOS 不是先从数据通路出发，再把控制塞进去；它首先把**控制流**当成第一等设计对象
- `thread` 是统一执行宿主，`HwInline` 是运行在其上的控制代码段
- `Process` 当前更接近 **service / environment / physical component**，而不是软件意义上的 OS process
- `export / declare` 只负责跨边界可见性与依赖记录；同一 process 内的本地代码可以继续直接 Scala/Chisel 交互
- `OSReaper` 是可选系统神力，不是基础语言或基础 runtime 的默认组成部分

这不是历史文档，也不是未来宣言。  
它只解释：**为什么当前主线被收敛成现在这个样子。**

## HwOS 试图解决的问题

HwOS 试图解决的不是“如何再包装一层 Chisel API”，而是以下几个更根本的问题：

1. 现代 RTL 更擅长复用结构和数据通路，不擅长复用复杂控制流。
2. 局部 FSM 很容易能工作，但很难组合，也很难在系统范围内复用。
3. 当多个执行流围绕同一个物理对象交互时，代码组织、仲裁和资源边界很快会失控。
4. 软件把空间和回收大量藏在编译器/操作系统里；硬件不能这么做，因为控制段、状态和 activation 本身就是面积与结构成本。
5. 如果只剩对象引用和层级穿透，thread 很难脱离具体 provider 被复用。

所以 HwOS 的目标不是“写起来像软件”，而是：

- 把控制流、执行宿主、物理对象、符号依赖和系统服务分层
- 让硬件控制代码可以作为正式的、可组合的代码段存在

## 为什么控制流是第一性对象

当前 HwOS 最核心的判断是：

> 复杂硬件设计中最难复用和最缺乏正式抽象的，不是数据通路，而是控制流。

因此主线先立住：

- `thread` 是执行体
- `Step` / `StepRef` 是编译期控制对象
- `HwInline` 是控制代码段

这意味着 HwOS 的重点不是“如何定义一堆寄存器和 wire”，而是：

- 如何把控制行为写成可组合对象
- 如何把一段控制代码搬到另一个环境里运行
- 如何让同一个物理对象承载多个控制流

## 为什么 `thread` 是执行宿主，而不是主要复用单元

当前最容易误解的一点是：`thread` 虽然重要，但它不是最值得复用的那一层。

`thread` 更像：

- CPU module
- runtime engine
- execution host

它持有：

- `RuntimeContext`
- `cursor`
- `stateReg`

而真正 portable / combinable / linkable 的，是运行在 thread 上的控制代码段：

- `HwInline`
- 所以后续如果要谈 “linkable”，最自然的落点首先不是 thread 壳，而是：

- code segment 的 portable / combinable / linkable

## 为什么 `HwInline` 不能直接等同于软件 function

`HwInline` 在行为上很像软件里的局部 helper。  
但 HwOS 不能直接套用软件 `function` 心智，因为：

- 软件的空间与回收代价很多被编译器和 OS 吸收
- 硬件里的控制段、局部状态、activation 方式本身就是结构成本

所以 HwOS 必须把这件事显式公开：

- `HwInline` 是局部/可调用控制段
- `thread` 是执行宿主

这不是命名偏好，而是在显式区分：

- 语义复用
- 结构代价

## 为什么 `Process` 现在更像 service / physical component

当前代码里的 `HwProcess` 仍然非常重要，但它不应再被理解成软件意义上的 OS process。

它更接近：

- 一个物理对象
- 一个共享状态与仲裁中心
- 一个环境壳
- 一个装配壳

它负责：

- 提供本地资源
- 挂载多个 thread / logic
- 作为多个控制流围绕的共享环境

对 thread 来说，process 内部的状态与接口更像“外设环境”；  
而对系统来说，process 又是一个有明确物理意义的组件，例如 `ScoreboardRegfile`。

## 为什么 symbolic v0 只承担两件事

当前 `export / declare` 的 symbolic v0 不追求完整链接器语义。  
它只承担两件事：

1. **可见性**
2. **依赖记录**

也就是说：

- 同一 process 内的局部 thread/logic，可以继续直接 Scala/Chisel 交互
- 只有跨边界复用、provider/consumer 解耦、或需要正式接口时，才走 `export / declare`

这使 symbolic 模型不会压垮日常开发，同时又能为可移植控制代码建立正式边界。

## 为什么 `OSReaper` 必须是可选系统神力

`OSReaper` 当前负责的是系统级 kill / reclaim / 强制收尾。  
它不应该污染基础语言和基础 runtime。

因此当前边界是：

- 普通 thread 的基础原语是 `reset()`
- `kill(thread)` 默认最终落到 `reset()`
- 只有显式接入 `OSReaperManaged` 的对象，才拥有额外的即时截断/强制回收能力

换句话说：

- `OSReaper` 是可选神力
- 不是所有对象的默认基础能力

## 当前主线的边界

这份哲学文档默认以下边界已经成立：

- HwOS 当前首先是 **控制流本位** 的硬件描述框架
- `thread` 是执行宿主
- `HwInline` 是控制代码段
- `Process` 更接近 service / environment / physical component
- `export / declare` 构成 lightweight symbolic v0
- `own / grant / ACL` 已退出当前主线
- `OSReaper` 是可选系统服务，不是基础模型

如果未来这些边界变了，这份文档也应跟着改。  
但在当前代码下，这些不是偏好，而是已经落地的系统定义。
