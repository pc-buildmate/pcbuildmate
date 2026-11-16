package pcbuildmate.app

import org.junit.Assert.assertTrue
import org.junit.Test
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.Candidate
import pcbuildmate.contracts.Diagnosis
import pcbuildmate.contracts.FieldRef
import pcbuildmate.contracts.NoCandidateReason
import pcbuildmate.contracts.OverallStatus
import pcbuildmate.contracts.ReplacementContext
import pcbuildmate.contracts.ReplacementEvent
import pcbuildmate.contracts.ReplacementState
import pcbuildmate.contracts.RiskLevel
import pcbuildmate.contracts.RuleOutcome
import pcbuildmate.contracts.Severity
import pcbuildmate.contracts.SuggestionResult
import pcbuildmate.contracts.TransitionResult
import pcbuildmate.contracts.VerificationState
import pcbuildmate.domain.state.ReplacementGuard
import pcbuildmate.domain.state.ReplacementMachine
import pcbuildmate.oracle.OracleReplacementInput
import pcbuildmate.oracle.ReplacementOracle

/**
 * C.2 替换状态机：生产实现 vs 参考模型（docs/04 §4.4）。
 *
 * 6 状态 × 10 事件 × 3 种 guard 上下文，比较下一状态、Effects 顺序、拒绝原因。
 * 重点看五条禁止转移：Previewing→Applied、Candidates→Applied、NoCandidate→Applied、
 * 过期 base 继续应用、Rechecking 接受旧 rev——这些在矩阵里必须都是"不进入 Applied"。
 */
class ReplacementOracleCrossCheckTest {

    private val rev7 = revision(7)
    private val rev8 = revision(8)
    private val rev9 = revision(9)
    private val ctx7 = ReplacementContext(7, "GPU_CASE_LENGTH", "GPU_CASE_LENGTH:gpu.lengthMm:case.maxGpuLengthMm")
    private val ctx6 = ctx7.copy(baseRevisionId = 6)
    private val conflict = RuleOutcome.Conflict(
        ruleId = "GPU_CASE_LENGTH",
        weakest = VerificationState.VERIFIED,
        conflictId = ctx7.conflictId,
        left = FieldRef("gpu", "lengthMm"),
        leftValue = "337",
        right = FieldRef("case", "maxGpuLengthMm"),
        rightValue = "320",
        severity = Severity.BLOCKER,
    )
    private val diagWithConflict = diagnosis(7, listOf(conflict))
    private val diagClean = diagnosis(8, emptyList())
    private val candidate = Candidate("cand-300", 100, emptyMap(), emptyList(), 1)
    private val noReason = NoCandidateReason("PSU_CONTINUOUS_POWER:psu.continuousW", "加预算")

