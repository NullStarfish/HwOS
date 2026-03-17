# Function API 指南

## 模块定位

这一组 API 负责解决：

- 如何写可复用的 inline helper
- 如何写当前 v1 的真实 `HwFunction`
- 如何通过 `SysCall.Call(...)` 调用它们

它不负责：

- thread 主控制流定义
- 地址表导出
- 高层结构化控制 DSL

主要源码：

- [function/HwInline.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/function/HwInline.scala)
- [function/HwFunction.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/function/HwFunction.scala)

## 文件与正式入口

建议从这些入口开始使用：

- `HwInline.atomic`
- `HwInline.thread`
- `HwInline.stateless`
- `HwInline.bindings`
- `HwFunction.thread`
- `Invoke()`
- `Invoke(returnTo)`

配套系统调用入口：

- `SysCall.Call(HwInline)`
- `SysCall.Call(HwInline, returnTo)`
- `SysCall.Call(HwFunction, returnTo)`
- `SysCall.Return()`

## 重要 API 清单

### `HwInline`

作用：

- 定义一段会被直接 emit 到当前 agent / thread 上的 inline 逻辑

关键边界：

- 它不是独立 activation
- 它不创建独立 runtime

#### `HwInline.atomic`

使用时机：

- 必须在 `Step` 内执行的 inline 逻辑

#### `HwInline.thread`

使用时机：

- 需要 thread 级上下文的 inline 逻辑

#### `HwInline.stateless`

使用时机：

- 只需要 agent，不依赖 thread runtime 的组合逻辑片段

#### `HwInline.bindings`

使用时机：

- 你只想拿到当前 agent，不强制上下文形状

### `HwFunction.thread`

作用：

- 定义当前 v1 的真实硬件函数

关键边界：

- 当前实现是隐藏 activation thread
- blocking call
- 单 activation slot
- 不是最终 function 架构

### `Invoke() / Invoke(returnTo)`

作用：

- 把 `HwFunction` 包装成可通过 `SysCall.Call(...)` 发起的调用入口

关键边界：

- `Invoke()` 本身不是运行
- 真正运行发生在 `SysCall.Call(...)`

### `SysCall.Return()`

在 function / inline 场景里的作用：

- 如果当前有 continuation，则回 continuation
- 如果当前是 root，则内部转成 kernel `exit`

## Usage examples

### 示例 1：`HwInline` helper

```scala
val zeroCounter = HwInline.atomic("ZeroCounter") { t =>
  counter <== 0.U
}

worker.entry {
  worker.Step("Init") {
    SysCall.Call(zeroCounter)
  }
  worker.Step("Finish") {
    SysCall.Call(SysCall.Return())
  }
}
```

### 示例 2：真实 `HwFunction`

```scala
val workerFn = HwFunction.thread("WorkerFn") { t =>
  t.Step("Body") {
    counter <== counter + 1.U
  }
  t.Step("Ret") {
    SysCall.Call(SysCall.Return())
  }
}
```

### 示例 3：caller 中调用 `HwFunction`

```scala
caller.entry {
  caller.Step("CallBody") {
    SysCall.Call(workerFn, "AfterCall")
  }
  caller.Step("AfterCall") {
    doneReg <== true.B
    SysCall.Call(SysCall.Return())
  }
}
```

这里：

- caller 会阻塞等待 activation 完成
- function 内的 `Return()` 会把 caller 带回 `AfterCall`

### 示例 4：inline return 的含义

```scala
val helper = HwInline.thread("Helper") { t =>
  SysCall.Call(SysCall.Return())
}
```

这里的 `Return()` 不是“退出一个独立函数实例”，而是：

- 如果有 continuation，就跳回 continuation
- 如果没有 continuation，就结束当前 root thread

## 常见误区

### `HwInline` 不是独立 activation

它只是被 emit 到当前 agent 上的代码段。  
所以它的 `Return()` 最终还是作用在当前 thread 上。

### `HwFunction` 不是纯 inline helper

当前 `HwFunction` 会创建隐藏 activation thread，并具备独立调用/阻塞/kill 传播语义。

### `Return` 不是公开 `exit`

用户写的是 `Return()`。  
是否落到 continuation 或 root-exit，由当前调用上下文决定。

## 与其他模块的关系

- thread 上下文与 `Step`，看 [thread.md](/Users/nullstarfish/HwOS_personal/docs/api/thread.md)
- `SysCall.Call/Return/start/kill` 的系统语义，看 [system.md](/Users/nullstarfish/HwOS_personal/docs/api/system.md)

