# System API 指南

## 模块定位

这一组 API 负责解决：

- 系统调用怎么驱动 thread / callable segment 生命周期
- kernel 地址表如何分配与导出

它不负责：

- thread 主控制流定义
- 高层结构化控制

主要源码：

- [system/SysCall.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/system/SysCall.scala)
- [system/KernelAddressSpace.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/system/KernelAddressSpace.scala)

## 文件与正式入口

建议直接使用这些入口：

- `SysCall.Call(...)`
- `SysCall.Return()`
- `SysCall.start(thread)`
- `SysCall.kill(thread)`
- `SysCall.kill(contextEntity)`
- `SysCall.fork(name) { ... }`
- `kernel.addressSpace.renderAddressTables()`
- `kernel.addressSpace.exportAddressTables(...)`

## 重要 API 清单

### `SysCall.Call(HwInline)`

作用：

- 发起一个显式 return-terminated 的 callable segment

关键边界：

- 被调用 segment 必须包含显式 `SysCall.Return()`
- `Return()` 会把控制流接回当前 `Call` 绑定的 continuation

### `SysCall.Return()`

作用：

- 返回 continuation
- 或在 root 下落到 kernel `exit`

关键边界：

- 它是 callable segment 的正式退出原语
- 它不是普通 jump 的别名
- 在 `SysCall.Call(...)` 上下文里，return edge 会携带 call-site continuation 语义

### `SysCall.start(target)`

作用：

- 启动目标 thread

关键边界：

- 当前是 process-local 直控主线
- 不再经过旧 lifecycle ACL

### `SysCall.kill(target: HardwareThread)`

作用：

- 终止目标 thread

当前边界：

- 普通 thread 默认最终就是 `reset()`
- 如果该 thread 关联了显式的 `OSReaperManaged` cleanup holder，系统会先做 reclaim/cleanup，再 `reset()`
- 也就是说，`OSReaper` 的即时截断是附加神力，不是所有 thread 的默认基础能力

### `SysCall.kill(target: HwContextEntity)`

作用：

- 对显式接入 `OSReaperManaged` 的对象发起系统切断

关键边界：

- 它不再被视为所有 entity 的天然能力
- 未接入 `OSReaperManaged` 的对象不应该把 `kill(contextEntity)` 当通用接口

### `KernelAddressSpace`

作用：

- 分配 state/code 双地址空间
- 管理：
  - `state table`
  - `code table`
  - `binding table`
  - `exported memory table`
  - `dependency table`
- 导出地址表

## Usage examples

### 示例 1：启动与 kill thread

```scala
controller.entry {
  controller.Step("StartWorker") {
    SysCall.Inline(SysCall.start(worker))
  }
  controller.Step("KillWorker") {
    SysCall.Inline(SysCall.kill(worker))
    SysCall.Return()
  }
}
```

### 示例 2：导出地址表

```scala
val text = kernel.addressSpace.renderAddressTables()
kernel.addressSpace.exportAddressTables("generated")
```

当前会导出：

- `state_table`
- `code_table`
- `binding_table`
- `exported_memory_table`
- `dependency_table`

## 常见误区

### `exit` 不是用户 API

当前用户态正式结束接口是 `SysCall.Return()`。  
`exit()` 是内核内部生命周期操作。

### `kill(thread)` 默认不是“OSReaper 神力”

普通 thread 的基础终止原语最终落点是 `reset()`。  
只有显式接入 OSReaper 的对象才会拥有额外的即时收尾能力。

### `KernelAddressSpace` 不只是导出器

它首先是地址和元数据分配器；导出只是它的一个外显能力。

### state 与 code 不是同一套地址

当前已经是双 allocator：

- state 对象地址只在 state space 内增长
- code 段地址只在 code space 内增长

## 与其他模块的关系

- `HwInline` 调用形态，看 [function.md](/Users/nullstarfish/HwOS_personal/docs/api/function.md)
- `RuntimeContext` 驱动下的 thread 观察接口，看 [thread.md](/Users/nullstarfish/HwOS_personal/docs/api/thread.md)
- 哲学与概念边界，看 [concepts.md](/Users/nullstarfish/HwOS_personal/docs/concepts.md)
