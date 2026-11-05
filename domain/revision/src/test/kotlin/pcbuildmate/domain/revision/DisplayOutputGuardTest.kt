package pcbuildmate.domain.revision

import pcbuildmate.contracts.BuildItem
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.CataloguePort
import pcbuildmate.contracts.CatalogueRecord
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.FieldValue
import pcbuildmate.contracts.VerificationState
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DisplayOutputGuardTest {

    @Test
    fun `discrete gpu always has display output`() {
        assertTrue(
            DisplayOutputGuard.hasDisplayOutput(
                revision(gpu = "gpu-1", cpu = "cpu-no-igpu"),
                MapCatalogue(listOf(cpu("cpu-no-igpu", "NO"), gpu("gpu-1"))),
            ),
        )
    }

    @Test
    fun `no gpu requires igpu`() {
        val port = MapCatalogue(listOf(cpu("cpu-igpu", "YES"), cpu("cpu-no-igpu", "NO")))

        assertTrue(DisplayOutputGuard.hasDisplayOutput(revision(gpu = null, cpu = "cpu-igpu"), port))
        assertFalse(DisplayOutputGuard.hasDisplayOutput(revision(gpu = null, cpu = "cpu-no-igpu"), port))
    }

    @Test
    fun `missing cpu or catalogue record fails closed`() {
        val port = MapCatalogue(listOf(cpu("cpu-igpu", "YES")))

        assertFalse(DisplayOutputGuard.hasDisplayOutput(revision(gpu = null, cpu = "missing"), port))
        assertFalse(
            DisplayOutputGuard.hasDisplayOutput(
                revision(gpu = null, cpu = null),
                port,
            ),
        )
    }

    private fun revision(gpu: String?, cpu: String?): BuildRevision {
        val items = buildList {
            if (cpu != null) add(BuildItem(cpu, Category.CPU, 1, 1000))
            if (gpu != null) add(BuildItem(gpu, Category.GPU, 1, 3000))
        }
        return BuildRevision(
            id = 1,
            buildId = "b",
            items = items,
            totalCny = BuildRevision.computeTotal(items),
            catalogueVersion = "v1",
            priceDate = "2026-11-04",
            userModified = false,
        )
    }

    private fun cpu(id: String, igpu: String) = CatalogueRecord(
        componentId = id,
        category = Category.CPU,
        manufacturer = "demo",
        model = id,
        partNumber = id,
        attributes = mapOf(
            "integratedGraphics" to FieldValue(igpu, VerificationState.VERIFIED),
        ),
        priceCny = 1000,
        priceDate = "2026-11-04",
        catalogueVersion = "v1",
        isDemo = true,
    )

    private fun gpu(id: String) = CatalogueRecord(
        componentId = id,
        category = Category.GPU,
        manufacturer = "demo",
        model = id,
        partNumber = id,
        attributes = emptyMap(),
        priceCny = 3000,
        priceDate = "2026-11-04",
        catalogueVersion = "v1",
        isDemo = true,
    )

    private class MapCatalogue(private val records: List<CatalogueRecord>) : CataloguePort {
        private val byId = records.associateBy { it.componentId }
        override fun getById(componentId: String) = byId[componentId]
        override fun search(query: String, category: Category?) = records
        override fun listByCategory(category: Category) = records.filter { it.category == category }
        override fun catalogueVersion() = "v1"
        override fun priceDate() = "2026-11-04"
    }
}
