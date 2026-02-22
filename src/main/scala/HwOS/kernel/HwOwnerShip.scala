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
  private[kernel] val grantedSignals = mutable.Map[Data, mutable.Set[HwOwner]]()
  
  // 生命周期授权表：允许谁来杀我/启动我 (如果是 Thread 的话)
  private[kernel] val grantedLifecycle = mutable.Set[HwOwner]()

  /**
   * 声明资源所有权
   * @param signal 裸的 Chisel Data (Wire/Reg/IO)
   * @return 原样返回，方便链式调用
   */
  def own[T <: Data](signal: T): T = {
    ResourceManager.registerOwner(signal, this)
    signal
  }

  /**
   * 数据写入授权
   * @param signal 必须是我 own 的信号
   * @param target 被授权的实体 (如某个 Thread)
   */
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