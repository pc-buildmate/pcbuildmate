package pcbuildmate.data.rules

import pcbuildmate.contracts.Aggregation
import pcbuildmate.contracts.RuleKind
import pcbuildmate.contracts.Severity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RuleParserTest {

    private fun rulesJson(): String =
        checkNotNull(javaClass.classLoader?.getResource("rules/rules-v1.json")) {
            "测试资源缺失：rules/rules-v1.json（由 :data 的 test sourceSet 指向 assets/）"
        }.readText()

    @Test
    fun `parses all 15 rules and sorts by ruleId`() {
        val table = RuleParser.parse(rulesJson())

        assertEquals(15, table.rules.size)
        assertEquals("rules-v1", table.rulesVersion)
        assertEquals("static-v1", table.catalogueVersion)
        assertEquals(table.rules.map { it.ruleId }.sorted(), table.rules.map { it.ruleId })
        assertTrue(table.rules.all { it.formula == null || it.ruleId == "PSU_CONTINUOUS_POWER" })
    }

    @Test
    fun `parses the anchor rules with their kinds and severities`() {
        val table = RuleParser.parse(rulesJson())
        val byId = table.rules.associateBy { it.ruleId }

        assertEquals(RuleKind.NUMERIC_LE, assertNotNull(byId["GPU_CASE_LENGTH"]).kind)
        assertEquals(Severity.BLOCKER, assertNotNull(byId["GPU_CASE_LENGTH"]).severity)
        assertEquals(Aggregation.PER_ITEM, assertNotNull(byId["MEMORY_DDR_GENERATION"]).aggregation)
        assertEquals(Aggregation.COUNT, assertNotNull(byId["MEMORY_SLOT_COUNT"]).aggregation)
        assertEquals(Aggregation.SUM, assertNotNull(byId["MEMORY_CAPACITY_LIMIT"]).aggregation)
    }

    @Test
    fun `keeps the frozen PSU formula`() {
        val table = RuleParser.parse(rulesJson())
        val psu = assertNotNull(table.rules.firstOrNull { it.ruleId == "PSU_CONTINUOUS_POWER" })

        assertEquals(RuleParser.PSU_REQUIRED_W_FORMULA, psu.formula)
        assertEquals(RuleKind.NUMERIC_GE, psu.kind)
    }

    @Test
    fun `rejects duplicate rule ids`() {
        val broken = rulesJson().replaceFirst("\"CPU_SOCKET_MATCH\"", "\"CPU_MB_CHIPSET_SUPPORT\"")

        assertFailsWith<IllegalArgumentException> {
            RuleParser.parse(broken)
        }
    }

    @Test
    fun `rejects an operand path outside the frozen table`() {
        val broken = rulesJson().replaceFirst("\"path\": \"socket\"", "\"path\": \"socketX\"")

        assertFailsWith<IllegalArgumentException> {
            RuleParser.parse(broken)
        }
    }

    @Test
    fun `rejects an unknown formula`() {
        val broken = rulesJson().replaceFirst(
            RuleParser.PSU_REQUIRED_W_FORMULA,
            "requiredW = cpu.tdpW + gpu.tdpW",
        )

        assertFailsWith<IllegalArgumentException> {
            RuleParser.parse(broken)
        }
    }

    @Test
    fun `rejects an unknown kind`() {
        val broken = rulesJson().replaceFirst("\"EQUALS\"", "\"ALMOST_EQUALS\"")

        assertFailsWith<IllegalArgumentException> {
            RuleParser.parse(broken)
        }
    }
}
