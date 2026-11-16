package pcbuildmate.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pcbuildmate.contracts.Build
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.Diagnosis
import pcbuildmate.contracts.DiagnosisState
import pcbuildmate.contracts.OverallStatus
import pcbuildmate.contracts.Preferences
import pcbuildmate.contracts.RiskLevel
import pcbuildmate.contracts.TextKey
import pcbuildmate.contracts.UseCase
import pcbuildmate.contracts.WorkspaceUiState

/**
 * 诊断屏的语义测试，重点是 INV-03 在 UI 上的落地：
 * 属于旧 revision 的诊断**不得**显示成本次结论。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w411dp-h891dp")
class DiagnosticsScreenSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a diagnosis for the current revision is shown`() {
        render(state(revisionId = 2, diagnosisRevisionId = 2))

        compose.onNodeWithText("全部通过").assertExists()
    }

    @Test
    fun `a diagnosis for an older revision is not shown as the current result`() {
        render(state(revisionId = 2, diagnosisRevisionId = 1))

        compose.onAllNodesWithText("全部通过").assertCountEquals(0)
        compose.onAllNodesWithText("风险", substring = true).assertCountEquals(0)
    }

    @Test
    fun `running state says it is checking`() {
        render(state(revisionId = 2, diagnosisRevisionId = null).copy(diagnosisState = DiagnosisState.Running(2)))

        compose.onNodeWithText("正在检查兼容性…").assertExists()
    }

    @Test
    fun `failed state surfaces the reason`() {
        render(state(revisionId = 2, diagnosisRevisionId = null).copy(diagnosisState = DiagnosisState.Failed(2, "超时")))

        compose.onNodeWithText("检查失败：超时").assertExists()
    }

    @Test
    fun `banner keys are rendered as user facing copy`() {
        render(state(revisionId = 2, diagnosisRevisionId = 1).copy(banner = TextKey.APPLY_REVISION_STALE))

        compose.onNodeWithText("配置已过期，请重新检查").assertExists()
    }

    private fun render(state: WorkspaceUiState) {
        compose.setContent {
            DiagnosticsScreen(state = state, labels = emptyMap(), onIntent = {}, onBack = {})
        }
    }

    private fun state(revisionId: Long, diagnosisRevisionId: Long?): WorkspaceUiState {
        val revision = BuildRevision(
            id = revisionId, buildId = "b1", items = emptyList(), totalCny = 0,
            catalogueVersion = "v1", priceDate = "2026-11-01", userModified = false,
        )
        return WorkspaceUiState(
            build = Build(
                buildId = "b1", budgetCny = 8000, useCase = UseCase.GAMING, preferences = Preferences(),
                currentRevisionId = revisionId, userModified = false, createdAt = "", updatedAt = "",
            ),
            revision = revision,
            diagnosis = diagnosisRevisionId?.let { Diagnosis(it, emptyList(), OverallStatus.PASSED, RiskLevel.LOW, null, "") },
        )
    }
}
