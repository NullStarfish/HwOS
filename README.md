# HwOS: A Thread-Level RTL Abstraction Framework

[![Scala Version](https://img.shields.io/badge/Scala-2.13+-red.svg)](https://www.scala-lang.org/)
[![Chisel Version](https://img.shields.io/badge/Chisel-3.6+-blue.svg)](https://www.chisel-lang.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-green.svg)](https://opensource.org/licenses/Apache-2.0)

**HwOS** is a novel hardware construction framework built on top of Chisel/Scala. It introduces Operating System (OS) abstractions into the Register-Transfer Level (RTL) domain, addressing the exponential growth of control logic complexity in modern heterogeneous computing and out-of-order processors. 

HwOS operates on a simple but radical philosophy: **"Hardware is an Operating System, Everything is a Thread."**

By replacing fragmented Finite State Machines (FSMs) and explicit `Valid-Ready` handshakes with a **Thread-Level RTL (TL-RTL)** paradigm, HwOS enables designers to write complex, concurrent hardware using an imperative, software-like mindset without sacrificing physical area or timing performance.

## ✨ Core Features

* **Thread-Level RTL (TL-RTL):** Write sequential hardware logic using `HardwareThread` and `Step`. The compiler automatically flattens these into optimal FSMs with assigned Program Counters (PCs).
* **Zero-Bubble `hijack`:** A compiler-level metaprogramming directive that inlines the closure of the next `Step` into the current cycle, enabling 0-cycle, zero-bubble state transitions without writing complex manual combinational bypasses.
* **Functional Hardware (`HwFunction`):** Decouples the execution container (the "CPU") from the logic payload (the "Program"). Hardware logic can be passed, nested, and invoked via `SysCall`.
* **Ownership & Context-Aware Safety:** Hardware resources (wires/registers) are strictly managed via an Access Control List (ACL). The native `<==` operator physically gates assignments based on the thread's active state, preventing illegal out-of-context logic generation (`ContextScope`).
* **OS Reaper & `HwLease`:** A system-level Garbage Collector for RTL. If a thread is aborted or killed, the OS Reaper uses privileged assignments (`<==!`) to forcibly reclaim stateful resources (like Mutexes) to prevent system deadlocks.
* **Hardware Stdlib:** Comes with a built-in, highly optimized concurrency library including `Mutex`, `Semaphore`, `WaitGroup` (using concurrent adder-trees), and `Scoreboard`.
* **Native Semantic Observability (HwOSgdb):** A DPI-C and ncurses-based TUI debugger. It visualizes the dual-track CallStack (macro-temporal and micro-combinational) and supports time-travel debugging.

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

Here is how you define a hardware process with thread-level concurrency and safe resource ownership in HwOS:

```scala
class CounterProcess(localName: String)(implicit kernel: Kernel) extends HwProcess(localName) {
  // 1. Declare resources and claim ownership
  val counter  = this.own(RegInit(0.U(32.W)))
  
  // 2. Spawn a hardware thread
  val mainThread = createThread("MainThread")

  override def entry(): Unit = {
    // 3. Grant write permissions to the thread
    this.grant(counter, mainThread)

    // 4. Define sequential logic
    mainThread.entry {
      mainThread.Step("Init") {
        counter <== 0.U // Safe assignment gated by thread's isActive
      }
      mainThread.Step("CountUp") {
        counter <== counter + 1.U
        // Hardware-level blocking: PC stalls here until condition is met
        mainThread.waitAndAct(counter === 10.U) {
          mainThread.Next.hijack() // Zero-Bubble transition to the next step
        }
      }
      mainThread.Step("Finish") {
        mainThread.exit() // Terminate thread lifecycle
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
* `kernel/`: The core framework (`HwProcess`, `HardwareThread`, `ContextScope`, `HwOwnerShip`, `SysCall`).
* `stdlib/`: Hardware synchronization primitives (`Mutex`, `WaitGroup`, `Scoreboard`).
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

This project is licensed under the Apache License 2.0 - see the [LICENSE](https://www.google.com/search?q=LICENSE) file for details.

