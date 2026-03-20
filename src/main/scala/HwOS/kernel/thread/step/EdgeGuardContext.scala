package HwOS.kernel.thread.step

private[kernel] object EdgeGuardContext {
  def withInlineBoundary[T](block: => T): T =
    if (PreLoweringAnalysis.isActive) {
      PreLoweringAnalysis.withScopedEdgeGuards {
        block
      }
    } else {
      block
    }
}
