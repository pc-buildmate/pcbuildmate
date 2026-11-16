package pcbuildmate.oracle

import pcbuildmate.contracts.Diagnosis
import pcbuildmate.contracts.Effect
import pcbuildmate.contracts.NoCandidateReason
import pcbuildmate.contracts.Rejection
import pcbuildmate.contracts.RejectionReason
import pcbuildmate.contracts.ReplacementContext
import pcbuildmate.contracts.ReplacementEvent
import pcbuildmate.contracts.ReplacementState
import pcbuildmate.contracts.RuleOutcome
import pcbuildmate.contracts.Severity
import pcbuildmate.contracts.TextKey
import pcbuildmate.contracts.TransitionResult

/**
 * C.2 替换状态机的参考模型（docs/04 §4.4）。
 *
 * 6 状态 × 10 事件，按文档的转移矩阵与明细重写；不 import `pcbuildmate.domain.state`。
 * 五条"禁止转移"（尤其 Previewing→Applied 跳过重检）在结构上就写不出来：
 * 应用只能经 Rechecking 再到 Applied。
 */
data class OracleReplacementInput(
    val currentRevisionId: Long,
    val diagnosis: Diagnosis?,
    val candidateIds: Set<String>,
)

object ReplacementOracle {

    private val invalidState = Rejection(RejectionReason.INVALID_STATE, TextKey.INVALID_STATE)

    fun transition(
        state: ReplacementState,
        event: ReplacementEvent,
        input: OracleReplacementInput,
    ): TransitionResult<ReplacementState> = when (state) {
        is ReplacementState.Idle -> idle(event, input)
        is ReplacementState.Candidates -> candidates(state, event, input)
        is ReplacementState.Previewing -> previewing(state, event, input)
        is ReplacementState.Rechecking -> rechecking(state, event, input)
        is ReplacementState.Applied -> settledWithoutCtx(state, event, input)
        is ReplacementState.NoCandidate -> settledWithoutCtx(state, event, input)
    }

    // ---------------- 各状态 ----------------

    private fun idle(event: ReplacementEvent, input: OracleReplacementInput): TransitionResult<ReplacementState> =
        when (event) {
            is ReplacementEvent.ConflictSelected -> selectConflict(event, input, stay = ReplacementState.Idle)
            is ReplacementEvent.BuildEdited -> TransitionResult(
                ReplacementState.Idle, listOf(Effect.SaveDraft(event.revision)),
            )
            else -> reject(ReplacementState.Idle)
        }

    private fun candidates(
        state: ReplacementState.Candidates,
        event: ReplacementEvent,
        input: OracleReplacementInput,
    ): TransitionResult<ReplacementState> = when (event) {
        is ReplacementEvent.ConflictSelected -> selectConflict(event, input, stay = state)
        is ReplacementEvent.CandidatesLoaded ->
            if (event.baseRevisionId != state.ctx.baseRevisionId) {
                // base 已过期：静默丢弃，用户不该看到报错（R-08）
                TransitionResult(ReplacementState.Idle)
            } else if (event.result.candidates.isEmpty()) {
                TransitionResult(
                    ReplacementState.NoCandidate(state.ctx, event.result.noCandidate ?: emptyReason()),
                )
            } else {
                TransitionResult(state)
            }
        is ReplacementEvent.PreviewCandidate ->
            when {
                // 先判 base 是否过期：base 都作废了，候选存不存在已经不重要。
                state.ctx.baseRevisionId != input.currentRevisionId -> rejectWith(
                    ReplacementState.Idle, RejectionReason.APPLY_REVISION_STALE, TextKey.APPLY_REVISION_STALE,
                )
                event.candidateId !in input.candidateIds -> rejectWith(state, RejectionReason.CANDIDATE_NOT_FOUND, TextKey.CANDIDATE_NOT_FOUND)
                else -> TransitionResult(
                    ReplacementState.Previewing(state.ctx, event.candidateId, null),
                    listOf(Effect.BuildPreview(state.ctx.baseRevisionId, event.candidateId)),
                )
            }
        is ReplacementEvent.Cancel, is ReplacementEvent.Dismiss -> TransitionResult(ReplacementState.Idle)
        is ReplacementEvent.BuildEdited -> TransitionResult(
            ReplacementState.Idle, listOf(Effect.SaveDraft(event.revision)),
        )
        else -> reject(state)
    }

