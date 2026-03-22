# HwOS: A Thread-Level RTL Abstraction Framework

[![Scala Version](https://img.shields.io/badge/Scala-2.13+-red.svg)](https://www.scala-lang.org/)
[![Chisel Version](https://img.shields.io/badge/Chisel-3.6+-blue.svg)](https://www.chisel-lang.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://opensource.org/licenses/Apache-2.0)

**HwOS** is a hardware construction framework built on top of Chisel/Scala. Its current main line is **control-flow-first**: it treats thread-hosted control flow and reusable control segments as first-class hardware design objects, rather than treating control as an afterthought attached to datapaths.

HwOS operates on a simple but radical philosophy: **"Hardware is an Operating System"**.

By replacing fragmented FSMs and ad-hoc control glue with a **thread-hosted control-flow model**, HwOS explores how hardware control can become portable, composable, and observable without hiding its structural cost.

For the current implementation architecture, see [docs/architecture.md](docs/architecture.md).  
For a paper-style statement of vision and core definitions, see [docs/vision.md](docs/vision.md).  
For the current philosophy and concept model, see [docs/philosophy.md](docs/philosophy.md), [docs/concepts.md](docs/concepts.md), and [docs/glossary.md](docs/glossary.md).  
For practical kernel API usage, see [docs/api/README.md](docs/api/README.md).  
`README.md` is the project overview; `docs/architecture.md` describes the current implementation; the philosophy/concept docs explain why the system is organized this way and what the core terms mean.

## ✨ Core Features

* **Thread-Level RTL (TL-RTL):** Write sequential hardware logic using `HardwareThread` and `Step`. The compiler automatically flattens these into optimal FSMs with assigned Program Counters (PCs).
* **Zero-Bubble `hijack`:** A compiler-level metaprogramming directive that inlines the closure of the next `Step` into the current cycle, enabling 0-cycle, zero-bubble state transitions without writing complex manual combinational bypasses.
* **Portable Control Segments:** `HwInline` is the current first-class control code segment API for callable/inline control transactions.
* **Thread as Execution Host:** `HardwareThread` is the unified runtime host for control flow, while control payload is carried by `HwInline` and process/service composition.
* **Lightweight Symbolic v0:** `export / declare` provide explicit cross-boundary visibility and dependency recording, while same-process local code can still use direct Scala/Chisel interaction.
* **Optional OSReaper Services:** system-level reclaim and forced cleanup exist as explicit extra power, not as the default semantics of every thread.
* **Hardware Stdlib:** Comes with a built-in, highly optimized concurrency library including `Semaphore` (with `initialCount = 1` as single-permit mutual exclusion), `WaitGroup` (using concurrent adder-trees), and `Scoreboard`.
* **Native Semantic Observability (HwOSgdb):** A DPI-C and ncurses-based TUI debugger. It visualizes the dual-track CallStack (macro-temporal and micro-combinational) and supports time-travel debugging.
* **Frontend/Backend Process Composition (Prototype CPU):** the current server-injected CPU prototype is organized as explicit `FrontendProcess` and `BackendProcess` containers, with decode dispatch decoupled from path execution and commit.

## 🚀 Quick Start

### Prerequisites
* JDK 17+ 
* sbt (Scala Build Tool)
* Verilator (for HwOSgdb and fast simulation)
* `ncurses` library (for HwOSgdb UI)

### 1. Run the Counter Example
The quickest way to see HwOS in action is to run the QuickStart counter process.

```bash
# Run the ScalaTest to simulate the Counter Process
sbt "testOnly HwOS.quick_start.TopModuleSpec"

# Or generate the SystemVerilog files and the Symbol Table
sbt "runMain HwOS.quick_start.QuickStart"

```

The generated SystemVerilog and `hwos.symbols` file will be located in the `generated/` directory.

### 2. A Glimpse of TL-RTL Code

Here is how you define a hardware process with a local thread in current HwOS:

```scala
class CounterProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  // 1. Local process state
  val counter  = RegInit(0.U(32.W))
  
  // 2. Spawn a hardware thread
  val mainThread = createThread("MainThread")

  override def entry(): Unit = {
    // 3. Define sequential logic
    mainThread.entry {
      mainThread.Step("Init") {
        counter := 0.U
      }
      mainThread.Step("CountUp") {
        counter := counter + 1.U
        mainThread.waitAndAct(counter === 10.U) {
          mainThread.hijack(mainThread.Next)
        }
      }
      mainThread.Step("Finish") {
        SysCall.Call(SysCall.Return())
      }
    }
  }
}

```

## 🛠️ HwOSgdb: Source-Level Hardware Debugger

Stop guessing states from raw VCD waveforms. **HwOSgdb** is a terminal-based UI debugger that leverages DPI-C to stream live telemetry from your RTL simulation.

### Building and Running HwOSgdb

```bash
# First, generate the Verilog and symbols via sbt
sbt "runMain HwOS.kernel.Example"

# Navigate to the debugger directory
cd HwOSgdb

# Build the C++ simulator and TUI
make

# Run the interactive debugger
./hwosgdb

```

### GDB Controls:

* `[SPACE]`: Step exactly one clock cycle.
* `[r]`: Run continuously.
* `[s]`: Step-over (Run until the focused thread changes state).
* `[TAB]`: Switch focus between Sidebar (Thread List) and Scope (Timeline).
* `[UP/DOWN]`: Scroll through the Time-Travel history buffer.

## 📂 Project Structure

* `src/main/scala/HwOS/`
* `kernel/`: The core framework (`HwProcess`, `HardwareThread`, `ThreadDef`, `HwInline`, `SysCall`).
* `stdlib/`: Hardware synchronization primitives (`Semaphore`, `WaitGroup`, `Scoreboard`).
* `lib/`: Standard components (e.g., `ScoreboardRegfile`).
* `quick_start/`: Hello World examples.

---

* `src/test/scala/HwOS/`: Comprehensive unit tests verifying deadlocks, lease reclaims, and RAW hazard stalling.
* `HwOSgdb/`: The C++ and `ncurses` based TUI debugger and Verilator simulation engine.

## 📄 Academic Citation

If you find HwOS useful in your research, please consider citing our Technical Report / Preprint:

```bibtex
@misc{chen2026hwos,
  author       = {Chen, Kaixin},
  title        = {HwOS: A Thread-Level RTL Abstraction for Composable and Observable Hardware Design},
  publisher    = {TechRxiv},
  year         = {2026},
  month        = {feb},
  howpublished = {Preprint},
  doi          = {10.36227/techrxiv.177155627.77438450/v1}, 
  url          = {https://doi.org/10.36227/techrxiv.177155627.77438450/v1}
}



@techreport{chen2026hwos1.1,
  title={HwOS 1.1 Technical Report: Ownership,
Functional Hardware, and Zero-Bubble
Concurrency},
  author={Chen, Kaixin},
  year={2026},
  doi={https://doi.org/10.5281/zenodo.18795624}
}

```

## 📜 License

This project is licensed under the Apache License 2.0
