# Control-Flow-First Hardware Construction with Protocol APIs

## Abstract
Hardware designers routinely describe behavior by first committing to structural organization: wires are drawn, protocol state machines are instantiated, and control emerges indirectly from those connections. This paper explores a different design center. We present a control-flow-first hardware construction style in which the primary frontend object is a `thread`: a sequential control program over explicit actions such as `Step`, `waitCondition`, `jump`, `Call`, and `Return`. In this model, protocol interactions are packaged as APIs rather than exposed as raw handshake choreography, and backend compilation is responsible for lowering those control programs into concrete scheduling, pipelining, and resource-allocation decisions.

We argue three points through a prototype implemented in Chisel. First, hardware can be naturally described as control programs rather than structural interconnect skeletons. Second, protocol-heavy interactions, such as AXI reads, can be encapsulated as reusable APIs without losing explicitness or synthesizability. Third, microarchitectural choices such as write ordering, reservation discipline, and commit timing can be pushed into a backend/runtime layer, as illustrated by an age-ordered scoreboard regfile. While our prototype remains an evolving research artifact rather than a polished compiler, it already suggests that control-flow-first construction can improve modularity, refactorability, and optimization freedom in hardware design.

## 1. Introduction
Most hardware design languages offer behavioral syntax, but the dominant design workflow remains structural. Designers still reason in terms of modules, ports, wires, arbiters, and protocol-specific state machines. Even when the intended behavior is fundamentally sequential, it is typically realized by constructing a web of structural connections first and then recovering the control story from that web.

This paper explores an alternative viewpoint: what if hardware construction began from control flow instead of structure?

Our prototype takes `thread` as the central frontend abstraction. A thread is not a software thread, nor a high-level simulation artifact. It is a hardware control program with a concrete runtime cursor and explicit lifecycle. Designers write ordered action sequences such as `Step("Decode") { ... }`, `waitCondition(...)`, `jump(...)`, and `SysCall.Call(...)`; these sequences are then compiled and lowered into actual hardware state and scheduling logic. The key ambition is to let frontend code capture *what should happen next*, while postponing *how the backend schedules and realizes it structurally*.

From this design center follow two additional ideas. First, protocols can be presented as APIs: a designer can write an AXI read as a callable transaction rather than manually coordinating `valid`, `ready`, response timing, and return sequencing at every call site. Second, backend lowering becomes the natural place to handle pipelining, arbitration placement, and resource allocation. The frontend should not have to be rewritten every time a long path is split, a service is decoupled, or an execution policy changes.

This paper makes three contributions:

1. It presents a **control-flow-first** hardware construction model centered on `thread` as an executable control program.
2. It shows how **protocols as APIs** can make protocol-heavy behavior reusable and compositional, using AXI as a concrete example.
3. It argues for **backend-driven optimization**, using an age-ordered scoreboard regfile as an example where scheduling and ordering policy live below the frontend control description.

The result is not yet a production compiler, and we do not claim full performance competitiveness against mature RTL flows. Our claim is narrower and more practical: a control-flow-first frontend can give hardware designers a more stable programming surface while enabling backend experimentation with microarchitectural organization.

## 2. Motivation
The work was motivated by repeated friction during CPU-style prototyping. In a decode-centric implementation, decode gradually accumulated responsibility for decode, execute, memory access, and writeback. This made the control chain long, hard to pipeline, and difficult to refactor. Meanwhile, when a path was extracted into a reusable service, it was easy to accidentally duplicate arbitration logic: both the caller and the callee might introduce ownership or issue control, even when only one layer actually faced a structural hazard.

These problems are symptoms of a deeper issue: the boundary between behavior, protocol, and microarchitecture is fixed too early. Once handshake structure and ownership policy are manually embedded into the frontend, small design changes require large rewrites.

A control-flow-first frontend aims to delay those commitments. The designer writes ordered actions and API calls; the backend determines where runtime state lives, where steps become standalone code slots, and where scheduling or arbitration is actually necessary.

