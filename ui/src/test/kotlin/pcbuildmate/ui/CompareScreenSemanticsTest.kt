package pcbuildmate.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.ComparisonResult
import pcbuildmate.contracts.TextKey

/**
 * 比较屏的语义测试：两份配置是下限，过期诊断只显示"请重新检查"。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w411dp-h891dp")
class CompareScreenSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `one revision is not enough to compare`() {
        render(ComparisonResult(buildIds = listOf("b1"), revisions = listOf(revision(1)), diagnoses = listOf(null), deltas = emptyList(), warnings = emptyList()))

        compose.onNodeWithText("至少保存两份配置才能比较").assertExists()
    }

    @Test
    fun `a stale diagnosis is announced instead of shown`() {
        render(
            ComparisonResult(
                buildIds = listOf("b1", "b2"),
                revisions = listOf(revision(1), revision(2)),
                diagnoses = listOf(null, null),
                deltas = emptyList(),
                warnings = listOf(TextKey.COMPARE_DIAGNOSTICS_STALE),
            ),
        )

        compose.onNodeWithText("有配置的诊断已过期；该列只显示“请重新检查”，不展示旧结论。").assertExists()
    }

    @Test
    fun `two revisions without warnings render the comparison`() {
        render(
            ComparisonResult(
                buildIds = listOf("b1", "b2"),
                revisions = listOf(revision(1), revision(2)),
                diagnoses = listOf(null, null),
                deltas = emptyList(),
                warnings = emptyList(),
            ),
        )

        compose.onNodeWithText("构建比较").assertExists()
    }

    private fun render(result: ComparisonResult) {
        compose.setContent { CompareScreen(result = result, labels = emptyMap(), onBack = {}) }
    }

    private fun revision(id: Long) = BuildRevision(
        id = id, buildId = "b$id", items = emptyList(), totalCny = 0,
        catalogueVersion = "v1", priceDate = "2026-11-01", userModified = false,
    )
}
