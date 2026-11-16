package pcbuildmate.app

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test
import pcbuildmate.contracts.BuildItem
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.RuleOutcome
import pcbuildmate.contracts.Severity
import pcbuildmate.data.catalogue.AssetCatalogueAdapter
import pcbuildmate.data.rules.RuleParser
import pcbuildmate.domain.replacement.DefaultReplacementAdvisor
import pcbuildmate.domain.rules.DefaultRuleEngine

/**
 * 替换建议的**属性**测试：用真实目录数据，而不是手工编的 4 条用例。
 *
 * 验证 `DefaultReplacementAdvisor` 文档承诺的三件事（docs/06 D5、INV-14）：
 * 1. 每个候选都能让目标冲突消失；
 * 2. 每个候选都不会引入**新的** BLOCKER 冲突；
 * 3. 排序是价格差升序 → 影响规则数升序 → componentId，且 rank 从 1 连续。
 *
 * 这不能替代参考模型，但它把"过滤条件"从若干特例提升成了对全部 GPU×CASE 组合的检查。
 */
class ReplacementPropertiesTest {

    private val root = File(System.getProperty("user.dir")!!).parentFile
    private val catalogue = AssetCatalogueAdapter {
        File(root, "assets/catalogue/catalogue-app.json").readText()
    }
    private val rules = RuleParser.parse(File(root, "assets/rules/rules-v1.json").readText()).rules
    private val engine = DefaultRuleEngine(rules, catalogue)
    private val advisor = DefaultReplacementAdvisor(catalogue, engine)

    @Test
    fun `every suggestion removes the conflict and adds no new blocker`() {
        val cases = catalogue.listByCategory(Category.CASE)
        val gpus = catalogue.listByCategory(Category.GPU)
        val failures = mutableListOf<String>()
        var suggestions = 0
        var conflictPairs = 0

        for (case in cases) {
            for (gpu in gpus) {
                val base = revision(
                    listOf(
                        gpu.componentId to gpu.priceCny,
                        case.componentId to case.priceCny,
                    ),
                )
                val outcomes = engine.evaluate(base)
                val conflict = outcomes.filterIsInstance<RuleOutcome.Conflict>()
                    .firstOrNull { it.ruleId == "GPU_CASE_LENGTH" } ?: continue
                conflictPairs++

                val suggestion = advisor.suggest(base, conflict)
                val baseBlockers = outcomes.filterIsInstance<RuleOutcome.Conflict>()
                    .filter { it.severity == Severity.BLOCKER }
                    .map { it.conflictId }
                    .toSet()

                var previous: pcbuildmate.contracts.Candidate? = null
                suggestion.candidates.forEachIndexed { index, candidate ->
                    suggestions++
                    val newRev = replaceGpu(base, gpu.componentId, candidate.componentId)
                    val expectedDelta = (catalogue.getById(candidate.componentId)?.priceCny ?: 0L) - gpu.priceCny
                    if (candidate.priceDeltaCny != expectedDelta) {
                        failures += "${gpu.componentId}+${case.componentId} -> ${candidate.componentId}: priceDelta=${candidate.priceDeltaCny}，应为 $expectedDelta"
                    }
                    val newOutcomes = engine.evaluate(newRev)
                    val conflicts = newOutcomes.filterIsInstance<RuleOutcome.Conflict>()

                    if (conflicts.any { it.conflictId == conflict.conflictId }) {
                        failures += "${gpu.componentId}+${case.componentId} -> ${candidate.componentId}: 目标冲突仍在"
                    }
                    val newBlockers = conflicts.filter { it.severity == Severity.BLOCKER }
                        .map { it.conflictId }
                        .filter { it !in baseBlockers }
                    if (newBlockers.isNotEmpty()) {
                        failures += "${gpu.componentId}+${case.componentId} -> ${candidate.componentId}: 引入新 BLOCKER $newBlockers"
                    }
                    if (candidate.rank != index + 1) {
                        failures += "${gpu.componentId}+${case.componentId} -> ${candidate.componentId}: rank=${candidate.rank}，应为 ${index + 1}"
                    }
                    val prev = previous
                    if (prev != null) {
                        val ordered =
                            prev.priceDeltaCny < candidate.priceDeltaCny ||
                                (prev.priceDeltaCny == candidate.priceDeltaCny &&
                                    prev.affectsOtherRules.size < candidate.affectsOtherRules.size) ||
                                (prev.priceDeltaCny == candidate.priceDeltaCny &&
                                    prev.affectsOtherRules.size == candidate.affectsOtherRules.size &&
                                    prev.componentId <= candidate.componentId)
                        if (!ordered) {
                            failures += "${gpu.componentId}+${case.componentId}: 排序不符合 INV-14（${prev.componentId} 之后出现 ${candidate.componentId}）"
                        }
                    }
                    previous = candidate
                }

                if (suggestion.candidates.isEmpty() && suggestion.noCandidate == null) {
                    failures += "${gpu.componentId}+${case.componentId}: 空候选但没有结构化原因"
                }
            }
        }

        assertTrue("目录里没有任何冲突组合，测试没有实际覆盖", conflictPairs > 0)
        assertTrue("没有任何候选被生成，测试没有覆盖过滤逻辑", suggestions > 0)
        assertTrue(
            "共 ${failures.size} 处违反：\n" + failures.take(10).joinToString("\n"),
            failures.isEmpty(),
        )
    }

    private fun replaceGpu(
        base: BuildRevision,
        oldGpuId: String,
        newGpuId: String,
    ): BuildRevision {
        val items = base.items.map { item ->
            if (item.componentId != oldGpuId) item
            else {
                val newRecord = catalogue.getById(newGpuId)
                    ?: error("候选 $newGpuId 不在目录里")
                BuildItem(newGpuId, Category.GPU, item.quantity, newRecord.priceCny)
            }
        }
        val total = items.sumOf { it.quantity.toLong() * it.unitPriceCny }
        return base.copy(items = items, totalCny = total, id = base.id + 1)
    }

    private fun revision(pairs: List<Pair<String, Long>>): BuildRevision {
        val items = pairs.map { (id, price) ->
            val record = catalogue.getById(id) ?: error("目录缺少 $id")
            BuildItem(id, record.category, 1, price)
        }
        return BuildRevision(
            id = 1,
            buildId = "props",
            items = items,
            totalCny = items.sumOf { it.quantity.toLong() * it.unitPriceCny },
            catalogueVersion = "",
            priceDate = "",
            userModified = false,
        )
    }
}
