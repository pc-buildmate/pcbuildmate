package pcbuildmate.domain.rules

import pcbuildmate.contracts.BuildItem
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.CataloguePort
import pcbuildmate.contracts.CatalogueRecord
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.FieldSource
import pcbuildmate.contracts.FieldValue
import pcbuildmate.contracts.RuleDef
import pcbuildmate.contracts.RuleKind
import pcbuildmate.contracts.RuleOperand
import pcbuildmate.contracts.RuleOutcome
import pcbuildmate.contracts.Severity
import pcbuildmate.contracts.UnverifiedReason
import pcbuildmate.contracts.VerificationState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * INV-06：来源冲突时 `weakest` 必须是参与比较字段里**最弱**的那个（VERIFIED > REPORTED > EMPTY）。
 *
 * 60 条 fixture 没有覆盖「冲突字段的来源强弱不一致」和「另一侧更弱」这两种组合，
 * 参考模型（`RuleOracle`）用的是 `min(左, 右)`，所以这里单独钉住生产实现。
 */
class SourceConflictWeakestTest {

    private val rule = RuleDef(
        "CPU_SOCKET_MATCH",
        RuleKind.EQUALS,
        RuleOperand(Category.CPU, "socket"),
        RuleOperand(Category.MOTHERBOARD, "socket"),
        Severity.BLOCKER,
    )

    @Test
    fun `conflicting field with mixed sources reports the weakest source`() {
        val cpuSocket = field(
            value = "AM5",
            state = VerificationState.REPORTED,
            conflict = true,
            sources = listOf(
                FieldSource("vendor-a", "AM5", VerificationState.VERIFIED),
                FieldSource("retailer-b", "AM4", VerificationState.REPORTED),
            ),
        )
        val outcome = evaluate(cpuSocket, field("AM5", VerificationState.VERIFIED))

        assertEquals(UnverifiedReason.SOURCE_CONFLICT, outcome.reason)
        assertEquals(VerificationState.REPORTED, outcome.weakest, "冲突字段里有一条只是 REPORTED，结果就不能标 VERIFIED")
    }

    @Test
    fun `weakest also follows the other side of the comparison`() {
        val cpuSocket = field(
            value = "AM5",
            state = VerificationState.VERIFIED,
            conflict = true,
            sources = listOf(
                FieldSource("vendor-a", "AM5", VerificationState.VERIFIED),
                FieldSource("vendor-b", "AM5", VerificationState.VERIFIED),
            ),
        )
        val boardSocket = field("AM5", VerificationState.REPORTED)

        val outcome = evaluate(cpuSocket, boardSocket)

        assertEquals(UnverifiedReason.SOURCE_CONFLICT, outcome.reason)
        assertEquals(
            VerificationState.REPORTED,
            outcome.weakest,
            "weakest = min(所有参与比较字段)：主板一侧更弱，就不能报 VERIFIED",
        )
    }

    @Test
    fun `insufficient data points at the side that cannot be parsed`() {
        // 左侧 numeric=null 但 value 能解析（"231"）；右侧 value 是 "abc"，读不出来。
        val lengthRule = RuleDef(
            "GPU_CASE_LENGTH",
            RuleKind.NUMERIC_LE,
            RuleOperand(Category.GPU, "lengthMm"),
            RuleOperand(Category.CASE, "maxGpuLengthMm"),
            Severity.BLOCKER,
        )
        val records = listOf(
            record("gpu", Category.GPU, mapOf("lengthMm" to FieldValue(value = "231", state = VerificationState.VERIFIED, sources = emptyList(), conflict = false, numeric = null))),
            record("case", Category.CASE, mapOf("maxGpuLengthMm" to FieldValue(value = "abc", state = VerificationState.VERIFIED, sources = emptyList(), conflict = false, numeric = null))),
        )
        val revision = BuildRevision(
            id = 1,
            buildId = "unparsable",
            items = listOf(
                BuildItem("gpu", Category.GPU, 1, 100),
                BuildItem("case", Category.CASE, 1, 100),
            ),
            totalCny = 200,
            catalogueVersion = "v1",
            priceDate = "2026-11-26",
            userModified = false,
        )
        val outcome = assertNotNull(
            DefaultRuleEngine(listOf(lengthRule), MapCatalogue(records)).evaluate(revision).single()
                as? RuleOutcome.Unverified,
        )

        assertEquals(UnverifiedReason.INSUFFICIENT_DATA, outcome.reason)
        assertEquals("case", outcome.missingField?.componentId, "读不出来的是机箱限长，不是显卡长度")
    }

    private fun evaluate(cpuSocket: FieldValue, boardSocket: FieldValue): RuleOutcome.Unverified {
        val records = listOf(
            record("cpu", Category.CPU, mapOf("socket" to cpuSocket)),
            record("mb", Category.MOTHERBOARD, mapOf("socket" to boardSocket)),
        )
        val revision = BuildRevision(
            id = 1,
            buildId = "conflict",
            items = listOf(
                BuildItem("cpu", Category.CPU, 1, 100),
                BuildItem("mb", Category.MOTHERBOARD, 1, 100),
            ),
            totalCny = 200,
            catalogueVersion = "v1",
            priceDate = "2026-11-26",
            userModified = false,
        )
        val outcome = DefaultRuleEngine(listOf(rule), MapCatalogue(records))
            .evaluate(revision)
            .single()

        return assertNotNull(outcome as? RuleOutcome.Unverified)
    }

    private fun field(
        value: String,
        state: VerificationState,
        conflict: Boolean = false,
        sources: List<FieldSource> = emptyList(),
    ) = FieldValue(value = value, state = state, sources = sources, conflict = conflict, numeric = null)

    private fun record(id: String, category: Category, attributes: Map<String, FieldValue>) =
        CatalogueRecord(
            componentId = id,
            category = category,
            manufacturer = "demo",
            model = id,
            partNumber = id,
            attributes = attributes,
            priceCny = 100,
            priceDate = "2026-11-26",
            catalogueVersion = "v1",
            isDemo = true,
        )

    private class MapCatalogue(private val records: List<CatalogueRecord>) : CataloguePort {
        private val byId = records.associateBy { it.componentId }
        override fun getById(componentId: String): CatalogueRecord? = byId[componentId]
        override fun search(query: String, category: Category?): List<CatalogueRecord> =
            records.filter { category == null || it.category == category }.sortedBy { it.componentId }
        override fun listByCategory(category: Category): List<CatalogueRecord> =
            records.filter { it.category == category }.sortedBy { it.componentId }
        override fun catalogueVersion(): String = "v1"
        override fun priceDate(): String = "2026-11-26"
    }
}
