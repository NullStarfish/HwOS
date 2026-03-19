package HwOS.kernel.debug

import scala.collection.mutable

/**
 * ContinuationNaming centralizes synthetic step naming for return and
 * function-call wait steps. It derives debug prefixes from CallStack, but the
 * counters live here rather than inside the stack tracker itself.
 */
private[kernel] object ContinuationNaming {
  private val returnCounters = mutable.HashMap.empty[(Int, String), Int]
  private val functionCallCounters = mutable.HashMap.empty[(Int, String), Int]

  private def sanitize(part: String): String = {
    val cleaned = part.replaceAll("[^A-Za-z0-9_]", "_").replaceAll("_+", "_").stripPrefix("_").stripSuffix("_")
    if (cleaned.isEmpty) "Anon" else cleaned
  }

  private def nextName(
      counters: mutable.HashMap[(Int, String), Int],
      threadKey: Int,
      semanticBase: String,
  ): String = {
    val key = (threadKey, semanticBase)
    val nextId = counters.getOrElse(key, 0)
    counters.update(key, nextId + 1)
    if (nextId == 0) semanticBase else s"${semanticBase}_$nextId"
  }

  def freshReturnStepName(threadKey: Int, target: String): String = {
    val prefix = sanitize(CallStack.getCurrentPrefix.stripSuffix("_"))
    val targetName = sanitize(target)
    val semanticBase = s"${prefix}_Return_to_${targetName}"
    nextName(returnCounters, threadKey, semanticBase)
  }

  def freshFunctionCallStepName(threadKey: Int, functionName: String, returnTo: String): String = {
    val prefix = sanitize(CallStack.getCurrentPrefix.stripSuffix("_"))
    val functionPart = sanitize(functionName)
    val targetPart = sanitize(returnTo)
    val semanticBase =
      if (prefix.isEmpty) s"${functionPart}_CallWait_to_${targetPart}"
      else s"${prefix}_${functionPart}_CallWait_to_${targetPart}"
    nextName(functionCallCounters, threadKey, semanticBase)
  }
}
