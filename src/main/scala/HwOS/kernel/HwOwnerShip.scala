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
    signalOwners(signal) = owner
  }
  
  def getOwner(signal: Data): Option[HwOwner] = {
    signalOwners.get(signal)
  }
}