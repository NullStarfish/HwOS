
# HwOS: A Thread-Level RTL Abstraction for Composable and Observable Hardware Design

[![Scala](https://img.shields.io/badge/Language-Scala%2FChisel-red)](https://www.chisel-lang.org/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![DOI](https://zenodo.org/badge/1145070977.svg)](https://doi.org/10.5281/zenodo.18608985)



**HwOS** is a hardware construction framework based on [Chisel](https://www.chisel-lang.org/), designed to introduce operating system abstractions into the RTL design domain. Its core philosophy is: **"Hardware is an Operating System, Everything is a Thread."**

By defining **Thread-Level RTL (TL-RTL)**, HwOS encapsulates sequential logic and fragmented FSMs into `HardwareThread` objects with independent lifecycles. Paired with the **HwOSgdb** debugger, it enables developers to monitor and debug hardware states in real-time at the source-code semantic level.

---

## ✨ Features

* **Thread-Level RTL (TL-RTL) Abstraction**
    * Describes timing behavior in an imperative style using primitives like `Step`, `Wait`, and `Fork`, replacing traditional FSM state transitions.
    * Supports **Implicit Context Capture**, allowing child threads to access parent variables directly with zero wiring overhead.

* **Driver as Control Flow Proxy**
    * Physical resources (e.g., Register Files, Buses) are encapsulated within `Drivers`.
    * **Instruction Injection Mechanism**: Drivers automatically inject blocking logic (e.g., `waitCondition`) into the caller's thread to handle resource arbitration and pipeline stalls, eliminating manual Ready-Valid handshakes.

* **Service-Based Pipeline**
    * Shifts the paradigm from "instructions flowing through pipes" to "instructions as active threads seeking services."
    * Naturally supports out-of-order execution and dynamic dependency resolution without centralized issue queues.

* **Fractal Architecture**
    * Uses a recursive `Kernel` - `Process` - `Thread` structure, supporting modular reuse and hierarchical system management.

* **Native Observability & HwOSgdb**
    * Maps hardware simulation states back to source symbols using SystemVerilog DPI-C interfaces.
    * Provides a **terminal-based debugger (HwOSgdb)** supporting semantic breakpoints (e.g., `break Thread.pc==2`), single-stepping, time travel, and state visualization.


## 📂 Project Structure

```text
HwOS/
├── src/main/scala/HwOS/kernel/    # Core Kernel Implementation
│   ├── Kernel.scala               # Global Manager, ID Allocation & DPI Interface
│   ├── HwProcess.scala            # Logic Container, supports recursive spawn
│   ├── HardwareAgent.scala        # Definitions for HardwareThread & HardwareLogic
│   ├── ContextScope.scala         # Stack management for metaprogramming (ThreadCtx)
│   ├── PhysicalDriver.scala       # Base Driver class & DriverMeta
│   └── drivers/                   # Standard Driver Library
│       ├── ScoreboardRegfileDriver.scala  # RegFile Driver with Scoreboard support
│       ├── PipelinedScoreboardRegfileDriver.scala
│       └── ...
├── src/test/scala/HwOS/           # Tests and Examples
│   ├── example/                   # Comprehensive Examples (App objects)
│   │   ├── SimpleTopforDPIDebug.scala 
│   │   └── PipelineDebugGen.scala
│   ├── kernel/                    # Unit Tests (Fork, Abort, MultiCore, etc.)
│   └── synth/                     # Synthesis Benchmarks (Micro-benchmarks) 
├── synth_results/                 # Synthesis Output & Reports (Verilog/Log) 
├── HwOSgdb.cpp                    # Source code for the terminal debugger 
├── compile_HwOSgdb.sh             # Build script for the debugger 
└── build.sbt                      # Scala/Chisel build configuration

```

---

## 🛠️ Prerequisites

Before you begin, ensure you have the following installed:

1. **Scala & sbt**: For building the Chisel project. (JDK 8+ required)
2. **Verilator**: Open-source Verilog simulator, required for DPI interface generation and fast simulation.
3. **C++ Compiler (g++)**: For compiling HwOSgdb.
4. **ncurses Library**: UI dependency for HwOSgdb.
* Ubuntu/Debian: `sudo apt-get install libncurses5-dev libncursesw5-dev`



---

## 🚀 Usage

### 1. Defining a Hardware Thread

In HwOS, you don't write `always` blocks or FSM `case` statements. Instead, define timing logic inside an `entry` block:

```scala
// Example: A simple Accumulator Thread
val t = createThread("Accumulator")
t.entry {
  // Define Steps, which automatically map to FSM states
  t.Step("Load") {
    accReg := 10.U
  }
  t.Step("Compute") {
    accReg := accReg * 2.U
  }
  // Use a Driver for atomic operations; handshakes are handled automatically
  t.Step("Store") {
    driver.writeAtomic(addr, accReg) {
      t.exit() // Task complete, exit thread
    }
  }
}

```

### 2. Build and Run

To run the simulation with the HwOSgdb debugger, follow this specific order:

**Step 1: Generate Verilog and Simulation Artifacts**
First, run the Scala application to generate the SystemVerilog code and DPI interfaces.

```bash
# Run the example App object
sbt "test:runMain HwOS.example.PipelineDebugGen"

```

*This step generates the Verilog files and the `hwos.symbols` symbol table required by the debugger.*

**Step 2: Compile the Debugger**
Once the Verilog/C++ models are generated (or if you are compiling the standalone debugger), run the provided script:

```bash
./compile_HwOSgdb.sh

```

**Step 3: Start Debugging**
Run the compiled executable (HwOSgdb) to start the interactive session:

```bash
./obj_dir/VSimpleTop

```

**Debugger Shortcuts**:

* `[Space]`: Step one clock cycle.
* `[r]`: Run continuously.
* `[b]`: Set breakpoint (e.g., `Thread_0.pc == 3`).
* `[s]`: Step until the focused thread changes state.
* `[TAB]`: Switch focus between Sidebar (Thread List) and Main View (Waveform/State).

### 3. Synthesis & Benchmarking

To generate synthesis-ready Verilog for benchmarking (as seen in the `synth_results` folder):

```bash
sbt "test:runMain HwOS.synth.synthThread"

```

The output verilog files will be located in the `generated/` directory.




---

*This is a personal research project built on Chisel 3.*


