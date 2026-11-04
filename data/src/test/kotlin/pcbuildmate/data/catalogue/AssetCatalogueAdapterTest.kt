package pcbuildmate.data.catalogue

import pcbuildmate.contracts.Category
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AssetCatalogueAdapterTest {

    private fun adapter(): AssetCatalogueAdapter {
        val text = checkNotNull(javaClass.classLoader?.getResource("catalogue/demo-v1.json")).readText()
        return AssetCatalogueAdapter { text }
    }

    @Test
    fun `getById returns null instead of throwing`() {
        assertNull(adapter().getById("does-not-exist"))
    }

    @Test
    fun `search is case insensitive and sorted by componentId`() {
        val port = adapter()

        val lower = port.search("rtx", null).map { it.componentId }
        val upper = port.search("RTX", null).map { it.componentId }

        assertEquals(lower, upper)
        assertEquals(lower.sorted(), lower)
        assertTrue(lower.isNotEmpty())
    }

    @Test
    fun `category filter never leaks other categories`() {
        val listed = adapter().listByCategory(Category.GPU)

        assertTrue(listed.isNotEmpty())
        assertTrue(listed.all { it.category == Category.GPU })
    }

    @Test
    fun `empty query with category returns the whole category`() {
        val port = adapter()
        val gpu = port.search("", Category.GPU)

        assertEquals(port.listByCategory(Category.GPU), gpu)
    }

    @Test
    fun `version and date are stable across calls`() {
        val port = adapter()

        assertEquals("static-v1", port.catalogueVersion())
        assertEquals("2026-10-31", port.priceDate())
        assertEquals(port.catalogueVersion(), port.catalogueVersion())
    }

    @Test
    fun `loads the anchor gpu with its numeric clearance`() {
        val gpu = assertNotNull(adapter().getById("gpu-demo-337"))

        assertEquals(337.0, gpu.attributes.getValue("lengthMm").numeric)
    }
}
