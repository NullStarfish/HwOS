package HwOS.kernel.lang
import chisel3._

object HwOSLanguage {
  implicit class SecureVecAccess[T <: Data](val vec: Vec[T]) extends AnyVal {
    def at(idx: UInt): T = vec(idx)
    def at(idx: Int): T  = vec(idx)
  }
}
