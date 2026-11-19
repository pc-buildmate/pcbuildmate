package pcbuildmate.oracle

import pcbuildmate.contracts.BuildItem
import pcbuildmate.contracts.CatalogueRecord
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.FieldValue
import pcbuildmate.contracts.RuleKind
import pcbuildmate.contracts.Severity
import pcbuildmate.contracts.VerificationState
import kotlin.math.ceil

/**
 * 规则引擎的参考模型（docs/08 §8.3）。
 *
 * 与生产的区别是刻意的：
 * - 规则表自己从 JSON 读，不复用 `:data` 的 `RuleParser`；
 * - 三态传播按 `docs/03 §3.4.2` 重写，不复用 `DefaultRuleEngine`；
 * - 不 import `pcbuildmate.domain.*`（`ci/check-boundaries.mjs` 强制）。
 *
 * 覆盖范围：7 种 kind + 4 种 aggregation + PSU 公式。
 * 不覆盖：推荐排序、替换排序（那些"最优"不可判定）。
 */
data class OracleRule(
    val ruleId: String,
    val kind: RuleKind,
    val leftCategory: Category,
    val leftPath: String,
    val rightCategory: Category,
    val rightPath: String,
    val severity: Severity,
    val aggregation: String,
    val formula: String?,
)

enum class OracleStatus { PASSED, CONFLICT, UNVERIFIED }

data class OracleOutcome(
    val ruleId: String,
    val status: OracleStatus,
    val weakest: VerificationState,
    val reason: String? = null,
    val leftValue: String? = null,
    val rightValue: String? = null,
    val missingPath: String? = null,
    val index: Int? = null,
    /** 冲突时用于构造 conflictId（docs/03 §3.5）；聚合/公式规则为 null。 */
    val leftComponentId: String? = null,
    val rightComponentId: String? = null,
    val severity: Severity? = null,
)

object RuleOracle {

    // ---------- 规则表 ----------

    fun loadRules(json: String): List<OracleRule> {
        val root = MiniJson.parse(json).asObject()
        return root["rules"].asArray().map { el ->
            val r = el.asObject()
            val left = r["left"].asObject()
            val right = r["right"].asObject()
            OracleRule(
                ruleId = r["ruleId"].asText(),
                kind = RuleKind.valueOf(r["kind"].asText()),
                leftCategory = Category.valueOf(left["category"].asText()),
                leftPath = left["path"].asText(),
                rightCategory = Category.valueOf(right["category"].asText()),
                rightPath = right["path"].asText(),
                severity = Severity.valueOf(r["severity"].asText()),
                aggregation = (r["aggregation"] as? String) ?: "SINGLE",
                formula = r["formula"] as? String,
            )
        }
    }

    // ---------- 判定 ----------

    fun evaluate(
        rules: List<OracleRule>,
        items: List<BuildItem>,
        catalogue: List<CatalogueRecord>,
    ): List<OracleOutcome> {
        val records = catalogue.associateBy { it.componentId }
        return rules
            .sortedBy { it.ruleId }
            .flatMap { rule -> evaluateRule(rule, items, records) }
            .sortedWith(compareBy({ it.ruleId }, { it.index ?: -1 }))
    }

    private fun evaluateRule(
        rule: OracleRule,
        items: List<BuildItem>,
        records: Map<String, CatalogueRecord>,
    ): List<OracleOutcome> {
        val leftItems = items.filter { it.category == rule.leftCategory }
        val rightItems = items.filter { it.category == rule.rightCategory }

        if (rule.formula != null) return psuFormula(rule, leftItems, rightItems, items, records)

        if (leftItems.isEmpty()) return listOf(unverified(rule, "MISSING_FIELD", "${rule.leftCategory}"))
        if (rightItems.isEmpty()) return listOf(unverified(rule, "MISSING_FIELD", "${rule.rightCategory}"))

        val right = resolve(rule.rightCategory, rightItems.first(), rule.rightPath, records)
            ?: return listOf(unverified(rule, "MISSING_FIELD", "${rule.rightCategory}.${rule.rightPath}"))
        if (right.state == VerificationState.EMPTY) {
            return listOf(
                unverified(rule, "MISSING_FIELD", "${rule.rightCategory}.${rule.rightPath}", weakest = VerificationState.EMPTY),
            )
        }

        return when (rule.aggregation) {
            "PER_ITEM" -> leftItems.mapIndexed { index, item -> evaluateOne(rule, item, right, records, index) }
            "COUNT", "SUM" -> listOf(evaluateAggregate(rule, leftItems, right, records))
            else -> listOf(evaluateOne(rule, leftItems.first(), right, records, null))
        }
    }

