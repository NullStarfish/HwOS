package HwOS.kernel.thread.step

import chisel3.Bool
import HwOS.kernel.system.CallProtocolContext.ContinuationSnapshot

private[kernel] object ThreadCompilePlan {
  sealed trait PatchTarget
  object PatchTarget {
    case object PassEdge extends PatchTarget
  }

  sealed trait EdgeEffect

  final case class WaitEffect(
      cond: Bool,
  ) extends EdgeEffect

  final case class JumpEffect(
      targetStepIndex: Int,
      guards: List[Bool] = Nil,
  ) extends EdgeEffect

  final case class ReturnEffect(
      continuation: Option[ContinuationSnapshot],
      guards: List[Bool] = Nil,
  ) extends EdgeEffect

  final case class HijackInlineEffect(
      targetStepIndex: Int,
  ) extends EdgeEffect

  final case class PatchedEdge(
      target: PatchTarget,
      effects: Seq[EdgeEffect],
      emitThunk: Option[() => Unit] = None,
      guards: List[Bool] = Nil,
  ) extends EdgeEffect

  final case class StepEntryPolicy(
      waits: Seq[WaitEffect] = Seq.empty,
  )

  final case class StepPlan(
      stepIndex: Int,
      standalone: Boolean,
      entryPolicy: StepEntryPolicy,
      effects: Seq[EdgeEffect],
  )

  final case class ThreadCompilePlan(
      stepPlans: Vector[StepPlan],
      suppressedStandalone: Set[Int],
      standaloneIndices: Vector[Int],
      hasReturningStep: Boolean,
      entryIndex: Int,
  ) {
    private val stepPlanMap = stepPlans.iterator.map(plan => plan.stepIndex -> plan).toMap

    def stepPlan(stepIndex: Int): StepPlan =
      stepPlanMap.getOrElse(
        stepIndex,
        throw new Exception(s"[Thread] Missing compile plan for step index '$stepIndex'."),
      )
  }
}
