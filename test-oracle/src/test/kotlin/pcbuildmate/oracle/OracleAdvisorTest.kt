package pcbuildmate.oracle

import pcbuildmate.contracts.BuildItem
import pcbuildmate.contracts.CatalogueRecord
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.FieldValue
import pcbuildmate.contracts.RuleKind
import pcbuildmate.contracts.Severity
import pcbuildmate.contracts.VerificationState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 候选过滤的三条规则，逐条钉死：
 * 1. 目标冲突没消失 → 淘汰；
 * 2. 引入新的 BLOCKER → 淘汰；
 * 3. 引入新的 MAJOR 不算淘汰（那是"影响面"，不是"装不上"）。
 */
class OracleAdvisorTest {

    private fun rule(id: String, kind: RuleKind, left: String, right: String, severity: Severity) =
        OracleRule(id, kind, Category.GPU, left, Category.CASE, right, severity, "SINGLE", null)

    private val lengthRule = rule("GPU_CASE_LENGTH", RuleKind.NUMERIC_LE, "lengthMm", "maxGpuLengthMm", Severity.BLOCKER)
    private val thicknessBlocker = rule("GPU_CASE_THICKNESS", RuleKind.NUMERIC_LE, "thicknessMm", "maxGpuThicknessMm", Severity.BLOCKER)
    private val thicknessMajor = rule("GPU_CASE_THICKNESS_MAJOR", RuleKind.NUMERIC_LE, "thicknessMm", "maxGpuThicknessMm", Severity.MAJOR)

    private fun record(id: String, category: Category, attrs: Map<String, String>) = CatalogueRecord(
        componentId = id,
        category = category,
        manufacturer = "demo",
        model = id,
        partNumber = id,
        attributes = attrs.mapValues { (_, v) ->
            FieldValue(value = v, state = VerificationState.VERIFIED, numeric = v.toDoubleOrNull())
        },
        priceCny = 0,
        priceDate = "",
        catalogueVersion = "",
        isDemo = true,
    )

    private val case320 = record("case-320", Category.CASE, mapOf("maxGpuLengthMm" to "320", "maxGpuThicknessMm" to "60"))
    private val baseGpu = record("gpu-337", Category.GPU, mapOf("lengthMm" to "337", "thicknessMm" to "55"))
    private val short = record("gpu-300", Category.GPU, mapOf("lengthMm" to "300", "thicknessMm" to "55"))
    private val exact = record("gpu-320", Category.GPU, mapOf("lengthMm" to "320", "thicknessMm" to "60"))
    private val stillLong = record("gpu-331", Category.GPU, mapOf("lengthMm" to "331", "thicknessMm" to "55"))
    private val tooThick = record("gpu-310-fat", Category.GPU, mapOf("lengthMm" to "310", "thicknessMm" to "65"))

    private val items = listOf(
        BuildItem("gpu-337", Category.GPU, 1, 0),
        BuildItem("case-320", Category.CASE, 1, 0),
    )
    private val records = listOf(baseGpu, case320, short, exact, stillLong, tooThick)
    private val target = "GPU_CASE_LENGTH:gpu-337.lengthMm:case-320.maxGpuLengthMm"

    @Test
    fun `a candidate must remove the target conflict`() {
        val kept = OracleAdvisor.filterCandidates(
            listOf(lengthRule), items, records, target, "gpu-337", Category.GPU,
        )
        assertEquals(listOf("gpu-300", "gpu-310-fat", "gpu-320"), kept)
        // 331 still exceeds 320, so it is filtered out
        assertEquals(false, kept.contains("gpu-331"))
    }

    @Test
    fun `a candidate that introduces a new blocker is filtered out`() {
        val kept = OracleAdvisor.filterCandidates(
            listOf(lengthRule, thicknessBlocker), items, records, target, "gpu-337", Category.GPU,
        )
        assertEquals(listOf("gpu-300", "gpu-320"), kept)
    }

    @Test
    fun `a new major conflict does not disqualify a candidate`() {
        val kept = OracleAdvisor.filterCandidates(
            listOf(lengthRule, thicknessMajor), items, records, target, "gpu-337", Category.GPU,
        )
        assertEquals(listOf("gpu-300", "gpu-310-fat", "gpu-320"), kept)
    }

    @Test
    fun `when the target conflict is not present nothing is suggested`() {
        val kept = OracleAdvisor.filterCandidates(
            listOf(lengthRule), items, records, "GPU_CASE_LENGTH:nope.lengthMm:case-320.maxGpuLengthMm", "gpu-337", Category.GPU,
        )
        assertEquals(emptyList(), kept)
    }
}
