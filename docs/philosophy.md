# HwOS 当前设计哲学

这份文档解释的是 **为什么当前系统被设计成这样**，而不是代码实现细节。  
如果你想看当前代码怎么组织、哪些模块在调用哪些模块，请先读 [architecture.md](/Users/nullstarfish/HwOS_personal/docs/architecture.md)。

## 概览

当前 HwOS 的核心哲学可以压成几句话：

- hardware 不只是“寄存器 + 组合逻辑 + 局部 FSM 拼装”
- hardware 可以被理解为一个受操作系统语义约束的执行系统
- `thread` 是正式控制流执行单元
- `context / own / grant` 是硬件边界管理工具
- `state` 与 `code` 是两套不同的空间
- `Return` 是用户语义，`exit` 是内核语义

这不是一份历史文档，也不是未来宣言。  
它只解释：**为什么当前代码主线被收敛成现在这个样子。**

## HwOS 试图解决的问题

HwOS 试图解决的不是“如何再包装一层 Chisel API”，而是以下几个更根本的问题：

1. 控制复杂度会随着并发、条件分支、跨模块交互迅速失控。
2. 局部 FSM 很容易能工作，但很难组合。
3. 资源边界如果只靠约定，很容易在跨 thread、跨 function、跨 process 时失守。
4. 被杀死、被中断、被 abort 的执行体如果没有系统级回收路径，会留下死锁和脏状态。
5. RTL 调试如果只剩波形，很难表达“谁在执行、为什么阻塞、资源归谁”。

所以 HwOS 的目标不是“写起来像软件”这么简单，而是：

- 把控制流、所有权、回收、调用、可观测性这些本来散落的语义，收成同一条系统主线
- 让复杂控制逻辑不再只能靠很多局部 FSM 和临时握手线勉强拼起来

## 为什么 thread 是正式执行单元

当前主线把 `thread` 设成唯一正式控制流执行内核，不再保留多 backend 竞争同一个语义位置。

原因不是为了简化 API，而是为了明确系统的第一性单位：

- 真正“活着”或“死掉”的，是某个执行体
- 真正“持有 cursor / stateReg / binding”的，是某个执行体
- 真正会被 kill、被 reclaim、被 debug 的，也是某个执行体

如果没有一个明确的执行单元，这些语义只能散在很多 helper 和局部协议里。  
一旦把 `thread` 确认为正式执行单元，就可以把下面这些东西都挂在同一主体上：

- 控制流
- runtime state
- context gating
- kill / reclaim
- debug trace

这就是为什么当前仓库已经不再保留多个 thread backend 的主线说法。

## 为什么控制流要收成 Step

HwOS 当前不是把控制流理解成“很多节点对象互相引用”，而是收成：

- `Step`
- `StepRef`
- `jump`
- `hijack`
- `waitCondition`

这里的关键哲学是：

- `Step` 是控制点
- `StepRef` 是编译期控制引用
- `jump` 是控制转移
- `hijack` 是编译期 splice

这样做的价值在于，控制流被拆成了两层：

1. 编译期控制结构
2. 运行期 cursor 驱动

也就是说：

- 运行期真的存在的是 `cursor`
- 编译期决定的是“哪些 step 需要独立 slot、哪些 step 只是被 splice 使用”

这比把一切都当 runtime node 跳转更贴近当前实现，也更能解释为什么 `hijack` 不是 runtime jump。

## 为什么 `hijack` 必须是编译期 splice

当前 `hijack` 的本质不是“立即跳到另一个 step 执行”，而是：

- 在编译期把被引用 step 的 body 展开到当前位置

这件事非常重要，因为它决定了 `hijack` 的边界：

- 它不是一个 runtime 指令
- 它不引入新的运行时状态
- 它不等价于 `jump`

如果把 `hijack` 做成 runtime jump，系统会重新掉回旧问题：

