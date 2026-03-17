# Process / Context API 指南

## 模块定位

这一组 API 负责解决：

- 如何组织 process 层级
- 如何创建 thread
- 如何声明资源 ownership
- 如何授权别人访问这些资源

它不负责：

- thread 控制流 lowering 细节
- function activation 调用协议
- code space / binding 细节

主要源码：

- [kernel/package.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/package.scala)
- [process/HwProcess.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/process/HwProcess.scala)
- [context/HwContextSystem.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/context/HwContextSystem.scala)

## 文件与正式入口

建议从下面这些入口开始使用：

- `HwProcess`
- `createThread(...)`
- `spawn(...)`
- `own(...)`
- `grant(...)`
- `grant(..., abi)`
- `grantLifecycle(...)`

不建议把下面这些当常规 public API 直接依赖：

- `ResourceManager`
- `ctx.kernelKillSignal`
- `ctx.activeLeases`

这些更偏内核/系统支撑。

## 重要 API 清单

### `HwProcess`

作用：

- 定义 process 层级和命名空间
- 创建 thread / logic
- 作为 root build 入口

使用时机：

- 你要组织一个硬件模块、子模块或子 process 时

关键边界：

- `HwProcess` 是结构容器，不是执行体
- 真正跑控制流的是它创建出来的 `HardwareThread`

### `createThread(name)`

作用：

- 在当前 process 下创建一个正式 thread

使用时机：

- 你需要一个有 `Step` / `jump` / `waitCondition` 的执行体时

关键边界：

- 当前只有统一 thread 主线，不需要再选 backend

### `spawn(child)`

作用：

- 创建子 process
- 调用子 process `build()`
- 对能安全推断 ABI 的 owned signals 做自动向上二次 `grant`

使用时机：

- 你要构造父子 process 层级时

关键边界：

- `Reg` 可以自动二次 `grant`
- `Wire` 不会自动猜 ABI；需要显式 `grant(..., abi)`

### `own(signal)`

作用：

- 声明该 signal 归当前 context/entity 所有
- 自动登记到 `state table`

使用时机：

- 当前 process / thread / activation 要拥有某个状态对象时

关键边界：

- `own` 只说明“这是我的”
- 不说明别人怎么访问它

### `grant(signal, target[, abi])`

作用：

- 授权另一个 target 访问这个 signal
- 同时登记 ABI metadata

使用时机：

- 资源要跨 context / process / thread 暴露时

关键边界：

- 对 `Reg` 可以使用默认 `RegisterWrite`
- 对 `Wire` 必须显式给 `GrantAbi.LevelDrivenWire` 或 `GrantAbi.PulseWire`

### `grantLifecycle(thread, target)`

作用：

- 授予 `target` 对某个 thread 的生命周期控制权

使用时机：

- 需要 `start` / `kill` 某个 thread 时

关键边界：

- lifecycle ACL 不是普通 resource ACL
- 它服务于 thread 的系统级控制，不等价于 `grant(signal, target)`

### `ctx.kernelKillSignal`

作用：

- context 级系统切断信号

关键边界：

- 它不是 thread 专用 kill 信号
- 对 thread 而言，OSReaper 默认会通过已注册的 runtime lease 顺带接管其 runtime

## Usage examples

### 示例 1：最小 `HwProcess` + `own` + `grant`

```scala
class CounterProc(name: String)(implicit kernel: Kernel) extends HwProcess(name) {
  val counter = own(RegInit(0.U(32.W)))
  val worker = createThread("Worker")

  override def entry(): Unit = {
    grant(counter, worker)

    worker.entry {
      worker.Step("Init") {
        counter <== 0.U
      }
      worker.Step("Done") {
        SysCall.Call(SysCall.Return())
      }
    }
  }
}
```

这里：

- `counter` 归当前 process 所有
- `worker` 想写它，必须显式 `grant`

### 示例 2：显式 ABI 的 `Wire` 授权

```scala
class IoProc(name: String)(implicit kernel: Kernel) extends HwProcess(name) {
  val fire = own(Wire(Bool()))
  val worker = createThread("Worker")

  override def entry(): Unit = {
    grant(fire, worker, GrantAbi.PulseWire)
  }
}
```

这里不能写成默认 `grant(fire, worker)`，因为 `Wire` 的 ABI 不能自动猜。

### 示例 3：父子 process

```scala
class Parent(name: String)(implicit kernel: Kernel) extends HwProcess(name) {
  override def entry(): Unit = {
    spawn(new Child("child"))
  }
}
```

`spawn` 会建立父子层级，并自动 build 子 process。

## 常见误区

### `own` 不等于 `grant`

`own` 只说明归属。  
如果另一个 context 要合法写这个资源，仍然必须 `grant`。

### `grant` 不等于 runtime bus

当前 `grant` 挂的是编译期 ABI metadata，不会自动生成一套运行时通信协议。

### lifecycle ACL 不等于普通 ACL

能写某个 signal，不等于能 `kill` 或 `start` 某个 thread。  
lifecycle 控制权走的是 thread 自己的系统级权限路径。

### `kernelKillSignal` 不等于 thread kill

它是 context 级 cut-off。  
thread kill 现在走 thread 自己的 runtime kill 路径。

## 与其他模块的关系

- thread 如何使用这些资源，看 [thread.md](/Users/nullstarfish/HwOS_personal/docs/api/thread.md)
- `grant` 挂的 ABI 在系统侧如何导出，看 [system.md](/Users/nullstarfish/HwOS_personal/docs/api/system.md)
- 设计哲学和概念边界，看 [concepts.md](/Users/nullstarfish/HwOS_personal/docs/concepts.md)
