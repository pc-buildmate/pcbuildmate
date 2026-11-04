package pcbuildmate.data.catalogue

import pcbuildmate.contracts.CataloguePort
import pcbuildmate.contracts.CatalogueRecord
import pcbuildmate.contracts.Category

internal class InMemoryCatalogue(
    private val records: List<CatalogueRecord>,
    private val version: String = "test-v1",
    private val date: String = "2026-10-31",
) : CataloguePort {

    private val byId = records.associateBy { it.componentId }

    override fun getById(componentId: String): CatalogueRecord? = byId[componentId]

    override fun search(query: String, category: Category?): List<CatalogueRecord> {
        val q = query.trim()
        return records.asSequence()
            .filter { category == null || it.category == category }
            .filter { q.isEmpty() || it.model.contains(q, true) || it.partNumber.contains(q, true) }
            .sortedBy { it.componentId }
            .toList()
    }

    override fun listByCategory(category: Category): List<CatalogueRecord> =
        records.filter { it.category == category }.sortedBy { it.componentId }

    override fun catalogueVersion(): String = version

    override fun priceDate(): String = date
}
