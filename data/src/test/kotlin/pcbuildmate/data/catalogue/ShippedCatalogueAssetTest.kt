package pcbuildmate.data.catalogue

import pcbuildmate.contracts.Category
import pcbuildmate.contracts.VerificationState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 上架资产契约（DATA-04）。
 *
 * 其余测试都跑 `catalogue/demo-v1.json`（32 条静态演示），而 App 真正加载的是
 * `catalogue/catalogue-app.json`（30 条，由 `pricedata` 导出）——这一份此前没有任何单测。
 * 本类只做断言，不改数据；数据有问题应当回 `pricedata` 重新导出，不在测试里迁就。
 */
class ShippedCatalogueAssetTest {

    private fun shippedText(): String =
        checkNotNull(javaClass.classLoader?.getResource("catalogue/catalogue-app.json")) {
            "测试资源缺失：catalogue-app.json（:data 的 test sourceSet 指向仓库 assets/）"
        }.readText()

    private fun snapshot() = CatalogueParser.parse(shippedText())

    @Test
    fun `parses the shipped asset with its own version and price date`() {
        val snapshot = snapshot()

        assertEquals(30, snapshot.records.size)
        assertEquals("2026.11", snapshot.catalogueVersion)
        assertEquals("2026-11-04", snapshot.priceDate)
        assertTrue(snapshot.isDemo, "上架目录当前仍是 demo 快照（isDemo=true）")
        assertTrue(snapshot.records.all { it.isDemo })
    }

    @Test
    fun `shipped asset is not the demo asset`() {
        // 生产入口 assetCatalogue() 默认读 catalogue-app.json；两者版本号不同，
        // 这条断言防止有人把 demo-v1.json 当成上架资产。见 docs/03 与 pricedata/README。
        assertEquals("2026.11", snapshot().catalogueVersion)
        assertTrue(snapshot().catalogueVersion != "static-v1")
    }

    @Test
    fun `covers all eight categories with the expected counts`() {
        val counts = snapshot().records.groupingBy { it.category }.eachCount()

        assertEquals(
            mapOf(
                Category.CPU to 5,
                Category.MOTHERBOARD to 6,
                Category.MEMORY to 3,
                Category.GPU to 6,
                Category.PSU to 3,
                Category.CASE to 2,
                Category.COOLER to 2,
                Category.STORAGE to 3,
            ),
            counts,
        )
    }

    @Test
    fun `component ids are unique and sorted`() {
        val ids = snapshot().records.map { it.componentId }

        assertEquals(ids.size, ids.toSet().size, "componentId 重复")
        assertEquals(ids.sorted(), ids, "目录必须按 componentId 字典序输出（公理 A3 确定性）")
    }

    @Test
    fun `every attribute path is allowed by the frozen field table`() {
        for (record in snapshot().records) {
            for (path in record.attributes.keys) {
                assertTrue(
                    FieldPathRegistry.isAllowed(record.category, path),
                    "${record.componentId}.$path 不在 docs/03 §3.3 冻结字段表内",
                )
            }
        }
    }

    @Test
    fun `every numeric field agrees with its raw value`() {
        for (record in snapshot().records) {
            for ((path, field) in record.attributes) {
                if (field.state == VerificationState.EMPTY) continue
                val numeric = field.numeric ?: continue
                val raw = assertNotNull(field.value, "${record.componentId}.$path 非 EMPTY 却没有 value")

                assertEquals(raw.toDouble(), numeric, "${record.componentId}.$path 的 numeric 与 value 不一致")
            }
        }
    }

    @Test
    fun `prices are positive and carry the header price date`() {
        val snapshot = snapshot()

        for (record in snapshot.records) {
            assertTrue(record.priceCny > 0, "${record.componentId} 价格非正数：${record.priceCny}")
            assertEquals(snapshot.priceDate, record.priceDate, "${record.componentId} 价格日期与目录头不一致")
            assertEquals(snapshot.catalogueVersion, record.catalogueVersion)
        }
    }

    @Test
    fun `keeps the single empty field the defense script relies on`() {
        val empty = snapshot().records.flatMap { record ->
            record.attributes.filterValues { it.state == VerificationState.EMPTY }
                .map { (path, _) -> "${record.componentId}.$path" }
        }

        // 答辩 9 步脚本的锚点：B650 的 BIOS 未验证 -> 规则必须返回 UNVERIFIED。
        // 数据补全后这条会红，属于「有意变更」，需同步 docs/03 与演示脚本。
        assertEquals(listOf("mb-demo-b650.biosGeneration"), empty)
    }

    @Test
    fun `exposes the demo anchor components`() {
        val snapshot = snapshot()
        val gpu300 = assertNotNull(snapshot.records.firstOrNull { it.componentId == "gpu-demo-300" })
        val gpu337 = assertNotNull(snapshot.records.firstOrNull { it.componentId == "gpu-demo-337" })
        val case320 = assertNotNull(snapshot.records.firstOrNull { it.componentId == "case-demo-320" })

        assertEquals(300.0, gpu300.attributes.getValue("lengthMm").numeric)
        assertEquals(337.0, gpu337.attributes.getValue("lengthMm").numeric)
        assertEquals(320.0, case320.attributes.getValue("maxGpuLengthMm").numeric)
    }
}
