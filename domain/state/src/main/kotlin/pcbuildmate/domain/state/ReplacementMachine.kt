package pcbuildmate.domain.state

import pcbuildmate.contracts.BuildRevision
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
 * C.2 替换状态机。
 *
 * 纯函数：输入 (state, event, guard) -> TransitionResult<ReplacementState>。
 * 实现严格遵循 docs/04-state-machines.md §4.4 的转移矩阵与明细。
 */
object ReplacementMachine {

    fun transition(
        state: ReplacementState,
        event: ReplacementEvent,
        guard: ReplacementGuard,
    ): TransitionResult<ReplacementState> = when (state) {
        is ReplacementState.Idle -> idle(state, event, guard)
        is ReplacementState.Candidates -> candidates(state, event, guard)
        is ReplacementState.Previewing -> previewing(state, event, guard)
        is ReplacementState.Rechecking -> rechecking(state, event, guard)
        is ReplacementState.Applied -> applied(state, event, guard)
        is ReplacementState.NoCandidate -> noCandidate(state, event, guard)
    }

    private fun idle(
        state: ReplacementState.Idle,
        event: ReplacementEvent,
        guard: ReplacementGuard,
    ): TransitionResult<ReplacementState> = when (event) {
        is ReplacementEvent.ConflictSelected -> {
            val conflict = guard.diagnosis?.findConflict(event.conflictId)
            if (conflict != null) {
                val ctx = ReplacementContext(
                    baseRevisionId = guard.currentRevisionId,
                    conflictRuleId = event.ruleId,
                    conflictId = event.conflictId,
                )
                TransitionResult(
                    state = ReplacementState.Candidates(ctx),
                    effects = listOf(
                        Effect.LoadCandidates(
                            baseRevisionId = ctx.baseRevisionId,
                            conflictRuleId = ctx.conflictRuleId,
                            conflictId = ctx.conflictId,
                        ),
                    ),
                )
            } else {
                reject(state, RejectionReason.NO_CONFLICT_SELECTED, TextKey.CONFLICT_SELECTED_NONE)
            }
        }

        is ReplacementEvent.BuildEdited -> TransitionResult(
            state = state,
            effects = listOf(Effect.SaveDraft(event.revision)),
        )

        else -> rejectInvalid(state)
    }

    private fun candidates(
        state: ReplacementState.Candidates,
        event: ReplacementEvent,
        guard: ReplacementGuard,
    ): TransitionResult<ReplacementState> = when (event) {
        is ReplacementEvent.ConflictSelected -> {
            val conflict = guard.diagnosis?.findConflict(event.conflictId)
            if (conflict != null) {
                val ctx = ReplacementContext(
                    baseRevisionId = guard.currentRevisionId,
                    conflictRuleId = event.ruleId,
                    conflictId = event.conflictId,
                )
                TransitionResult(
                    state = ReplacementState.Candidates(ctx),
                    effects = listOf(
                        Effect.LoadCandidates(
                            baseRevisionId = ctx.baseRevisionId,
                            conflictRuleId = ctx.conflictRuleId,
                            conflictId = ctx.conflictId,
                        ),
                    ),
                )
            } else {
                reject(state, RejectionReason.NO_CONFLICT_SELECTED, TextKey.CONFLICT_SELECTED_NONE)
            }
        }

        is ReplacementEvent.CandidatesLoaded -> {
            when {
                event.baseRevisionId == state.ctx.baseRevisionId && event.result.candidates.isNotEmpty() ->
                    TransitionResult(state = state)

                event.baseRevisionId == state.ctx.baseRevisionId ->
                    TransitionResult(
                        state = ReplacementState.NoCandidate(
                            ctx = state.ctx,
                            reason = event.result.noCandidate
                                ?: NoCandidateReason(
                                    blockingConstraint = "",
                                    suggestedDirection = "",
                                ),
                        ),
                    )

                else -> TransitionResult(state = ReplacementState.Idle)
            }
        }

        is ReplacementEvent.PreviewCandidate -> {
            when {
                isBaseStale(state.ctx, guard) ->
                    TransitionResult(
                        state = ReplacementState.Idle,
                        rejection = staleRejection(),
                    )

                event.candidateId in guard.candidateIds -> {
                    TransitionResult(
                        state = ReplacementState.Previewing(
                            ctx = state.ctx,
                            candidateId = event.candidateId,
                            previewRevisionId = null,
                        ),
                        effects = listOf(
                            Effect.BuildPreview(
                                baseRevisionId = state.ctx.baseRevisionId,
                                componentId = event.candidateId,
                            ),
                        ),
                    )
                }

                else -> reject(
                    state,
                    RejectionReason.CANDIDATE_NOT_FOUND,
                    TextKey.CANDIDATE_NOT_FOUND,
                )
            }
        }

        is ReplacementEvent.Cancel,
        is ReplacementEvent.Dismiss -> TransitionResult(state = ReplacementState.Idle)

        is ReplacementEvent.BuildEdited -> TransitionResult(
            state = ReplacementState.Idle,
            effects = listOf(Effect.SaveDraft(event.revision)),
        )

        else -> rejectInvalid(state)
    }

