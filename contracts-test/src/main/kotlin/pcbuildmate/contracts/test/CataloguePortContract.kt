package pcbuildmate.contracts.test

import pcbuildmate.contracts.CataloguePort
import pcbuildmate.contracts.CatalogueRecord
import pcbuildmate.contracts.Category
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 端口契约测试基类。
 *
 * 用法：真实实现（Room / 内存）与测试 Fake 都必须继承并通过本类。
 * 这是「Fake 与真实实现行为一致」的唯一保证；没有它，用 Fake 写的一切单测都在自说自话。
 *
 * 见 docs/07-unit-verification.md 第 7.6 节。
 */
abstract class CataloguePortContract {

    /** 用给定记录构造一个空目录实现（可以是 Room，可以是内存 Map）。 */
    protected abstract fun newPort(records: List<CatalogueRecord>): CataloguePort

    /** 至少两条不同类目的记录，其中一条 model 含 "RTX"，componentId 唯一。 */
    protected abstract fun sampleRecords(): List<CatalogueRecord>

    private fun port() = newPort(sampleRecords())

    @Test
    fun `getById returns the exact record`() {
        val records = sampleRecords()
        val target = records.first()
        assertEquals(target, port().getById(target.componentId))
    }

    @Test
    fun `getById returns null instead of throwing`() {
        assertNull(port().getById("does-not-exist"))
    }

    @Test
    fun `listByCategory filters and never leaks other categories`() {
        val records = sampleRecords()
        val category = records.first().category
        val listed = port().listByCategory(category)
        assertTrue(listed.all { it.category == category })
        assertEquals(records.count { it.category == category }, listed.size)
    }

    @Test
    fun `search is case insensitive on model and partNumber`() {
        val all = port()
        assertTrue(all.search("rtx", null).isNotEmpty())
        assertTrue(all.search("RTX", null).isNotEmpty())
        assertEquals(
            all.search("rtx", null).map { it.componentId }.sorted(),
            all.search("RTX", null).map { it.componentId }.sorted(),
        )
    }

    @Test
    fun `search with category narrows the result`() {
        val all = port()
        val narrowed = all.search("", Category.GPU)
        assertTrue(narrowed.all { it.category == Category.GPU })
    }

    @Test
    fun `catalogueVersion and priceDate are stable across calls`() {
        val p = port()
        assertEquals(p.catalogueVersion(), port().catalogueVersion())
        assertEquals(p.priceDate(), p.priceDate())
    }

    @Test
    fun `results are deterministic in order`() {
        val a = port().listByCategory(sampleRecords().first().category).map { it.componentId }
        val b = port().listByCategory(sampleRecords().first().category).map { it.componentId }
        assertEquals(a, b, "同一目录两次查询顺序必须一致（公理 A3）")
    }
}
