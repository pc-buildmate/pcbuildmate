package pcbuildmate.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import pcbuildmate.contracts.CataloguePort
import pcbuildmate.contracts.CatalogueRecord
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.RuleOutcome
import pcbuildmate.data.rules.RuleParser
import pcbuildmate.domain.rules.DefaultRuleEngine
import pcbuildmate.oracle.FixtureLoader
import pcbuildmate.oracle.OracleStatus
import pcbuildmate.oracle.RuleOracle

/**
 * 生产规则引擎与参考模型的交叉校验（docs/08 §8.3）。
 *
 * 这不是"再跑一遍同样的测试"：两边是两套独立实现——
 * 生产走 `:data` 的 `RuleParser` + `:domain:rules` 的 `DefaultRuleEngine`，
 * 参考模型走 `test-oracle` 自带的 JSON 读取器和三态传播。
 * 同一份 fixture 下，两边对每条规则给出的结果必须逐条一致。
 *
 * 放在 `app` 里是因为只有它能同时依赖两边；`test-oracle` 自己被禁止 import
 * `pcbuildmate.domain.*`（那正是它有价值的原因）。
 */
class OracleCrossCheckTest {

    private val root = File(System.getProperty("user.dir")!!).parentFile
    private val rulesJson = File(root, "assets/rules/rules-v1.json").readText()

    @Test
    fun `oracle agrees with the rule engine on all 60 rule fixtures`() {
        crossCheck("assets/fixtures/fixtures-rules-v1.json", 60)
    }

    @Test
    fun `oracle agrees with the rule engine on the 7 anchor fixtures`() {
        crossCheck("assets/fixtures/fixtures-anchor-v1.json", 7)
    }

    private fun crossCheck(path: String, expectedCount: Int) {
        val production = RuleParser.parse(rulesJson).rules
        val oracleRules = RuleOracle.loadRules(rulesJson)
        val fixtures = FixtureLoader.load(File(root, path).readText())
        val failures = mutableListOf<String>()

        for (fx in fixtures) {
            val prod = DefaultRuleEngine(production, ListCatalogue(fx.catalogue)).evaluate(fx.revision)
            val oracle = RuleOracle.evaluate(oracleRules, fx.revision.items, fx.catalogue)

            val prodByRule = prod.groupBy { it.ruleId }
            val oracleByRule = oracle.groupBy { it.ruleId }

            for (ruleId in (prodByRule.keys + oracleByRule.keys)) {
                val p = prodByRule[ruleId].orEmpty().map(::describe).toList()
                val o = oracleByRule[ruleId].orEmpty().map(::describe).toList()
                if (p != o) {
                    failures += "${fx.id}/$ruleId\n      生产：$p\n      oracle：$o"
                }
            }

            // 第二重：oracle 也要对得上 fixture 里冻结的期望值（不只是跟生产一致）。
            for ((ruleId, expected) in fx.expected) {
                val actual = oracleByRule[ruleId].orEmpty().map { "${it.status}/${it.weakest}" }
                val ok = actual.size == 1 &&
                    if (expected.endsWith("/-")) actual[0] == expected.removeSuffix("/-") else actual[0] == expected
                if (!ok) {
                    failures += "${fx.id}/$ruleId（期望）\n      期望：$expected\n      oracle：$actual"
                }
            }
        }

        assertTrue(
            "fixture 数量应为 $expectedCount，实际 ${fixtures.size}",
            fixtures.size == expectedCount,
        )
        assertTrue(
            "生产实现与参考模型有 ${failures.size} 处不一致：\n" + failures.joinToString("\n"),
            failures.isEmpty(),
        )
    }

    private fun describe(o: RuleOutcome): String = when (o) {
        is RuleOutcome.Passed -> "PASSED/${o.weakest}"
        is RuleOutcome.Conflict -> "CONFLICT/${o.weakest}"
        is RuleOutcome.Unverified -> "UNVERIFIED/${o.weakest}/${o.reason.name}"
    }

    private fun describe(o: pcbuildmate.oracle.OracleOutcome): String =
        if (o.status == OracleStatus.UNVERIFIED) "UNVERIFIED/${o.weakest}/${o.reason}" else "${o.status}/${o.weakest}"

    private class ListCatalogue(private val records: List<CatalogueRecord>) : CataloguePort {
        private val byId = records.associateBy { it.componentId }
        override fun getById(componentId: String) = byId[componentId]
        override fun search(query: String, category: Category?) =
            records.filter { (category == null || it.category == category) && it.model.contains(query, ignoreCase = true) }
        override fun listByCategory(category: Category) = records.filter { it.category == category }
        override fun catalogueVersion() = ""
        override fun priceDate() = ""
    }
}
