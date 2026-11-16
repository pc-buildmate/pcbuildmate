package pcbuildmate.ui.preview

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import pcbuildmate.contracts.BuildItem
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.CataloguePort
import pcbuildmate.contracts.CatalogueRecord
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.FieldValue
import pcbuildmate.contracts.VerificationState

/**
 * 3D 间隙预览的语义测试：空态、机箱显隐开关、重置视角。
 * 画布本身不参与语义（Canvas 没有文本节点），这里测的是它能被操作的那部分。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [31], qualifiers = "w411dp-h891dp")
class Preview3DScreenSemanticsTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun `without a revision the screen explains what to do`() {
        compose.setContent { Preview3DScreen(revision = null, catalogue = FakeCatalogue(), onBack = {}) }

        compose.onNodeWithText("还没有配置，先载入演示配置或生成一份配置。").assertExists()
    }

    @Test
    fun `the case ghost can be hidden and shown again`() {
        compose.setContent {
            Preview3DScreen(revision = revisionWithGpuAndCase(), catalogue = FakeCatalogue(), onBack = {})
        }

        compose.onAllNodesWithText("还没有配置，先载入演示配置或生成一份配置。").assertCountEquals(0)
        compose.onNodeWithText("隐藏机箱").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("显示机箱").assertExists()
    }

    @Test
    fun `the view can be reset`() {
        compose.setContent {
            Preview3DScreen(revision = revisionWithGpuAndCase(), catalogue = FakeCatalogue(), onBack = {})
        }

        compose.onNodeWithText("重置视角").performClick()
        compose.waitForIdle()

        compose.onNodeWithText("3D 间隙预览").assertExists()
    }

    private fun revisionWithGpuAndCase(): BuildRevision {
        val items = listOf(
            BuildItem("gpu", Category.GPU, 1, 3400),
            BuildItem("case", Category.CASE, 1, 500),
        )
        return BuildRevision(
            id = 1, buildId = "b1", items = items,
            totalCny = items.sumOf { it.quantity.toLong() * it.unitPriceCny },
            catalogueVersion = "v1", priceDate = "2026-11-01", userModified = false,
        )
    }

    private class FakeCatalogue : CataloguePort {
        private val records = listOf(
            record("gpu", Category.GPU, mapOf("lengthMm" to "337", "thicknessMm" to "61", "slotWidth" to "3")),
            record("case", Category.CASE, mapOf("maxGpuLengthMm" to "320", "maxGpuThicknessMm" to "60", "maxGpuSlots" to "3")),
        )
        override fun getById(componentId: String) = records.firstOrNull { it.componentId == componentId }
        override fun search(query: String, category: Category?) = records
        override fun listByCategory(category: Category) = records.filter { it.category == category }
        override fun catalogueVersion() = "v1"
        override fun priceDate() = "2026-11-01"

        private fun record(id: String, category: Category, attrs: Map<String, String>) = CatalogueRecord(
            componentId = id,
            category = category,
            manufacturer = "demo",
            model = id,
            partNumber = id,
            attributes = attrs.mapValues { (_, v) ->
                FieldValue(value = v, state = VerificationState.VERIFIED, numeric = v.toDoubleOrNull())
            },
            priceCny = 100,
            priceDate = "2026-11-01",
            catalogueVersion = "v1",
            isDemo = true,
        )
    }
}
