# HwOS 愿景与定义

这份文档面向研究者和接手开发的工程师，目标是用**论文级别的语言**回答一个更根本的问题：

**HwOS 到底在做什么？**

它不是实现文档，也不是 API 手册。  
如果你想看当前代码怎么组织，请读 [architecture.md](/Users/nullstarfish/HwOS_personal/docs/architecture.md)。  
如果你想看具体接口怎么用，请读 [docs/api/README.md](/Users/nullstarfish/HwOS_personal/docs/api/README.md)。

## 摘要

HwOS 是一个面向 RTL 的系统化抽象框架。  
它的核心主张不是“让硬件更像软件”，而是：

**把操作系统级语义重新引入到硬件控制流中，使复杂硬件系统可以被描述、组合、约束、回收和观测。**

在传统 RTL 中，复杂控制往往被编码为：

- 局部 FSM
- 分散的 enable/valid/ready 协议
- 人工拼接的 ownership 约定
- 分散的 kill/abort/reclaim 逻辑

这些技术都能工作，但它们通常缺少一个统一的语义中心。  
HwOS 的工作，是把这些原本分散的机制，收敛成一条统一主线：

- `thread` 作为正式执行单元
- `Step` 作为编译期控制点
- `RuntimeContext` 作为运行期执行上下文
- `own/grant` 作为资源边界
- `KernelAddressSpace` 作为 state/code 元数据平面
- `OSReaper` 作为系统级回收器

因此，HwOS 不是一个“更漂亮的 Chisel DSL”，而是一个关于**硬件执行模型**的提案。

## 1. 问题陈述

HwOS 面对的问题不是单一模块的 RTL 书写，而是**复杂控制系统的系统化表达**。

当一个硬件系统开始同时拥有这些特征时，传统 RTL 会迅速暴露出结构性压力：

1. 控制流跨越多个状态机和模块边界。
2. 资源被多个执行体共享，但缺乏统一的 ownership 和授权机制。
3. 阻塞、等待、取消、重启、回收等生命周期语义被散落到不同模块中。
4. 组合逻辑优化和时序边界往往需要通过“局部技巧”维护，而不是通过正式语义维护。
5. 调试时只能看到波形，却很难恢复“哪个执行体在做什么、为什么被阻塞、资源归谁”。

这意味着，复杂硬件设计中真正困难的部分，并不是“写一个状态机”，而是：

- 如何把多个状态机、多个握手协议、多个资源边界组织成**可组合的系统**

HwOS 的研究问题可以表述为：

> 是否可以为 RTL 提供一种统一的系统级语义，使控制流、资源边界、调用、回收和可观测性可以在同一模型下被描述？

## 2. 核心命题

HwOS 的核心命题可以写成一句话：

> Hardware is an Operating System.

这句话并不意味着“硬件真的运行了一个软件 OS”，而是意味着：

- 复杂硬件系统已经客观地拥有了执行体、上下文、资源、生命周期和回收问题
- 因此，应该用类似操作系统的语义去组织它们，而不是只用局部 FSM 和握手线去拼装它们

在这个命题下，HwOS 当前主线做出的最重要定义是：

1. **thread 是正式执行单元**
2. **Step 是控制流的编译期控制点**
3. **cursor/stateReg 是运行期控制状态**
4. **own/grant 是资源边界语义**
5. **state/code 是两套不同的空间**
6. **Return 是用户级结束语义，exit 是内核级生命周期语义**
7. **reclaim 是系统级问题，不是局部模块自觉问题**

也就是说，HwOS 想建立的不是一种“语法糖”，而是一套：

- 执行模型
- 控制模型
- 资源模型
- 生命周期模型
- 可观测性模型

## 3. HwOS 的定义

下面给出当前语境下的工作性定义。

### 定义 1：执行单元

在 HwOS 中，**thread 是正式执行单元**。

它至少具备：

- 一份运行期控制状态
- 一条编译期控制流
- 一个可被系统观测和回收的上下文

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

### 定义 3：运行期上下文

在 HwOS 中，**执行体的运行期本体由 `RuntimeContext` 表示**。

当前最小模型包含：

- `cursor`
- `stateReg`
- `binding`

这意味着 HwOS 并不把 thread 生命周期寄托在隐式协议上，而是把它收成正式 runtime state。

### 定义 4：资源边界

在 HwOS 中，**资源不是“谁都能写，靠自觉不冲突”，而是带 ownership 和 grant 的状态对象**。

其中：

- `own` 声明归属
- `grant` 声明授权
- ABI 挂在 `grant` 上，而不是挂在 `own` 上

这样做的意义是：

- “这是我的资源”
- “别人可以怎么合法访问它”

被明确分成两层语义。

### 定义 5：系统回收

在 HwOS 中，**kill / abort / reclaim 是系统级语义，不是局部模块的善后约定**。

当前主线中，这体现为：

