# Process / Context API 指南

## 模块定位

这一组 API 负责解决：

- 如何组织 process 层级
- 如何创建和安装 thread
- 如何导出资源给其他对象使用
- 如何在同一 process 内组织本地状态和本地逻辑

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
- `install(threadDef, name)`
- `spawn(...)`
- `export(...)`
- `declare(...)`

当前不再推荐依赖旧资源组织模型；`own / grant / grantLifecycle / GrantAbi` 已退出主线。

## 重要 API 清单

### `HwProcess`

作用：

- 定义 process 层级和命名空间
- 提供本地状态与本地逻辑环境
- 创建 thread / logic
- 作为 root build 入口

关键边界：

- 当前 `HwProcess` 更接近 service / environment / physical component
- 它不是 code-space 一等对象
- 真正跑控制流的是它承载的 `HardwareThread`

### `createThread(name)`

作用：

- 在当前 process 下创建一个正式 thread

使用时机：

- 你要一个本地 thread，并且线程代码就写在当前 process 里时

关键边界：

- 这是 instance-first 入口
- 如果想让 thread code 独立成单文件，优先使用 `install(threadDef, name)`

### `install(threadDef, name)`

作用：

- 把 definition-first 的 thread 定义安装到当前 process

使用时机：

- 你想把 thread code 独立成可复用定义对象时

关键边界：

- process 负责环境和装配
- `ThreadDef` 负责 code definition

### `spawn(child)`

作用：

- 创建子 process
- 调用子 process `build()`

使用时机：

- 你要构造父子 process 层级时

关键边界：

- `spawn` 继续是 process 组合/实例化动作
- 它不再承担旧 `own/grant` 的资源 ACL 传播

### `export(symbol, signal, caps)`

作用：

- 把一个资源注册到 exported memory table

使用时机：

- 该资源需要被跨边界复用、声明依赖、或正式导出时

关键边界：

- v0 下它只承担两件事：
  - 可见性
  - 依赖记录
- 它不追踪所有本地 Chisel 值

### `declare(symbol, caps)`

作用：

- 按符号获取一个虚拟句柄

使用时机：

- thread/logic 需要通过正式接口使用外部资源时

关键边界：

- 同一 process 内的局部实现不强制使用 `declare`
- `declare` 主要服务于跨边界 provider/consumer 解耦

## Usage examples

### 示例 1：本地模式

```scala
class CounterProc(name: String)(implicit kernel: Kernel) extends HwProcess(name) {
  val counter = RegInit(0.U(32.W))
  val worker = createThread("Worker")

  override def entry(): Unit = {
    worker.entry {
      worker.Step("Init") {
        counter := 0.U
      }
      worker.Step("Done") {
        SysCall.Call(SysCall.Return())
      }
    }
  }
}
```

这里：

- `counter` 只是本地实现细节
- 不进入 exported memory table
- 不记录 dependency

### 示例 2：symbolic 导出

```scala
class Provider(name: String)(implicit kernel: Kernel) extends HwProcess(name) {
  val counter = RegInit(0.U(32.W))

  override def entry(): Unit = {
    export("demo.counter.value", counter, ExportCapability.ReadWrite)
  }
}
```

### 示例 3：definition-first thread 安装

```scala
object WorkerDef extends ThreadDef {
  override def define(t: HardwareThread): Unit = {
    t.entry {
      t.Step("Done") {
        SysCall.Call(SysCall.Return())
      }
    }
  }
}

class Parent(name: String)(implicit kernel: Kernel) extends HwProcess(name) {
  val worker = install(WorkerDef, "Worker")
}
```

## 常见误区

### `Process` 不是软件意义上的 OS process

当前它更接近：

- service
- environment
- physical component

### `export / declare` 不是默认开发方式

v0 下，只有跨边界复用和正式接口才推荐 symbolic。  
同一 process 内的局部 thread/logic 继续允许直接 Scala/Chisel 交互。

### `spawn` 不再负责旧 ACL 传播

当前 `spawn` 只是组合/实例化过程，不再承担 `own/grant` 自动传播。

## 与其他模块的关系

- thread 如何组织控制流，看 [thread.md](/Users/nullstarfish/HwOS_personal/docs/api/thread.md)
- `HwInline` 如何作为控制代码段使用，看 [function.md](/Users/nullstarfish/HwOS_personal/docs/api/function.md)
- 地址表导出与 `exported/dependency` 两张表，看 [system.md](/Users/nullstarfish/HwOS_personal/docs/api/system.md)
