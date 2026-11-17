package pcbuildmate.oracle

import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.Diagnosis
import pcbuildmate.contracts.DiagnosisEvent
import pcbuildmate.contracts.DiagnosisState
import pcbuildmate.contracts.Effect
import pcbuildmate.contracts.OverallStatus
import pcbuildmate.contracts.ReplacementContext
import pcbuildmate.contracts.ReplacementEvent
import pcbuildmate.contracts.ReplacementState
import pcbuildmate.contracts.RiskLevel
import pcbuildmate.contracts.RuleOutcome
import pcbuildmate.contracts.FieldRef
import pcbuildmate.contracts.Severity
import pcbuildmate.contracts.VerificationState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 两部状态机参考模型的自身单测：不跑生产实现也能验证 docs/04 的几条硬规则。
 */
class StateMachineOracleTest {

    private fun revision(id: Long) = BuildRevision(
        id = id, buildId = "b", items = emptyList(), totalCny = 0,
        catalogueVersion = "", priceDate = "", userModified = false,
    )

    private fun diagnosis(revisionId: Long, outcomes: List<RuleOutcome>) = Diagnosis(
        revisionId = revisionId, outcomes = outcomes, overall = OverallStatus.PASSED,
        riskLevel = RiskLevel.LOW, score = null, computedAt = "",
    )

    private fun conflict(ruleId: String, severity: Severity = Severity.BLOCKER) = RuleOutcome.Conflict(
        ruleId = ruleId, weakest = VerificationState.VERIFIED, conflictId = "$ruleId:id",
        left = FieldRef("a", "x"), leftValue = "1", right = FieldRef("b", "y"), rightValue = "0",
        severity = severity,
    )

    // ---------------- 诊断状态机 ----------------

    @Test
    fun `start check without a build is rejected`() {
        val r = DiagnosisOracle.transition(
            DiagnosisState.Idle, DiagnosisEvent.StartCheck,
            OracleDiagnosisInput(hasBuild = false, revisionId = 7),
        )
        assertEquals(DiagnosisState.Idle, r.state)
        assertEquals("DIAGNOSTICS_REQUIRED", r.rejection?.reason?.name)
    }

    @Test
    fun `a completion for a different revision is dropped`() {
        val r = DiagnosisOracle.transition(
            DiagnosisState.Running(7), DiagnosisEvent.CheckCompleted(9, diagnosis(9, emptyList())),
            OracleDiagnosisInput(hasBuild = true, revisionId = 7),
        )
        assertEquals(DiagnosisState.Running(7), r.state)
        assertTrue(r.effects.isEmpty())
        assertNull(r.rejection)
    }

    @Test
    fun `editing while running cancels the diagnosis and saves a draft`() {
        val rev = revision(8)
        val r = DiagnosisOracle.transition(
            DiagnosisState.Running(7), DiagnosisEvent.BuildEdited(rev),
            OracleDiagnosisInput(hasBuild = true, revisionId = 7),
        )
        assertEquals(DiagnosisState.Idle, r.state)
        assertEquals(listOf(Effect.CancelDiagnosis, Effect.SaveDraft(rev)), r.effects)
    }

    @Test
    fun `done rejects a late completion instead of overwriting`() {
        val r = DiagnosisOracle.transition(
            DiagnosisState.Done(7), DiagnosisEvent.CheckCompleted(7, diagnosis(7, emptyList())),
            OracleDiagnosisInput(hasBuild = true, revisionId = 7),
        )
        assertEquals(DiagnosisState.Done(7), r.state)
        assertEquals("INVALID_STATE", r.rejection?.reason?.name)
    }

    @Test
    fun `restarting from done always uses the current revision`() {
        val r = DiagnosisOracle.transition(
            DiagnosisState.Done(7), DiagnosisEvent.StartCheck,
            OracleDiagnosisInput(hasBuild = true, revisionId = 9),
        )
        assertEquals(DiagnosisState.Running(9), r.state)
        assertEquals(listOf(Effect.RunDiagnosis(9)), r.effects)
    }

    // ---------------- 替换状态机 ----------------

