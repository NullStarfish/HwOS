package HwOS.kernel.context

import chisel3._
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import HwOS.kernel.lang.HwOSLanguage._
import HwOS.kernel.system.{GrantAbi, Kernel}
import HwOS.kernel.thread.{HardwareAgent, HardwareThread}

// ==========================================
// 核心抽象：硬件租约
// ==========================================
trait HwLease {
  def isActive: Bool 
  private[kernel] def forceReclaim(agent: HardwareAgent): Unit 
}

// ==========================================
// HwContext：统一的执行上下文 / 权限中心
// ==========================================
// contract:
// 1. 所有受保护赋值都以 ctx.isActive 为最终门控
// 2. kernelKillSignal 是 context 级别的系统切断机制，不是 lifecycle 语义
// 3. 资源 ownership / ACL 挂在 context 上
// 4. isActive 必须由承载该 context 的类显式绑定；未绑定即为错误
class HwContext(val self: HwContextEntity) {
  private[kernel] val owns = scala.collection.mutable.Set[Data]()
  private[kernel] val granteds = scala.collection.mutable.Set[Data]()

  private[kernel] val activeLeases = ArrayBuffer[HwLease]()
  private var activeExpr: Option[Bool] = None

  def registerLease(lease: HwLease): Unit = {
    activeLeases += lease
  }

  def name: String = self.name

  def own[T <: Data](signal: T): T = {
    owns += signal
    scala.util.Try(self.kernel.addressSpace.registerOwnedSignal(self.name, signal))
    ResourceManager.registerOwner(signal, this)
    signal
  }

  def exemptVectorAcl[T <: Data](vec: Vec[T]): Vec[T] = {
    ResourceManager.registerAclExemptVector(vec)
    vec
  }

  private[kernel] val kernelKillSignal = own(RegInit(false.B))

  def grant(signal: Data, target: HwContext): Unit = {
    grant(signal, target, self.kernel.addressSpace.inferGrantAbi(signal))
  }

  def grant(signal: Data, target: HwContext, abi: GrantAbi): Unit = {
    ResourceManager.getOwner(signal) match {
      case None =>
        ResourceManager.registerOwner(signal, this)
      case Some(owner) if owner == this =>
        // Re-assert the owner's ACL membership in case the signal was created
        // during an early-init path that registered ownership before grant-time.
        ResourceManager.registerOwner(signal, this)
      case _ =>
    }
    ResourceManager.delegatePermission(signal, this, target)
    self.kernel.addressSpace.registerGrant(self.name, target.name, signal, abi)
    target.granteds += signal
  }

  private[kernel] def getAllOwnedSignals(): Iterable[Data] = owns

  def bindIsActive(expr: Bool): Unit = {
    activeExpr = Some(expr)
  }

  def isActive: Bool =
    activeExpr
      .map(_ && !kernelKillSignal)
      .getOrElse(throw new Exception(s"[HwOS Context Error] isActive is not bound for context '${self.name}'."))
}


object HwContext {
  def apply(self: HwContextEntity) : HwContext = new HwContext(self)
}

// ---------------------------------------------------------
// 任何持有上下文的实体
// ---------------------------------------------------------
trait HwContextEntity {
  def name: String 
  def kernel: Kernel

  // 每个实体都显式持有一个 context。
  // context 是受保护赋值、resource ACL、kernel kill cut-off 的中心；
  // entity 只是承载这个 context 的对象壳。
  val ctx = new HwContext(this)

  def own[T <: Data](signal: T): T = {
    ctx.own(signal)
  }

  def exemptVectorAcl[T <: Data](vec: Vec[T]): Vec[T] = {
    ctx.exemptVectorAcl(vec)
  }

  def grant(signal: Data, target: HwContextEntity): Unit = {
    ctx.grant(signal, target.ctx)
  }

  def grant(signal: Data, target: HwContextEntity, abi: GrantAbi): Unit = {
    ctx.grant(signal, target.ctx, abi)
  }


  def grantLifecycle(thread: HardwareThread, target: HwContextEntity): Unit = {
    // lifecycle 控制权不是 HwContext 的普通 ACL，而是 thread 自己的系统级权限表。
    thread.grantLifecycleAccess(target.ctx)
  }
}