    private fun previewing(
        state: ReplacementState.Previewing,
        event: ReplacementEvent,
        guard: ReplacementGuard,
    ): TransitionResult<ReplacementState> = when (event) {
        is ReplacementEvent.PreviewCandidate -> {
            when {
                isBaseStale(state.ctx, guard) ->
                    reject(state, RejectionReason.APPLY_REVISION_STALE, TextKey.APPLY_REVISION_STALE)

                event.candidateId in guard.candidateIds ->
                    TransitionResult(
                        state = ReplacementState.Previewing(
                            ctx = state.ctx,
                            candidateId = event.candidateId,
                            previewRevisionId = null,
                        ),
                        effects = listOf(
                            Effect.BuildPreview(
                                baseRevisionId = state.ctx.baseRevisionId,
                                componentId = event.candidateId,
                            ),
                        ),
                    )

                else -> reject(
                    state,
                    RejectionReason.CANDIDATE_NOT_FOUND,
                    TextKey.CANDIDATE_NOT_FOUND,
                )
            }
        }

        is ReplacementEvent.PreviewBuilt -> TransitionResult(
            state = state.copy(previewRevisionId = event.previewRevision.id),
        )

        is ReplacementEvent.Apply -> {
            val previewRevisionId = state.previewRevisionId
            when {
                isBaseStale(state.ctx, guard) ->
                    TransitionResult(
                        state = ReplacementState.Idle,
                        rejection = staleRejection(),
                    )

                previewRevisionId == null ->
                    reject(
                        state,
                        RejectionReason.PREVIEW_NOT_READY,
                        TextKey.PREVIEW_NOT_READY,
                    )

                else -> TransitionResult(
                    state = ReplacementState.Rechecking(
                        ctx = state.ctx,
                        previewRevisionId = previewRevisionId,
                    ),
                    effects = listOf(
                        Effect.RecheckRevision(previewRevisionId),
                    ),
                )
            }
        }

        is ReplacementEvent.Cancel -> TransitionResult(
            state = ReplacementState.Candidates(state.ctx),
        )

        is ReplacementEvent.BuildEdited -> TransitionResult(
            state = ReplacementState.Idle,
            effects = listOf(Effect.SaveDraft(event.revision)),
        )

        is ReplacementEvent.Dismiss -> TransitionResult(state = ReplacementState.Idle)

        else -> rejectInvalid(state)
    }

    private fun rechecking(
        state: ReplacementState.Rechecking,
        event: ReplacementEvent,
        guard: ReplacementGuard,
    ): TransitionResult<ReplacementState> = when (event) {
        is ReplacementEvent.RecheckCompleted -> {
            if (event.revision.id != state.previewRevisionId) {
                TransitionResult(state = ReplacementState.Idle)
            } else {
                val newConflict = event.diagnosis.findPrimaryConflict()
                if (newConflict != null) {
                    val ctx = ReplacementContext(
                        baseRevisionId = event.revision.id,
                        conflictRuleId = newConflict.ruleId,
                        conflictId = newConflict.conflictId,
                    )
                    TransitionResult(
                        state = ReplacementState.Candidates(ctx),
                        effects = listOf(
                            Effect.LoadCandidates(
                                baseRevisionId = ctx.baseRevisionId,
                                conflictRuleId = ctx.conflictRuleId,
                                conflictId = ctx.conflictId,
                            ),
                        ),
                    )
                } else {
                    TransitionResult(
                        state = ReplacementState.Applied(event.revision.id),
                        effects = listOf(Effect.PersistApplied(event.revision)),
                    )
                }
            }
        }

        is ReplacementEvent.RecheckRejected -> TransitionResult(
            state = ReplacementState.Candidates(state.ctx),
        )

        is ReplacementEvent.Cancel -> TransitionResult(
            state = ReplacementState.Candidates(state.ctx),
        )

        is ReplacementEvent.BuildEdited -> TransitionResult(
            state = ReplacementState.Idle,
            effects = listOf(Effect.SaveDraft(event.revision)),
        )

        is ReplacementEvent.Dismiss -> TransitionResult(state = ReplacementState.Idle)

        else -> rejectInvalid(state)
    }