    /** 单条（SINGLE / PER_ITEM）。 */
    private fun evaluateOne(
        rule: OracleRule,
        leftItem: BuildItem,
        right: Resolved,
        records: Map<String, CatalogueRecord>,
        index: Int?,
    ): OracleOutcome {
        val left = resolve(rule.leftCategory, leftItem, rule.leftPath, records)
            ?: return unverified(rule, "MISSING_FIELD", "${rule.leftCategory}.${rule.leftPath}", index)
        if (left.state == VerificationState.EMPTY) {
            return unverified(
                rule, "MISSING_FIELD", "${rule.leftCategory}.${rule.leftPath}", index,
                weakest = VerificationState.EMPTY,
            )
        }
        return compare(rule, left, right, leftItem.quantity, index)
    }

    /** COUNT_LE / SUM_LE：先乘再求和，然后同一个比较。 */
    private fun evaluateAggregate(
        rule: OracleRule,
        leftItems: List<BuildItem>,
        right: Resolved,
        records: Map<String, CatalogueRecord>,
    ): OracleOutcome {
        val weakest = minState(right.state, leftItems.map { item ->
            resolve(rule.leftCategory, item, rule.leftPath, records)?.state ?: VerificationState.EMPTY
        }.fold(VerificationState.VERIFIED) { acc, s -> minState(acc, s) })

        if (leftItems.any { it.conflicts(rule, records) }) {
            return unverified(rule, "SOURCE_CONFLICT", null, null, weakest)
        }
        var sum = 0.0
        for (item in leftItems) {
            val v = resolve(rule.leftCategory, item, rule.leftPath, records) ?: return unverified(
                rule, "MISSING_FIELD", "${rule.leftCategory}.${rule.leftPath}", null, weakest,
            )
            if (v.state == VerificationState.EMPTY) {
                return unverified(
                    rule, "MISSING_FIELD", "${rule.leftCategory}.${rule.leftPath}", null,
                    VerificationState.EMPTY,
                )
            }
            val n = v.number() ?: return unverified(
                rule, "INSUFFICIENT_DATA", "${rule.leftCategory}.${rule.leftPath}", null, weakest,
            )
            sum += n * item.quantity
        }
        if (right.conflict) return unverified(rule, "SOURCE_CONFLICT", null, null, weakest)

        val limit = right.number() ?: return unverified(
            rule, "INSUFFICIENT_DATA", "${rule.rightCategory}.${rule.rightPath}", null, weakest,
        )
        val ok = when (rule.kind) {
            RuleKind.COUNT_LE, RuleKind.SUM_LE -> sum <= limit
            else -> error("聚合只适用于 COUNT_LE / SUM_LE，收到 ${rule.kind}")
        }
        return OracleOutcome(
            ruleId = rule.ruleId,
            status = if (ok) OracleStatus.PASSED else OracleStatus.CONFLICT,
            weakest = weakest,
            leftValue = trimNumber(sum),
            rightValue = trimNumber(limit),
        )
    }

    /** PSU 负载：requiredW = ceil((cpu.tdpW + gpu.tdpW + 100) * 1.2)，见 docs/03 §3.7。 */
    private fun psuFormula(
        rule: OracleRule,
        psuItems: List<BuildItem>,
        cpuItems: List<BuildItem>,
        allItems: List<BuildItem>,
        records: Map<String, CatalogueRecord>,
    ): List<OracleOutcome> {
        val gpuItems = allItems.filter { it.category == Category.GPU }
        if (psuItems.isEmpty() || cpuItems.isEmpty()) {
            return listOf(unverified(rule, "MISSING_FIELD", "PSU/CPU"))
        }
        if (gpuItems.isEmpty()) {
            // 无独显配置：首版不评估，返回 UNVERIFIED 而不是 PASSED（docs/03 §3.7）。
            return listOf(unverified(rule, "MISSING_FIELD", "GPU"))
        }
        val cpuTdp = resolve(Category.CPU, cpuItems.first(), "tdpW", records)
            ?: return listOf(unverified(rule, "MISSING_FIELD", "CPU.tdpW"))
        val gpuTdp = resolve(Category.GPU, gpuItems.first(), "tdpW", records)
            ?: return listOf(unverified(rule, "MISSING_FIELD", "GPU.tdpW"))
        val psuW = resolve(Category.PSU, psuItems.first(), rule.leftPath, records)
            ?: return listOf(unverified(rule, "MISSING_FIELD", "PSU.${rule.leftPath}"))
        if (psuW.state == VerificationState.EMPTY) {
            return listOf(
                unverified(rule, "MISSING_FIELD", "PSU.${rule.leftPath}", weakest = VerificationState.EMPTY),
            )
        }
        if (cpuTdp.state == VerificationState.EMPTY) {
            return listOf(unverified(rule, "MISSING_FIELD", "CPU.tdpW", weakest = VerificationState.EMPTY))
        }
        if (gpuTdp.state == VerificationState.EMPTY) {
            return listOf(unverified(rule, "MISSING_FIELD", "GPU.tdpW", weakest = VerificationState.EMPTY))
        }

        val weakest = minState(
            VerificationState.VERIFIED,
            minState(psuW.state, minState(cpuTdp.state, gpuTdp.state)),
        )
        if (psuW.conflict || cpuTdp.conflict || gpuTdp.conflict) {
            return listOf(unverified(rule, "SOURCE_CONFLICT", null, null, weakest))
        }
        val c = cpuTdp.number() ?: return listOf(unverified(rule, "INSUFFICIENT_DATA", "CPU.tdpW", null, weakest))
        val g = gpuTdp.number() ?: return listOf(unverified(rule, "INSUFFICIENT_DATA", "GPU.tdpW", null, weakest))
        val p = psuW.number() ?: return listOf(unverified(rule, "INSUFFICIENT_DATA", "PSU.${rule.leftPath}", null, weakest))

        val required = ceil((c + g + 100.0) * 1.2)
        return listOf(
            OracleOutcome(
                ruleId = rule.ruleId,
                status = if (p >= required) OracleStatus.PASSED else OracleStatus.CONFLICT,
                weakest = weakest,
                leftValue = trimNumber(p),
                rightValue = trimNumber(required),
            ),
        )
    }

