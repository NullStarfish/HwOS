# HwOS 术语表

这份术语表用于统一当前主线下的命名。  
如果术语和代码不一致，应以当前代码为准并及时更新这份表。

## A-C

### `activation thread`（历史）

历史 `HwFunction` v1 内部隐藏执行 thread 的叫法。  
当前主线已移除 `HwFunction`，该术语只用于阅读旧材料。

### `binding`

把某份 runtime state 与某段 code segment 联系起来的元数据。  
它不是 cursor 本身，也不是 code segment 本身。

### `binding table`

`KernelAddressSpace` 导出的元数据表之一。  
它记录 cursor/runtime state 与 code segment 的对应关系。

### `call binding`（历史）

历史 `HwFunction` v1 的调用绑定状态。  
当前主线不再把它作为正式机制。

### `code segment`

HwOS 中真正可移植、可组合、可被链接的控制代码段。  
当前主线的正式代码段 API 是 `HwInline`。

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

### `HwFunction`（历史）

历史 v1 的函数模型；当前主线已移除。

### `HwInline`

纯 inline 控制段。  
它不会创建独立 activation thread。  
它很像软件里的局部 helper，但其结构代价在硬件里必须显式考虑。  
需要可调用语义时，使用 `SysCall.Call(HwInline, returnTo)` + 显式 `SysCall.Return()`。

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

### `registerReset`

thread 的本地 reset hook 注册接口。  
用户可以显式把 `lease.forceReclaim()` 之类的本地清理动作挂到 `thread.reset()` 上。

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
额外 reclaim 若需要，应通过 `thread.registerReset { ... }` 显式接入，而不是被视为 thread 生命周期本体。

### “state/code 共用一个地址空间”

当前 `state space` 与 `code space` 已经分离，使用两套独立 allocator。
