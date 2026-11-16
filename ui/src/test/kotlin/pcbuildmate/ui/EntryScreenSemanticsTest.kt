package pcbuildmate.ui

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pcbuildmate.contracts.Build
import pcbuildmate.contracts.Preferences
import pcbuildmate.contracts.UseCase

/**
 * Entry 屏的 Compose 语义测试（JVM + Robolectric，不需要设备）。
 *
 * 这是 `HANDOFF` 里"UI 只有真机 smoke、没有 Compose semantics"的第一块：
 * 它验证的是**交互契约**——输入什么、点了会传出什么——而不是像素。
 */
@RunWith(RobolectricTestRunner::class)
// 默认 Robolectric 屏幕只有 320×470，Entry 的按钮会落在可视区外，点击不生效。
@Config(sdk = [31], qualifiers = "w411dp-h891dp")
class EntryScreenSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `default budget and use case are passed to the recommender`() {
        var received: Pair<Long, UseCase>? = null
        compose.setContent {
            EntryScreen(onStartDemo = {}, onRecommend = { budget, useCase -> received = budget to useCase })
        }

        compose.onNodeWithText("生成配置").performClick()

        assertEquals(8000L to UseCase.GAMING, received)
    }

    @Test
    fun `edited budget and selected use case reach the callback`() {
        var received: Pair<Long, UseCase>? = null
        compose.setContent {
            EntryScreen(onStartDemo = {}, onRecommend = { budget, useCase -> received = budget to useCase })
        }

        compose.onNodeWithText("8000").performTextReplacement("5000")
        compose.onNodeWithText("办公/学习").performClick()
        compose.onNodeWithText("生成配置").performClick()

        assertEquals(5000L to UseCase.STUDY_OFFICE, received)
    }

    @Test
    fun `empty budget disables the generate button`() {
        compose.setContent { EntryScreen(onStartDemo = {}, onRecommend = { _, _ -> }) }

        compose.onNodeWithText("8000").performTextReplacement("")

        compose.onNodeWithText("生成配置").assertIsNotEnabled()
    }

    @Test
    fun `demo entry, saved builds and errors are all reachable`() {
        var demo = false
        var opened: String? = null
        compose.setContent {
            EntryScreen(
                onStartDemo = { demo = true },
                onRecommend = { _, _ -> },
                error = "预算必须大于 0",
                savedBuilds = listOf(savedBuild("b-1")),
                onOpenSaved = { opened = it },
            )
        }

        compose.onNodeWithText("预算必须大于 0").assertExists()
        compose.onNodeWithText("生成配置").assertIsEnabled()
        compose.onNodeWithText("载入演示配置 · ¥7,800").performClick()
        compose.onNodeWithText("继续上次配置 · 已保存 1 份").performClick()
        compose.waitForIdle()

        assertTrue("点演示入口应回调 onStartDemo", demo)
        assertEquals("b-1", opened)
    }

    private fun savedBuild(id: String) = Build(
        buildId = id,
        budgetCny = 8000,
        useCase = UseCase.GAMING,
        preferences = Preferences(),
        currentRevisionId = 1,
        userModified = false,
        createdAt = "",
        updatedAt = "",
    )
}
