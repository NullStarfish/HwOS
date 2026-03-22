# HwOS Kernel API 指南

这组文档面向接手开发的工程师，解释 **当前 `kernel` 主线 API 该怎么用**。

它和其他文档的分工是：

- [architecture.md](/Users/nullstarfish/HwOS_personal/docs/architecture.md)：讲当前实现怎么组织
- [philosophy.md](/Users/nullstarfish/HwOS_personal/docs/philosophy.md)：讲为什么这样设计
- [concepts.md](/Users/nullstarfish/HwOS_personal/docs/concepts.md)：讲核心概念和边界
- [glossary.md](/Users/nullstarfish/HwOS_personal/docs/glossary.md)：讲术语
- `docs/api/*`：讲开发者实际如何使用这些 API

当前这组 API 文档只覆盖 `kernel` 主线，不覆盖 `stdlib`。

## 推荐阅读顺序

1. [process-context.md](/Users/nullstarfish/HwOS_personal/docs/api/process-context.md)
2. [thread.md](/Users/nullstarfish/HwOS_personal/docs/api/thread.md)
3. [function.md](/Users/nullstarfish/HwOS_personal/docs/api/function.md)
4. [system.md](/Users/nullstarfish/HwOS_personal/docs/api/system.md)
5. [control.md](/Users/nullstarfish/HwOS_personal/docs/api/control.md)

## 模块索引

### Process / Context

- [process-context.md](/Users/nullstarfish/HwOS_personal/docs/api/process-context.md)
- 对应源码：
  - [kernel/package.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/package.scala)
  - [process/HwProcess.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/process/HwProcess.scala)
  - [context/HwContextSystem.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/context/HwContextSystem.scala)

### Thread

- [thread.md](/Users/nullstarfish/HwOS_personal/docs/api/thread.md)
- 对应源码：
  - [thread/HardwareAgent.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/thread/HardwareAgent.scala)
  - [thread/ThreadControlApi.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/thread/ThreadControlApi.scala)
  - [thread/ThreadRuntimeApi.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/thread/ThreadRuntimeApi.scala)
  - [thread/ThreadCore.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/thread/ThreadCore.scala)

### Function

- [function.md](/Users/nullstarfish/HwOS_personal/docs/api/function.md)
- 对应源码：
  - [function/HwInline.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/function/HwInline.scala)

### System

- [system.md](/Users/nullstarfish/HwOS_personal/docs/api/system.md)
- 对应源码：
  - [system/SysCall.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/system/SysCall.scala)
  - [system/KernelAddressSpace.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/system/KernelAddressSpace.scala)

### Control

- [control.md](/Users/nullstarfish/HwOS_personal/docs/api/control.md)
- 对应源码：
  - [control/StructuredControl.scala](/Users/nullstarfish/HwOS_personal/src/main/scala/HwOS/kernel/control/StructuredControl.scala)

## 使用约定

- 这组文档里的示例优先展示当前正式 API，而不是历史兼容写法
- `StepRef` 是正式主语义，字符串跳转只算过渡包装
- `Return` 是正式用户结束语义，`exit` 不是公开 API
- thread 只有统一主线，不再有多个 backend 的分流说明
