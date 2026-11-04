package pcbuildmate.domain.compare

import pcbuildmate.contracts.Build
import pcbuildmate.contracts.BuildItem
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.Diagnosis
import pcbuildmate.contracts.OverallStatus
import pcbuildmate.contracts.Preferences
import pcbuildmate.contracts.RiskLevel
import pcbuildmate.contracts.TextKey
import pcbuildmate.contracts.UseCase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DefaultBuildComparatorTest {

    private fun revision(id: Long, buildId: String, gpuPrice: Long, total: Long) = BuildRevision(
        id = id,
        buildId = buildId,
        items = listOf(
            BuildItem("gpu-1", Category.GPU, 1, gpuPrice),
            BuildItem("cpu-1", Category.CPU, 1, 1500),
        ),
        totalCny = total,
        catalogueVersion = "static-v1",
        priceDate = "2026-10-31",
        userModified = false,
    )

    private fun build(id: String, revId: Long) = Build(
        buildId = id,
        budgetCny = 8000,
        useCase = UseCase.GAMING,
        preferences = Preferences(),
        currentRevisionId = revId,
        userModified = false,
        createdAt = "2026-10-31",
        updatedAt = "2026-10-31",
    )

    private fun diagnosis(revId: Long, overall: OverallStatus) = Diagnosis(
        revisionId = revId,
        outcomes = emptyList(),
        overall = overall,
        riskLevel = RiskLevel.LOW,
        score = 100,
        computedAt = "2026-10-31",
    )

    @Test
    fun `aligns two builds and highlights changed total`() {
        val a = build("a", 1)
        val b = build("b", 2)
        val ra = revision(1, "a", 3400, 4900)
        val rb = revision(2, "b", 3500, 5000)
        val result = DefaultBuildComparator(
            builds = listOf(a, b),
            revisions = mapOf("a" to ra, "b" to rb),
            diagnoses = mapOf("a" to diagnosis(1, OverallStatus.PASSED), "b" to diagnosis(2, OverallStatus.UNVERIFIED)),
        ).compare(listOf("a", "b"))

        assertEquals(listOf("a", "b"), result.buildIds)
        assertEquals(listOf(1L, 2L), result.revisions.map { it.id })
        assertEquals(2, result.diagnoses.size)
        val total = result.deltas.first { it.field == "totalCny" }
        assertEquals(listOf("4900", "5000"), total.values)
        assertTrue(total.highlighted)
        val status = result.deltas.first { it.field == "status" }
        assertEquals(listOf("PASSED", "UNVERIFIED"), status.values)
    }

    @Test
    fun `stale diagnosis becomes null and adds warning`() {
        val a = build("a", 1)
        val b = build("b", 2)
        val ra = revision(1, "a", 3400, 4900)
        val rb = revision(2, "b", 3500, 5000)
        val result = DefaultBuildComparator(
            builds = listOf(a, b),
            revisions = mapOf("a" to ra, "b" to rb),
            diagnoses = mapOf("a" to diagnosis(1, OverallStatus.PASSED), "b" to diagnosis(1, OverallStatus.PASSED)),
        ).compare(listOf("a", "b"))

        assertEquals(OverallStatus.PASSED, result.diagnoses[0]?.overall)
        assertNull(result.diagnoses[1])
        assertTrue(TextKey.COMPARE_DIAGNOSTICS_STALE in result.warnings)
    }

    @Test
    fun `fewer than two builds is rejected with a key`() {
        val result = DefaultBuildComparator(emptyList(), emptyMap(), emptyMap()).compare(listOf("a"))

        assertTrue(result.revisions.isEmpty())
        assertEquals(listOf(TextKey.COMPARE_INSUFFICIENT_BUILDS), result.warnings)
    }

    @Test
    fun `missing build fails closed`() {
        val ex = runCatching {
            DefaultBuildComparator(emptyList(), emptyMap(), emptyMap()).compare(listOf("a", "b"))
        }.exceptionOrNull()

        assertTrue(ex is IllegalArgumentException)
    }
}
