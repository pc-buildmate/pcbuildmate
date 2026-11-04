package pcbuildmate.domain.state

import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.DiagnosisEvent
import pcbuildmate.contracts.DiagnosisState
import pcbuildmate.contracts.Effect
import pcbuildmate.contracts.Rejection
import pcbuildmate.contracts.RejectionReason
import pcbuildmate.contracts.TextKey
import pcbuildmate.contracts.TransitionResult

/**
 * C.1 诊断状态机。
 *
 * 纯函数：输入 (state, event, ctx) -> TransitionResult<DiagnosisState>。
 * 实现严格遵循 docs/04-state-machines.md §4.3 的转移矩阵与明细。
 */
object DiagnosisMachine {

    fun transition(
        state: DiagnosisState,
        event: DiagnosisEvent,
        ctx: DiagnosisContext,
    ): TransitionResult<DiagnosisState> = when (state) {
        is DiagnosisState.Idle -> idle(state, event, ctx)
        is DiagnosisState.Running -> running(state, event, ctx)
        is DiagnosisState.Done -> done(state, event, ctx)
        is DiagnosisState.Failed -> failed(state, event, ctx)
    }

    private fun idle(
        state: DiagnosisState.Idle,
        event: DiagnosisEvent,
        ctx: DiagnosisContext,
    ): TransitionResult<DiagnosisState> = when (event) {
        is DiagnosisEvent.StartCheck -> {
            val revision = ctx.revision
            if (ctx.build != null && revision != null) {
                TransitionResult(
                    state = DiagnosisState.Running(revision.id),
                    effects = listOf(Effect.RunDiagnosis(revision.id)),
                )
            } else {
                TransitionResult(
                    state = state,
                    rejection = Rejection(
                        reason = RejectionReason.DIAGNOSTICS_REQUIRED,
                        textKey = TextKey.DIAGNOSTICS_REQUIRED,
                    ),
                )
            }
        }

        is DiagnosisEvent.CheckCompleted,
        is DiagnosisEvent.CheckFailed -> rejectInvalid(state)

        is DiagnosisEvent.BuildEdited -> TransitionResult(
            state = state,
            effects = listOf(Effect.SaveDraft(event.revision)),
        )
    }

    private fun running(
        state: DiagnosisState.Running,
        event: DiagnosisEvent,
        ctx: DiagnosisContext,
    ): TransitionResult<DiagnosisState> = when (event) {
        is DiagnosisEvent.StartCheck -> TransitionResult(state = state)

        is DiagnosisEvent.CheckCompleted ->
            if (event.revisionId == state.revisionId) {
                TransitionResult(state = DiagnosisState.Done(event.revisionId))
            } else {
                TransitionResult(state = state)
            }

        is DiagnosisEvent.CheckFailed ->
            if (event.revisionId == state.revisionId) {
                TransitionResult(
                    state = DiagnosisState.Failed(event.revisionId, event.reason),
                )
            } else {
                TransitionResult(state = state)
            }

        is DiagnosisEvent.BuildEdited -> TransitionResult(
            state = DiagnosisState.Idle,
            effects = listOf(
                Effect.CancelDiagnosis,
                Effect.SaveDraft(event.revision),
            ),
        )
    }

    private fun done(
        state: DiagnosisState.Done,
        event: DiagnosisEvent,
        ctx: DiagnosisContext,
    ): TransitionResult<DiagnosisState> = when (event) {
        is DiagnosisEvent.StartCheck -> {
            val revision = ctx.revision
            if (revision != null) {
                TransitionResult(
                    state = DiagnosisState.Running(revision.id),
                    effects = listOf(Effect.RunDiagnosis(revision.id)),
                )
            } else {
                TransitionResult(
                    state = state,
                    rejection = Rejection(
                        reason = RejectionReason.DIAGNOSTICS_REQUIRED,
                        textKey = TextKey.DIAGNOSTICS_REQUIRED,
                    ),
                )
            }
        }

        is DiagnosisEvent.CheckCompleted,
        is DiagnosisEvent.CheckFailed -> rejectInvalid(state)

        is DiagnosisEvent.BuildEdited -> TransitionResult(
            state = DiagnosisState.Idle,
            effects = listOf(Effect.SaveDraft(event.revision)),
        )
    }

    private fun failed(
        state: DiagnosisState.Failed,
        event: DiagnosisEvent,
        ctx: DiagnosisContext,
    ): TransitionResult<DiagnosisState> = when (event) {
        is DiagnosisEvent.StartCheck -> {
            val revision = ctx.revision
            if (revision != null) {
                TransitionResult(
                    state = DiagnosisState.Running(revision.id),
                    effects = listOf(Effect.RunDiagnosis(revision.id)),
                )
            } else {
                TransitionResult(
                    state = state,
                    rejection = Rejection(
                        reason = RejectionReason.DIAGNOSTICS_REQUIRED,
                        textKey = TextKey.DIAGNOSTICS_REQUIRED,
                    ),
                )
            }
        }

        is DiagnosisEvent.CheckCompleted,
        is DiagnosisEvent.CheckFailed -> rejectInvalid(state)

        is DiagnosisEvent.BuildEdited -> TransitionResult(
            state = DiagnosisState.Idle,
            effects = listOf(Effect.SaveDraft(event.revision)),
        )
    }

    private fun rejectInvalid(state: DiagnosisState): TransitionResult<DiagnosisState> =
        TransitionResult(
            state = state,
            rejection = Rejection(
                reason = RejectionReason.INVALID_STATE,
                textKey = TextKey.INVALID_STATE,
            ),
        )
}
