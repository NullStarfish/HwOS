# Thread API 指南

## 模块定位

这一组 API 负责解决：

- 如何定义一个正式 thread
- 如何用 `Step` 组织控制流
- 如何通过 `StepRef` 做 `jump` / `hijack`
- 如何观察 thread runtime 状态

它不负责：

- process 层级与 ownership 策略
- function 调用协议本身
- 地址表导出

主要源码：

- [thread/HardwareAgent.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/thread/HardwareAgent.scala)
- [thread/ThreadControlApi.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/thread/ThreadControlApi.scala)
- [thread/ThreadRuntimeApi.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/thread/ThreadRuntimeApi.scala)
- [thread/ThreadCore.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/thread/ThreadCore.scala)

## 文件与正式入口

当前建议直接使用这些正式入口：

- `HardwareThread`
- `entry { ... }`
- `Step(name) { ... }`
- `Next`
- `stepRef(name)`
- `jump(ref)`
- `hijack(ref)`
- `waitCondition(cond)`
- `waitAndAct(cond) { ... }`
- `Global { ... }`
- `active`
- `done`
- `pc`

内部实现背景：

- `thread/step/ThreadIR.scala`
- `thread/step/ThreadLayout.scala`
- `thread/step/ThreadRuntimeLogic.scala`

这些是 lowering 支撑，不是优先给使用者直接依赖的 API 面。

## 重要 API 清单

### `entry { ... }`

作用：

- 定义 thread 的主控制流体

使用时机：

- 每个 thread 构建时

关键边界：

- 同一个 thread 只能 `entry` 一次

### `Step(name) { ... }`

作用：

- 定义一个正式控制点

使用时机：

- 需要显式切分时序/控制阶段时

关键边界：

- `Step` 是编译期控制点
- 运行期真正移动的是 cursor

### `Next`

作用：

- 表示“当前 step 的后继 step”的编译期引用

使用时机：

- 要做 `hijack(thread.Next)` 这类当前后继 splice 时

关键边界：

- 它是 `StepRef`
- 不是 runtime pointer

### `stepRef(name)`

作用：

- 生成一个命名 `StepRef`

使用时机：

- 要显式引用某个 step 时

### `jump(ref)`

作用：

- 在运行期把 thread 的控制流跳到目标 standalone step

关键边界：

- 正式主语义是 `jump(stepRef(...))`
- `jump("name")` 只是过渡包装

### `hijack(ref)`

作用：

- 在编译期把目标 step body splice 到当前位置

关键边界：

- 它不是 runtime jump
- 目标 step 可能因此不再拥有独立 standalone slot

### `waitCondition(cond)`

作用：

- 当条件不满足时让当前入口 step stall

关键边界：

- 它 stall 回入口 step
- 不会跳进某个被 splice 的内部位置

### `waitAndAct(cond) { ... }`

作用：

- 条件满足时执行 block，否则等价于 wait

### `Global { ... }`

作用：

- 注册 thread 级全局逻辑块

### `active / done / pc`

作用：

- 暴露 thread 的 runtime 观察接口

边界：

- `active` / `done` 来自 `RuntimeContext.stateReg`
- `pc` 对应当前 `cursor`
- `start` / `kill` 不是 thread 自己对外的高层 API，而是通过 `SysCall`

## Usage examples

### 示例 1：最小 thread

```scala
val worker = createThread("Worker")

worker.entry {
  worker.Step("Init") {
    // do something
  }
  worker.Step("Finish") {
    SysCall.Call(SysCall.Return())
  }
}
```

### 示例 2：命名跳转

```scala
worker.entry {
  worker.Step("A") {
    worker.jump(worker.stepRef("C"))
  }
  worker.Step("B") {}
  worker.Step("C") {
    SysCall.Call(SysCall.Return())
  }
}
```

`jump` 目标必须是一个真正保留下来的 standalone step。

### 示例 3：`hijack(Next)`

```scala
worker.entry {
  worker.Step("Enter") {
    worker.hijack(worker.Next)
  }
  worker.Step("Body") {
    // Body 会在编译期被 splice 到 Enter
  }
  worker.Step("End") {
    SysCall.Call(SysCall.Return())
  }
}
```

### 示例 4：`waitCondition`

```scala
worker.entry {
  worker.Step("WaitReady") {
    worker.waitCondition(ioReady)
  }
  worker.Step("Run") {
    SysCall.Call(SysCall.Return())
  }
}
```

这里如果 `ioReady` 不满足，thread 会停在 `WaitReady` 入口 step。

## 常见误区

### `hijack` 不是 runtime jump

它是编译期 splice。  
如果你想在运行期改控制流，应使用 `jump`。

### `Next.hijack()` 不再是正式接口

当前正式写法是：

```scala
thread.hijack(thread.Next)
```

### `active/done` 不是 lease 状态

当前 thread runtime 首先由 `RuntimeContext(cursor + stateReg + binding)` 驱动。  
lease 主要用于资源/调用期语义，不是 thread 生命周期本体。

## 与其他模块的关系

- thread 创建与 ownership，看 [process-context.md](/Users/nullstarfish/HwOS_personal/docs/api/process-context.md)
- inline / function 调用，看 [function.md](/Users/nullstarfish/HwOS_personal/docs/api/function.md)
- `start/kill/Return`，看 [system.md](/Users/nullstarfish/HwOS_personal/docs/api/system.md)

