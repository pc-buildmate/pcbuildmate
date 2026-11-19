package pcbuildmate.oracle

import pcbuildmate.contracts.BuildItem
import pcbuildmate.contracts.CatalogueRecord
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.Severity

/**
 * 候选硬约束过滤的参考模型（docs/08 §8.3 的最后一项）。
 *
 * 只做**过滤**，不做排序：
 * 1. 换上去之后目标冲突必须消失；
 * 2. 不得引入 base 里没有的 BLOCKER 冲突；
 * 3. 其它新增冲突（MAJOR/MINOR）不算淘汰条件——它们属于"影响面"，由 UI 提示。
 *
 * 与生产 `DefaultReplacementAdvisor` 的差别：这里不读价格、不排序、不生成
 * `affectsOtherRules`；只回答"这个候选在硬约束上合不合格"。
 */
object OracleAdvisor {

    /**
     * @param targetConflictId docs/03 §3.5 格式的冲突 id
     * @param replaceComponentId base 里要被替换的部件
     * @param category 候选应该来自的类别
     * @return 通过硬约束的候选 componentId，按字典序（参考模型不排序）
     */
    fun filterCandidates(
        rules: List<OracleRule>,
        baseItems: List<BuildItem>,
        records: List<CatalogueRecord>,
        targetConflictId: String,
        replaceComponentId: String,
        category: Category,
    ): List<String> {
        val ruleById = rules.associateBy { it.ruleId }
        fun conflictIdOf(o: OracleOutcome): String? {
            if (o.status != OracleStatus.CONFLICT) return null
            val rule = ruleById[o.ruleId] ?: return null
            val leftId = o.leftComponentId ?: return null
            val rightId = o.rightComponentId ?: return null
            return "${o.ruleId}:$leftId.${rule.leftPath}:$rightId.${rule.rightPath}"
        }

        val base = RuleOracle.evaluate(rules, baseItems, records)
        val baseConflicts = base.mapNotNull(::conflictIdOf)
        if (targetConflictId !in baseConflicts) return emptyList()
        val baseBlockers = base
            .filter { it.status == OracleStatus.CONFLICT && it.severity == Severity.BLOCKER }
            .mapNotNull(::conflictIdOf)
            .toSet()

        return records
            .asSequence()
            .filter { it.category == category && it.componentId != replaceComponentId }
            .filter { candidate ->
                val swapped = baseItems.map { item ->
                    if (item.componentId == replaceComponentId) {
                        item.copy(componentId = candidate.componentId, category = item.category)
                    } else {
                        item
                    }
                }
                val after = RuleOracle.evaluate(rules, swapped, records)
                val ids = after.mapNotNull(::conflictIdOf)
                val targetGone = targetConflictId !in ids
                val newBlockers = after
                    .filter { it.status == OracleStatus.CONFLICT && it.severity == Severity.BLOCKER }
                    .mapNotNull(::conflictIdOf)
                    .filter { it !in baseBlockers }
                targetGone && newBlockers.isEmpty()
            }
            .map { it.componentId }
            .sorted()
            .toList()
    }
}