## 3. Thread as the Control-Flow Abstraction
The first claim of this work is that hardware can be described as sequences of actions rather than primarily as structural connections. In our system, the central frontend abstraction is the `thread`.

A thread is the formal execution host of a hardware control program. It owns a runtime cursor, has explicit lifecycle state (`active`, `done`, `reset`), and executes a program built from named `Step`s. Each step contains ordinary Chisel statements together with control actions such as `jump`, `hijack`, `waitCondition`, `SysCall.Call`, and `SysCall.Return`. The important point is that the designer writes these as a control narrative: what this hardware does next, under what condition it stalls, and where it returns.

This matters because it changes how the code is organized. Instead of beginning from a graph of modules and wires, the frontend begins from a sequence of actions:

```scala
worker.entry {
  worker.Step("Issue") {
    bus.ar.valid := true.B
    worker.waitCondition(bus.ar.ready)
  }
  worker.Step("Receive") {
    worker.waitCondition(bus.r.valid)
    when(bus.r.valid) {
      response := bus.r.data
      SysCall.Return()
    }
  }
}
```

This example still lowers to hardware with explicit registers and control state, but the authored program is organized around control flow. In our experience, this organization makes large refactors easier. A long path can be split by inserting a new step or by moving a call boundary, without immediately forcing a redesign of the surrounding structural wiring. Likewise, edge-local behavior can be attached through `StepRef.edge.add { ... }`, which now supports arbitrary action blocks rather than only special-cased control primitives.

The thread abstraction therefore serves two roles at once: it gives the user a concrete execution model, and it gives the backend a structured control program to optimize.

## 4. Protocols as APIs
The second claim is that handshake protocols and pipeline interactions can be packaged as APIs rather than exposed as raw protocol choreography at every use site.

Our AXI read example illustrates the point. In a conventional style, each use site would directly manipulate `ar.valid`, observe `ar.ready`, later assert `r.ready`, wait for `r.valid`, check `r.resp`, and finally move data into the surrounding control path. In our prototype, this interaction is wrapped as a reusable callable unit:

```scala
def axi_read(bus: Axi4ReadOnly, addr: UInt): HwInline[UInt] =
  HwInline.thread("axi_read") { t =>
    t.Step("IssueAddr") {
      bus.ar.valid := true.B
      bus.ar.addr := addr
      t.waitCondition(bus.ar.ready)
    }

    t.Step("WaitData") {
      bus.r.ready := true.B
      t.waitCondition(bus.r.valid)
      when(bus.r.valid) {
        responseData := bus.r.data
        SysCall.Return()
      }
    }

    responseData
  }
```

The call site then expresses intent rather than protocol choreography:

```scala
val value = SysCall.Call(axi_read(io.axi, addrReg))
```

This is not merely syntactic sugar. The API boundary establishes a reusable control segment with explicit call/return semantics. The caller does not need to re-encode the handshake protocol each time, and protocol evolution can be localized. At the same time, the resulting hardware remains explicit: the API is still lowered into concrete handshake behavior, and return edges can still carry additional actions through call-site patches.

The same style extends beyond bus protocols. In our prototype CPU, execution paths and commit operations can be turned into service-like APIs rather than directly embedded into decode logic. The important distinction is that protocol/API boundaries capture *semantic transactions*; they need not freeze a specific ownership or arbitration policy into the frontend.

## 5. Backend-Driven Optimization
The third claim is that scheduling, pipelining, and resource allocation should be handled by a backend compiler or runtime lowering layer rather than being baked into the frontend control description.

Our regfile stack provides a concrete example. The frontend can interact with a register file through operations such as `Read`, `Reserve`, and `WritebackAndClear`, but the actual implementation is layered. A minimal base regfile provides direct storage. A semaphore-backed variant adds explicit write-slot control. An age-ordered scoreboard regfile adds reservation tracking, in-flight ordering, and delayed publish/commit discipline through an ordered window.

The most interesting case is `AgeOrderedScoreboardRegfileProcess`. From the caller's perspective, a write follows a clean sequence such as:

