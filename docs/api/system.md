# System API 指南

## 模块定位

这一组 API 负责解决：

- 系统调用怎么驱动 thread / function 生命周期
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

不建议把下面这些当常规用户入口直接依赖：

- `SysCall.exit()`
- `KernelAddressSpace.reserveAddressObject(...)`

它们更偏内核内部接口。

## 重要 API 清单

### `SysCall.Call(HwInline)`

作用：

- 把一段 inline 逻辑 emit 到当前调用上下文

### `SysCall.Call(HwInline, returnTo)`

作用：

- 给 inline 调用显式绑定 continuation

### `SysCall.Call(HwFunction, returnTo)`

作用：

- 发起当前 v1 的真实函数调用
- 启动 activation thread
- caller 在 call step 等待 activation done

### `SysCall.Return()`

作用：

- 返回 continuation
- 或在 root 下落到 kernel `exit`

### `SysCall.start(target)`

作用：

- 启动目标 thread

关键边界：

- 需要 lifecycle ACL

### `SysCall.kill(target)`

作用：

- 杀死目标 thread

关键边界：

- 当前语义是系统侧的 `thread_kill = context kill + runtime lease reclaim/reset`
- kill 后 thread runtime 会回到 `Idle`
- 如果 caller 正在等待某个 `HwFunction`，当前主线会触发 activation 连坐回收

### `SysCall.kill(contextEntity)`

作用：

- 触发 context 级系统切断

关键边界：

- 它的作用对象是 `HwContextEntity`
- 它不再天然等价于 thread kill
- 对 thread context 而言，OSReaper 默认会接管其 runtime lease

### `SysCall.fork(name) { ... }`

作用：

- 创建并启动一个子 thread

### `KernelAddressSpace`

作用：

- 分配 state/code 双地址空间
- 管理 `state table` / `code table` / `binding table` / `grant table`
- 导出地址表

## Usage examples

### 示例 1：启动与 kill thread

```scala
controller.entry {
  controller.Step("StartWorker") {
    SysCall.Call(SysCall.start(worker))
  }
  controller.Step("KillWorker") {
    SysCall.Call(SysCall.kill(worker))
    SysCall.Call(SysCall.Return())
  }
}
```

### 示例 2：context kill

```scala
controller.entry {
  controller.Step("KillProcessContext") {
    SysCall.Call(SysCall.kill(proc: HwContextEntity))
  }
}
```

这会切断 `proc` 的 context。  
它不会天然等于任意 thread 的专用 kill；但如果目标本身就是 thread context，OSReaper 默认会顺带 reset 其 runtime。

### 示例 3：`Return` 到 continuation

```scala
caller.entry {
  caller.Step("CallInline") {
    SysCall.Call(helperInline, "AfterInline")
  }
  caller.Step("AfterInline") {
    SysCall.Call(SysCall.Return())
  }
}
```

如果当前有 continuation，`Return()` 会回到它，而不是直接结束整个 thread。

### 示例 4：导出地址表

```scala
val text = kernel.addressSpace.renderAddressTables()
kernel.addressSpace.exportAddressTables("generated")
```

当前会导出：

- `state_table`
- `code_table`
- `binding_table`
- `grant_table`

并且 `state` / `code` 使用两套独立地址空间。

## 常见误区

### `exit` 不是用户 API

当前用户态正式结束接口是 `SysCall.Return()`。  
`exit()` 是内核内部生命周期操作。

### `kill(contextEntity)` 不等于 `kill(thread)`

一个是 context 级 cut-off，一个是 thread 专用系统终止。  
当前系统明确区分这两类 kill。

### `KernelAddressSpace` 不只是导出器

它首先是地址和元数据分配器；导出只是它的一个外显能力。

### state 与 code 不是同一套地址

当前已经是双 allocator：

- state 对象地址只在 state space 内增长
- code 段地址只在 code space 内增长

## 与其他模块的关系

- `HwInline` / `HwFunction` 调用形态，看 [function.md](/Users/nullstarfish/HwOS_personal/docs/api/function.md)
- `RuntimeContext` 驱动下的 thread 观察接口，看 [thread.md](/Users/nullstarfish/HwOS_personal/docs/api/thread.md)
- 哲学与概念边界，看 [concepts.md](/Users/nullstarfish/HwOS_personal/docs/concepts.md)
