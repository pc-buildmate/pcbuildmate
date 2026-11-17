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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 参考模型的**自身**单测：不等 `:app` 的交叉校验也要能发现问题。
 *
 * 交叉校验证明"两边一致"，这里证明"oracle 单独也按 docs/03 的语义走"——
 * 例如三态的传递、缺失字段不算通过、PSU 公式的取整位置。
 */
class RuleOracleTest {

    private fun rule(
        id: String,
        kind: RuleKind,
        leftCat: Category = Category.CPU,
        leftPath: String = "socket",
        rightCat: Category = Category.MOTHERBOARD,
        rightPath: String = "socket",
        aggregation: String = "SINGLE",
        formula: String? = null,
        severity: Severity = Severity.BLOCKER,
    ) = OracleRule(id, kind, leftCat, leftPath, rightCat, rightPath, severity, aggregation, formula)

    private fun record(id: String, cat: Category, attrs: Map<String, String>, states: Map<String, VerificationState> = emptyMap(), conflicts: Set<String> = emptySet()) =
        CatalogueRecord(
            componentId = id,
            category = cat,
            manufacturer = "demo",
            model = id,
            partNumber = id,
            attributes = attrs.mapValues { (k, v) ->
                FieldValue(
                    value = v,
                    state = states[k] ?: VerificationState.VERIFIED,
                    conflict = k in conflicts,
                    sources = if (k in conflicts) listOf(
                        pcbuildmate.contracts.FieldSource("a", v, VerificationState.VERIFIED),
                        pcbuildmate.contracts.FieldSource("b", v, VerificationState.VERIFIED),
                    ) else emptyList(),
                    numeric = v.toDoubleOrNull(),
                )
            },
            priceCny = 1,
            priceDate = "",
            catalogueVersion = "",
            isDemo = true,
        )

    private fun outcomeOf(outcomes: List<OracleOutcome>, id: String) = outcomes.single { it.ruleId == id }

    @Test
    fun `equals passes and conflicts`() {
        val r = rule("CPU_SOCKET_MATCH", RuleKind.EQUALS)
        val cpu = BuildItem("cpu", Category.CPU, 1, 1)
        val mb = BuildItem("mb", Category.MOTHERBOARD, 1, 1)

        val same = RuleOracle.evaluate(
            listOf(r), listOf(cpu, mb),
            listOf(record("cpu", Category.CPU, mapOf("socket" to "AM5")), record("mb", Category.MOTHERBOARD, mapOf("socket" to "AM5"))),
        )
        assertEquals(OracleStatus.PASSED, outcomeOf(same, "CPU_SOCKET_MATCH").status)

        val different = RuleOracle.evaluate(
            listOf(r), listOf(cpu, mb),
            listOf(record("cpu", Category.CPU, mapOf("socket" to "AM5")), record("mb", Category.MOTHERBOARD, mapOf("socket" to "AM4"))),
        )
        assertEquals(OracleStatus.CONFLICT, outcomeOf(different, "CPU_SOCKET_MATCH").status)
    }

    @Test
    fun `a missing field is unverified, never a pass`() {
        val r = rule("GPU_CASE_LENGTH", RuleKind.NUMERIC_LE, Category.GPU, "lengthMm", Category.CASE, "maxGpuLengthMm")
        val items = listOf(BuildItem("gpu", Category.GPU, 1, 1), BuildItem("case", Category.CASE, 1, 1))
        val records = listOf(
            record("gpu", Category.GPU, emptyMap()), // 没有 lengthMm
            record("case", Category.CASE, mapOf("maxGpuLengthMm" to "320")),
        )

        val o = outcomeOf(RuleOracle.evaluate(listOf(r), items, records), "GPU_CASE_LENGTH")
        assertEquals(OracleStatus.UNVERIFIED, o.status)
        assertEquals("MISSING_FIELD", o.reason)
        assertEquals("GPU.lengthMm", o.missingPath)
    }

    @Test
    fun `an empty field keeps the weakest state empty`() {
        val r = rule("CPU_SOCKET_MATCH", RuleKind.EQUALS)
        val items = listOf(BuildItem("cpu", Category.CPU, 1, 1), BuildItem("mb", Category.MOTHERBOARD, 1, 1))
        val records = listOf(
            record("cpu", Category.CPU, mapOf("socket" to ""), states = mapOf("socket" to VerificationState.EMPTY)),
            record("mb", Category.MOTHERBOARD, mapOf("socket" to "AM5")),
        )

        val o = outcomeOf(RuleOracle.evaluate(listOf(r), items, records), "CPU_SOCKET_MATCH")
        assertEquals(OracleStatus.UNVERIFIED, o.status)
        assertEquals(VerificationState.EMPTY, o.weakest)
    }

    @Test
    fun `source conflict outranks the comparison`() {
        val r = rule("CPU_SOCKET_MATCH", RuleKind.EQUALS)
        val items = listOf(BuildItem("cpu", Category.CPU, 1, 1), BuildItem("mb", Category.MOTHERBOARD, 1, 1))
        val records = listOf(
            record("cpu", Category.CPU, mapOf("socket" to "AM5"), conflicts = setOf("socket")),
            record("mb", Category.MOTHERBOARD, mapOf("socket" to "AM5")),
        )

        val o = outcomeOf(RuleOracle.evaluate(listOf(r), items, records), "CPU_SOCKET_MATCH")
        assertEquals(OracleStatus.UNVERIFIED, o.status)
        assertEquals("SOURCE_CONFLICT", o.reason)
    }

