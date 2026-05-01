# HwOS: Transaction-Oriented RTL Construction

[![Scala Version](https://img.shields.io/badge/Scala-2.13+-red.svg)](https://www.scala-lang.org/)
[![Chisel Version](https://img.shields.io/badge/Chisel-7.0-blue.svg)](https://www.chisel-lang.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://opensource.org/licenses/Apache-2.0)

HwOS is an experimental hardware construction framework built on Chisel/Scala.
Its current focus is **transaction-oriented RTL construction with timing-semantic APIs**:
hardware operations such as channel transfers, AXI reads, register-file reservation, and
writeback are packaged as APIs whose meaning includes timing behavior such as blocking,
sequencing, and commit points.

The goal is not to hide RTL or replace ordinary signals and state machines. Instead, HwOS
tries to move repeated protocol choreography and ad hoc FSM glue behind reusable API
boundaries. A call site should express the transaction it wants; the implementation should
own the handshake timing, arbitration, forwarding, or publication policy needed to realize it.

This repository is an active research artifact. It contains the core control model, reusable
transaction components, standard-library synchronization primitives, register-file variants,
and CPU-oriented prototypes that exercise the API style beyond toy examples.

## Core Ideas

* **Transaction as frontend unit:** a transaction is a hardware operation with a control
  boundary, such as `push(packet)`, `axi_read(addr)`, `Reserve(rd)`, or
  `WritebackAndClear(rd, data)`.
* **Timing-semantic APIs:** APIs can carry timing behavior. A transaction may block,
  wait for a protocol condition, return a value, or commit side effects at a control boundary.
* **Thread-hosted control:** `HardwareThread` executes a program written with `Step`,
  `waitCondition`, `jump`, `Call`, and `Return`.
* **Reusable control segments:** `HwInline` packages callable or inline transaction logic
  that can be reused across call sites.
* **Backend policy behind stable APIs:** arbitration, forwarding, ordered publication,
  and commit discipline can live inside a component implementation rather than leaking
  into every caller.

## What Is Included

### Kernel

`src/main/scala/HwOS/kernel/`

The kernel provides the control construction substrate:

* `HwProcess` as the service/component container
* `HardwareThread` and `Step` as the control-flow execution model
* `HwInline` for transaction segments
* `SysCall.Call`, `SysCall.Return`, and thread lifecycle operations
* `KernelAddressSpace` metadata for code/state/export/dependency tables
* structured-control helpers and thread debug validation

### Library Components

`src/main/scala/HwOS/lib/`

Reusable examples of timing-semantic APIs:

* `lib/axi4`: AXI read transaction API
* `lib/regfile`: base, semaphore-backed, scoreboard-backed, and age-ordered scoreboard
  register files

### Standard Library

`src/main/scala/HwOS/stdlib/`

Synchronization and ordering components used by the examples:

* `Semaphore`
* `Mutex`
* `WaitGroup`
* `Scoreboard`
* `OrderedWindow`

### CPU-Oriented Prototypes

`src/main/scala/HwOS/prototype/cpu/`

Small CPU/service experiments that exercise transaction APIs across interacting components:

* server-injected decode
* frontend/backend process composition
* arithmetic, load, path, and commit services
* scoreboard and age-ordered regfile integration
* module-wrapper examples for wrapping service-style APIs

These prototypes are not presented as performance-tuned CPU cores. They are stress cases for
API boundaries, backpressure, arbitration, and backend policy factoring.

### Tests

`src/test/scala/HwOS/`

The test suite covers kernel control behavior, symbolic export/declare behavior, standard
library primitives, AXI APIs, register files, quick-start examples, and CPU prototypes.

## Quick Start

### Prerequisites

* JDK 17+
* sbt
* Verilator, if you want to use the generated RTL/debugger flow
* `ncurses`, if you want to build the HwOSgdb TUI debugger

### Run the Quick Start Test

```bash
sbt "testOnly HwOS.quick_start.TopModuleSpec"
```

### Generate the Quick Start SystemVerilog

```bash
sbt "runMain HwOS.quick_start.QuickStart"
```

Generated SystemVerilog and symbol metadata are emitted under `generated/`.

### Run Focused Component Tests

```bash
sbt "testOnly HwOS.lib.axi4.Axi4ReadApiSpec"
sbt "testOnly HwOS.lib.regfile.AgeOrderedRegfileSpec"
sbt "testOnly HwOS.stdlib.MutexSpec"
sbt "testOnly HwOS.prototype.cpu.ServerInjectedFreeFlowSpec"
```

## Minimal Example

This sketch shows the basic control style. A thread advances through named steps, can block
with `waitCondition`, and can return through the system call layer.

```scala
class CounterProcess(localName: String)(implicit kernel: Kernel)
    extends HwProcess(localName) {
  val counter = RegInit(0.U(32.W))
  val main = createThread("Main")

  override def entry(): Unit = {
    main.entry {
      main.Step("Init") {
        counter := 0.U
      }

      main.Step("Count") {
        counter := counter + 1.U
        main.waitCondition(counter === 10.U)
      }

      main.Step("Finish") {
        SysCall.Return()
      }
    }
  }
}
```

## Documentation

The docs are still catching up with the current transaction-oriented framing, but they remain
useful for understanding the implementation:

* [docs/architecture.md](docs/architecture.md): current implementation architecture
* [docs/concepts.md](docs/concepts.md): core concept model
* [docs/philosophy.md](docs/philosophy.md): design philosophy
* [docs/vision.md](docs/vision.md): broader research framing
* [docs/glossary.md](docs/glossary.md): terminology
* [docs/api/README.md](docs/api/README.md): kernel API guide

## HwOSgdb

`HwOSgdb/` contains an experimental DPI-C and ncurses-based debugger. It is intended to make
thread-level execution and call-stack behavior easier to inspect during RTL simulation.

Typical flow:

```bash
sbt "runMain HwOS.kernel.examples.Example"
cd HwOSgdb
make
./hwosgdb
```



## License

This project is licensed under the Apache License 2.0.
