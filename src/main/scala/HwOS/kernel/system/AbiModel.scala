package HwOS.kernel.system

sealed trait GrantAbi {
  def name: String
}

object GrantAbi {
  case object RegisterWrite extends GrantAbi {
    override val name: String = "register-write"
  }

  case object LevelDrivenWire extends GrantAbi {
    override val name: String = "level-driven-wire"
  }

  case object PulseWire extends GrantAbi {
    override val name: String = "pulse-wire"
  }
}

final class GrantTableEntry(
    val ownerName: String,
    val targetName: String,
    val signalObject: AddressObject,
    val abi: GrantAbi,
)
