package pcbuildmate.data.store

import pcbuildmate.contracts.Build
import pcbuildmate.contracts.BuildItem
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.CataloguePort
import pcbuildmate.contracts.Preferences
import pcbuildmate.contracts.UseCase
import pcbuildmate.data.catalogue.AssetCatalogueAdapter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * 目录 → 配置单 → 持久化 的字段保真（DATA-04）。
 *
 * `BuildRevision` 只持久化每个条目的四个字段（componentId / category / quantity / unitPriceCny），
 * 其余信息靠 `catalogueVersion` + `priceDate` 回查目录。所以「repository 有没有漏字段」的正确问法是：
 * 从真实上架目录构造的 revision，存进去再读出来，版本、价格日期、单价、类目是否原样保留。
 */
class CatalogueBackedRevisionTest {

    private fun catalogue(): CataloguePort {
        val text = checkNotNull(javaClass.classLoader?.getResource("catalogue/catalogue-app.json")) {
            "测试资源缺失：catalogue-app.json（:data 的 test sourceSet 指向仓库 assets/）"
        }.readText()
        return AssetCatalogueAdapter { text }
    }

    private fun build(): Build = Build(
        buildId = "build-demo",
        budgetCny = 8000,
        useCase = UseCase.GAMING,
        preferences = Preferences(caseSize = null, noiseLevel = null, upgradeRoom = false),
        currentRevisionId = 1,
        userModified = false,
        createdAt = "2026-11-04T00:00:00Z",
        updatedAt = "2026-11-04T00:00:00Z",
    )

    private fun revisionOf(port: CataloguePort, vararg componentIds: String): BuildRevision {
        val items = componentIds.map { id ->
            val record = assertNotNull(port.getById(id), "目录里没有 $id")
            BuildItem(record.componentId, record.category, 1, record.priceCny)
        }
        return BuildRevision(
            id = 1,
            buildId = "build-demo",
            items = items,
            totalCny = items.sumOf { it.quantity.toLong() * it.unitPriceCny },
            catalogueVersion = port.catalogueVersion(),
            priceDate = port.priceDate(),
            userModified = false,
        )
    }

    @Test
    fun `every item taken from the catalogue is resolvable again`() {
        val port = catalogue()
        val revision = revisionOf(port, "cpu-demo-9600x", "gpu-demo-300", "case-demo-320")

        for (item in revision.items) {
            val record = assertNotNull(port.getById(item.componentId), "${item.componentId} 回查不到")
            assertEquals(item.category, record.category, "${item.componentId} 类目与目录不一致")
            assertEquals(item.unitPriceCny, record.priceCny, "${item.componentId} 单价与目录不一致")
        }
    }

    @Test
    fun `a saved revision keeps catalogue version, price date and item prices`() {
        val port = catalogue()
        val revision = revisionOf(port, "cpu-demo-9600x", "mb-demo-b650", "gpu-demo-337")
        val store = InMemoryBuildStore()

        store.saveRevision(build(), revision)
        val loaded = store.listRevisions("build-demo").single()

        assertEquals(revision, loaded)
        assertEquals(port.catalogueVersion(), loaded.catalogueVersion)
        assertEquals(port.priceDate(), loaded.priceDate)
        assertEquals(revision.items.map { it.unitPriceCny }, loaded.items.map { it.unitPriceCny })
        assertTrue(loaded.totalCny > 0)
    }

    @Test
    fun `a draft round trip keeps the same revision`() {
        val port = catalogue()
        val revision = revisionOf(port, "mem-demo-ddr5-32", "storage-demo-nvme")
        val drafts = InMemoryDraftStore()

        drafts.saveDraft("build-demo", revision)

        assertEquals(revision, drafts.loadDraft("build-demo"))

        drafts.clearDraft("build-demo")
        assertEquals(null, drafts.loadDraft("build-demo"))
    }
}
