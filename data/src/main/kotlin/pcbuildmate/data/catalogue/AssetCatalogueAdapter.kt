package pcbuildmate.data.catalogue

import android.content.Context
import pcbuildmate.contracts.CataloguePort
import pcbuildmate.contracts.CatalogueRecord
import pcbuildmate.contracts.Category

/**
 * D1 的真实实现：启动时从 assets 读一次 JSON，之后只读内存。
 *
 * 确定性：所有查询按 componentId 字典序；不读时钟、不读系统语言。
 */
class AssetCatalogueAdapter(
    source: CatalogueJsonSource,
) : CataloguePort {

    private val snapshot: CatalogueSnapshot by lazy { CatalogueParser.parse(source.read()) }

    private val byId: Map<String, CatalogueRecord> by lazy {
        snapshot.records.associateBy { it.componentId }
    }

    override fun getById(componentId: String): CatalogueRecord? = byId[componentId]

    override fun search(query: String, category: Category?): List<CatalogueRecord> {
        val q = query.trim()
        return snapshot.records.asSequence()
            .filter { category == null || it.category == category }
            .filter { q.isEmpty() || it.model.contains(q, ignoreCase = true) || it.partNumber.contains(q, ignoreCase = true) }
            .sortedBy { it.componentId }
            .toList()
    }

    override fun listByCategory(category: Category): List<CatalogueRecord> =
        snapshot.records.asSequence()
            .filter { it.category == category }
            .sortedBy { it.componentId }
            .toList()

    override fun catalogueVersion(): String = snapshot.catalogueVersion

    override fun priceDate(): String = snapshot.priceDate
}

/** 生产入口：只读 assets，不联网。 */
fun assetCatalogue(
    context: Context,
    assetPath: String = "catalogue/catalogue-app.json",
): CataloguePort = AssetCatalogueAdapter {
    context.assets.open(assetPath).bufferedReader().use { it.readText() }
}