    // ---------- 比较 ----------

    private fun compare(
        rule: OracleRule,
        left: Resolved,
        right: Resolved,
        leftQuantity: Int,
        index: Int?,
    ): OracleOutcome {
        val weakest = minState(left.state, right.state)
        if (left.conflict || right.conflict) {
            return unverified(rule, "SOURCE_CONFLICT", null, index, weakest)
        }
        val ok = when (rule.kind) {
            RuleKind.EQUALS -> left.text.trim() == right.text.trim()
            // 左是集合、右是单值（docs/03 §3.2）。
            RuleKind.CSV_CONTAINS -> splitCsv(left.text).contains(right.text.trim())
            RuleKind.CSV_SUBSET -> {
                val need = splitCsv(right.text)
                val have = splitCsv(left.text).toSet()
                need.all { it in have }
            }
            RuleKind.NUMERIC_LE -> {
                val l = left.number() ?: return unverified(rule, "INSUFFICIENT_DATA", "${rule.leftCategory}.${rule.leftPath}", index, weakest)
                val r = right.number() ?: return unverified(rule, "INSUFFICIENT_DATA", "${rule.rightCategory}.${rule.rightPath}", index, weakest)
                l <= r
            }
            RuleKind.NUMERIC_GE -> {
                val l = left.number() ?: return unverified(rule, "INSUFFICIENT_DATA", "${rule.leftCategory}.${rule.leftPath}", index, weakest)
                val r = right.number() ?: return unverified(rule, "INSUFFICIENT_DATA", "${rule.rightCategory}.${rule.rightPath}", index, weakest)
                l >= r
            }
            RuleKind.COUNT_LE, RuleKind.SUM_LE -> {
                val l = left.number() ?: return unverified(rule, "INSUFFICIENT_DATA", "${rule.leftCategory}.${rule.leftPath}", index, weakest)
                val r = right.number() ?: return unverified(rule, "INSUFFICIENT_DATA", "${rule.rightCategory}.${rule.rightPath}", index, weakest)
                l * leftQuantity <= r
            }
        }
        return OracleOutcome(
            ruleId = rule.ruleId,
            status = if (ok) OracleStatus.PASSED else OracleStatus.CONFLICT,
            weakest = weakest,
            leftValue = left.text,
            rightValue = right.text,
            index = index,
            leftComponentId = left.id,
            rightComponentId = right.id,
            severity = rule.severity,
        )
    }

    // ---------- 工具 ----------

    private data class Resolved(
        val id: String,
        val text: String,
        val state: VerificationState,
        val conflict: Boolean,
        val numeric: Double?,
    ) {
        fun number(): Double? = numeric ?: text.toDoubleOrNull()
    }

    private fun resolve(
        category: Category,
        item: BuildItem,
        path: String,
        records: Map<String, CatalogueRecord>,
    ): Resolved? {
        val record = records[item.componentId] ?: return null
        // 类别不一致按"部件缺失"处理（docs/03 §3.4.1）。
        if (record.category != category) return null
        val fv: FieldValue = record.attributes[path] ?: return null
        val text = fv.value ?: ""
        return Resolved(item.componentId, text, fv.state, fv.conflict, fv.numeric)
    }

    private fun BuildItem.conflicts(
        rule: OracleRule,
        records: Map<String, CatalogueRecord>,
    ): Boolean = resolve(rule.leftCategory, this, rule.leftPath, records)?.conflict == true

    private fun splitCsv(v: String): List<String> =
        v.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /** VERIFIED > REPORTED > EMPTY：ordinal 越大越弱，所以取较大者。 */
    private fun minState(a: VerificationState, b: VerificationState): VerificationState =
        if (a.ordinal >= b.ordinal) a else b

    private fun unverified(
        rule: OracleRule,
        reason: String,
        missingPath: String? = null,
        index: Int? = null,
        weakest: VerificationState = VerificationState.VERIFIED,
    ): OracleOutcome = OracleOutcome(
        ruleId = rule.ruleId,
        status = OracleStatus.UNVERIFIED,
        weakest = weakest,
        reason = reason,
        missingPath = missingPath,
        index = index,
    )

    private fun trimNumber(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
}
