package pcbuildmate.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pcbuildmate.contracts.Build
import pcbuildmate.contracts.BuildItem
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.Diagnosis
import pcbuildmate.contracts.OverallStatus
import pcbuildmate.contracts.Preferences
import pcbuildmate.contracts.RiskLevel
import pcbuildmate.contracts.UseCase
import pcbuildmate.contracts.WorkspaceUiState

/**
 * 工作区的语义测试：编辑之后，旧诊断必须变成一条"请重新检查"的横幅，
 * 而不是悄悄消失（用户会以为配置已经被检查过）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w411dp-h891dp")
class WorkspaceScreenSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `a stale diagnosis turns into a recheck banner`() {
        render(state(revisionId = 2, diagnosisRevisionId = 1))

        compose.onNodeWithText("配置已修改，请重新检查兼容性").assertExists()
    }

    @Test
    fun `a matching diagnosis does not show the stale banner`() {
        render(state(revisionId = 2, diagnosisRevisionId = 2))

        compose.onAllNodesWithText("配置已修改，请重新检查兼容性").assertCountEquals(0)
    }

    @Test
    fun `undo is hidden when there is nothing to undo`() {
        render(state(revisionId = 2, diagnosisRevisionId = 2))

        compose.onAllNodesWithText("撤销上一步").assertCountEquals(0)
    }

    @Test
    fun `undo is offered when the state allows it`() {
        render(state(revisionId = 2, diagnosisRevisionId = 2).copy(canUndo = true))

        compose.onNodeWithText("撤销上一步").assertExists()
    }

    @Test
    fun `the 3d clearance entry is reachable`() {
        var previewed = false
        compose.setContent {
            WorkspaceScreen(
                state = state(revisionId = 2, diagnosisRevisionId = 2),
                labels = emptyMap(),
                weights = emptyMap(),
                onIntent = {},
                onCheck = {},
                onCompare = {},
                onPreview3d = { previewed = true },
                onBack = {},
            )
        }

        compose.onNodeWithText("3D 间隙预览").performClick()
        compose.waitForIdle()

        assertTrue("点 3D 入口应回调 onPreview3d", previewed)
    }

    private fun render(state: WorkspaceUiState) {
        compose.setContent {
            WorkspaceScreen(
                state = state,
                labels = emptyMap(),
                weights = emptyMap(),
                onIntent = {},
                onCheck = {},
                onCompare = {},
                onBack = {},
            )
        }
    }

    private fun state(revisionId: Long, diagnosisRevisionId: Long): WorkspaceUiState {
        val item = BuildItem("gpu-demo-337", Category.GPU, 1, 3400)
        val revision = BuildRevision(
            id = revisionId, buildId = "b1", items = listOf(item), totalCny = 3400,
            catalogueVersion = "v1", priceDate = "2026-11-01", userModified = false,
        )
        return WorkspaceUiState(
            build = Build(
                buildId = "b1", budgetCny = 8000, useCase = UseCase.GAMING, preferences = Preferences(),
                currentRevisionId = revisionId, userModified = false, createdAt = "", updatedAt = "",
            ),
            revision = revision,
            diagnosis = Diagnosis(
                revisionId = diagnosisRevisionId, outcomes = emptyList(),
                overall = OverallStatus.PASSED, riskLevel = RiskLevel.LOW, score = null, computedAt = "",
            ),
        )
    }
}
