package HwOS.kernel.thread

import HwOS.kernel.process.HwProcess

/** Definition-first thread object.
  *
  * A [[ThreadDef]] owns executable control-flow definition, while the hosting
  * [[HwProcess]] only provides environment/resources and installs the thread
  * into its local thread list.
  */
trait ThreadDef {
  def define(thread: HardwareThread): Unit

  final def install(process: HwProcess, threadName: String): HardwareThread = {
    val thread = process.createThread(threadName)
    define(thread)
    thread
  }
}

/** Marker trait for thread definitions that are expected to resolve external
  * resources through export/declare symbolic access rather than direct object
  * references.
  */
trait SymbolicThreadDef extends ThreadDef