    @Test
    fun `replacement machine agrees with the oracle on the 6x10 matrix`() {
        val states = listOf(
            ReplacementState.Idle,
            ReplacementState.Candidates(ctx7),
            ReplacementState.Candidates(ctx6),
            ReplacementState.Previewing(ctx7, "cand-300", null),
            ReplacementState.Previewing(ctx7, "cand-300", 8),
            ReplacementState.Rechecking(ctx7, 8),
            ReplacementState.Applied(8),
            ReplacementState.NoCandidate(ctx7, noReason),
            ReplacementState.NoCandidate(null, noReason),
        )
        val events = listOf(
            ReplacementEvent.ConflictSelected(ctx7.conflictId, ctx7.conflictRuleId),
            ReplacementEvent.ConflictSelected("MISSING_CONFLICT", "GPU_CASE_LENGTH"),
            ReplacementEvent.CandidatesLoaded(7, SuggestionResult(7, "GPU_CASE_LENGTH", ctx7.conflictId, listOf(candidate), null)),
            ReplacementEvent.CandidatesLoaded(7, SuggestionResult(7, "GPU_CASE_LENGTH", ctx7.conflictId, emptyList(), noReason)),
            ReplacementEvent.CandidatesLoaded(9, SuggestionResult(9, "GPU_CASE_LENGTH", ctx7.conflictId, listOf(candidate), null)),
            ReplacementEvent.PreviewCandidate("cand-300"),
            ReplacementEvent.PreviewCandidate("missing"),
            ReplacementEvent.PreviewBuilt(rev8),
            ReplacementEvent.Apply,
            ReplacementEvent.Cancel,
            ReplacementEvent.RecheckCompleted(rev8, diagClean),
            ReplacementEvent.RecheckCompleted(rev8, diagWithConflict),
            ReplacementEvent.RecheckCompleted(rev9, diagClean),
            ReplacementEvent.RecheckRejected("boom"),
            ReplacementEvent.BuildEdited(rev9),
            ReplacementEvent.Dismiss,
        )
        val contexts = listOf(
            Triple(
                ReplacementGuard(currentRevisionId = 7, diagnosis = diagWithConflict, candidateIds = setOf("cand-300")),
                OracleReplacementInput(7, diagWithConflict, setOf("cand-300")),
                "current+conflict+candidate",
            ),
            Triple(
                ReplacementGuard(currentRevisionId = 9, diagnosis = diagWithConflict, candidateIds = setOf("cand-300")),
                OracleReplacementInput(9, diagWithConflict, setOf("cand-300")),
                "stale-base",
            ),
            Triple(
                ReplacementGuard(currentRevisionId = 7, diagnosis = diagClean, candidateIds = emptySet()),
                OracleReplacementInput(7, diagClean, emptySet()),
                "no-conflict-no-candidate",
            ),
        )

        val failures = mutableListOf<String>()
        for ((guard, oracleInput, ctxName) in contexts) {
            for (state in states) {
                for (event in events) {
                    val prod = ReplacementMachine.transition(state, event, guard)
                    val oracle = ReplacementOracle.transition(state, event, oracleInput)
                    if (!same(prod, oracle)) {
                        failures += "${describeState(state)} x ${eventName(event)} [$ctxName]\n" +
                            "      生产：${describe(prod)}\n      oracle：${describe(oracle)}"
                    }
                }
            }
        }

        assertTrue(
            "共 ${failures.size} 处不一致：\n" + failures.take(12).joinToString("\n"),
            failures.isEmpty(),
        )
    }

    private fun same(a: TransitionResult<ReplacementState>, b: TransitionResult<ReplacementState>): Boolean =
        a.state == b.state && a.effects == b.effects && a.rejection?.reason == b.rejection?.reason

    private fun describe(r: TransitionResult<ReplacementState>): String =
        "${describeState(r.state)} effects=${r.effects} rejection=${r.rejection?.reason}"

    private fun describeState(s: ReplacementState): String = when (s) {
        is ReplacementState.Idle -> "Idle"
        is ReplacementState.Candidates -> "Cand(base=${s.ctx.baseRevisionId})"
        is ReplacementState.Previewing -> "Prev(${s.candidateId},${s.previewRevisionId})"
        is ReplacementState.Rechecking -> "Rech(${s.previewRevisionId})"
        is ReplacementState.Applied -> "Appl(${s.newRevisionId})"
        is ReplacementState.NoCandidate -> "NoC(base=${s.ctx?.baseRevisionId})"
    }

    private fun eventName(e: ReplacementEvent): String = when (e) {
        is ReplacementEvent.ConflictSelected -> "ConfSel(${e.conflictId.take(12)})"
        is ReplacementEvent.CandidatesLoaded -> "CandLoaded(base=${e.baseRevisionId},n=${e.result.candidates.size})"
        is ReplacementEvent.PreviewCandidate -> "PreviewCand(${e.candidateId})"
        is ReplacementEvent.PreviewBuilt -> "PreviewBuilt(${e.previewRevision.id})"
        is ReplacementEvent.Apply -> "Apply"
        is ReplacementEvent.Cancel -> "Cancel"
        is ReplacementEvent.RecheckCompleted -> "RechDone(${e.revision.id},conflicts=${e.diagnosis.outcomes.size})"
        is ReplacementEvent.RecheckRejected -> "RechRej"
        is ReplacementEvent.BuildEdited -> "Edited(${e.revision.id})"
        is ReplacementEvent.Dismiss -> "Dismiss"
    }

    private fun diagnosis(revisionId: Long, outcomes: List<RuleOutcome>) = Diagnosis(
        revisionId = revisionId,
        outcomes = outcomes,
        overall = if (outcomes.isEmpty()) OverallStatus.PASSED else OverallStatus.CONFLICT,
        riskLevel = if (outcomes.isEmpty()) RiskLevel.LOW else RiskLevel.HIGH,
        score = null,
        computedAt = "",
    )

    private fun revision(id: Long) = BuildRevision(
        id = id, buildId = "b1", items = emptyList(), totalCny = 0,
        catalogueVersion = "v1", priceDate = "2026-11-01", userModified = false,
    )
}
