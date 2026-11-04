package pcbuildmate.domain.state

import org.junit.Test
import pcbuildmate.contracts.Build
import pcbuildmate.contracts.BuildItem
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.Diagnosis
import pcbuildmate.contracts.DiagnosisEvent
import pcbuildmate.contracts.DiagnosisState
import pcbuildmate.contracts.Effect
import pcbuildmate.contracts.OverallStatus
import pcbuildmate.contracts.Preferences
import pcbuildmate.contracts.RejectionReason
import pcbuildmate.contracts.RiskLevel
import pcbuildmate.contracts.UseCase
import kotlin.test.assertEquals

class DiagnosisMachineTest {

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

    private val diagnosis = Diagnosis(
        revisionId = 1L,
        outcomes = emptyList(),
        overall = OverallStatus.PASSED,
        riskLevel = RiskLevel.LOW,
        score = null,
        computedAt = "2026-01-01",
    )

    data class Case(
        val name: String,
        val from: DiagnosisState,
        val event: DiagnosisEvent,
        val ctx: DiagnosisContext,
        val expectState: DiagnosisState,
        val expectEffects: List<Effect> = emptyList(),
        val expectRejection: RejectionReason? = null,
    )

    private val cases: List<Case> = listOf(
        // D-01
        Case(
            name = "Idle + StartCheck with build/revision -> Running",
            from = DiagnosisState.Idle,
            event = DiagnosisEvent.StartCheck,
            ctx = DiagnosisContext(build = build, revision = rev1, diagnosis = null),
            expectState = DiagnosisState.Running(rev1.id),
            expectEffects = listOf(Effect.RunDiagnosis(rev1.id)),
        ),
        // D-02
        Case(
            name = "Idle + StartCheck without build/revision -> rejected",
            from = DiagnosisState.Idle,
            event = DiagnosisEvent.StartCheck,
            ctx = DiagnosisContext(build = null, revision = null, diagnosis = null),
            expectState = DiagnosisState.Idle,
            expectRejection = RejectionReason.DIAGNOSTICS_REQUIRED,
        ),
        // D-03
        Case(
            name = "Idle + CheckCompleted -> rejected",
            from = DiagnosisState.Idle,
            event = DiagnosisEvent.CheckCompleted(rev1.id, diagnosis),
            ctx = DiagnosisContext(build = build, revision = rev1, diagnosis = null),
            expectState = DiagnosisState.Idle,
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "Idle + CheckFailed -> rejected",
            from = DiagnosisState.Idle,
            event = DiagnosisEvent.CheckFailed(rev1.id, "fail"),
            ctx = DiagnosisContext(build = build, revision = rev1, diagnosis = null),
            expectState = DiagnosisState.Idle,
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        // D-04
        Case(
            name = "Idle + BuildEdited -> Idle + SaveDraft",
            from = DiagnosisState.Idle,
            event = DiagnosisEvent.BuildEdited(rev2),
            ctx = DiagnosisContext(build = build, revision = rev1, diagnosis = null),
            expectState = DiagnosisState.Idle,
            expectEffects = listOf(Effect.SaveDraft(rev2)),
        ),
        // D-05
        Case(
            name = "Running + StartCheck -> ignore duplicate",
            from = DiagnosisState.Running(rev1.id),
            event = DiagnosisEvent.StartCheck,
            ctx = DiagnosisContext(build = build, revision = rev1, diagnosis = null),
            expectState = DiagnosisState.Running(rev1.id),
        ),
        // D-06
        Case(
            name = "Running + CheckCompleted matching -> Done",
            from = DiagnosisState.Running(rev1.id),
            event = DiagnosisEvent.CheckCompleted(rev1.id, diagnosis),
            ctx = DiagnosisContext(build = build, revision = rev1, diagnosis = null),
            expectState = DiagnosisState.Done(rev1.id),
        ),
        // D-07
        Case(
            name = "Running + CheckCompleted mismatch -> drop",
            from = DiagnosisState.Running(rev1.id),
            event = DiagnosisEvent.CheckCompleted(rev2.id, diagnosis),
            ctx = DiagnosisContext(build = build, revision = rev1, diagnosis = null),
            expectState = DiagnosisState.Running(rev1.id),
        ),
        // D-08
        Case(
            name = "Running + CheckFailed matching -> Failed",
            from = DiagnosisState.Running(rev1.id),
            event = DiagnosisEvent.CheckFailed(rev1.id, "fail"),
            ctx = DiagnosisContext(build = build, revision = rev1, diagnosis = null),
            expectState = DiagnosisState.Failed(rev1.id, "fail"),
        ),
        // D-09
        Case(
            name = "Running + CheckFailed mismatch -> drop",
            from = DiagnosisState.Running(rev1.id),
            event = DiagnosisEvent.CheckFailed(rev2.id, "fail"),
            ctx = DiagnosisContext(build = build, revision = rev1, diagnosis = null),
            expectState = DiagnosisState.Running(rev1.id),
        ),
        // D-10
        Case(
            name = "Running + BuildEdited -> Idle + Cancel + SaveDraft",
            from = DiagnosisState.Running(rev1.id),
            event = DiagnosisEvent.BuildEdited(rev2),
            ctx = DiagnosisContext(build = build, revision = rev1, diagnosis = null),
            expectState = DiagnosisState.Idle,
            expectEffects = listOf(Effect.CancelDiagnosis, Effect.SaveDraft(rev2)),
        ),
        // D-11
        Case(
            name = "Done + StartCheck with revision -> rerun current",
            from = DiagnosisState.Done(rev1.id),
            event = DiagnosisEvent.StartCheck,
            ctx = DiagnosisContext(build = build, revision = rev2, diagnosis = diagnosis),
            expectState = DiagnosisState.Running(rev2.id),
            expectEffects = listOf(Effect.RunDiagnosis(rev2.id)),
        ),
        Case(
            name = "Done + StartCheck without revision -> rejected",
            from = DiagnosisState.Done(rev1.id),
            event = DiagnosisEvent.StartCheck,
            ctx = DiagnosisContext(build = build, revision = null, diagnosis = diagnosis),
            expectState = DiagnosisState.Done(rev1.id),
            expectRejection = RejectionReason.DIAGNOSTICS_REQUIRED,
        ),
        // D-12
        Case(
            name = "Done + CheckCompleted -> rejected",
            from = DiagnosisState.Done(rev1.id),
            event = DiagnosisEvent.CheckCompleted(rev2.id, diagnosis),
            ctx = DiagnosisContext(build = build, revision = rev2, diagnosis = diagnosis),
            expectState = DiagnosisState.Done(rev1.id),
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "Done + CheckFailed -> rejected",
            from = DiagnosisState.Done(rev1.id),
            event = DiagnosisEvent.CheckFailed(rev2.id, "fail"),
            ctx = DiagnosisContext(build = build, revision = rev2, diagnosis = diagnosis),
            expectState = DiagnosisState.Done(rev1.id),
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        // D-13
        Case(
            name = "Done + BuildEdited -> Idle + SaveDraft",
            from = DiagnosisState.Done(rev1.id),
            event = DiagnosisEvent.BuildEdited(rev2),
            ctx = DiagnosisContext(build = build, revision = rev2, diagnosis = diagnosis),
            expectState = DiagnosisState.Idle,
            expectEffects = listOf(Effect.SaveDraft(rev2)),
        ),
        // D-14
        Case(
            name = "Failed + StartCheck with revision -> rerun current",
            from = DiagnosisState.Failed(rev1.id, "fail"),
            event = DiagnosisEvent.StartCheck,
            ctx = DiagnosisContext(build = build, revision = rev2, diagnosis = diagnosis),
            expectState = DiagnosisState.Running(rev2.id),
            expectEffects = listOf(Effect.RunDiagnosis(rev2.id)),
        ),
        Case(
            name = "Failed + StartCheck without revision -> rejected",
            from = DiagnosisState.Failed(rev1.id, "fail"),
            event = DiagnosisEvent.StartCheck,
            ctx = DiagnosisContext(build = build, revision = null, diagnosis = diagnosis),
            expectState = DiagnosisState.Failed(rev1.id, "fail"),
            expectRejection = RejectionReason.DIAGNOSTICS_REQUIRED,
        ),
        // D-15
        Case(
            name = "Failed + CheckCompleted -> rejected",
            from = DiagnosisState.Failed(rev1.id, "fail"),
            event = DiagnosisEvent.CheckCompleted(rev2.id, diagnosis),
            ctx = DiagnosisContext(build = build, revision = rev2, diagnosis = diagnosis),
            expectState = DiagnosisState.Failed(rev1.id, "fail"),
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        Case(
            name = "Failed + CheckFailed -> rejected",
            from = DiagnosisState.Failed(rev1.id, "fail"),
            event = DiagnosisEvent.CheckFailed(rev2.id, "fail2"),
            ctx = DiagnosisContext(build = build, revision = rev2, diagnosis = diagnosis),
            expectState = DiagnosisState.Failed(rev1.id, "fail"),
            expectRejection = RejectionReason.INVALID_STATE,
        ),
        // D-16
        Case(
            name = "Failed + BuildEdited -> Idle + SaveDraft",
            from = DiagnosisState.Failed(rev1.id, "fail"),
            event = DiagnosisEvent.BuildEdited(rev2),
            ctx = DiagnosisContext(build = build, revision = rev2, diagnosis = diagnosis),
            expectState = DiagnosisState.Idle,
            expectEffects = listOf(Effect.SaveDraft(rev2)),
        ),
    )

    @Test
    fun `diagnosis full matrix`() {
        cases.forEach { c ->
            val result = DiagnosisMachine.transition(c.from, c.event, c.ctx)
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
            val first = DiagnosisMachine.transition(c.from, c.event, c.ctx)
            val second = DiagnosisMachine.transition(c.from, c.event, c.ctx)
            assertEquals(first, second, "[${c.name}] deterministic transition")
        }
    }

    @Test
    fun `prohibited transitions`() {
        // Done -> Done by direct CheckCompleted
        assertEquals(
            DiagnosisState.Done(rev1.id),
            DiagnosisMachine.transition(
                DiagnosisState.Done(rev1.id),
                DiagnosisEvent.CheckCompleted(rev2.id, diagnosis),
                DiagnosisContext(build = build, revision = rev2, diagnosis = diagnosis),
            ).state,
        )

        // Failed -> Done is impossible (CheckCompleted rejected)
        assertEquals(
            RejectionReason.INVALID_STATE,
            DiagnosisMachine.transition(
                DiagnosisState.Failed(rev1.id, "fail"),
                DiagnosisEvent.CheckCompleted(rev2.id, diagnosis),
                DiagnosisContext(build = build, revision = rev2, diagnosis = diagnosis),
            ).rejection?.reason,
        )

        // Idle -> Done is impossible
        assertEquals(
            RejectionReason.INVALID_STATE,
            DiagnosisMachine.transition(
                DiagnosisState.Idle,
                DiagnosisEvent.CheckCompleted(rev1.id, diagnosis),
                DiagnosisContext(build = build, revision = rev1, diagnosis = null),
            ).rejection?.reason,
        )

        // BuildEdited must not auto-start check
        listOf(
            DiagnosisState.Idle,
            DiagnosisState.Done(rev1.id),
            DiagnosisState.Failed(rev1.id, "fail"),
        ).forEach { state ->
            val result = DiagnosisMachine.transition(
                state,
                DiagnosisEvent.BuildEdited(rev2),
                DiagnosisContext(build = build, revision = rev2, diagnosis = diagnosis),
            )
            assertEquals(DiagnosisState.Idle, result.state)
            assertEquals(listOf(Effect.SaveDraft(rev2)), result.effects)
            assertEquals(null, result.rejection)
        }
    }
}
