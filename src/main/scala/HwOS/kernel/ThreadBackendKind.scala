package HwOS.kernel

sealed trait ThreadBackendKind

object ThreadBackendKind {
  case object Default extends ThreadBackendKind
  case object Inline extends ThreadBackendKind
}
