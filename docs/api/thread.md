# Thread API 指南

## 模块定位

这一组 API 负责解决：

- 如何定义一个正式 thread
- 如何用 `Step` 组织控制流
- 如何通过 `StepRef` 做 `jump` / `hijack`
- 如何观察和复位 thread runtime

它不负责：

- process 资源导出策略
- function 调用协议本身
- 地址表导出

主要源码：

- [thread/HardwareAgent.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/thread/HardwareAgent.scala)
- [thread/ThreadControlApi.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/thread/ThreadControlApi.scala)
- [thread/ThreadRuntimeApi.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/thread/ThreadRuntimeApi.scala)
- [thread/ThreadCore.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/thread/ThreadCore.scala)
- [thread/ThreadDef.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/thread/ThreadDef.scala)

## 模块定位补充

当前主线里：

- `thread` 是 **code-space 的正式执行宿主**
- 它更像 runtime engine / CPU host
- 真正 portable / combinable 的控制代码通常是运行在 thread 上的 `HwInline`

所以 thread 很重要，但它不是当前最核心的代码复用单元。

## 文件与正式入口

当前建议直接使用这些正式入口：

- `HardwareThread`
- `ThreadDef`
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
- `reset()`

内部实现背景：

- `thread/step/ThreadIR.scala`
- `thread/step/ThreadLayout.scala`
- `thread/step/ThreadRuntimeLogic.scala`

这些是 lowering 支撑，不是优先给使用者直接依赖的 API 面。

## 重要 API 清单

### `ThreadDef`

作用：

- 定义一个 definition-first 的 thread code object

使用时机：

- 你想把 thread code 独立成单文件并由 process 安装时

关键边界：

- `ThreadDef` 负责 code definition
- `HwProcess.install(...)` 负责实例化和装配

### `entry { ... }`

作用：

- 定义 thread 的主控制流体

使用时机：

- 每个 thread 构建时

关键边界：

- 同一个 thread 只能 `entry` 一次
- 当前 `ThreadDef` 的默认实现仍然通过 `entry { ... }` 来定义 thread body

### `Step(name) { ... }`

作用：

- 定义一个正式控制点

关键边界：

- `Step` 是编译期控制点
- 运行期真正移动的是 cursor

### `Next`

作用：

- 表示“当前 step 的后继 step”的编译期引用

关键边界：

- 它是 `StepRef`
- 不是 runtime pointer

### `stepRef(name)`

作用：

- 生成一个命名 `StepRef`

### `jump(ref)`

作用：

- 在运行期把 thread 的控制流跳到目标 standalone step

### `hijack(ref)`

作用：

- 在编译期把目标 step body splice 到当前位置

关键边界：

- 它不是 runtime jump
- 目标 step 可能因此不再拥有独立 standalone slot

### `waitCondition(cond)`

作用：

- 当条件不满足时让当前入口 step stall

### `active / done / pc`

作用：

- 暴露 thread 的 runtime 观察接口

边界：

- `active` / `done` 来自 `RuntimeContext.stateReg`
- `pc` 对应当前 `cursor`

### `reset()`

作用：

- 复位 thread runtime

当前语义：

- `cursor := entry`
- `stateReg := Idle`

边界：

- 这是 thread 自己的基础原语
- 普通 `kill(thread)` 默认最终落到 `reset()`
- `OSReaper` 的即时截断和强制收尾是额外系统神力，不是 `reset()` 本体

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

### 示例 2：definition-first thread

```scala
object WorkerDef extends ThreadDef {
  override def define(worker: HardwareThread): Unit = {
    worker.entry {
      worker.Step("Body") {}
      SysCall.Call(SysCall.Return())
    }
  }
}
```

### 示例 3：命名跳转

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

### 示例 4：`hijack(Next)`

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

## 常见误区

### `hijack` 不是 runtime jump

它是编译期 splice。  
如果你想在运行期改控制流，应使用 `jump`。

### `thread` 不是主要的代码复用单元

当前 thread 更像执行宿主。  
真正 portable / combinable 的控制代码更多体现在：

- `HwInline`

### `Process` 不是 thread 的“父线程”

它更像 environment / service / physical component。  
对 thread 来说，process 内部资源更像它所运行的外设环境。

## 与其他模块的关系

- process 如何提供环境和安装 thread，看 [process-context.md](/Users/nullstarfish/HwOS_personal/docs/api/process-context.md)
- `HwInline` 作为控制代码段的用法，看 [function.md](/Users/nullstarfish/HwOS_personal/docs/api/function.md)
- `start/kill/Return` 的系统语义，看 [system.md](/Users/nullstarfish/HwOS_personal/docs/api/system.md)
