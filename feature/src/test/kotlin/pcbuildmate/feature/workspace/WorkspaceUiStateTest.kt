package pcbuildmate.feature.workspace

import org.junit.Test
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.Diagnosis
import pcbuildmate.contracts.OverallStatus
import pcbuildmate.contracts.RiskLevel
import pcbuildmate.contracts.WorkspaceUiState
import kotlin.test.assertNull

class WorkspaceUiStateTest {

    @Test
    fun `stale diagnosis is not exposed as current`() {
        val revision = BuildRevision(
            id = 2,
            buildId = "b",
            items = emptyList(),
            totalCny = 0,
            catalogueVersion = "v1",
            priceDate = "2026-11-04",
            userModified = false,
        )
        val stale = Diagnosis(
            revisionId = 1,
            outcomes = emptyList(),
            overall = OverallStatus.PASSED,
            riskLevel = RiskLevel.LOW,
            score = 100,
            computedAt = "2026-11-04",
        )

        val state = WorkspaceUiState(revision = revision, diagnosis = stale)

        assertNull(state.currentDiagnosis, "INV-03: 过期诊断不得作为当前结论")
    }
}