    private fun previewing(
        state: ReplacementState.Previewing,
        event: ReplacementEvent,
        input: OracleReplacementInput,
    ): TransitionResult<ReplacementState> = when (event) {
        is ReplacementEvent.PreviewCandidate ->
            if (state.ctx.baseRevisionId != input.currentRevisionId) {
                rejectWith(state, RejectionReason.APPLY_REVISION_STALE, TextKey.APPLY_REVISION_STALE)
            } else if (event.candidateId in input.candidateIds) {
                TransitionResult(
                    ReplacementState.Previewing(state.ctx, event.candidateId, null),
                    listOf(Effect.BuildPreview(state.ctx.baseRevisionId, event.candidateId)),
                )
            } else {
                rejectWith(state, RejectionReason.CANDIDATE_NOT_FOUND, TextKey.CANDIDATE_NOT_FOUND)
            }
        is ReplacementEvent.PreviewBuilt ->
            TransitionResult(ReplacementState.Previewing(state.ctx, state.candidateId, event.previewRevision.id))
        is ReplacementEvent.Apply -> {
            val preview = state.previewRevisionId
            when {
                state.ctx.baseRevisionId != input.currentRevisionId ->
                    rejectWith(ReplacementState.Idle, RejectionReason.APPLY_REVISION_STALE, TextKey.APPLY_REVISION_STALE)
                preview == null ->
                    rejectWith(state, RejectionReason.PREVIEW_NOT_READY, TextKey.PREVIEW_NOT_READY)
                else -> TransitionResult(
                    ReplacementState.Rechecking(state.ctx, preview),
                    listOf(Effect.RecheckRevision(preview)),
                )
            }
        }
        is ReplacementEvent.Cancel -> TransitionResult(ReplacementState.Candidates(state.ctx))
        is ReplacementEvent.Dismiss -> TransitionResult(ReplacementState.Idle)
        is ReplacementEvent.BuildEdited -> TransitionResult(
            ReplacementState.Idle, listOf(Effect.SaveDraft(event.revision)),
        )
        else -> reject(state)
    }

    private fun rechecking(
        state: ReplacementState.Rechecking,
        event: ReplacementEvent,
        input: OracleReplacementInput,
    ): TransitionResult<ReplacementState> = when (event) {
        is ReplacementEvent.RecheckCompleted -> when {
            event.revision.id != state.previewRevisionId -> TransitionResult(ReplacementState.Idle)
            else -> {
                val conflict = firstConflict(event.diagnosis)
                if (conflict == null) {
                    TransitionResult(
                        ReplacementState.Applied(event.revision.id),
                        listOf(Effect.PersistApplied(event.revision)),
                    )
                } else {
                    // 自动为新冲突找候选，不 Applied（R-21）
                    TransitionResult(
                        ReplacementState.Candidates(
                            ReplacementContext(event.revision.id, conflict.ruleId, conflict.conflictId),
                        ),
                        listOf(
                            Effect.LoadCandidates(event.revision.id, conflict.ruleId, conflict.conflictId),
                        ),
                    )
                }
            }
        }
        is ReplacementEvent.RecheckRejected -> TransitionResult(ReplacementState.Candidates(state.ctx))
        is ReplacementEvent.Cancel -> TransitionResult(ReplacementState.Candidates(state.ctx))
        is ReplacementEvent.Dismiss -> TransitionResult(ReplacementState.Idle)
        is ReplacementEvent.BuildEdited -> TransitionResult(
            ReplacementState.Idle, listOf(Effect.SaveDraft(event.revision)),
        )
        else -> reject(state)
    }

    /** Applied / NoCandidate：都能再选一条冲突继续修。 */
    private fun settledWithoutCtx(
        state: ReplacementState,
        event: ReplacementEvent,
        input: OracleReplacementInput,
    ): TransitionResult<ReplacementState> = when (event) {
        is ReplacementEvent.ConflictSelected -> selectConflict(event, input, stay = state)
        is ReplacementEvent.Cancel, is ReplacementEvent.Dismiss -> TransitionResult(ReplacementState.Idle)
        is ReplacementEvent.BuildEdited -> TransitionResult(
            ReplacementState.Idle, listOf(Effect.SaveDraft(event.revision)),
        )
        else -> reject(state)
    }

    // ---------------- 共用判断 ----------------

    private fun selectConflict(
        event: ReplacementEvent.ConflictSelected,
        input: OracleReplacementInput,
        stay: ReplacementState,
    ): TransitionResult<ReplacementState> {
        val hit = input.diagnosis?.outcomes
            ?.filterIsInstance<RuleOutcome.Conflict>()
            ?.firstOrNull { it.conflictId == event.conflictId }
        return if (hit == null) {
            rejectWith(stay, RejectionReason.NO_CONFLICT_SELECTED, TextKey.CONFLICT_SELECTED_NONE)
        } else {
            val ctx = ReplacementContext(input.currentRevisionId, event.ruleId, event.conflictId)
            TransitionResult(
                ReplacementState.Candidates(ctx),
                listOf(Effect.LoadCandidates(input.currentRevisionId, event.ruleId, event.conflictId)),
            )
        }
    }

    /** 「新 Conflict」：BLOCKER 优先，其余按 ruleId 字典序（docs/04 §4.4.3 脚注）。 */
    private fun firstConflict(diagnosis: Diagnosis): RuleOutcome.Conflict? {
        val conflicts = diagnosis.outcomes.filterIsInstance<RuleOutcome.Conflict>()
        return conflicts.filter { it.severity == Severity.BLOCKER }.minByOrNull { it.ruleId }
            ?: conflicts.minByOrNull { it.ruleId }
    }

    private fun reject(state: ReplacementState): TransitionResult<ReplacementState> =
        TransitionResult(state, rejection = invalidState)

    private fun rejectWith(
        state: ReplacementState,
        reason: RejectionReason,
        key: TextKey,
    ): TransitionResult<ReplacementState> = TransitionResult(state, rejection = Rejection(reason, key))

    private fun emptyReason() = NoCandidateReason(blockingConstraint = "", suggestedDirection = "")
}