    @Test
    fun `reported evidence propagates without blocking`() {
        val r = rule("CPU_SOCKET_MATCH", RuleKind.EQUALS)
        val items = listOf(BuildItem("cpu", Category.CPU, 1, 1), BuildItem("mb", Category.MOTHERBOARD, 1, 1))
        val records = listOf(
            record("cpu", Category.CPU, mapOf("socket" to "AM5")),
            record("mb", Category.MOTHERBOARD, mapOf("socket" to "AM5"), states = mapOf("socket" to VerificationState.REPORTED)),
        )

        val o = outcomeOf(RuleOracle.evaluate(listOf(r), items, records), "CPU_SOCKET_MATCH")
        assertEquals(OracleStatus.PASSED, o.status)
        assertEquals(VerificationState.REPORTED, o.weakest)
    }

    @Test
    fun `psu formula rounds up before comparing`() {
        val r = rule(
            "PSU_CONTINUOUS_POWER", RuleKind.NUMERIC_GE, Category.PSU, "continuousW", Category.CPU, "tdpW",
            formula = "requiredW = ceil((cpu.tdpW + gpu.tdpW + 100) * 1.2)",
        )
        val items = listOf(
            BuildItem("psu", Category.PSU, 1, 1),
            BuildItem("cpu", Category.CPU, 1, 1),
            BuildItem("gpu", Category.GPU, 1, 1),
        )
        // (105 + 285 + 100) * 1.2 = 588 → 600 W 通过，587 W 冲突
        fun run(psuW: String) = outcomeOf(
            RuleOracle.evaluate(
                listOf(r), items,
                listOf(
                    record("psu", Category.PSU, mapOf("continuousW" to psuW)),
                    record("cpu", Category.CPU, mapOf("tdpW" to "105")),
                    record("gpu", Category.GPU, mapOf("tdpW" to "285")),
                ),
            ),
            "PSU_CONTINUOUS_POWER",
        )

        val pass = run("600")
        assertEquals(OracleStatus.PASSED, pass.status)
        assertEquals("588", pass.rightValue)

        assertEquals(OracleStatus.CONFLICT, run("587").status)
    }

    @Test
    fun `psu formula without a gpu is unverified`() {
        val r = rule(
            "PSU_CONTINUOUS_POWER", RuleKind.NUMERIC_GE, Category.PSU, "continuousW", Category.CPU, "tdpW",
            formula = "requiredW = ceil((cpu.tdpW + gpu.tdpW + 100) * 1.2)",
        )
        val items = listOf(BuildItem("psu", Category.PSU, 1, 1), BuildItem("cpu", Category.CPU, 1, 1))
        val o = outcomeOf(
            RuleOracle.evaluate(
                listOf(r), items,
                listOf(record("psu", Category.PSU, mapOf("continuousW" to "750")), record("cpu", Category.CPU, mapOf("tdpW" to "105"))),
            ),
            "PSU_CONTINUOUS_POWER",
        )
        assertEquals(OracleStatus.UNVERIFIED, o.status)
        assertEquals("MISSING_FIELD", o.reason)
    }

    @Test
    fun `count aggregation multiplies quantity before comparing`() {
        val r = rule("MEMORY_SLOT_COUNT", RuleKind.COUNT_LE, Category.MEMORY, "moduleCount", Category.MOTHERBOARD, "memorySlots", aggregation = "COUNT")
        val items = listOf(
            BuildItem("mem", Category.MEMORY, 2, 1), // 两套 × 2 条 = 4
            BuildItem("mb", Category.MOTHERBOARD, 1, 1),
        )
        val records = listOf(
            record("mem", Category.MEMORY, mapOf("moduleCount" to "2")),
            record("mb", Category.MOTHERBOARD, mapOf("memorySlots" to "4")),
        )
        val ok = outcomeOf(RuleOracle.evaluate(listOf(r), items, records), "MEMORY_SLOT_COUNT")
        assertEquals(OracleStatus.PASSED, ok.status)
        assertEquals("4", ok.leftValue)
        assertNull(ok.reason)
    }

    @Test
    fun `per item rules produce one outcome per item`() {
        val r = rule(
            "MEMORY_DDR_GENERATION", RuleKind.EQUALS, Category.MEMORY, "generation", Category.MOTHERBOARD, "memoryGeneration",
            aggregation = "PER_ITEM",
        )
        val items = listOf(
            BuildItem("m1", Category.MEMORY, 1, 1),
            BuildItem("m2", Category.MEMORY, 1, 1),
            BuildItem("mb", Category.MOTHERBOARD, 1, 1),
        )
        val records = listOf(
            record("m1", Category.MEMORY, mapOf("generation" to "DDR5")),
            record("m2", Category.MEMORY, mapOf("generation" to "DDR4")),
            record("mb", Category.MOTHERBOARD, mapOf("memoryGeneration" to "DDR5")),
        )
        val outcomes = RuleOracle.evaluate(listOf(r), items, records).filter { it.ruleId == "MEMORY_DDR_GENERATION" }
        assertEquals(2, outcomes.size)
        assertEquals(listOf(OracleStatus.PASSED, OracleStatus.CONFLICT), outcomes.map { it.status })
    }

    @Test
    fun `results are ordered by ruleId`() {
        val rules = listOf(
            rule("Z_RULE", RuleKind.EQUALS),
            rule("A_RULE", RuleKind.EQUALS),
        )
        val items = listOf(BuildItem("cpu", Category.CPU, 1, 1), BuildItem("mb", Category.MOTHERBOARD, 1, 1))
        val records = listOf(
            record("cpu", Category.CPU, mapOf("socket" to "AM5")),
            record("mb", Category.MOTHERBOARD, mapOf("socket" to "AM5")),
        )
        val ids = RuleOracle.evaluate(rules, items, records).map { it.ruleId }
        assertEquals(listOf("A_RULE", "Z_RULE"), ids)
        assertTrue(ids == ids.sorted())
    }
}
