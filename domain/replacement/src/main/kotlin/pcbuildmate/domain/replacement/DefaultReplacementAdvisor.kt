package pcbuildmate.domain.replacement

import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.Candidate
import pcbuildmate.contracts.CataloguePort
import pcbuildmate.contracts.CatalogueRecord
import pcbuildmate.contracts.CompatibilityEvaluator
import pcbuildmate.contracts.NoCandidateReason
import pcbuildmate.contracts.ReplacementAdvisorPort
import pcbuildmate.contracts.RuleOutcome
import pcbuildmate.contracts.Severity
import pcbuildmate.contracts.SuggestionResult

/**
 * D5 替换建议：先过硬约束，再排序。
 *
 * 过滤：
 *  1. 候选必须让目标 `conflictId` 不再是 Conflict；
 *  2. 候选不得引入**新的** BLOCKER Conflict（base 里已有的不算新）；
 *  3. 其它新增/变化的规则只记录到 `affectsOtherRules`，不改变可行性。
 *
 * 排序（INV-14）：
 *  价格差升序 → 影响的其它规则数升序（余量影响代理）→ componentId 字典序。
 */
class DefaultReplacementAdvisor(
    private val catalogue: CataloguePort,
    private val evaluator: CompatibilityEvaluator,
) : ReplacementAdvisorPort {

    override fun suggest(base: BuildRevision, conflict: RuleOutcome.Conflict): SuggestionResult {
        val baseItem = base.items.firstOrNull { it.componentId == conflict.left.componentId }
            ?: return stale(base, conflict)

        val oldRecord = catalogue.getById(baseItem.componentId)
        val baseOutcomes = evaluator.evaluate(base)
        val baseBlockers = baseOutcomes
            .filterIsInstance<RuleOutcome.Conflict>()
            .filter { it.severity == Severity.BLOCKER }
            .map { it.conflictId }
            .toSet()

        val categoryRecords = catalogue.listByCategory(baseItem.category)
        if (categoryRecords.isEmpty()) return catalogueEmpty(base, conflict)
        val records = categoryRecords.filter { it.componentId != baseItem.componentId }

        val candidates = records.mapNotNull { record ->
            evaluateCandidate(
                base = base,
                baseItemComponentId = baseItem.componentId,
                oldRecord = oldRecord,
                newRecord = record,
                conflict = conflict,
                baseOutcomes = baseOutcomes,
                baseBlockers = baseBlockers,
            )
        }
            .sortedWith(
                compareBy<Candidate> { it.priceDeltaCny }
                    .thenBy { it.affectsOtherRules.size }
                    .thenBy { it.componentId },
            )
            .mapIndexed { index, candidate -> candidate.copy(rank = index + 1) }

        return SuggestionResult(
            baseRevisionId = base.id,
            conflictRuleId = conflict.ruleId,
            conflictId = conflict.conflictId,
            candidates = candidates,
            noCandidate = if (candidates.isEmpty()) {
                NoCandidateReason(
                    blockingConstraint = "${conflict.ruleId}:${conflict.left.fieldPath}",
                    suggestedDirection = DIRECTION_CHANGE_COMPONENT_OR_BUDGET,
                )
            } else {
                null
            },
        )
    }

    private fun evaluateCandidate(
        base: BuildRevision,
        baseItemComponentId: String,
        oldRecord: CatalogueRecord?,
        newRecord: CatalogueRecord,
        conflict: RuleOutcome.Conflict,
        baseOutcomes: List<RuleOutcome>,
        baseBlockers: Set<String>,
    ): Candidate? {
        val items = base.items.map { item ->
            if (item.componentId == baseItemComponentId) {
                item.copy(
                    componentId = newRecord.componentId,
                    unitPriceCny = newRecord.priceCny,
                )
            } else {
                item
            }
        }
        val newRevision = base.copy(
            items = items,
            totalCny = BuildRevision.computeTotal(items),
        )

        val newOutcomes = evaluator.evaluate(newRevision)

        val targetStillConflicts = newOutcomes.any {
            it is RuleOutcome.Conflict && it.conflictId == conflict.conflictId
        }
        if (targetStillConflicts) return null

        val newBlockers = newOutcomes
            .filterIsInstance<RuleOutcome.Conflict>()
            .filter { it.severity == Severity.BLOCKER }
            .filter { it.conflictId !in baseBlockers }
            .toList()
        if (newBlockers.isNotEmpty()) return null

        val affects = changedRules(baseOutcomes, newOutcomes, conflict.ruleId)

        return Candidate(
            componentId = newRecord.componentId,
            priceDeltaCny = newRevision.totalCny - base.totalCny,
            specDelta = specDelta(oldRecord, newRecord),
            affectsOtherRules = affects,
            rank = 0,
        )
    }

    /** 其它规则里，结果列表发生变化的 ruleId；排除被解决的目标规则。 */
    private fun changedRules(
        baseOutcomes: List<RuleOutcome>,
        newOutcomes: List<RuleOutcome>,
        targetRuleId: String,
    ): List<String> {
        val baseByRule = baseOutcomes.groupBy { it.ruleId }
        val newByRule = newOutcomes.groupBy { it.ruleId }
        return (baseByRule.keys + newByRule.keys)
            .filter { it != targetRuleId }
            .filter { ruleId -> baseByRule[ruleId].orEmpty() != newByRule[ruleId].orEmpty() }
            .sorted()
    }

    private fun specDelta(old: CatalogueRecord?, new: CatalogueRecord): Map<String, String> {
        if (old == null) return emptyMap()
        val keys = (old.attributes.keys + new.attributes.keys).sorted()
        return keys.mapNotNull { path ->
            val before = old.attributes[path]?.value
            val after = new.attributes[path]?.value
            if (before == after) null else path to "${before ?: "?"} -> ${after ?: "?"}"
        }.toMap()
    }

    private fun stale(base: BuildRevision, conflict: RuleOutcome.Conflict): SuggestionResult =
        SuggestionResult(
            baseRevisionId = base.id,
            conflictRuleId = conflict.ruleId,
            conflictId = conflict.conflictId,
            candidates = emptyList(),
            noCandidate = NoCandidateReason(
                blockingConstraint = "STALE_REVISION",
                suggestedDirection = DIRECTION_RECHECK,
            ),
        )

    private fun catalogueEmpty(base: BuildRevision, conflict: RuleOutcome.Conflict): SuggestionResult =
        SuggestionResult(
            baseRevisionId = base.id,
            conflictRuleId = conflict.ruleId,
            conflictId = conflict.conflictId,
            candidates = emptyList(),
            noCandidate = NoCandidateReason(
                blockingConstraint = "CATALOGUE_EMPTY",
                suggestedDirection = DIRECTION_CHANGE_COMPONENT_OR_BUDGET,
            ),
        )

    companion object {
        const val DIRECTION_CHANGE_COMPONENT_OR_BUDGET = "CHANGE_COMPONENT_OR_RAISE_BUDGET"
        const val DIRECTION_RECHECK = "RECHECK_REVISION"
    }
}
