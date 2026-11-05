package pcbuildmate.domain.rules

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import pcbuildmate.contracts.Aggregation
import pcbuildmate.contracts.BuildItem
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.CataloguePort
import pcbuildmate.contracts.CatalogueRecord
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.FieldRef
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * 60 条单规则 fixture 的端到端回归。
 *
 * 这里用 `kotlinx.serialization.json` 的 DOM API **只解析测试数据**，不引入
 * `@Serializable` 编译器插件；因此 `:domain:rules` 主源码仍然不认识序列化。
 * 允许通过 `ci/allowlist.txt` 记录在案（docs/09 §9.3）。
 */
class FixtureTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `all 60 rule fixtures match expected outcomes`() {
        checkFixtures("fixtures/fixtures-rules-v1.json", 60)
    }

    @Test
    fun `all 7 rule anchor fixtures match expected outcomes`() {
        checkFixtures("fixtures/fixtures-anchor-v1.json", 7)
    }

    private fun checkFixtures(path: String, expectedCount: Int) {
        val rules = loadRules()
        val fixtures = loadFixtures(path)
        val failures = mutableListOf<String>()

        for (fx in fixtures) {
            val outcomes = try {
                DefaultRuleEngine(rules, MapCatalogue(fx.catalogue)).evaluate(fx.revision())
            } catch (e: Exception) {
                failures += "${fx.id}: 引擎抛出 ${e::class.simpleName}: ${e.message}"
                continue
            }

            for ((ruleId, expected) in fx.expected) {
                val actual = outcomes.firstOrNull { it.ruleId == ruleId }
                if (actual == null) {
                    failures += "${fx.id}/$ruleId: 没有产出结果"
                    continue
                }
                compare(failures, fx.id, expected, actual)
            }
        }

        assertEquals(expectedCount, fixtures.size, "fixture 数量应为 $expectedCount")
        assertTrue(
            failures.isEmpty(),
            "有 ${failures.size} 条 fixture 不符合预期：\n" + failures.joinToString("\n"),
        )
    }

    @Test
    fun `evaluation is deterministic`() {
        val rules = loadRules()
        val fx = loadFixtures("fixtures/fixtures-rules-v1.json").first()
        val port = MapCatalogue(fx.catalogue)
        val a = DefaultRuleEngine(rules, port).evaluate(fx.revision())
        val b = DefaultRuleEngine(rules, port).evaluate(fx.revision())

        assertEquals(a, b)
    }

    private fun compare(
        failures: MutableList<String>,
        fixtureId: String,
        expected: Expected,
        actual: RuleOutcome,
    ) {
        when (expected.outcome) {
            "PASSED" -> {
                if (actual !is RuleOutcome.Passed) {
                    failures += "$fixtureId/${actual.ruleId}: 期望 PASSED，实际 ${actual::class.simpleName}"
                    return
                }
            }

            "CONFLICT" -> {
                if (actual !is RuleOutcome.Conflict) {
                    failures += "$fixtureId/${actual.ruleId}: 期望 CONFLICT，实际 ${actual::class.simpleName}"
                    return
                }
                expected.severity?.let {
                    if (actual.severity != Severity.valueOf(it)) {
                        failures += "$fixtureId/${actual.ruleId}: severity 期望 $it，实际 ${actual.severity}"
                    }
                }
                expected.conflictId?.let {
                    if (actual.conflictId != it) {
                        failures += "$fixtureId/${actual.ruleId}: conflictId 期望 $it，实际 ${actual.conflictId}"
                    }
                }
            }

            "UNVERIFIED" -> {
                if (actual !is RuleOutcome.Unverified) {
                    failures += "$fixtureId/${actual.ruleId}: 期望 UNVERIFIED，实际 ${actual::class.simpleName}"
                    return
                }
                expected.reason?.let {
                    if (actual.reason != UnverifiedReason.valueOf(it)) {
                        failures += "$fixtureId/${actual.ruleId}: reason 期望 $it，实际 ${actual.reason}"
                    }
                }
                when (expected.missingFieldIsNull) {
                    true -> if (actual.missingField != null) {
                        failures += "$fixtureId/${actual.ruleId}: 期望 missingField=null，实际 ${actual.missingField}"
                    }

                    false -> if (actual.missingField == null) {
                        failures += "$fixtureId/${actual.ruleId}: 期望 missingField 非空"
                    }

                    null -> Unit
                }
            }

            else -> failures += "$fixtureId/${actual.ruleId}: 未知 expected.outcome=${expected.outcome}"
        }

        expected.weakest?.let {
            if (actual.weakest != VerificationState.valueOf(it)) {
                failures += "$fixtureId/${actual.ruleId}: weakest 期望 $it，实际 ${actual.weakest}"
            }
        }
    }

    // ---------------------------------------------------------------- loading

    private fun loadRules(): List<RuleDef> {
        val root = parse("rules/rules-v1.json")
        return root.getValue("rules").jsonArray.map { element ->
            val o = element.jsonObject
            RuleDef(
                ruleId = o.text("ruleId"),
                kind = RuleKind.valueOf(o.text("kind")),
                left = operand(o.getValue("left").jsonObject),
                right = operand(o.getValue("right").jsonObject),
                severity = Severity.valueOf(o.text("severity")),
                aggregation = Aggregation.valueOf(o.textOrNull("aggregation") ?: "SINGLE"),
                formula = o.textOrNull("formula"),
            )
        }
    }

    private fun loadFixtures(path: String): List<Fixture> {
        val root = parse(path)
        return root.getValue("fixtures").jsonArray.mapNotNull { element ->
            val o = element.jsonObject
            // 第 8 条 anchor 是 suggestion 场景，没有 expected，只校验规则锚点。
            if (!o.containsKey("expected")) return@mapNotNull null
            val items = o.getValue("items").jsonArray.map { itemEl ->
                val i = itemEl.jsonObject
                BuildItem(
                    componentId = i.text("componentId"),
                    category = Category.valueOf(i.text("category")),
                    quantity = i.getValue("quantity").jsonPrimitive.int,
                    unitPriceCny = i.getValue("unitPriceCny").jsonPrimitive.long,
                )
            }
            val catalogue = o.getValue("catalogue").jsonArray.map { componentEl ->
                component(componentEl.jsonObject)
            }
            val expected = o.getValue("expected").jsonObject.entries.associate { (ruleId, value) ->
                val e = value.jsonObject
                ruleId to Expected(
                    outcome = e.text("outcome"),
                    weakest = e.textOrNull("weakest"),
                    reason = e.textOrNull("reason"),
                    severity = e.textOrNull("severity"),
                    missingFieldIsNull = e["missingFieldIsNull"]?.jsonPrimitive?.booleanOrNull,
                    conflictId = e.textOrNull("conflictId"),
                )
            }
            Fixture(
                id = o.text("fixtureId"),
                items = items,
                catalogue = catalogue,
                expected = expected,
            )
        }
    }

    private fun component(o: JsonObject): CatalogueRecord {
        val attributes = o["attributes"]?.jsonObject?.mapValues { (_, value) ->
            field(value.jsonObject)
        }.orEmpty()
        return CatalogueRecord(
            componentId = o.text("componentId"),
            category = Category.valueOf(o.text("category")),
            manufacturer = o.textOrNull("manufacturer") ?: "",
            model = o.textOrNull("model") ?: "",
            partNumber = o.textOrNull("partNumber") ?: "",
            attributes = attributes,
            priceCny = o["priceCny"]?.jsonPrimitive?.long ?: 0L,
            priceDate = "2026-10-31",
            catalogueVersion = "static-v1",
            isDemo = true,
        )
    }

    private fun field(o: JsonObject): FieldValue {
        val sources = o["sources"]?.jsonArray?.map { sourceEl ->
            val s = sourceEl.jsonObject
            FieldSource(
                source = s.text("source"),
                rawValue = s.text("rawValue"),
                state = VerificationState.valueOf(s.text("state")),
            )
        }.orEmpty()
        return FieldValue(
            value = o.textOrNull("value"),
            state = VerificationState.valueOf(o.text("state")),
            sources = sources,
            conflict = o["conflict"]?.jsonPrimitive?.booleanOrNull ?: false,
            numeric = o["numeric"]?.jsonPrimitive?.doubleOrNull,
        )
    }

    private fun operand(o: JsonObject): RuleOperand =
        RuleOperand(category = Category.valueOf(o.text("category")), path = o.text("path"))

    private fun parse(path: String): JsonObject {
        val text = checkNotNull(javaClass.classLoader?.getResource(path)) {
            "测试资源缺失：$path（应将 assets/ 加入 test resources）"
        }.readText()
        return json.parseToJsonElement(text).jsonObject
    }

    private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.contentOrNull
        ?: error("缺少字符串字段 $key")

    private fun JsonObject.textOrNull(key: String): String? =
        this[key]?.takeIf { it !is JsonNull }?.jsonPrimitive?.contentOrNull

    private data class Fixture(
        val id: String,
        val items: List<BuildItem>,
        val catalogue: List<CatalogueRecord>,
        val expected: Map<String, Expected>,
    ) {
        fun revision(): BuildRevision = BuildRevision(
            id = 1,
            buildId = "fx-$id",
            items = items,
            totalCny = BuildRevision.computeTotal(items),
            catalogueVersion = "static-v1",
            priceDate = "2026-10-31",
            userModified = false,
        )
    }

    private data class Expected(
        val outcome: String,
        val weakest: String?,
        val reason: String?,
        val severity: String?,
        val missingFieldIsNull: Boolean?,
        val conflictId: String?,
    )

    private class MapCatalogue(private val records: List<CatalogueRecord>) : CataloguePort {
        private val byId = records.associateBy { it.componentId }

        override fun getById(componentId: String): CatalogueRecord? = byId[componentId]
        override fun search(query: String, category: Category?): List<CatalogueRecord> =
            records.filter { category == null || it.category == category }.sortedBy { it.componentId }

        override fun listByCategory(category: Category): List<CatalogueRecord> =
            records.filter { it.category == category }.sortedBy { it.componentId }

        override fun catalogueVersion(): String = "static-v1"
        override fun priceDate(): String = "2026-10-31"
    }
}
