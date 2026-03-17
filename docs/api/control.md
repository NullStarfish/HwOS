# Control API 指南

## 模块定位

这一组 API 负责解决：

- 在 thread 主线之上提供更高层的控制构造器

它不负责：

- thread runtime 本体
- `StepRef` lowering 内核
- process / context / system 调度

主要源码：

- [control/StructuredControl.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/control/StructuredControl.scala)

## 文件与正式入口

当前正式入口主要是 `StructuredControl`：

- `If(...)`
- `While(...)`
- `ForRange(...)`
- `LoopControl.Break()`
- `LoopControl.Continue()`

它建立在 thread 正式 API 上：

- `Step`
- `stepRef`
- `jump`
- `hijack`
- `SysCall.Call(...)`

所以你应该把它理解成：

- 高层控制构造器
- 不是 thread 真身

## 重要 API 清单

### `StructuredControl.If(...)`

作用：

- 构造一组条件 branch

特点：

- then / elseif / else body 都是 `HwInline[Unit]`
- 最终被 lower 成 thread steps

### `StructuredControl.While(...)`

作用：

- 构造 while 循环

特点：

- body 通过 `LoopControl` 拿到 `Break` / `Continue`

### `StructuredControl.ForRange(...)`

作用：

- 构造一个带显式迭代 index 的 for-range 循环

特点：

- 会生成 loop-local index `Reg`
- body 仍然是 inline 片段

### `LoopControl.Break() / Continue()`

作用：

- 在结构化循环里表达 break / continue

关键边界：

- 它们本质上还是基于底层 `jump` 实现

## Usage examples

### 示例 1：`If`

```scala
StructuredControl.If(worker, "CheckBusy", busyReg) {
  HwInline.thread("WhenBusy") { _ =>
    status <== 1.U
  }
}.Else {
  HwInline.thread("WhenIdle") { _ =>
    status <== 0.U
  }
}
```

### 示例 2：`While`

```scala
StructuredControl.While(worker, "Drain", queueValid) { loop =>
  HwInline.thread("DrainBody") { _ =>
    when(stopNow) {
      loop.Break()
    }
  }
}
```

### 示例 3：`ForRange`

```scala
StructuredControl.ForRange(worker, "InitSlots", start = 0, endExclusive = 4) { (idx, loop) =>
  HwInline.thread("InitOne") { _ =>
    slots(idx) <== 0.U
    when(skipRest) {
      loop.Continue()
    }
  }
}
```

## 常见误区

### `StructuredControl` 不是 thread 本体

它只是建立在 thread 主线之上的高层构造器。  
thread 真正的正式内核仍然是 `ThreadCore` 这一条主线。

### 高层控制不等于脱离 `Step`

`StructuredControl` 最终仍会 lower 到 thread steps。  
它没有发明第二套 runtime。

### 不是所有场景都适合高层 DSL

如果控制流本身已经很清晰，直接写：

- `Step`
- `jump`
- `hijack`
- `waitCondition`

往往更直接、更好读。

## 与其他模块的关系

- 真正的 thread 正式接口，看 [thread.md](/Users/nullstarfish/HwOS_personal/docs/api/thread.md)
- inline body 怎么定义，看 [function.md](/Users/nullstarfish/HwOS_personal/docs/api/function.md)
- 实现内核怎么 lower，不在这篇解释，去看 [architecture.md](/Users/nullstarfish/HwOS_personal/docs/architecture.md)