// ---------------------------------------------------------
// 统一安全网关
// ---------------------------------------------------------
private[kernel] object ResourceManager {
  private final case class SignalRef(signal: Data) {
    override def equals(other: Any): Boolean = other match {
      case SignalRef(otherSignal) => signal eq otherSignal
      case _ => false
    }

    override def hashCode(): Int = System.identityHashCode(signal)
  }

  private val signalOwners = mutable.HashMap[SignalRef, HwContext]()
  private val acl = mutable.HashMap[SignalRef, mutable.Set[HwContext]]()
  private val driverRegistry = mutable.HashMap[SignalRef, mutable.Set[HwContext]]()
  private val aclExemptVectors = mutable.HashSet[SignalRef]()
  private val dynamicVecParents = mutable.HashMap[SignalRef, SignalRef]()

  private def ref(signal: Data): SignalRef = SignalRef(signal)

  def reset(): Unit = {
    signalOwners.clear()
    acl.clear()
    driverRegistry.clear()
    aclExemptVectors.clear()
    dynamicVecParents.clear()
  }
  
  def registerOwner(signal: Data, owner: HwContext): Unit = {
    val key = ref(signal)
    signalOwners.get(key) match {
      case Some(existingOwner) if existingOwner != owner =>
        throw new Exception(s"[HwOS Ownership Error] Signal ${signal} is already owned by '${existingOwner.name}'. " +
          s"Cannot be owned by '${owner.name}' simultaneously.")
      case _ =>
        signalOwners(key) = owner
        acl.getOrElseUpdate(key, mutable.Set[HwContext]()) += owner
    }
  }
  
  def getOwner(signal: Data): Option[HwContext] = signalOwners.get(ref(signal))

  def registerAclExemptVector(vec: Data): Unit = {
    aclExemptVectors += ref(vec)
  }

  def markDynamicVecAccess(child: Data, parentVec: Data): Unit = {
    dynamicVecParents(ref(child)) = ref(parentVec)
  }

  def isAclExemptDynamicVecAccess(signal: Data): Boolean = {
    dynamicVecParents.get(ref(signal)).exists(aclExemptVectors.contains)
  }

  def delegatePermission(signal: Data, delegator: HwContext, target: HwContext): Unit = {
    val allowedActors = acl.getOrElseUpdate(ref(signal), mutable.Set[HwContext]())
    if (!allowedActors.contains(delegator)) {
      throw new Exception(s"[HwOS Security Error] Agent '${delegator.name}' attempted to grant permission of '${signal}' to '${target.name}', but it lacks permission itself!")
    }
    allowedActors += target
  }

  def getAllowedActors(signal: Data): Iterable[HwContext] = {
    acl.getOrElse(ref(signal), mutable.Set[HwContext]())
  }

  def recordDrive(signal: Data, actor: HwContext): Unit = {
    val drivers = driverRegistry.getOrElseUpdate(ref(signal), mutable.Set[HwContext]())
    if (drivers.nonEmpty && !drivers.contains(actor)) {
      println(s"\u001b[33m[HwOS Warning] lastconnect warning detected on signal '${signal}'!\u001b[0m")
      println(s"  Current Actor: ${actor.name}")
      println(s"  Previous Actor(s): ${drivers.map(_.name).mkString(", ")}")
      println(s"  Note: The value from '${actor.name}' will override others due to lastConnect priority.")
    }
    
    drivers += actor
  }

  def checkSignalWrite(signal: Data, currentActor: HwContext): Unit = {
    if (isAclExemptDynamicVecAccess(signal)) {
      return
    }
    val allowedActors = acl.getOrElse(ref(signal), mutable.Set[HwContext]())
    if (!allowedActors.contains(currentActor)) {
      val ownerName = signalOwners.get(ref(signal)).map(_.name).getOrElse("Unknown")
      throw new Exception(s"[HwOS SegFault] Access Denied! Agent '${currentActor.name}' cannot write to resource owned by '${ownerName}'. Requires explicit grant().")
    }
  }
}
