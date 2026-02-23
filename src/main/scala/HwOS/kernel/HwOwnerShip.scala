package HwOS.kernel

import chisel3._
import scala.collection.mutable

// ---------------------------------------------------------
// 任何可以拥有硬件资源的实体，都必须混入此特质
// ---------------------------------------------------------
trait HwOwner {
  // 必须提供一个名字，用于 Debug 报错时溯源
  def name: String 

  // 资源授权表：我拥有的某个信号 -> 允许谁来写
  private[kernel] val ownedSignals = scala.collection.mutable.Set[Data]()
  private[kernel] val grantedSignals = mutable.Map[Data, mutable.Set[HwOwner]]()
  private[kernel] val grantedLifecycle = mutable.Set[HwOwner]()

  def own[T <: Data](signal: T): T = {
    ownedSignals += signal
    ResourceManager.registerOwner(signal, this)
    signal
  }
  private[kernel] def getAllOwnedSignals(): Iterable[Data] = ownedSignals

  def grant(signal: Data, target: HwOwner): Unit = {
    val currentGrants = grantedSignals.getOrElseUpdate(signal, mutable.Set[HwOwner]())
    currentGrants += target
  }

  def grantLifecycle(target: HwOwner): Unit = {
    grantedLifecycle += target
  }
}

// ---------------------------------------------------------
// 资源管理器：现在映射关系变成了 Data -> HwOwner
// ---------------------------------------------------------
private[kernel] object ResourceManager {
  val signalOwners = mutable.Map[Data, HwOwner]()
  
  def registerOwner(signal: Data, owner: HwOwner): Unit = {
    signalOwners.get(signal) match {
      case Some(existingOwner) if existingOwner != owner =>
        throw new Exception(s"[HwOS Ownership Error] Signal ${signal} is already owned by '${existingOwner.name}'. " +
          s"Cannot be owned by '${owner.name}' simultaneously.")
      case _ =>
        signalOwners(signal) = owner
    }
  }
  
  def getOwner(signal: Data): Option[HwOwner] = {
    signalOwners.get(signal)
  }


  private val driverRegistry = mutable.Map[Data, mutable.Set[HwOwner]]()
  def recordDrive(signal: Data, actor: HwOwner): Unit = {
    val drivers = driverRegistry.getOrElseUpdate(signal, mutable.Set[HwOwner]())
    
    // 如果已经有别人驱动过这根线，且不是当前这个 actor
    if (drivers.nonEmpty && !drivers.contains(actor)) {
      println(s"\u001b[33m[HwOS Warning] lastConnect detected on signal '${signal}'!\u001b[0m")
      println(s"  Current Actor: ${actor.name}")
      println(s"  Previous Actor(s): ${drivers.map(_.name).mkString(", ")}")
      println(s"  Note: The value from '${actor.name}' will override others due to lastConnect priority.")
    }
    
    drivers += actor
  }
}