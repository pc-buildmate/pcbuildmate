package pcbuildmate.data.catalogue

import pcbuildmate.contracts.Category
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CatalogueParserTest {

    private fun demoJson(): String =
        checkNotNull(javaClass.classLoader?.getResource("catalogue/demo-v1.json")) {
            "测试资源缺失：catalogue/demo-v1.json（由 :data 的 test sourceSet 指向 assets/）"
        }.readText()

    @Test
    fun `parses the demo catalogue with stable version and date`() {
        val snapshot = CatalogueParser.parse(demoJson())

        assertEquals(31, snapshot.records.size)
        assertEquals("static-v1", snapshot.catalogueVersion)
        assertEquals("2026-11-04", snapshot.priceDate)
        assertTrue(snapshot.isDemo)
        assertTrue(snapshot.records.all { it.isDemo }, "demo 目录中的每条记录都必须 isDemo=true（INV-11）")
        assertEquals(snapshot.records.map { it.componentId }.sorted(), snapshot.records.map { it.componentId })
    }

    @Test
    fun `keeps numeric values and categories`() {
        val snapshot = CatalogueParser.parse(demoJson())
        val gpu = assertNotNull(snapshot.records.firstOrNull { it.componentId == "gpu-demo-337" })

        assertEquals(Category.GPU, gpu.category)
        assertEquals(337.0, gpu.attributes.getValue("lengthMm").numeric)
        assertEquals("337", gpu.attributes.getValue("lengthMm").value)
    }

    @Test
    fun `rejects duplicate component ids`() {
        val broken = demoJson().replaceFirst("\"cpu-demo-7600x\"", "\"cpu-demo-13600k\"")

        assertFailsWith<IllegalArgumentException> {
            CatalogueParser.parse(broken)
        }
    }

    @Test
    fun `rejects a field path outside the frozen table`() {
        val broken = demoJson().replaceFirst("\"socket\"", "\"socketX\"")

        assertFailsWith<IllegalArgumentException> {
            CatalogueParser.parse(broken)
        }
    }

    @Test
    fun `rejects a non-normalised csv token`() {
        val broken = demoJson().replaceFirst("\"B650,X670\"", "\"B650,x670\"")

        assertFailsWith<IllegalArgumentException> {
            CatalogueParser.parse(broken)
        }
    }

    @Test
    fun `rejects a component whose version differs from the catalogue`() {
        // 只改文件头的版本，组件仍写 static-v1 -> 必须 fail-fast
        val broken = demoJson().replaceFirst(
            "\"catalogueVersion\": \"static-v1\"",
            "\"catalogueVersion\": \"static-v2\"",
        )

        assertFailsWith<IllegalArgumentException> {
            CatalogueParser.parse(broken)
        }
    }
}