- 组合优化会更差
- 当前 step 与被引用 step 之间的零泡语义会消失
- `waitCondition` 的入口 stall 语义会变得更难统一

所以现在把 `hijack` 收成编译期 splice，是一个明确的哲学选择，不是语法偏好。

## 为什么要区分 state space 和 code space

HwOS 当前已经明确把地址空间分成两套：

- `state space`
- `code space`

原因是这两者在本体上就不是一类东西。

`state space` 里放的是真实硬件状态：

- `Reg`
- cursor
- `stateReg`
- lease backing state

`code space` 里放的是控制编码：

- step 的 code slot
- function 的入口编码
- continuation 对应的 code 位置

如果把两者混成一套地址，就会出现一种看起来“统一”，但语义很怪的系统：

- code 起始地址会被前面分配过多少 `Reg` 污染
- control 编号会失去自己的解释空间
- 导出的表会很难读

当前主线选择把两者拆开，就是在承认：

- 控制编码不是状态寄存器
- cursor 是 state，但它承载的是 code 编号
- binding 是连接两套空间的桥

## 为什么 ABI 只停留在编译期

当前 ABI 不被实现成运行时总线协议，而只是：

- grant 的编译期语义声明
- 编译期检查
- 元数据导出

原因很直接：

- `own` 说明“谁拥有资源”
- `grant` 说明“别人如何与这个资源交互”

ABI 放在 `grant` 上，就能表达：

- 这是一个寄存器写接口
- 这是一个持续驱动的 wire
- 这是一个单拍脉冲

但 ABI 不应该自动变成一套新的 runtime 通信语言。  
一旦把它推成运行时协议，系统会立刻从“当前内核”变成“新的总线语言/接口语言”，这不是当前主线的目标。

## 为什么 thread runtime 不是 lease-first

当前系统保留了一个很重要的分层：

- thread runtime 以 `RuntimeContext(cursor + stateReg + binding)` 为主
- lease 负责资源/调用期语义

也就是说：

- thread 的生命周期不是靠 lease 才成立
- `active/done` 首先来自 `stateReg`
- lease 更多用于：
  - 资源占用
  - function call 绑定
  - reaper 回收边界

这样做的好处是：

- thread 仍然是稳定的执行体
- function activation 可以使用 lease 语义
- 不会把所有生命周期问题都压到一套更重的抽象上

## 为什么 `Return` 和 `exit` 要分开

当前语义明确区分：

- `Return` 是用户可见的控制语义
- `exit` 是内核实现语义

这背后的哲学是：用户应当表达“结束当前控制流”，而不是直接操纵内核生命周期状态机。

所以：

- 普通 continuation return：回到调用点
- root 下的 `Return`：内部走 kernel `exit`

用户只需要理解 `Return`。  
`exit` 应该被保留为内核概念，而不是用户 API。

## 为什么 `HwFunction` 现在仍然是 v1

当前 `HwFunction` 不是最终函数模型，而是一个务实的 v1：

- 隐藏 activation thread
- blocking call
- 单 activation slot
- 显式 call binding
- caller kill 时 activation 连坐回收

这条路线的哲学不是“函数必须长得像软件函数”，而是：

- 先让调用、阻塞、回收、kill 传播语义闭合
- 再去追求更理想的 function runtime 形态

也就是说，当前 `HwFunction` 首先是一个**可被系统接管的调用执行体**，而不是语法糖。

## 当前主线的边界

这份哲学文档默认以下边界已经成立：

- thread 没有多个 backend
- `StepRef` 是正式主语义
- `hijack` 是编译期 splice
- ABI 是编译期 metadata，不是运行时协议
- state/code 是两套独立地址空间
- thread runtime 不是 lifecycle lease-first
- `exit` 是内核概念
- `HwFunction` 还是 v1

如果未来这些边界变了，这份文档也应跟着改。  
但在当前代码下，这些不是偏好，而是已经落地的系统定义。