    private fun applied(
        state: ReplacementState.Applied,
        event: ReplacementEvent,
        guard: ReplacementGuard,
    ): TransitionResult<ReplacementState> = when (event) {
        is ReplacementEvent.ConflictSelected -> {
            val conflict = guard.diagnosis?.findConflict(event.conflictId)
            if (conflict != null) {
                val ctx = ReplacementContext(
                    baseRevisionId = guard.currentRevisionId,
                    conflictRuleId = event.ruleId,
                    conflictId = event.conflictId,
                )
                TransitionResult(
                    state = ReplacementState.Candidates(ctx),
                    effects = listOf(
                        Effect.LoadCandidates(
                            baseRevisionId = ctx.baseRevisionId,
                            conflictRuleId = ctx.conflictRuleId,
                            conflictId = ctx.conflictId,
                        ),
                    ),
                )
            } else {
                reject(state, RejectionReason.NO_CONFLICT_SELECTED, TextKey.CONFLICT_SELECTED_NONE)
            }
        }

        is ReplacementEvent.Cancel -> TransitionResult(state = ReplacementState.Idle)

        is ReplacementEvent.BuildEdited -> TransitionResult(
            state = ReplacementState.Idle,
            effects = listOf(Effect.SaveDraft(event.revision)),
        )

        is ReplacementEvent.Dismiss -> TransitionResult(state = ReplacementState.Idle)

        else -> rejectInvalid(state)
    }

    private fun noCandidate(
        state: ReplacementState.NoCandidate,
        event: ReplacementEvent,
        guard: ReplacementGuard,
    ): TransitionResult<ReplacementState> = when (event) {
        is ReplacementEvent.ConflictSelected -> {
            val conflict = guard.diagnosis?.findConflict(event.conflictId)
            if (conflict != null) {
                val ctx = ReplacementContext(
                    baseRevisionId = guard.currentRevisionId,
                    conflictRuleId = event.ruleId,
                    conflictId = event.conflictId,
                )
                TransitionResult(
                    state = ReplacementState.Candidates(ctx),
                    effects = listOf(
                        Effect.LoadCandidates(
                            baseRevisionId = ctx.baseRevisionId,
                            conflictRuleId = ctx.conflictRuleId,
                            conflictId = ctx.conflictId,
                        ),
                    ),
                )
            } else {
                reject(state, RejectionReason.NO_CONFLICT_SELECTED, TextKey.CONFLICT_SELECTED_NONE)
            }
        }

        is ReplacementEvent.Cancel,
        is ReplacementEvent.Dismiss -> TransitionResult(state = ReplacementState.Idle)

        is ReplacementEvent.BuildEdited -> TransitionResult(
            state = ReplacementState.Idle,
            effects = listOf(Effect.SaveDraft(event.revision)),
        )

        else -> rejectInvalid(state)
    }

    private fun isBaseStale(ctx: ReplacementContext, guard: ReplacementGuard): Boolean =
        ctx.baseRevisionId != guard.currentRevisionId

    private fun staleRejection(): Rejection = Rejection(
        reason = RejectionReason.APPLY_REVISION_STALE,
        textKey = TextKey.APPLY_REVISION_STALE,
    )

    private fun reject(
        state: ReplacementState,
        reason: RejectionReason,
        textKey: TextKey,
    ): TransitionResult<ReplacementState> = TransitionResult(
        state = state,
        rejection = Rejection(reason = reason, textKey = textKey),
    )

    private fun rejectInvalid(state: ReplacementState): TransitionResult<ReplacementState> =
        reject(state, RejectionReason.INVALID_STATE, TextKey.INVALID_STATE)

    private fun Diagnosis.findConflict(conflictId: String): RuleOutcome.Conflict? =
        outcomes.filterIsInstance<RuleOutcome.Conflict>().find { it.conflictId == conflictId }

    private fun Diagnosis.findPrimaryConflict(): RuleOutcome.Conflict? {
        val conflicts = outcomes.filterIsInstance<RuleOutcome.Conflict>()
        if (conflicts.isEmpty()) return null
        val blocker = conflicts
            .filter { it.severity == Severity.BLOCKER }
            .minByOrNull { it.ruleId }
        return blocker ?: conflicts.minByOrNull { it.ruleId }
    }
}