```scala
val writePort = SysCall.Inline(regFile.RequestWritePort(portIdx))
SysCall.Inline(writePort.Reserve(addr))
...
SysCall.Inline(writePort.WritebackAndClear(addr, data))
```

Yet the backend/runtime implementation maintains substantially richer policy:

- reservation of the architectural destination,
- buffering of pending writes,
- readiness tracking for produced data,
- age-ordered publication through an ordered window,
- release of scoreboard and write-port resources only at commit time.

This is exactly the sort of policy we do not want frontend algorithm code to spell out repeatedly. The frontend names the intended actions. The backend and library implementation decide where the state lives, when publication becomes visible, and how ordering is enforced.

This separation is especially important for pipeline exploration. If the designer wants to move reserve earlier, split commit from execute, or change whether a path blocks or decouples, the right place for those changes is the lowering and service organization layer. Otherwise, every optimization attempt turns into a large semantic rewrite.

## 6. Case Study: From Decode-Centric Control to Decoupled Services
The prototype CPU in our repository began with a decode-centric organization. Decode effectively owned most of the end-to-end lifecycle: interpreting instruction fields, invoking execution, waiting for service completion, and performing writeback. That organization was easy to start with, but it led to an overly long control chain and made service reuse difficult.

We then refactored toward a decoupled organization in which execution paths and commit logic were moved into dedicated processes. The key lesson from this transition was not merely that pipelining helps performance; rather, it was that decoupling requires an appropriate control abstraction. If the caller is forced to micromanage the callee's full lifecycle, then splitting a path still leaves the frontend entangled with backend timing. API-style service boundaries let the caller describe requests while allowing the callee and backend to own execution policy.

This case study also sharpened our understanding of arbitration placement. Some layers genuinely need arbitration because they mediate scarce physical resources, such as load ports or arithmetic ports. Other layers do not. A control-flow-first/frontend-plus-backend split makes this easier to reason about: arbitration should be introduced where structural hazards exist, not duplicated at every abstraction boundary.

## 7. Discussion
The central message of this work is not that wires, ports, or explicit protocols should disappear. Hardware remains hardware. Rather, we argue that the *frontend factoring* can change. Control intent can be written first, APIs can represent transactions, and lowering can reintroduce the necessary structural machinery.

This perspective is especially valuable for exploratory microarchitecture work. In conventional RTL, changing a pipeline cut often means rewriting control, protocol, and resource ownership simultaneously. In our model, those concerns can be teased apart. The frontend remains a control program; the backend absorbs more of the scheduling burden.

There are limits. Our current system still has rough edges, and some analyses remain conservative. We do not yet claim a full optimizing compiler or a complete out-of-order story. Still, even in its present form, the prototype suggests that control-flow-first construction is a viable research direction for making hardware more programmable without pretending it is software.

## 8. Limitations and Future Work
This artifact is still an active prototype. Some internal layers remain under cleanup, and our evaluation is currently stronger on programmability and refactorability than on finalized area/performance numbers. In particular, while backend-driven optimization is a central thesis of the work, the current implementation should be understood as a proof of direction rather than a fully developed optimization framework.

Several next steps follow naturally. We need stronger static analyses for edge-local control behavior and return-path guarantees. We also need broader benchmarks and more systematic comparisons against conventional RTL workflows. Finally, the protocol-as-API idea should be tested beyond AXI and simple service calls, ideally on a broader family of standard protocols and larger designs.

## 9. Conclusion
This paper presented a control-flow-first hardware construction style centered on `thread` as an executable control program, protocols as reusable APIs, and backend-driven microarchitectural optimization. Through concrete examples from our prototype, we showed how a thread-centered frontend can express hardware behavior as sequences of actions, how an AXI interaction can be lifted into a callable API, and how a regfile backend can internalize ordering and resource-allocation policy without burdening frontend code.

Our argument is not that structural design disappears, but that it can move later in the flow. By treating control as the primary frontend object and structure as a lowering concern, hardware design may gain a more stable programming surface for iteration, reuse, and backend experimentation.
