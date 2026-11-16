package pcbuildmate.oracle

import pcbuildmate.contracts.DiagnosisEvent
import pcbuildmate.contracts.DiagnosisState
import pcbuildmate.contracts.Effect
import pcbuildmate.contracts.Rejection
import pcbuildmate.contracts.RejectionReason
import pcbuildmate.contracts.TextKey
import pcbuildmate.contracts.TransitionResult

/**
 * C.1 诊断状态机的参考模型（docs/04 §4.3）。
 *
 * 按转移矩阵和明细表重写，不 import `pcbuildmate.domain.state`——
 * 状态类型和事件来自 contracts，本来就是共享词表，不算"抄实现"。
 *
 * 只实现判定，不实现文案：拒绝时带的 TextKey 取该判定对应的 key，
 * 交叉校验只比较 reason（文案归 POLISH-01 管）。
 */
data class OracleDiagnosisInput(
    val hasBuild: Boolean,
    val revisionId: Long?,
)

object DiagnosisOracle {

    fun transition(
        state: DiagnosisState,
        event: DiagnosisEvent,
        input: OracleDiagnosisInput,
    ): TransitionResult<DiagnosisState> = when (state) {
        is DiagnosisState.Idle -> idle(event, input)
        is DiagnosisState.Running -> running(state, event)
        is DiagnosisState.Done -> settled(state, event, input)
        is DiagnosisState.Failed -> settled(state, event, input)
    }

    private fun idle(event: DiagnosisEvent, input: OracleDiagnosisInput): TransitionResult<DiagnosisState> =
        when (event) {
            is DiagnosisEvent.StartCheck -> start(
                input = input,
                guard = input.hasBuild && input.revisionId != null,
                stay = DiagnosisState.Idle,
            )
            is DiagnosisEvent.CheckCompleted -> reject(DiagnosisState.Idle)
            is DiagnosisEvent.CheckFailed -> reject(DiagnosisState.Idle)
            is DiagnosisEvent.BuildEdited -> TransitionResult(
                state = DiagnosisState.Idle,
                effects = listOf(Effect.SaveDraft(event.revision)),
            )
        }

    private fun running(state: DiagnosisState.Running, event: DiagnosisEvent): TransitionResult<DiagnosisState> =
        when (event) {
            is DiagnosisEvent.StartCheck -> TransitionResult(state)
            is DiagnosisEvent.CheckCompleted ->
                // rev 不匹配就丢弃，不覆盖、不报错（INV-03 / D-07）
                if (event.revisionId == state.revisionId) {
                    TransitionResult(DiagnosisState.Done(event.revisionId))
                } else {
                    TransitionResult(state)
                }
            is DiagnosisEvent.CheckFailed ->
                if (event.revisionId == state.revisionId) {
                    TransitionResult(DiagnosisState.Failed(event.revisionId, event.reason))
                } else {
                    TransitionResult(state)
                }
            is DiagnosisEvent.BuildEdited -> TransitionResult(
                state = DiagnosisState.Idle,
                effects = listOf(Effect.CancelDiagnosis, Effect.SaveDraft(event.revision)),
            )
        }

    /** Done / Failed：StartCheck 重跑（Done）或重试（Failed），完成后的事件一律拒绝。 */
    private fun settled(
        state: DiagnosisState,
        event: DiagnosisEvent,
        input: OracleDiagnosisInput,
    ): TransitionResult<DiagnosisState> = when (event) {
        is DiagnosisEvent.StartCheck -> start(
            input = input,
            guard = input.revisionId != null,
            stay = state,
        )
        is DiagnosisEvent.CheckCompleted, is DiagnosisEvent.CheckFailed -> reject(state)
        is DiagnosisEvent.BuildEdited -> TransitionResult(
            state = DiagnosisState.Idle,
            effects = listOf(Effect.SaveDraft(event.revision)),
        )
    }

    private fun start(
        input: OracleDiagnosisInput,
        guard: Boolean,
        stay: DiagnosisState,
    ): TransitionResult<DiagnosisState> {
        val rev = input.revisionId
        return if (guard && rev != null) {
            TransitionResult(DiagnosisState.Running(rev), effects = listOf(Effect.RunDiagnosis(rev)))
        } else {
            TransitionResult(
                state = stay,
                rejection = Rejection(RejectionReason.DIAGNOSTICS_REQUIRED, TextKey.DIAGNOSTICS_REQUIRED),
            )
        }
    }

    private fun reject(state: DiagnosisState): TransitionResult<DiagnosisState> =
        TransitionResult(state, rejection = Rejection(RejectionReason.INVALID_STATE, TextKey.INVALID_STATE))
}
