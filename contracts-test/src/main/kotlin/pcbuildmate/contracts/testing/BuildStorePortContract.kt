package pcbuildmate.contracts.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pcbuildmate.contracts.Build
import pcbuildmate.contracts.BuildItem
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.BuildStorePort
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.Preferences
import pcbuildmate.contracts.UseCase

abstract class BuildStorePortContract {

    protected abstract fun newStore(): BuildStorePort

    @Test
    fun `save then load build and revision`() {
        val store = newStore()
        val build = build("b1", 1)
        val revision = revision("b1", 1, 7800)

        store.saveRevision(build, revision)

        assertEquals(build, store.loadBuild("b1"))
        assertEquals(listOf(revision), store.listRevisions("b1"))
    }

    @Test
    fun `revisions are sorted by id`() {
        val store = newStore()
        store.saveRevision(build("b1", 3), revision("b1", 3, 300))
        store.saveRevision(build("b1", 2), revision("b1", 2, 200))

        assertEquals(listOf(2L, 3L), store.listRevisions("b1").map { it.id })
    }

    @Test
    fun `same revision id overwrites instead of duplicating`() {
        val store = newStore()
        store.saveRevision(build("b1", 1), revision("b1", 1, 7000))
        store.saveRevision(build("b1", 1), revision("b1", 1, 7800))

        assertEquals(1, store.listRevisions("b1").size)
        assertEquals(7800, store.listRevisions("b1").single().totalCny)
    }

    @Test
    fun `delete removes build and revisions`() {
        val store = newStore()
        store.saveRevision(build("b1", 1), revision("b1", 1, 7800))

        store.deleteBuild("b1")

        assertNull(store.loadBuild("b1"))
        assertEquals(emptyList<BuildRevision>(), store.listRevisions("b1"))
    }

    @Test
    fun `listSavedBuilds contains saved builds`() {
        val store = newStore()
        store.saveRevision(build("b1", 1), revision("b1", 1, 7800))
        store.saveRevision(build("b2", 2), revision("b2", 2, 9000))

        assertEquals(setOf("b1", "b2"), store.listSavedBuilds().map { it.buildId }.toSet())
        assertNotEquals(store.loadBuild("b1"), store.loadBuild("b2"))
    }

    protected fun build(id: String, revisionId: Long) = Build(
        buildId = id,
        budgetCny = 8000,
        useCase = UseCase.GAMING,
        preferences = Preferences(),
        currentRevisionId = revisionId,
        userModified = false,
        createdAt = "2026-11-05",
        updatedAt = "2026-11-05",
    )

    protected fun revision(buildId: String, id: Long, total: Long) = BuildRevision(
        id = id,
        buildId = buildId,
        items = listOf(BuildItem("cpu-demo-7600x", Category.CPU, 1, total)),
        totalCny = total,
        catalogueVersion = "2026.11",
        priceDate = "2026-11-04",
        userModified = false,
    )
}
