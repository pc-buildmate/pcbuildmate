package pcbuildmate.domain.recommend

import pcbuildmate.contracts.BuildItem
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.BudgetRecommenderPort
import pcbuildmate.contracts.CataloguePort
import pcbuildmate.contracts.CatalogueRecord
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.CompatibilityEvaluator
import pcbuildmate.contracts.RecommendationRequest
import pcbuildmate.contracts.RecommendationResult
import pcbuildmate.contracts.RuleOutcome
import pcbuildmate.contracts.Severity
import pcbuildmate.contracts.UseCase
import kotlin.math.abs

/**
 * D4 预算推荐：权重可见、先过硬约束、预算不可行必须报告。
 *
 * 范围：CPU / 主板 / 内存 / 显卡（可选）/ 电源 / 硬盘。
 * 机箱/散热不在长期价格数据里，App demo 仍单独保留锚点，不参与本推荐器。
 *
 * 无 GPU 时必须选有核显的 CPU；否则不会生成一台点不亮的机器。
 */
class DefaultBudgetRecommender(
    private val catalogue: CataloguePort,
    private val evaluator: CompatibilityEvaluator,
) : BudgetRecommenderPort {

    override fun recommend(request: RecommendationRequest): RecommendationResult {
        val weights = weightsFor(request.useCase)
        val needsGpu = (weights[Category.GPU] ?: 0.0) > 0.0

        if (request.budgetCny <= 0) {
            return RecommendationResult(
                feasible = false,
                build = null,
                shortfallCny = request.budgetCny.coerceAtLeast(1),
                relaxedPreferences = emptyList(),
                weightsVisible = weights,
            )
        }

        val categories = buildList {
            add(Category.CPU)
            add(Category.MOTHERBOARD)
            add(Category.MEMORY)
            if (needsGpu) add(Category.GPU)
            add(Category.PSU)
            add(Category.STORAGE)
        }

        val optionsByCategory = categories.associateWith { category ->
            options(category, request.budgetCny, weights[category] ?: 0.0, needsGpu)
        }
        if (optionsByCategory.values.any { it.isEmpty() }) {
            return RecommendationResult(
                feasible = false,
                build = null,
                shortfallCny = request.budgetCny,
                relaxedPreferences = emptyList(),
                weightsVisible = weights,
            )
        }

        var bestFeasible: Combo? = null
        var bestOverBudget: Combo? = null

        fun walk(index: Int, chosen: MutableList<CatalogueRecord>, total: Long) {
            if (index == categories.size) {
                val revision = revisionOf(chosen, request.budgetCny, needsGpu)
                if (hasBlocker(revision)) return

                val deviation = chosen.sumOf { record ->
                    val target = request.budgetCny * (weights[record.category] ?: 0.0)
                    abs(record.priceCny - target)
                }.toDouble()

                val combo = Combo(chosen.toList(), revision, total, deviation)
                if (total <= request.budgetCny) {
                    if (bestFeasible == null || combo.betterThan(bestFeasible!!)) bestFeasible = combo
                } else if (bestOverBudget == null || combo.overBudgetBetterThan(bestOverBudget!!)) {
                    bestOverBudget = combo
                }
                return
            }

            val category = categories[index]
            for (record in optionsByCategory.getValue(category)) {
                chosen += record
                walk(index + 1, chosen, total + record.priceCny)
                chosen.removeAt(chosen.lastIndex)
            }
        }

        walk(0, mutableListOf(), 0L)

        val feasible = bestFeasible
        if (feasible != null) {
            return RecommendationResult(
                feasible = true,
                build = feasible.revision,
                shortfallCny = 0,
                relaxedPreferences = emptyList(),
                weightsVisible = weights,
            )
        }

        val closest = bestOverBudget
        return if (closest != null) {
            RecommendationResult(
                feasible = false,
                build = closest.revision,
                shortfallCny = closest.total - request.budgetCny,
                relaxedPreferences = emptyList(),
                weightsVisible = weights,
            )
        } else {
            RecommendationResult(
                feasible = false,
                build = null,
                shortfallCny = request.budgetCny,
                relaxedPreferences = emptyList(),
                weightsVisible = weights,
            )
        }
    }

    private fun options(
        category: Category,
        budget: Long,
        weight: Double,
        needsGpu: Boolean,
    ): List<CatalogueRecord> {
        val target = budget * weight
        return catalogue.listByCategory(category)
            .asSequence()
            .filter { record ->
                if (category == Category.CPU && !needsGpu) {
                    record.attributes["integratedGraphics"]?.value == "YES"
                } else {
                    true
                }
            }
            .sortedWith(
                compareBy<CatalogueRecord> { abs(it.priceCny - target) }
                    .thenBy { it.priceCny }
                    .thenBy { it.componentId },
            )
            .take(6)
            .toList()
    }

    private fun revisionOf(
        records: List<CatalogueRecord>,
        budget: Long,
        needsGpu: Boolean,
    ): BuildRevision {
        val items = records.map {
            BuildItem(
                componentId = it.componentId,
                category = it.category,
                quantity = 1,
                unitPriceCny = it.priceCny,
            )
        }
        return BuildRevision(
            id = 1,
            buildId = "recommend-${if (needsGpu) "gpu" else "igpu"}",
            items = items,
            totalCny = BuildRevision.computeTotal(items),
            catalogueVersion = catalogue.catalogueVersion(),
            priceDate = catalogue.priceDate(),
            userModified = false,
        )
    }

    private fun hasBlocker(revision: BuildRevision): Boolean =
        evaluator.evaluate(revision).any {
            it is RuleOutcome.Conflict && it.severity == Severity.BLOCKER
        }

    private fun weightsFor(useCase: UseCase): Map<Category, Double> = when (useCase) {
        UseCase.GAMING -> mapOf(
            Category.CPU to 0.20,
            Category.MOTHERBOARD to 0.12,
            Category.MEMORY to 0.08,
            Category.GPU to 0.40,
            Category.PSU to 0.08,
            Category.STORAGE to 0.12,
        )

        UseCase.STUDY_OFFICE -> mapOf(
            Category.CPU to 0.32,
            Category.MOTHERBOARD to 0.18,
            Category.MEMORY to 0.16,
            Category.PSU to 0.12,
            Category.STORAGE to 0.22,
        )

        UseCase.CONTENT_CREATION -> mapOf(
            Category.CPU to 0.28,
            Category.MOTHERBOARD to 0.12,
            Category.MEMORY to 0.14,
            Category.GPU to 0.28,
            Category.PSU to 0.08,
            Category.STORAGE to 0.10,
        )

        UseCase.LOCAL_AI -> mapOf(
            Category.CPU to 0.22,
            Category.MOTHERBOARD to 0.10,
            Category.MEMORY to 0.20,
            Category.GPU to 0.32,
            Category.PSU to 0.08,
            Category.STORAGE to 0.08,
        )
    }

    private data class Combo(
        val records: List<CatalogueRecord>,
        val revision: BuildRevision,
        val total: Long,
        val deviation: Double,
    ) {
        fun betterThan(other: Combo): Boolean =
            deviation < other.deviation ||
                (deviation == other.deviation && total > other.total) ||
                (deviation == other.deviation && total == other.total &&
                    revision.buildId < other.revision.buildId)

        fun overBudgetBetterThan(other: Combo): Boolean =
            total < other.total || (total == other.total && deviation < other.deviation)
    }
}
