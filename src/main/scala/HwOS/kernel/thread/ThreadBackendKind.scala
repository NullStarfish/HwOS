package HwOS.kernel.thread

sealed trait ThreadBackendKind

object ThreadBackendKind {
  case object Default extends ThreadBackendKind
  case object Inline extends ThreadBackendKind
}