    private val ctx = ReplacementContext(7, "GPU_CASE_LENGTH", "GPU_CASE_LENGTH:id")

    private fun input(rev: Long = 7, diag: Diagnosis? = null, candidates: Set<String> = emptySet()) =
        OracleReplacementInput(rev, diag, candidates)

    @Test
    fun `previewing cannot jump straight to applied`() {
        val r = ReplacementOracle.transition(
            ReplacementState.Previewing(ctx, "cand", null), ReplacementEvent.Apply, input(),
        )
        assertEquals(ReplacementState.Previewing(ctx, "cand", null), r.state)
        assertEquals("PREVIEW_NOT_READY", r.rejection?.reason?.name)
    }

    @Test
    fun `applying with a stale base returns to idle`() {
        val r = ReplacementOracle.transition(
            ReplacementState.Previewing(ctx, "cand", 8), ReplacementEvent.Apply, input(rev = 9),
        )
        assertEquals(ReplacementState.Idle, r.state)
        assertEquals("APPLY_REVISION_STALE", r.rejection?.reason?.name)
    }

    @Test
    fun `applying a ready preview enters rechecking`() {
        val r = ReplacementOracle.transition(
            ReplacementState.Previewing(ctx, "cand", 8), ReplacementEvent.Apply, input(),
        )
        assertEquals(ReplacementState.Rechecking(ctx, 8), r.state)
        assertEquals(listOf(Effect.RecheckRevision(8)), r.effects)
    }

    @Test
    fun `a clean recheck applies and persists`() {
        val rev = revision(8)
        val r = ReplacementOracle.transition(
            ReplacementState.Rechecking(ctx, 8),
            ReplacementEvent.RecheckCompleted(rev, diagnosis(8, emptyList())),
            input(),
        )
        assertEquals(ReplacementState.Applied(8), r.state)
        assertEquals(listOf(Effect.PersistApplied(rev)), r.effects)
    }

    @Test
    fun `a recheck that finds a new blocker goes back to candidates`() {
        val rev = revision(8)
        val r = ReplacementOracle.transition(
            ReplacementState.Rechecking(ctx, 8),
            ReplacementEvent.RecheckCompleted(rev, diagnosis(8, listOf(conflict("PSU_CONTINUOUS_POWER")))),
            input(),
        )
        assertTrue(r.state is ReplacementState.Candidates)
        assertEquals("PSU_CONTINUOUS_POWER", (r.state as ReplacementState.Candidates).ctx.conflictRuleId)
        assertEquals(
            listOf(Effect.LoadCandidates(8, "PSU_CONTINUOUS_POWER", "PSU_CONTINUOUS_POWER:id")),
            r.effects,
        )
    }

    @Test
    fun `a stale recheck result is discarded`() {
        val r = ReplacementOracle.transition(
            ReplacementState.Rechecking(ctx, 8),
            ReplacementEvent.RecheckCompleted(revision(9), diagnosis(9, emptyList())),
            input(),
        )
        assertEquals(ReplacementState.Idle, r.state)
        assertTrue(r.effects.isEmpty())
    }

    @Test
    fun `selecting a conflict that is not in the diagnosis is rejected`() {
        val r = ReplacementOracle.transition(
            ReplacementState.Idle,
            ReplacementEvent.ConflictSelected("nope", "GPU_CASE_LENGTH"),
            input(diag = diagnosis(7, listOf(conflict("GPU_CASE_LENGTH")))),
        )
        assertEquals(ReplacementState.Idle, r.state)
        assertEquals("NO_CONFLICT_SELECTED", r.rejection?.reason?.name)
    }

    @Test
    fun `candidates loaded for a stale base are dropped silently`() {
        val r = ReplacementOracle.transition(
            ReplacementState.Candidates(ctx),
            ReplacementEvent.CandidatesLoaded(
                6,
                pcbuildmate.contracts.SuggestionResult(6, "GPU_CASE_LENGTH", "GPU_CASE_LENGTH:id", emptyList(), null),
            ),
            input(rev = 7),
        )
        assertEquals(ReplacementState.Idle, r.state)
        assertNull(r.rejection)
    }
}
