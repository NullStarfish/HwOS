# HwOS 术语表

这份术语表用于统一当前主线下的命名。  
它不是历史记录，也不是愿景说明。  
如果术语和代码不一致，应以当前代码为准并及时更新这份表。

## A-C

### `activation thread`

当前 `HwFunction` v1 内部隐藏的执行 thread。  
它承载 function body 的真实运行，不等于 caller thread。

### `ABI`

当前系统中的编译期交互语义，挂在 `grant(...)` 上。  
它不是运行时总线协议。

### `binding`

把某份 runtime state 与某段 code segment 联系起来的元数据。  
它不是 cursor 本身，也不是 code segment 本身。

### `binding table`

`KernelAddressSpace` 导出的元数据表之一。  
它记录 cursor/runtime state 与 code segment 的对应关系。

### `call binding`

`HwFunction` v1 为一次调用建立的显式绑定状态。  
它支撑 continuation、kill propagation 与 reclaim。

### `code space`

step/function 控制编码所在的空间。  
它与 `state space` 分离。

### `code table`

`KernelAddressSpace` 导出的元数据表之一。  
它描述 code space 中的 segment、entry 和 labels。

## G-H

### `GrantAbi.LevelDrivenWire`

表示某个导出的 `Wire` 应按持续电平驱动来理解。  
常用于 enable、mode、valid-like 信号。

### `GrantAbi.PulseWire`

表示某个导出的 `Wire` 应按单拍脉冲来理解。  
常用于 done、notify 一类事件信号。

### `grant table`

`KernelAddressSpace` 导出的元数据表之一。  
它记录 `grant(...)` 的目标、地址对象与 ABI。

### `HwFunction`

当前 v1 的真实函数模型。  
它通过隐藏 activation thread、blocking call 与 call binding 获得调用语义。

### `HwInline`

纯 inline 逻辑包装。  
它不会创建独立 activation thread，也不提供真实调用协议。

## K-N

### `KernelAddressSpace`

当前所有地址与元数据分配的唯一入口。  
它管理 state/code 双 allocator，以及 state/code/binding/grant 四张表。

### `NamedStepRef`

按名字引用 step 的编译期引用。  
它是 `StepRef` 的正式形态之一。

### `NextStepRef`

引用“当前 step 的后继 step”的编译期引用。  
它不是 runtime pointer。

## O-R

### `OSReaper`

系统级回收器。  
它负责在 kill / abort 后回收 active leases，并处理 function activation 的连坐 reclaim。

### `own`

注册某个状态对象的 ownership。  
它说明“这是我的资源”，并会让该对象进入 `state table`。

### `Process`

通常指 `HwProcess`。  
它是结构层级与资源组织容器，不是执行体。

### `Return`

用户可见的结束控制流语义。  
在 root 下，`Return` 会内部落到 kernel `exit`。

### `RuntimeContext`

thread runtime 的第一性结构。  
当前包含 `cursor`、`stateReg` 与 `binding`。

## S

### `state space`

真实硬件状态所在的地址空间。  
例如 `Reg`、cursor、`stateReg`、lease backing state。

### `state table`

`KernelAddressSpace` 导出的元数据表之一。  
它记录 state space 中的状态对象。

### `stateReg`

thread runtime 的生命周期状态寄存器。  
当前主语义由它编码 `Idle / Running / Done`。

### `Step`

thread 控制流中的正式控制点。  
运行时不是 `Step` 在执行，而是 cursor 在这些控制点之间移动。

### `StepRef`

编译期 step 引用。  
当前正式形态是 `NamedStepRef` 和 `NextStepRef`。

## T-Z

### `ThreadCore`

当前 thread 的正式内核入口。  
它负责收集 `entry { ... }`、绑定 runtime、接入 IR/layout/runtime lowering。

### `ThreadStepDemo`

当前的 demo façade。  
它复用 thread 主线，但不是 thread 真身。

### `waitCondition`

thread 内部的阻塞原语之一。  
它会 stall 回入口 step，而不是跳入某个被 splice 的内部 step。

### `cursor`

thread runtime 中表示当前控制位置的状态寄存器。  
它是 state object，但保存的是 code space 编号。

### `exit`

内核内部生命周期操作。  
它不是当前主线下鼓励用户直接调用的 API。

## 不再作为当前主线描述使用的旧说法

下面这些说法不应再被用来描述当前主线：

### “多 backend thread”

当前 thread 已经收成统一主线，不再有多个 backend 竞争同一语义位置。

### “`Next.hijack()` 是正式接口”

当前正式接口是：

- `thread.Next`
- `thread.hijack(thread.Next)`

`Next.hijack()` 不再是正式主语义。

### “thread lifecycle 主要靠 lifecycle lease”

当前 thread lifecycle 首先来自 `RuntimeContext(cursor + stateReg + binding)`。  
lease 主要用于资源/调用期语义。

### “state/code 共用一个地址空间”

当前 `state space` 与 `code space` 已经分离，使用两套独立 allocator。
