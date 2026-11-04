package pcbuildmate.domain.state

import org.junit.Test
import pcbuildmate.contracts.Build
import pcbuildmate.contracts.BuildItem
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.Candidate
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.Diagnosis
import pcbuildmate.contracts.Effect
import pcbuildmate.contracts.FieldRef
import pcbuildmate.contracts.NoCandidateReason
import pcbuildmate.contracts.OverallStatus
import pcbuildmate.contracts.Preferences
import pcbuildmate.contracts.RejectionReason
import pcbuildmate.contracts.ReplacementContext
import pcbuildmate.contracts.ReplacementEvent
import pcbuildmate.contracts.ReplacementState
import pcbuildmate.contracts.RiskLevel
import pcbuildmate.contracts.RuleOutcome
import pcbuildmate.contracts.Severity
import pcbuildmate.contracts.SuggestionResult
import pcbuildmate.contracts.UseCase
import pcbuildmate.contracts.VerificationState
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ReplacementMachineTest {

    private val rev1 = BuildRevision(
        id = 1L,
        buildId = "b1",
        items = listOf(BuildItem("cpu-1", Category.CPU, 1, 1000L)),
        totalCny = 1000L,
        catalogueVersion = "v1",
        priceDate = "2026-01-01",
        userModified = false,
    )

    private val rev2 = BuildRevision(
        id = 2L,
        buildId = "b1",
        items = listOf(BuildItem("cpu-2", Category.CPU, 1, 2000L)),
        totalCny = 2000L,
        catalogueVersion = "v1",
        priceDate = "2026-01-01",
        userModified = false,
    )

    private val previewRev = BuildRevision(
        id = 3L,
        buildId = "b1",
        items = listOf(BuildItem("cpu-am5", Category.CPU, 1, 1200L)),
        totalCny = 1200L,
        catalogueVersion = "v1",
        priceDate = "2026-01-01",
        userModified = false,
    )

    private val build = Build(
        buildId = "b1",
        budgetCny = 5000L,
        useCase = UseCase.GAMING,
        preferences = Preferences(),
        currentRevisionId = 2L,
        userModified = false,
        createdAt = "2026-01-01",
        updatedAt = "2026-01-01",
    )

    private val conflict1 = RuleOutcome.Conflict(
        ruleId = "CPU_SOCKET",
        weakest = VerificationState.VERIFIED,
        conflictId = "c1",
        left = FieldRef("cpu-1", "socket"),
        leftValue = "AM5",
        right = FieldRef("mb-1", "socket"),
        rightValue = "LGA1700",
        severity = Severity.BLOCKER,
    )

    private val conflict2 = RuleOutcome.Conflict(
        ruleId = "RAM_TYPE",
        weakest = VerificationState.VERIFIED,
        conflictId = "c2",
        left = FieldRef("ram-1", "type"),
        leftValue = "DDR4",
        right = FieldRef("mb-1", "type"),
        rightValue = "DDR5",
        severity = Severity.MAJOR,
    )

    private val diagnosisWithC1 = Diagnosis(
        revisionId = 1L,
        outcomes = listOf(conflict1),
        overall = OverallStatus.CONFLICT,
        riskLevel = RiskLevel.HIGH,
        score = null,
        computedAt = "2026-01-01",
    )

    private val diagnosisWithBoth = Diagnosis(
        revisionId = 1L,
        outcomes = listOf(conflict2, conflict1),
        overall = OverallStatus.CONFLICT,
        riskLevel = RiskLevel.HIGH,
        score = null,
        computedAt = "2026-01-01",
    )

    private val diagnosisNoConflict = Diagnosis(
        revisionId = 3L,
        outcomes = emptyList(),
        overall = OverallStatus.PASSED,
        riskLevel = RiskLevel.LOW,
        score = null,
        computedAt = "2026-01-01",
    )

    private val diagnosisWithC2 = Diagnosis(
        revisionId = 3L,
        outcomes = listOf(conflict2),
        overall = OverallStatus.CONFLICT,
        riskLevel = RiskLevel.HIGH,
        score = null,
        computedAt = "2026-01-01",
    )

    private val candidate = Candidate(
        componentId = "cpu-am5",
        priceDeltaCny = 200L,
        specDelta = emptyMap(),
        affectsOtherRules = emptyList(),
        rank = 1,
    )

    private val candidateB = Candidate(
        componentId = "cpu-b",
        priceDeltaCny = 0L,
        specDelta = emptyMap(),
        affectsOtherRules = emptyList(),
        rank = 2,
    )

    private val noCandidateReason = NoCandidateReason(
        blockingConstraint = "PSU_CONTINUOUS_POWER:psu.continuousW",
        suggestedDirection = "increase_budget",
    )

    private val ctx1 = ReplacementContext(
        baseRevisionId = 1L,
        conflictRuleId = "CPU_SOCKET",
        conflictId = "c1",
    )

    private val ctx2Base1 = ReplacementContext(
        baseRevisionId = 1L,
        conflictRuleId = "RAM_TYPE",
        conflictId = "c2",
    )

    private val ctx2Base3 = ReplacementContext(
        baseRevisionId = 3L,
        conflictRuleId = "RAM_TYPE",
        conflictId = "c2",
    )

    private val resultWithCandidates = SuggestionResult(
        baseRevisionId = 1L,
        conflictRuleId = "CPU_SOCKET",
        conflictId = "c1",
        candidates = listOf(candidate, candidateB),
        noCandidate = null,
    )

    private val resultNoCandidate = SuggestionResult(
        baseRevisionId = 1L,
        conflictRuleId = "CPU_SOCKET",
        conflictId = "c1",
        candidates = emptyList(),
        noCandidate = noCandidateReason,
    )

    private val resultStaleBase = SuggestionResult(
        baseRevisionId = 99L,
        conflictRuleId = "CPU_SOCKET",
        conflictId = "c1",
        candidates = listOf(candidate),
        noCandidate = null,
    )

    private val freshGuard = ReplacementGuard(
        currentRevisionId = 1L,
        diagnosis = diagnosisWithC1,
        candidateIds = setOf("cpu-am5", "cpu-b"),
    )

    private val staleGuard = ReplacementGuard(
        currentRevisionId = 2L,
        diagnosis = diagnosisWithC1,
        candidateIds = setOf("cpu-am5", "cpu-b"),
    )

    private val noCandidateGuard = ReplacementGuard(
        currentRevisionId = 1L,
        diagnosis = diagnosisWithC1,
        candidateIds = setOf("other"),
    )

    private val emptyCandidatesGuard = ReplacementGuard(
        currentRevisionId = 1L,
        diagnosis = diagnosisWithC1,
        candidateIds = emptySet(),
    )

    private val bothConflictsGuard = ReplacementGuard(
        currentRevisionId = 1L,
        diagnosis = diagnosisWithBoth,
        candidateIds = setOf("cpu-am5", "cpu-b"),
    )

    private val appliedGuard = ReplacementGuard(
        currentRevisionId = 3L,
        diagnosis = diagnosisWithBoth,
        candidateIds = setOf("cpu-am5", "cpu-b"),
    )

    private val noDiagnosisGuard = ReplacementGuard(
        currentRevisionId = 1L,
        diagnosis = null,
        candidateIds = emptySet(),
    )

    data class Case(
        val name: String,
        val from: ReplacementState,
        val event: ReplacementEvent,
        val guard: ReplacementGuard,
        val expectState: ReplacementState,
        val expectEffects: List<Effect> = emptyList(),
        val expectRejection: RejectionReason? = null,
    )

    private val cases: List<Case> = listOf(
        // ---------- Idle ----------
        // R-01
        Case(
            name = "Idle + ConflictSelected existing -> Candidates + LoadCandidates",
            from = ReplacementState.Idle,
            event = ReplacementEvent.ConflictSelected("c1", "CPU_SOCKET"),
            guard = freshGuard,
            expectState = ReplacementState.Candidates(ctx1),
            expectEffects = listOf(Effect.LoadCandidates(1L, "CPU_SOCKET", "c1")),
        ),
        // R-02
        Case(
            name = "Idle + ConflictSelected missing -> rejected",
            from = ReplacementState.Idle,
            event = ReplacementEvent.ConflictSelected("c2", "RAM_TYPE"),
            guard = freshGuard,
            expectState = ReplacementState.Idle,
            expectRejection = RejectionReason.NO_CONFLICT_SELECTED,
        ),
        // R-03
        Case(
            name = "Idle + BuildEdited -> Idle + SaveDraft",
            from = ReplacementState.Idle,
            event = ReplacementEvent.BuildEdited(rev2),
            guard = freshGuard,
            expectState = ReplacementState.Idle,
            expectEffects = listOf(Effect.SaveDraft(rev2)),
        ),
        Case(
            name = "Idle + CandidatesLoaded -> rejected",
            from = ReplacementState.Idle,
            event = ReplacementEvent.CandidatesLoaded(1L, resultWithCandidates),
            guard = freshGuard,
            expectState = ReplacementState.Idle,
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "Idle + PreviewCandidate -> rejected",
            from = ReplacementState.Idle,
            event = ReplacementEvent.PreviewCandidate("cpu-am5"),
            guard = freshGuard,
            expectState = ReplacementState.Idle,
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "Idle + PreviewBuilt -> rejected",
            from = ReplacementState.Idle,
            event = ReplacementEvent.PreviewBuilt(previewRev),
            guard = freshGuard,
            expectState = ReplacementState.Idle,
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "Idle + Apply -> rejected",
            from = ReplacementState.Idle,
            event = ReplacementEvent.Apply,
            guard = freshGuard,
            expectState = ReplacementState.Idle,
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "Idle + Cancel -> rejected",
            from = ReplacementState.Idle,
            event = ReplacementEvent.Cancel,
            guard = freshGuard,
            expectState = ReplacementState.Idle,
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "Idle + RecheckCompleted -> rejected",
            from = ReplacementState.Idle,
            event = ReplacementEvent.RecheckCompleted(previewRev, diagnosisNoConflict),
            guard = freshGuard,
            expectState = ReplacementState.Idle,
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "Idle + RecheckRejected -> rejected",
            from = ReplacementState.Idle,
            event = ReplacementEvent.RecheckRejected("fail"),
            guard = freshGuard,
            expectState = ReplacementState.Idle,
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "Idle + Dismiss -> rejected",
            from = ReplacementState.Idle,
            event = ReplacementEvent.Dismiss,
            guard = freshGuard,
            expectState = ReplacementState.Idle,
            expectRejection = RejectionReason.INVALID_STATE,
        ),

        // ---------- Candidates ----------
        // R-04
        Case(
            name = "Candidates + ConflictSelected new existing -> switch conflict",
            from = ReplacementState.Candidates(ctx1),
            event = ReplacementEvent.ConflictSelected("c2", "RAM_TYPE"),
            guard = bothConflictsGuard,
            expectState = ReplacementState.Candidates(ctx2Base1),
            expectEffects = listOf(Effect.LoadCandidates(1L, "RAM_TYPE", "c2")),
        ),
        // R-05
        Case(
            name = "Candidates + ConflictSelected new missing -> rejected",
            from = ReplacementState.Candidates(ctx1),
            event = ReplacementEvent.ConflictSelected("c2", "RAM_TYPE"),
            guard = freshGuard,
            expectState = ReplacementState.Candidates(ctx1),
            expectRejection = RejectionReason.NO_CONFLICT_SELECTED,
        ),
        // R-06
        Case(
            name = "Candidates + CandidatesLoaded matching non-empty -> stay",
            from = ReplacementState.Candidates(ctx1),
            event = ReplacementEvent.CandidatesLoaded(1L, resultWithCandidates),
            guard = freshGuard,
            expectState = ReplacementState.Candidates(ctx1),
        ),
        // R-07
        Case(
            name = "Candidates + CandidatesLoaded matching empty -> NoCandidate",
            from = ReplacementState.Candidates(ctx1),
            event = ReplacementEvent.CandidatesLoaded(1L, resultNoCandidate),
            guard = freshGuard,
            expectState = ReplacementState.NoCandidate(ctx1, noCandidateReason),
        ),
        // R-08
        Case(
            name = "Candidates + CandidatesLoaded stale base -> drop to Idle",
            from = ReplacementState.Candidates(ctx1),
            event = ReplacementEvent.CandidatesLoaded(99L, resultWithCandidates),
            guard = freshGuard,
            expectState = ReplacementState.Idle,
        ),
        // R-09
        Case(
            name = "Candidates + PreviewCandidate existing -> Previewing",
            from = ReplacementState.Candidates(ctx1),
            event = ReplacementEvent.PreviewCandidate("cpu-am5"),
            guard = freshGuard,
            expectState = ReplacementState.Previewing(ctx1, "cpu-am5", null),
            expectEffects = listOf(Effect.BuildPreview(1L, "cpu-am5")),
        ),
        // R-10
        Case(
            name = "Candidates + PreviewCandidate missing -> rejected",
            from = ReplacementState.Candidates(ctx1),
            event = ReplacementEvent.PreviewCandidate("missing"),
            guard = freshGuard,
            expectState = ReplacementState.Candidates(ctx1),
            expectRejection = RejectionReason.CANDIDATE_NOT_FOUND,
        ),
        // R-11
        Case(
            name = "Candidates + PreviewCandidate stale base -> rejected + Idle",
            from = ReplacementState.Candidates(ctx1),
            event = ReplacementEvent.PreviewCandidate("cpu-am5"),
            guard = staleGuard,
            expectState = ReplacementState.Idle,
            expectRejection = RejectionReason.APPLY_REVISION_STALE,
        ),
        // R-12
        Case(
            name = "Candidates + Cancel -> Idle",
            from = ReplacementState.Candidates(ctx1),
            event = ReplacementEvent.Cancel,
            guard = freshGuard,
            expectState = ReplacementState.Idle,
        ),
        Case(
            name = "Candidates + Dismiss -> Idle",
            from = ReplacementState.Candidates(ctx1),
            event = ReplacementEvent.Dismiss,
            guard = freshGuard,
            expectState = ReplacementState.Idle,
        ),
        // R-13
        Case(
            name = "Candidates + BuildEdited -> Idle + SaveDraft",
            from = ReplacementState.Candidates(ctx1),
            event = ReplacementEvent.BuildEdited(rev2),
            guard = freshGuard,
            expectState = ReplacementState.Idle,
            expectEffects = listOf(Effect.SaveDraft(rev2)),
        ),
        Case(
            name = "Candidates + PreviewBuilt -> rejected",
            from = ReplacementState.Candidates(ctx1),
            event = ReplacementEvent.PreviewBuilt(previewRev),
            guard = freshGuard,
            expectState = ReplacementState.Candidates(ctx1),
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "Candidates + Apply -> rejected",
            from = ReplacementState.Candidates(ctx1),
            event = ReplacementEvent.Apply,
            guard = freshGuard,
            expectState = ReplacementState.Candidates(ctx1),
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "Candidates + RecheckCompleted -> rejected",
            from = ReplacementState.Candidates(ctx1),
            event = ReplacementEvent.RecheckCompleted(previewRev, diagnosisNoConflict),
            guard = freshGuard,
            expectState = ReplacementState.Candidates(ctx1),
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "Candidates + RecheckRejected -> rejected",
            from = ReplacementState.Candidates(ctx1),
            event = ReplacementEvent.RecheckRejected("fail"),
            guard = freshGuard,
            expectState = ReplacementState.Candidates(ctx1),
            expectRejection = RejectionReason.INVALID_STATE,
        ),

        // ---------- Previewing ----------
        // R-14
        Case(
            name = "Previewing + PreviewCandidate existing -> switch candidate",
            from = ReplacementState.Previewing(ctx1, "cpu-am5", null),
            event = ReplacementEvent.PreviewCandidate("cpu-b"),
            guard = freshGuard,
            expectState = ReplacementState.Previewing(ctx1, "cpu-b", null),
            expectEffects = listOf(Effect.BuildPreview(1L, "cpu-b")),
        ),
        Case(
            name = "Previewing + PreviewCandidate missing -> rejected",
            from = ReplacementState.Previewing(ctx1, "cpu-am5", null),
            event = ReplacementEvent.PreviewCandidate("missing"),
            guard = freshGuard,
            expectState = ReplacementState.Previewing(ctx1, "cpu-am5", null),
            expectRejection = RejectionReason.CANDIDATE_NOT_FOUND,
        ),
        Case(
            name = "Previewing + PreviewCandidate stale base -> rejected",
            from = ReplacementState.Previewing(ctx1, "cpu-am5", null),
            event = ReplacementEvent.PreviewCandidate("cpu-b"),
            guard = staleGuard,
            expectState = ReplacementState.Previewing(ctx1, "cpu-am5", null),
            expectRejection = RejectionReason.APPLY_REVISION_STALE,
        ),
        // R-15
        Case(
            name = "Previewing + PreviewBuilt -> stores previewRevisionId",
            from = ReplacementState.Previewing(ctx1, "cpu-am5", null),
            event = ReplacementEvent.PreviewBuilt(previewRev),
            guard = freshGuard,
            expectState = ReplacementState.Previewing(ctx1, "cpu-am5", previewRev.id),
        ),
        // R-16
        Case(
            name = "Previewing + Apply ready -> Rechecking",
            from = ReplacementState.Previewing(ctx1, "cpu-am5", previewRev.id),
            event = ReplacementEvent.Apply,
            guard = freshGuard,
            expectState = ReplacementState.Rechecking(ctx1, previewRev.id),
            expectEffects = listOf(Effect.RecheckRevision(previewRev.id)),
        ),
        // R-17
        Case(
            name = "Previewing + Apply stale base -> rejected + Idle",
            from = ReplacementState.Previewing(ctx1, "cpu-am5", previewRev.id),
            event = ReplacementEvent.Apply,
            guard = staleGuard,
            expectState = ReplacementState.Idle,
            expectRejection = RejectionReason.APPLY_REVISION_STALE,
        ),
        // R-18
        Case(
            name = "Previewing + Apply preview not ready -> rejected",
            from = ReplacementState.Previewing(ctx1, "cpu-am5", null),
            event = ReplacementEvent.Apply,
            guard = freshGuard,
            expectState = ReplacementState.Previewing(ctx1, "cpu-am5", null),
            expectRejection = RejectionReason.PREVIEW_NOT_READY,
        ),
        // R-19
        Case(
            name = "Previewing + Cancel -> Candidates",
            from = ReplacementState.Previewing(ctx1, "cpu-am5", previewRev.id),
            event = ReplacementEvent.Cancel,
            guard = freshGuard,
            expectState = ReplacementState.Candidates(ctx1),
        ),
        Case(
            name = "Previewing + BuildEdited -> Idle + SaveDraft",
            from = ReplacementState.Previewing(ctx1, "cpu-am5", previewRev.id),
            event = ReplacementEvent.BuildEdited(rev2),
            guard = freshGuard,
            expectState = ReplacementState.Idle,
            expectEffects = listOf(Effect.SaveDraft(rev2)),
        ),
        Case(
            name = "Previewing + Dismiss -> Idle",
            from = ReplacementState.Previewing(ctx1, "cpu-am5", previewRev.id),
            event = ReplacementEvent.Dismiss,
            guard = freshGuard,
            expectState = ReplacementState.Idle,
        ),
        Case(
            name = "Previewing + ConflictSelected -> rejected",
            from = ReplacementState.Previewing(ctx1, "cpu-am5", previewRev.id),
            event = ReplacementEvent.ConflictSelected("c2", "RAM_TYPE"),
            guard = bothConflictsGuard,
            expectState = ReplacementState.Previewing(ctx1, "cpu-am5", previewRev.id),
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "Previewing + CandidatesLoaded -> rejected",
            from = ReplacementState.Previewing(ctx1, "cpu-am5", previewRev.id),
            event = ReplacementEvent.CandidatesLoaded(1L, resultWithCandidates),
            guard = freshGuard,
            expectState = ReplacementState.Previewing(ctx1, "cpu-am5", previewRev.id),
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "Previewing + RecheckCompleted -> rejected",
            from = ReplacementState.Previewing(ctx1, "cpu-am5", previewRev.id),
            event = ReplacementEvent.RecheckCompleted(previewRev, diagnosisNoConflict),
            guard = freshGuard,
            expectState = ReplacementState.Previewing(ctx1, "cpu-am5", previewRev.id),
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "Previewing + RecheckRejected -> rejected",
            from = ReplacementState.Previewing(ctx1, "cpu-am5", previewRev.id),
            event = ReplacementEvent.RecheckRejected("fail"),
            guard = freshGuard,
            expectState = ReplacementState.Previewing(ctx1, "cpu-am5", previewRev.id),
            expectRejection = RejectionReason.INVALID_STATE,
        ),

        // ---------- Rechecking ----------
        // R-20
        Case(
            name = "Rechecking + RecheckCompleted matching no conflict -> Applied",
            from = ReplacementState.Rechecking(ctx1, previewRev.id),
            event = ReplacementEvent.RecheckCompleted(previewRev, diagnosisNoConflict),
            guard = freshGuard,
            expectState = ReplacementState.Applied(previewRev.id),
            expectEffects = listOf(Effect.PersistApplied(previewRev)),
        ),
        // R-21
        Case(
            name = "Rechecking + RecheckCompleted matching new conflict -> Candidates",
            from = ReplacementState.Rechecking(ctx1, previewRev.id),
            event = ReplacementEvent.RecheckCompleted(previewRev, diagnosisWithC2),
            guard = freshGuard,
            expectState = ReplacementState.Candidates(ctx2Base3),
            expectEffects = listOf(Effect.LoadCandidates(3L, "RAM_TYPE", "c2")),
        ),
        // R-22
        Case(
            name = "Rechecking + RecheckCompleted stale rev -> drop to Idle",
            from = ReplacementState.Rechecking(ctx1, previewRev.id),
            event = ReplacementEvent.RecheckCompleted(rev2, diagnosisNoConflict),
            guard = freshGuard,
            expectState = ReplacementState.Idle,
        ),
        // R-23
        Case(
            name = "Rechecking + RecheckRejected -> Candidates",
            from = ReplacementState.Rechecking(ctx1, previewRev.id),
            event = ReplacementEvent.RecheckRejected("fail"),
            guard = freshGuard,
            expectState = ReplacementState.Candidates(ctx1),
        ),
        // R-24
        Case(
            name = "Rechecking + Cancel -> Candidates",
            from = ReplacementState.Rechecking(ctx1, previewRev.id),
            event = ReplacementEvent.Cancel,
            guard = freshGuard,
            expectState = ReplacementState.Candidates(ctx1),
        ),
        Case(
            name = "Rechecking + BuildEdited -> Idle + SaveDraft",
            from = ReplacementState.Rechecking(ctx1, previewRev.id),
            event = ReplacementEvent.BuildEdited(rev2),
            guard = freshGuard,
            expectState = ReplacementState.Idle,
            expectEffects = listOf(Effect.SaveDraft(rev2)),
        ),
        Case(
            name = "Rechecking + Dismiss -> Idle",
            from = ReplacementState.Rechecking(ctx1, previewRev.id),
            event = ReplacementEvent.Dismiss,
            guard = freshGuard,
            expectState = ReplacementState.Idle,
        ),
        Case(
            name = "Rechecking + ConflictSelected -> rejected",
            from = ReplacementState.Rechecking(ctx1, previewRev.id),
            event = ReplacementEvent.ConflictSelected("c2", "RAM_TYPE"),
            guard = bothConflictsGuard,
            expectState = ReplacementState.Rechecking(ctx1, previewRev.id),
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "Rechecking + CandidatesLoaded -> rejected",
            from = ReplacementState.Rechecking(ctx1, previewRev.id),
            event = ReplacementEvent.CandidatesLoaded(1L, resultWithCandidates),
            guard = freshGuard,
            expectState = ReplacementState.Rechecking(ctx1, previewRev.id),
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "Rechecking + PreviewCandidate -> rejected",
            from = ReplacementState.Rechecking(ctx1, previewRev.id),
            event = ReplacementEvent.PreviewCandidate("cpu-am5"),
            guard = freshGuard,
            expectState = ReplacementState.Rechecking(ctx1, previewRev.id),
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "Rechecking + PreviewBuilt -> rejected",
            from = ReplacementState.Rechecking(ctx1, previewRev.id),
            event = ReplacementEvent.PreviewBuilt(previewRev),
            guard = freshGuard,
            expectState = ReplacementState.Rechecking(ctx1, previewRev.id),
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "Rechecking + Apply -> rejected",
            from = ReplacementState.Rechecking(ctx1, previewRev.id),
            event = ReplacementEvent.Apply,
            guard = freshGuard,
            expectState = ReplacementState.Rechecking(ctx1, previewRev.id),
            expectRejection = RejectionReason.INVALID_STATE,
        ),

        // ---------- Applied ----------
        // R-25
        Case(
            name = "Applied + ConflictSelected existing -> Candidates",
            from = ReplacementState.Applied(previewRev.id),
            event = ReplacementEvent.ConflictSelected("c2", "RAM_TYPE"),
            guard = appliedGuard,
            expectState = ReplacementState.Candidates(ctx2Base3),
            expectEffects = listOf(Effect.LoadCandidates(3L, "RAM_TYPE", "c2")),
        ),
        // R-26
        Case(
            name = "Applied + ConflictSelected missing -> rejected",
            from = ReplacementState.Applied(previewRev.id),
            event = ReplacementEvent.ConflictSelected("c2", "RAM_TYPE"),
            guard = ReplacementGuard(
                currentRevisionId = 3L,
                diagnosis = diagnosisWithC1,
                candidateIds = setOf("cpu-am5", "cpu-b"),
            ),
            expectState = ReplacementState.Applied(previewRev.id),
            expectRejection = RejectionReason.NO_CONFLICT_SELECTED,
        ),
        // R-27
        Case(
            name = "Applied + BuildEdited -> Idle + SaveDraft",
            from = ReplacementState.Applied(previewRev.id),
            event = ReplacementEvent.BuildEdited(rev2),
            guard = appliedGuard,
            expectState = ReplacementState.Idle,
            expectEffects = listOf(Effect.SaveDraft(rev2)),
        ),
        Case(
            name = "Applied + Cancel -> Idle",
            from = ReplacementState.Applied(previewRev.id),
            event = ReplacementEvent.Cancel,
            guard = appliedGuard,
            expectState = ReplacementState.Idle,
        ),
        Case(
            name = "Applied + Dismiss -> Idle",
            from = ReplacementState.Applied(previewRev.id),
            event = ReplacementEvent.Dismiss,
            guard = appliedGuard,
            expectState = ReplacementState.Idle,
        ),
        Case(
            name = "Applied + CandidatesLoaded -> rejected",
            from = ReplacementState.Applied(previewRev.id),
            event = ReplacementEvent.CandidatesLoaded(3L, resultWithCandidates),
            guard = appliedGuard,
            expectState = ReplacementState.Applied(previewRev.id),
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "Applied + PreviewCandidate -> rejected",
            from = ReplacementState.Applied(previewRev.id),
            event = ReplacementEvent.PreviewCandidate("cpu-am5"),
            guard = appliedGuard,
            expectState = ReplacementState.Applied(previewRev.id),
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "Applied + PreviewBuilt -> rejected",
            from = ReplacementState.Applied(previewRev.id),
            event = ReplacementEvent.PreviewBuilt(previewRev),
            guard = appliedGuard,
            expectState = ReplacementState.Applied(previewRev.id),
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "Applied + Apply -> rejected",
            from = ReplacementState.Applied(previewRev.id),
            event = ReplacementEvent.Apply,
            guard = appliedGuard,
            expectState = ReplacementState.Applied(previewRev.id),
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "Applied + RecheckCompleted -> rejected",
            from = ReplacementState.Applied(previewRev.id),
            event = ReplacementEvent.RecheckCompleted(previewRev, diagnosisNoConflict),
            guard = appliedGuard,
            expectState = ReplacementState.Applied(previewRev.id),
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "Applied + RecheckRejected -> rejected",
            from = ReplacementState.Applied(previewRev.id),
            event = ReplacementEvent.RecheckRejected("fail"),
            guard = appliedGuard,
            expectState = ReplacementState.Applied(previewRev.id),
            expectRejection = RejectionReason.INVALID_STATE,
        ),

        // ---------- NoCandidate ----------
        // R-28
        Case(
            name = "NoCandidate + ConflictSelected existing -> Candidates",
            from = ReplacementState.NoCandidate(ctx1, noCandidateReason),
            event = ReplacementEvent.ConflictSelected("c2", "RAM_TYPE"),
            guard = bothConflictsGuard,
            expectState = ReplacementState.Candidates(ctx2Base1),
            expectEffects = listOf(Effect.LoadCandidates(1L, "RAM_TYPE", "c2")),
        ),
        Case(
            name = "NoCandidate + ConflictSelected missing -> rejected",
            from = ReplacementState.NoCandidate(ctx1, noCandidateReason),
            event = ReplacementEvent.ConflictSelected("c2", "RAM_TYPE"),
            guard = freshGuard,
            expectState = ReplacementState.NoCandidate(ctx1, noCandidateReason),
            expectRejection = RejectionReason.NO_CONFLICT_SELECTED,
        ),
        // R-29
        Case(
            name = "NoCandidate + Cancel -> Idle",
            from = ReplacementState.NoCandidate(ctx1, noCandidateReason),
            event = ReplacementEvent.Cancel,
            guard = freshGuard,
            expectState = ReplacementState.Idle,
        ),
        Case(
            name = "NoCandidate + Dismiss -> Idle",
            from = ReplacementState.NoCandidate(ctx1, noCandidateReason),
            event = ReplacementEvent.Dismiss,
            guard = freshGuard,
            expectState = ReplacementState.Idle,
        ),
        // R-30
        Case(
            name = "NoCandidate + BuildEdited -> Idle + SaveDraft",
            from = ReplacementState.NoCandidate(ctx1, noCandidateReason),
            event = ReplacementEvent.BuildEdited(rev2),
            guard = freshGuard,
            expectState = ReplacementState.Idle,
            expectEffects = listOf(Effect.SaveDraft(rev2)),
        ),
        Case(
            name = "NoCandidate + CandidatesLoaded -> rejected",
            from = ReplacementState.NoCandidate(ctx1, noCandidateReason),
            event = ReplacementEvent.CandidatesLoaded(1L, resultWithCandidates),
            guard = freshGuard,
            expectState = ReplacementState.NoCandidate(ctx1, noCandidateReason),
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "NoCandidate + PreviewCandidate -> rejected",
            from = ReplacementState.NoCandidate(ctx1, noCandidateReason),
            event = ReplacementEvent.PreviewCandidate("cpu-am5"),
            guard = freshGuard,
            expectState = ReplacementState.NoCandidate(ctx1, noCandidateReason),
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "NoCandidate + PreviewBuilt -> rejected",
            from = ReplacementState.NoCandidate(ctx1, noCandidateReason),
            event = ReplacementEvent.PreviewBuilt(previewRev),
            guard = freshGuard,
            expectState = ReplacementState.NoCandidate(ctx1, noCandidateReason),
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "NoCandidate + Apply -> rejected",
            from = ReplacementState.NoCandidate(ctx1, noCandidateReason),
            event = ReplacementEvent.Apply,
            guard = freshGuard,
            expectState = ReplacementState.NoCandidate(ctx1, noCandidateReason),
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "NoCandidate + RecheckCompleted -> rejected",
            from = ReplacementState.NoCandidate(ctx1, noCandidateReason),
            event = ReplacementEvent.RecheckCompleted(previewRev, diagnosisNoConflict),
            guard = freshGuard,
            expectState = ReplacementState.NoCandidate(ctx1, noCandidateReason),
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "NoCandidate + RecheckRejected -> rejected",
            from = ReplacementState.NoCandidate(ctx1, noCandidateReason),
            event = ReplacementEvent.RecheckRejected("fail"),
            guard = freshGuard,
            expectState = ReplacementState.NoCandidate(ctx1, noCandidateReason),
            expectRejection = RejectionReason.INVALID_STATE,
        ),
    )

    @Test
    fun `replacement full matrix`() {
        cases.forEach { c ->
            val result = ReplacementMachine.transition(c.from, c.event, c.guard)
            assertEquals(
                c.expectState,
                result.state,
                "[${c.name}] state mismatch",
            )
            assertEquals(
                c.expectEffects,
                result.effects,
                "[${c.name}] effects mismatch",
            )
            assertEquals(
                c.expectRejection,
                result.rejection?.reason,
                "[${c.name}] rejection mismatch",
            )
        }
    }

    @Test
    fun `same input yields same result`() {
        cases.forEach { c ->
            val first = ReplacementMachine.transition(c.from, c.event, c.guard)
            val second = ReplacementMachine.transition(c.from, c.event, c.guard)
            assertEquals(first, second, "[${c.name}] deterministic transition")
        }
    }

    @Test
    fun `prohibited transitions`() {
        // Previewing -> Applied is impossible (Apply goes to Rechecking)
        val previewingReady = ReplacementState.Previewing(ctx1, "cpu-am5", previewRev.id)
        val applyResult = ReplacementMachine.transition(
            previewingReady,
            ReplacementEvent.Apply,
            freshGuard,
        )
        assertEquals(ReplacementState.Rechecking(ctx1, previewRev.id), applyResult.state)
        assertNotEquals(ReplacementState.Applied(previewRev.id), applyResult.state)

        // Candidates -> Applied is impossible
        assertEquals(
            RejectionReason.INVALID_STATE,
            ReplacementMachine.transition(
                ReplacementState.Candidates(ctx1),
                ReplacementEvent.Apply,
                freshGuard,
            ).rejection?.reason,
        )

        // NoCandidate -> Applied is impossible
        assertEquals(
            RejectionReason.INVALID_STATE,
            ReplacementMachine.transition(
                ReplacementState.NoCandidate(ctx1, noCandidateReason),
                ReplacementEvent.Apply,
                freshGuard,
            ).rejection?.reason,
        )

        // Apply old preview after base edited -> rejected + Idle
        assertEquals(
            ReplacementState.Idle,
            ReplacementMachine.transition(
                previewingReady,
                ReplacementEvent.Apply,
                staleGuard,
            ).state,
        )
        assertEquals(
            RejectionReason.APPLY_REVISION_STALE,
            ReplacementMachine.transition(
                previewingReady,
                ReplacementEvent.Apply,
                staleGuard,
            ).rejection?.reason,
        )

        // Rechecking accepts only matching revision's RecheckCompleted
        assertEquals(
            ReplacementState.Idle,
            ReplacementMachine.transition(
                ReplacementState.Rechecking(ctx1, previewRev.id),
                ReplacementEvent.RecheckCompleted(rev2, diagnosisNoConflict),
                freshGuard,
            ).state,
        )
    }
}
