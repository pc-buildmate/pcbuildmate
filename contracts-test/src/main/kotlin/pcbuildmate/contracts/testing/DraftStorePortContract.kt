package pcbuildmate.contracts.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pcbuildmate.contracts.BuildItem
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.DraftStorePort

abstract class DraftStorePortContract {

    protected abstract fun newStore(): DraftStorePort

    @Test
    fun `save then load draft`() {
        val store = newStore()
        val revision = revision(1)

        store.saveDraft("b1", revision)

        assertEquals(revision, store.loadDraft("b1"))
    }

    @Test
    fun `clear removes draft`() {
        val store = newStore()
        store.saveDraft("b1", revision(1))
        store.clearDraft("b1")

        assertNull(store.loadDraft("b1"))
    }

    @Test
    fun `draft is scoped by build id`() {
        val store = newStore()
        store.saveDraft("b1", revision(1))
        store.saveDraft("b2", revision(2))

        assertEquals(1L, store.loadDraft("b1")?.id)
        assertEquals(2L, store.loadDraft("b2")?.id)
    }

    private fun revision(id: Long) = BuildRevision(
        id = id,
        buildId = "b1",
        items = listOf(BuildItem("cpu-demo-7600x", Category.CPU, 1, 1500)),
        totalCny = 1500,
        catalogueVersion = "2026.11",
        priceDate = "2026-11-04",
        userModified = false,
    )
}