- `kernelKillSignal`
- `OSReaper`
- `HwLease`
- `ThreadRuntimeLease`
- function call binding 的 kill propagation

这一定义的关键意义在于：

- 被终止的执行体不应把资源善后留给“运气”
- reclaim 必须可被系统观察和强制执行

## 4. 与传统 RTL 方法的区别

HwOS 并不否认 FSM、握手线和组合逻辑优化本身的价值。  
它的不同之处在于：**它试图把这些机制放在一个更高阶的系统模型之中。**

### 4.1 与局部 FSM 的区别

局部 FSM 的问题不是不能表达控制流，而是它天然偏向：

- 局部
- 单模块
- 手工组合

HwOS 想解决的是：

- 如何让控制流成为系统级对象，而不是局部编码产物

换句话说：

- 传统 FSM 更像实现单元
- HwOS 试图先定义控制语义，再让 FSM 成为 lowering 结果

### 4.2 与 valid/ready 风格的区别

valid/ready 非常适合流式数据通道，但不天然适合表达：

- 生命周期
- kill/abort
- 资源归属
- 调用/返回
- 系统级调试视图

HwOS 不是要替代所有握手协议，而是要为这些更高阶问题提供一条统一主线。

### 4.3 与“软件式 DSL” 的区别

很多硬件 DSL 会强调“像软件一样写硬件”。  
HwOS 的重点不在于语法像不像软件，而在于：

- 是否引入了真正的系统语义

因此，HwOS 不是在模拟软件函数调用栈，也不是在把 CPU 模型照搬到 RTL。  
它是在 RTL 里重新定义：

- 执行体
- 上下文
- 调用
- 资源回收

## 5. 当前系统的研究贡献表达

如果用更论文式的语言，当前 HwOS 的工作可以概括为以下几点。

### 5.1 提出 thread-first 的 RTL 执行模型

HwOS 不再把 thread 当作“另一种写法”，而是把它设为正式执行单元。  
这使控制流、生命周期和调试都能围绕同一主体组织。

### 5.2 把控制流拆分为编译期结构与运行期状态

`Step` / `StepRef` / `hijack` / `jump` 这一组概念，把控制流拆分为：

- 编译期控制结构
- 运行期 cursor 驱动

这让零泡 splice、入口级 stall、standalone suppression 等语义可以在统一模型下成立。

### 5.3 引入 ownership / grant / ABI 的资源边界模型

HwOS 不是把资源访问留给约定，而是引入：

- 归属
- 授权
- ABI 交互意图

从而把“谁拥有、谁可写、如何暴露”区分为不同语义层。

### 5.4 把 reclaim 提升为系统语义

通过 `OSReaper`、`HwLease` 与 kill propagation，HwOS 把资源回收从局部模块责任提升为系统级机制。

当前更进一步的一点是：

- context kill
- thread kill
- thread reset

已经被拆成不同语义层，而 runtime 本体通过 runtime lease 暴露给系统接管。

### 5.5 引入 state/code 双空间模型

HwOS 明确区分：

- state 是真实状态对象空间
- code 是控制编码空间

这让 runtime、control、binding 三者之间的关系被正式表达，而不是被混在一套模糊地址里。

## 6. 当前系统边界

这份愿景文档必须明确当前已经落地的边界，而不是把未来目标写成既成事实。

### 已经成立的边界

- thread 是唯一正式控制流内核
- `StepRef` 是正式主语义
- `hijack` 是编译期 splice
- state/code 已分离为两套空间
- ABI 只停留在编译期 metadata / check
- `Return` 是用户级结束语义
- `exit` 是内核概念

### 仍然是 v1 的部分

- `HwFunction` 仍然是 v1
  - 隐藏 activation thread
  - blocking call
  - 单 activation slot
- 这不是最终 function runtime 形态

也就是说，HwOS 当前已经建立了一条明确主线，但并没有宣称所有终局设计都已完成。

## 7. 这份工作的正确理解方式

如果要用一句更准确的话来理解 HwOS，现在最合适的表述不是：

- “它是一个更高级的 FSM DSL”
- “它是把硬件写得像软件”

而是：

> HwOS 是一个尝试把操作系统级语义引入 RTL 控制流的系统化框架。

它当前已经提出并部分实现了一套统一模型，用于描述：

- 执行
- 控制
- 授权
- 调用
- 回收
- 可观测性

因此，HwOS 的核心价值不在某一个 API，而在它试图建立的**统一解释框架**。

## 8. 相关文档

- [当前架构说明](/Users/nullstarfish/HwOS_personal/docs/architecture.md)
- [当前设计哲学](/Users/nullstarfish/HwOS_personal/docs/philosophy.md)
- [核心概念说明](/Users/nullstarfish/HwOS_personal/docs/concepts.md)
- [术语表](/Users/nullstarfish/HwOS_personal/docs/glossary.md)
- [Kernel API 指南](/Users/nullstarfish/HwOS_personal/docs/api/README.md)
