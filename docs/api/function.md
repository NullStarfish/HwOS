# Function API 指南

## 模块定位

当前主线里，`function` 包只保留一类正式控制代码段：`HwInline`。

它负责：

- 定义可复用控制段
- 作为 `SysCall.Call(...)` 的 callable segment
- 作为 `SysCall.Inline(...)` 的 inline segment

它不负责：

- 独立 activation thread
- 隐藏函数 runtime
- 自动仲裁/自动回收策略

主要源码：

- [function/HwInline.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/function/HwInline.scala)

## 当前边界

- `HwFunction` 已从当前主线实现中移除。
- `thread` 仍是执行宿主。
- `HwInline` 是控制段载体，真正的资源仲裁/生命周期由 process/service 显式实现。

## 文件与正式入口

建议从这些入口开始使用：

- `HwInline.atomic`
- `HwInline.thread`
- `HwInline.stateless`
- `HwInline.bindings`
- `SysCall.Inline(HwInline)`
- `SysCall.Call(HwInline, returnTo)`
- `SysCall.Call(HwInline)`
- `SysCall.Return()`

## 重要 API 清单

### `HwInline.atomic`

使用时机：

- 必须在 thread step 内执行的一段控制逻辑

关键边界：

- 更像“当前上下文内原子控制段”
- 不创建独立执行宿主

### `HwInline.thread`

使用时机：

- 需要 thread 级控制语义（`Step` / `jump` / `waitCondition` 等）的可复用段

关键边界：

- 被 `SysCall.Inline(...)` 使用时可以作为组合/内联控制段
- 被 `SysCall.Call(...)` 使用时需要显式 `SysCall.Return()`

### `HwInline.stateless`

使用时机：

- 只依赖 agent，不依赖 thread runtime 的轻量组合段

### `SysCall.Return()`

作用：

- callable segment 的正式退出原语

关键边界：

- 在 `SysCall.Call(...)` 上下文中，`Return()` 会回到 continuation
- 在 root 下会落到内核退出语义

## Usage examples

### 示例 1：inline helper

```scala
val zeroCounter = HwInline.atomic("ZeroCounter") { _ =>
  counter := 0.U
}

worker.entry {
  worker.Step("Init") {
    SysCall.Inline(zeroCounter)
  }
  worker.Step("Done") {
    SysCall.Return()
  }
}
```

### 示例 2：callable segment

```scala
val callable = HwInline.thread("Callable") { t =>
  t.Step("Body") {
    counter := counter + 1.U
  }
  SysCall.Return()
}

caller.entry {
  caller.Step("CallBody") {
    SysCall.Call(callable, "AfterCall")
  }
  caller.Step("AfterCall") {
    done := true.B
    SysCall.Return()
  }
}
```

## 常见误区

### `HwInline` 不是“零成本软件函数”

它是硬件控制段。结构代价、时序语义和状态边界都需要显式设计。

### callable segment 不等于异步 service

`SysCall.Call(...)` 语义是 call/return。
如果要做异步生命周期解耦（request/pending/commit），应在 process/service 层显式实现。

## 与其他模块的关系

- thread 控制流组织见 [thread.md](/Users/nullstarfish/HwOS_personal/docs/api/thread.md)
- `Call/Return/start/kill` 系统语义见 [system.md](/Users/nullstarfish/HwOS_personal/docs/api/system.md)
