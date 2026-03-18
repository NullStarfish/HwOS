package HwOS.kernel.examples.symbolic

import HwOS.kernel.thread.SymbolicThreadDef

/**
 * Backward-compatible alias used by the demo code. The kernel now exposes the
 * formal definition-first API as SymbolicThreadDef.
 */
trait SymbolicThreadUnit extends SymbolicThreadDef
