# HwOS 术语表

这份术语表用于统一当前主线下的命名。  
如果术语和代码不一致，应以当前代码为准并及时更新这份表。

## A-C

### `activation thread`

当前 `HwFunction` v1 内部隐藏的执行 thread。  
它承载 function body 的真实运行，不等于 caller thread。

### `binding`

把某份 runtime state 与某段 code segment 联系起来的元数据。  
它不是 cursor 本身，也不是 code segment 本身。

### `binding table`

`KernelAddressSpace` 导出的元数据表之一。  
它记录 cursor/runtime state 与 code segment 的对应关系。

### `call binding`

`HwFunction` v1 为一次调用建立的显式绑定状态。  
它支撑 continuation、kill propagation 与 reclaim。

### `code segment`

HwOS 中真正可移植、可组合、可被链接的控制代码段。  
当前 `HwInline` 和 `HwFunction` 都属于 code segment 家族，但结构代价不同。

### `code space`

step/function 控制编码所在的空间。  
它与 `state space` 分离。

### `code table`

`KernelAddressSpace` 导出的元数据表之一。  
它描述 code space 中的 segment、entry 和 labels。

## D-H

### `declare`

symbolic v0 中的依赖声明接口。  
consumer/thread/logic 通过 `declare(symbol)` 获取虚拟句柄。  
它的作用是建立正式依赖并记录到 dependency table。

### `dependency table`

`KernelAddressSpace` 导出的元数据表之一。  
它记录谁声明了哪个 exported symbol，以及请求了什么 capability。

### `export`

symbolic v0 中的导出接口。  
provider/process 通过 `export(symbol, signal, caps)` 把资源注册到 exported memory table。

### `exported memory table`

`KernelAddressSpace` 导出的元数据表之一。  
它只记录显式 export 的资源，不记录所有本地 Chisel 值。

### `HwFunction`

当前 v1 的真实硬件函数模型。  
它通过隐藏 activation thread、blocking call 与 call binding 获得调用语义。  
它是独立 code segment，不是纯 inline helper。

### `HwInline`

纯 inline 控制段。  
它不会创建独立 activation thread，也不提供真实调用协议。  
它很像软件里的局部 helper，但其结构代价在硬件里必须显式考虑。

## K-N

### `KernelAddressSpace`

当前所有地址与元数据分配的唯一入口。  
它管理 state/code 双 allocator，以及 state/code/binding/exported/dependency 五张表。

### `NamedStepRef`

按名字引用 step 的编译期引用。  
它是 `StepRef` 的正式形态之一。

### `NextStepRef`

引用“当前 step 的后继 step”的编译期引用。  
它不是 runtime pointer。

## O-R

### `OSReaper`

可选系统级回收器。  
它负责在 kill / abort 后做系统级强制收尾与 reclaim。  
它不是基础语言或基础 runtime 的默认组成部分。

### `OSReaperManaged`

显式声明接入 OSReaper 服务的对象接口。  
只有 mixin 了这个 trait 的对象，才会暴露 reaper kill / reclaim 所需接口。

### `Process`

通常指 `HwProcess`。  
当前更接近 service / environment / physical component，而不是软件意义上的 OS process。  
它是共享状态、仲裁和装配的物理边界。

### `Return`

用户可见的结束控制流语义。  
在 root 下，`Return` 会内部落到 kernel `exit`。

### `RuntimeContext`

thread runtime 的第一性结构。  
当前包含 `cursor`、`stateReg` 与 `binding`。

## S

### `state space`

真实硬件状态所在的地址空间。  
例如 `Reg`、cursor、`stateReg`、export backing state。

### `state table`

`KernelAddressSpace` 导出的元数据表之一。  
它记录 state space 中被 kernel 跟踪的状态对象。

### `stateReg`

thread runtime 的生命周期状态寄存器。  
当前主语义由它编码 `Idle / Running / Done`。

### `Step`

thread 控制流中的正式控制点。  
运行时不是 `Step` 在执行，而是 cursor 在这些控制点之间移动。

### `StepRef`

编译期 step 引用。  
当前正式形态是 `NamedStepRef` 和 `NextStepRef`。

### `symbolic v0`

当前最轻量的 symbolic 主线。  
它只承担两件事：可见性与依赖记录。  
同一 process 内的本地实现仍允许直接 Scala/Chisel 交互。

## T-Z

### `ThreadCore`

当前 thread 的正式内核入口。  
它负责收集 `entry { ... }`、绑定 runtime、接入 IR/layout/runtime lowering。

### `ThreadDef`

definition-first 的 thread 定义接口。  
它让 thread code 可以独立成单文件定义，再由 process 负责安装。

### `thread`

HwOS 的正式执行宿主。  
它持有 `RuntimeContext` 并执行 code segment。  
它更像 CPU/runtime engine，而不是主要的代码复用单元。

### `waitCondition`

thread 内部的阻塞原语之一。  
它会 stall 回入口 step，而不是跳入某个被 splice 的内部 step。

### `cursor`

thread runtime 中表示当前控制位置的状态寄存器。  
它是 state object，但保存的是 code space 编号。

### `exit`

内核内部生命周期操作。  
它不是当前主线下鼓励用户直接调用的 API。

### `kill(contextEntity)`

context-like 对象的系统切断接口。  
当前只对显式接入 OSReaper 的对象有效，不再被视为所有 entity 的天然能力。

### `reset`

thread 自己的 runtime 复位语义。  
默认只复位 `cursor/stateReg`，不主动 reclaim 普通状态。

## 不再作为当前主线描述使用的旧说法

下面这些说法不应再被用来描述当前主线：

### “`own / grant` 是主编程模型”

当前主线已经收成：

- 本地实现：直接 Scala/Chisel 交互
- 跨边界接口：`export / declare`

`own / grant` 已退出主线。

### “grant table”

当前 `KernelAddressSpace` 不再导出 `grant table`。  
它已经被 exported/dependency 两张 symbolic 表替代。

### “thread lifecycle 主要靠 lifecycle lease”

当前 thread lifecycle 首先来自 `RuntimeContext(cursor + stateReg + binding)`。  
`OSReaper` 是可选系统服务，不是 thread 生命周期本体。

### “state/code 共用一个地址空间”

当前 `state space` 与 `code space` 已经分离，使用两套独立 allocator。
