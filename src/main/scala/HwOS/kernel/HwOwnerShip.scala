package HwOS.kernel

import chisel3._
import scala.collection.mutable

// ---------------------------------------------------------
// 任何可以拥有硬件资源的实体，都必须混入此特质
// ---------------------------------------------------------
trait HwOwner {
  def name: String 

  // 资源授权表：只认 Data (Signal)，彻底抹除对 Thread/Lifecycle 的特判
  private[kernel] val ownedSignals = mutable.Set[Data]()
  private[kernel] val grantedSignals = mutable.Map[Data, mutable.Set[HwOwner]]()

  // 宣誓主权
  def own[T <: Data](signal: T): T = {
    ownedSignals += signal
    ResourceManager.registerOwner(signal, this)
    signal
  }
  
  private[kernel] def getAllOwnedSignals(): Iterable[Data] = ownedSignals

  // 授予写入权限
  // 注意：我们不禁止一个资源被 grant 给多个实体
  def grant(signal: Data, target: HwOwner): Unit = {
    val currentGrants = grantedSignals.getOrElseUpdate(signal, mutable.Set[HwOwner]())
    currentGrants += target
  }
}

// ---------------------------------------------------------
// 统一安全网关 (Security Enclave & Resource Manager)
// ---------------------------------------------------------
private[kernel] object ResourceManager {
  val signalOwners = mutable.Map[Data, HwOwner]()
  private val driverRegistry = mutable.Map[Data, mutable.Set[HwOwner]]()
  
  // 1. 强制禁止一个资源被 own 给多个实体 (编译期不可饶恕的错误)
  def registerOwner(signal: Data, owner: HwOwner): Unit = {
    signalOwners.get(signal) match {
      case Some(existingOwner) if existingOwner != owner =>
        throw new Exception(s"[HwOS Ownership Error] Signal ${signal} is already owned by '${existingOwner.name}'. " +
          s"Cannot be owned by '${owner.name}' simultaneously.")
      case _ =>
        signalOwners(signal) = owner
    }
  }
  
  def getOwner(signal: Data): Option[HwOwner] = signalOwners.get(signal)

  // 2. 动态检测并警告多重驱动 (lastconnect warning)
  // 当资源被两个实体 <== 的时候，报 warning
  def recordDrive(signal: Data, actor: HwOwner): Unit = {
    val drivers = driverRegistry.getOrElseUpdate(signal, mutable.Set[HwOwner]())
    
    // 如果已经有别人驱动过这根线，且不是当前这个 actor
    if (drivers.nonEmpty && !drivers.contains(actor)) {
      println(s"\u001b[33m[HwOS Warning] lastconnect warning detected on signal '${signal}'!\u001b[0m")
      println(s"  Current Actor: ${actor.name}")
      println(s"  Previous Actor(s): ${drivers.map(_.name).mkString(", ")}")
      println(s"  Note: The value from '${actor.name}' will override others due to lastConnect priority.")
    }
    
    drivers += actor
  }

  // 3. 核心鉴权枢纽：检查当前实体是否有权写入该线缆
  def checkSignalWrite(signal: Data, currentActor: HwOwner): Unit = {
    val ownerOpt = getOwner(signal)
    
    ownerOpt.foreach { owner =>
      val isOwner = (owner == currentActor)
      val isGranted = owner.grantedSignals.get(signal).exists(_.contains(currentActor)) 
      if (!isOwner && !isGranted) {
         throw new Exception(s"[HwOS SegFault] Access Denied! '${currentActor.name}' cannot write to resource owned by '${owner.name}'. Requires explicit grant().")
      }
    }
  }
}