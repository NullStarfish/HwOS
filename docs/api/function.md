# Function API 指南

## 模块定位

这一组 API 负责解决：

- 如何写可复用的局部控制段
- 如何写当前 v1 的真实 `HwFunction`
- 如何把控制代码作为 portable / composable segment 来组织

它不负责：

- thread 主控制流定义
- 地址表导出
- 高层结构化控制 DSL

主要源码：

- [function/HwInline.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/function/HwInline.scala)
- [function/HwFunction.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/function/HwFunction.scala)

## 模块定位补充

当前主线下：

- `thread` 是执行宿主
- `HwInline` 和 `HwFunction` 是运行在 thread 上的控制代码段

其中：

- `HwInline` 更像局部可组合控制段
- `HwFunction` 更像独立 code segment

它们都不能简单等同于软件里的普通 function，因为在硬件里，控制段本身就带结构代价。

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

- 定义一段会被直接 emit 到当前 agent / thread 上的 inline 控制代码

关键边界：

- 它不是独立 activation
- 它不创建独立 runtime
- 它很像软件里的局部 helper，但结构代价在硬件里必须显式看待

#### `HwInline.atomic`

使用时机：

- 必须在 `Step` 内执行的 inline 逻辑

#### `HwInline.thread`

使用时机：

- 需要 thread 级上下文的 inline 控制段

关键边界：

- 被 `SysCall.Inline(...)` 使用时，可以自然 fallthrough
- 被 `SysCall.Call(...)` 使用时，必须包含显式 `SysCall.Return()`

#### `HwInline.stateless`

使用时机：

- 只需要 agent，不依赖 thread runtime 的组合逻辑片段

### `HwFunction.thread`

作用：

- 定义当前 v1 的真实硬件函数

关键边界：

- 当前实现是隐藏 activation thread
- blocking call
- 单 activation slot
- 不是最终 function 架构
- 但它已经是正式独立 code segment，而不是纯 inline helper

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
- 它是 `Call`-terminated segment 的正式退出原语
- 在 `SysCall.Call(...)` 上下文中，lowering 会把 call-site continuation 语义附着到 `Return()` 上

## Usage examples

### 示例 1：`HwInline` helper

```scala
val zeroCounter = HwInline.atomic("ZeroCounter") { t =>
  counter := 0.U
}

worker.entry {
  worker.Step("Init") {
    SysCall.Inline(zeroCounter)
  }
  worker.Step("Finish") {
    SysCall.Return()
  }
}
```

### 示例 2：真实 `HwFunction`

```scala
val workerFn = HwFunction.thread("WorkerFn") { t =>
  t.Step("Body") {
    counter := counter + 1.U
  }
  t.Step("Ret") {
    SysCall.Return()
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
    doneReg := true.B
    SysCall.Return()
  }
}
```

## 常见误区

### `HwInline` 不是软件意义上的普通 function

它承担的是局部控制段职责。  
在硬件里，控制段的结构代价不能像软件那样被编译器/OS 完全隐藏。

当它被 `SysCall.Call(...)` 使用时，当前语义会把它视为显式 return-terminated segment，而不是自然结束的 helper。

### `HwFunction` 不是纯 inline helper

当前 `HwFunction` 会创建隐藏 activation thread，并具备独立调用/阻塞/kill 传播语义。

### `function` 的核心价值不只是“像软件”

当前更重要的是：

- 它让控制代码段成为可移植、可组合的正式对象
- 它把控制复用从“只复用数据通路”推进到了“复用控制流”

## 与其他模块的关系

- thread 上下文与 `Step`，看 [thread.md](/Users/nullstarfish/HwOS_personal/docs/api/thread.md)
- `SysCall.Call/Return/start/kill` 的系统语义，看 [system.md](/Users/nullstarfish/HwOS_personal/docs/api/system.md)
