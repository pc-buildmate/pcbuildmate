package pcbuildmate.domain.rules

import pcbuildmate.contracts.Aggregation
import pcbuildmate.contracts.BuildItem
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.CataloguePort
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.CompatibilityEvaluator
import pcbuildmate.contracts.FieldRef
import pcbuildmate.contracts.FieldValue
import pcbuildmate.contracts.RuleDef
import pcbuildmate.contracts.RuleKind
import pcbuildmate.contracts.RuleOutcome
import pcbuildmate.contracts.UnverifiedReason
import pcbuildmate.contracts.VerificationState
import kotlin.math.ceil

/**
 * D3 规则引擎：纯函数，只依赖 contracts。
 *
 * 五步判定（docs/02 §2.3.1）：
 *  1. 部件缺失 / 类别不匹配 -> Unverified(MISSING_FIELD, missingField=null)
 *  2. 输入字段 conflict -> Unverified(SOURCE_CONFLICT)
 *  3. 输入字段 EMPTY / value null -> Unverified(MISSING_FIELD)
 *  4. 比较通过 -> Passed(weakest)
 *  5. 比较失败 -> Conflict
 *
 * 顺序不可交换。输出按 ruleId 字典序（稳定排序）。
 */
class DefaultRuleEngine(
    private val rules: List<RuleDef>,
    private val catalogue: CataloguePort,
) : CompatibilityEvaluator {

    override fun evaluate(revision: BuildRevision): List<RuleOutcome> {
        if (revision.items.isEmpty()) return emptyList()

        val byCategory = revision.items.groupBy { it.category }
        return rules
            .sortedBy { it.ruleId }
            .flatMap { rule -> evaluateRule(rule, byCategory) }
            .sortedBy { it.ruleId }
    }

    private fun evaluateRule(
        rule: RuleDef,
        byCategory: Map<Category, List<BuildItem>>,
    ): List<RuleOutcome> {
        val leftItems = byCategory[rule.left.category].orEmpty()
        val rightItems = byCategory[rule.right.category].orEmpty()

        if (leftItems.isEmpty() || rightItems.isEmpty()) {
            return listOf(missingPart(rule))
        }

        if (rule.formula != null) {
            return listOf(evaluateFormula(rule, byCategory))
        }

        return when (rule.aggregation) {
            Aggregation.PER_ITEM -> leftItems.mapIndexed { index, item ->
                evaluateSingle(rule, item, rightItems.first(), index)
            }

            Aggregation.COUNT, Aggregation.SUM ->
                listOf(evaluateAggregate(rule, leftItems, rightItems.first()))

            Aggregation.SINGLE ->
                listOf(evaluateSingle(rule, leftItems.first(), rightItems.first(), null))
        }
    }

    private fun evaluateSingle(
        rule: RuleDef,
        leftItem: BuildItem,
        rightItem: BuildItem,
        itemIndex: Int?,
    ): RuleOutcome {
        val left = resolve(leftItem, rule.left.path)
        val right = resolve(rightItem, rule.right.path)

        if (left is Side.MissingPart || right is Side.MissingPart) return missingPart(rule)

        val conflict = firstConflict(left, right)
        if (conflict != null) {
            return RuleOutcome.Unverified(
                ruleId = rule.ruleId,
                weakest = conflict.weakest,
                missingField = conflict.ref,
                reason = UnverifiedReason.SOURCE_CONFLICT,
            )
        }

        val missing = firstMissing(left, right)
        if (missing != null) {
            return RuleOutcome.Unverified(
                ruleId = rule.ruleId,
                weakest = missing.state,
                missingField = missing.ref,
                reason = UnverifiedReason.MISSING_FIELD,
            )
        }

        val l = left as Side.Present
        val r = right as Side.Present
        val weakest = weakest(l.state, r.state)

        val passed = try {
            compare(rule.kind, l.field, r.field)
        } catch (e: NumberFormatException) {
            return RuleOutcome.Unverified(
                ruleId = rule.ruleId,
                weakest = weakest,
                missingField = if (l.field.numeric == null) l.ref else r.ref,
                reason = UnverifiedReason.INSUFFICIENT_DATA,
            )
        }

        return if (passed) {
            RuleOutcome.Passed(ruleId = rule.ruleId, weakest = weakest)
        } else {
            RuleOutcome.Conflict(
                ruleId = rule.ruleId,
                weakest = weakest,
                conflictId = conflictId(rule, l.ref, r.ref, itemIndex),
                left = l.ref,
                leftValue = l.field.value.orEmpty(),
                right = r.ref,
                rightValue = r.field.value.orEmpty(),
                severity = rule.severity,
            )
        }
    }


    /**
     * docs/03 §3.7 冻结公式：
     * requiredW = ceil((cpu.tdpW + gpu.tdpW + 100) * 1.2)
     *
     * 只有 PSU_CONTINUOUS_POWER 走这里；其它 formula 在解析期就被拒绝。
     */
    private fun evaluateFormula(
        rule: RuleDef,
        byCategory: Map<Category, List<BuildItem>>,
    ): RuleOutcome {
        val psuItem = byCategory[Category.PSU].orEmpty().firstOrNull() ?: return missingPart(rule)
        val cpuItem = byCategory[Category.CPU].orEmpty().firstOrNull() ?: return missingPart(rule)
        val gpuItem = byCategory[Category.GPU].orEmpty().firstOrNull() ?: return missingPart(rule)

        val psu = resolve(psuItem, rule.left.path)
        val cpu = resolve(cpuItem, rule.right.path)
        val gpu = resolve(gpuItem, "tdpW")

        if (psu is Side.MissingPart || cpu is Side.MissingPart || gpu is Side.MissingPart) {
            return missingPart(rule)
        }

        val conflict = firstConflict(psu, cpu) ?: firstConflict(gpu, psu)
        if (conflict != null) {
            return RuleOutcome.Unverified(
                ruleId = rule.ruleId,
                weakest = conflict.weakest,
                missingField = conflict.ref,
                reason = UnverifiedReason.SOURCE_CONFLICT,
            )
        }

        val missing = firstMissing(psu, cpu) ?: firstMissing(gpu, psu)
        if (missing != null) {
            return RuleOutcome.Unverified(
                ruleId = rule.ruleId,
                weakest = missing.state,
                missingField = missing.ref,
                reason = UnverifiedReason.MISSING_FIELD,
            )
        }

        val p = psu as Side.Present
        val c = cpu as Side.Present
        val g = gpu as Side.Present

        val cpuTdp = c.field.numeric ?: c.field.value?.toDoubleOrNull()
        val gpuTdp = g.field.numeric ?: g.field.value?.toDoubleOrNull()
        val psuW = p.field.numeric ?: p.field.value?.toDoubleOrNull()
        if (cpuTdp == null || gpuTdp == null || psuW == null) {
            val ref = when {
                cpuTdp == null -> c.ref
                gpuTdp == null -> g.ref
                else -> p.ref
            }
            return RuleOutcome.Unverified(
                ruleId = rule.ruleId,
                weakest = VerificationState.EMPTY,
                missingField = ref,
                reason = UnverifiedReason.INSUFFICIENT_DATA,
            )
        }

        val requiredW = ceil((cpuTdp + gpuTdp + 100.0) * 1.2)
        val weakest = weakest(weakest(p.state, c.state), g.state)

        return if (psuW >= requiredW) {
            RuleOutcome.Passed(ruleId = rule.ruleId, weakest = weakest)
        } else {
            RuleOutcome.Conflict(
                ruleId = rule.ruleId,
                weakest = weakest,
                conflictId = "${rule.ruleId}:${p.ref.componentId}.${p.ref.fieldPath}:${c.ref.componentId}.${c.ref.fieldPath}",
                left = p.ref,
                leftValue = p.field.value.orEmpty(),
                right = c.ref,
                rightValue = formatNumber(requiredW),
                severity = rule.severity,
            )
        }
    }

    private fun evaluateAggregate(
        rule: RuleDef,
        leftItems: List<BuildItem>,
        rightItem: BuildItem,
    ): RuleOutcome {
        val right = resolve(rightItem, rule.right.path)
        if (right is Side.MissingPart) return missingPart(rule)

        var weakest = VerificationState.VERIFIED
        var total = 0.0
        var firstRef: FieldRef? = null

        for (item in leftItems) {
            val left = resolve(item, rule.left.path)
            if (left is Side.MissingPart) return missingPart(rule)

            val conflict = firstConflict(left, right)
            if (conflict != null) {
                return RuleOutcome.Unverified(
                    ruleId = rule.ruleId,
                    weakest = conflict.weakest,
                    missingField = conflict.ref,
                    reason = UnverifiedReason.SOURCE_CONFLICT,
                )
            }

            val missing = firstMissing(left, right)
            if (missing != null) {
                return RuleOutcome.Unverified(
                    ruleId = rule.ruleId,
                    weakest = missing.state,
                    missingField = missing.ref,
                    reason = UnverifiedReason.MISSING_FIELD,
                )
            }

            val l = left as Side.Present
            val numeric = l.field.numeric ?: l.field.value?.toDoubleOrNull()
                ?: return RuleOutcome.Unverified(
                    ruleId = rule.ruleId,
                    weakest = l.state,
                    missingField = l.ref,
                    reason = UnverifiedReason.INSUFFICIENT_DATA,
                )
            total += numeric * item.quantity
            weakest = weakest(weakest, l.state)
            if (firstRef == null) firstRef = l.ref
        }

        if (right is Side.MissingField) {
            return RuleOutcome.Unverified(
                ruleId = rule.ruleId,
                weakest = right.state,
                missingField = right.ref,
                reason = UnverifiedReason.MISSING_FIELD,
            )
        }

        val r = right as Side.Present
        val rightNumeric = r.field.numeric ?: r.field.value?.toDoubleOrNull()
            ?: return RuleOutcome.Unverified(
                ruleId = rule.ruleId,
                weakest = r.state,
                missingField = r.ref,
                reason = UnverifiedReason.INSUFFICIENT_DATA,
            )
        weakest = weakest(weakest, r.state)

        val leftRef = firstRef ?: return missingPart(rule)
        return if (total <= rightNumeric) {
            RuleOutcome.Passed(ruleId = rule.ruleId, weakest = weakest)
        } else {
            RuleOutcome.Conflict(
                ruleId = rule.ruleId,
                weakest = weakest,
                conflictId = conflictId(rule, leftRef, r.ref, null),
                left = leftRef,
                leftValue = formatNumber(total),
                right = r.ref,
                rightValue = r.field.value.orEmpty(),
                severity = rule.severity,
            )
        }
    }

    private fun compare(kind: RuleKind, left: FieldValue, right: FieldValue): Boolean {
        val l = left.value.orEmpty().trim()
        val r = right.value.orEmpty().trim()
        return when (kind) {
            RuleKind.EQUALS -> l.equals(r, ignoreCase = true)
            RuleKind.CSV_CONTAINS -> tokens(l).contains(r)
            RuleKind.CSV_SUBSET -> tokens(r).all { tokens(l).contains(it) }
            RuleKind.NUMERIC_LE -> number(left) <= number(right)
            RuleKind.NUMERIC_GE -> number(left) >= number(right)
            RuleKind.COUNT_LE, RuleKind.SUM_LE -> number(left) <= number(right)
        }
    }

    private fun number(field: FieldValue): Double =
        field.numeric ?: field.value?.toDoubleOrNull()
        ?: throw NumberFormatException("数值字段无法解析：${field.value}")

    private fun tokens(raw: String): Set<String> =
        raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private fun resolve(item: BuildItem, path: String): Side {
        val record = catalogue.getById(item.componentId) ?: return Side.MissingPart
        if (record.category != item.category) return Side.MissingPart

        val ref = FieldRef(item.componentId, path)
        val field = record.attributes[path] ?: return Side.MissingField(ref, VerificationState.EMPTY)
        if (field.state == VerificationState.EMPTY || field.value == null) {
            return Side.MissingField(ref, field.state)
        }
        return Side.Present(ref = ref, field = field, state = field.state)
    }

    private fun firstConflict(left: Side, right: Side): ConflictHit? {
        val field = when {
            left is Side.Present && left.field.conflict -> left
            right is Side.Present && right.field.conflict -> right
            else -> null
        } ?: return null
        return ConflictHit(
            ref = field.ref,
            weakest = weakestOfSources(field.field),
        )
    }

    private fun firstMissing(left: Side, right: Side): MissingHit? {
        val side = when {
            left is Side.MissingField -> left
            right is Side.MissingField -> right
            else -> null
        } ?: return null
        return MissingHit(ref = side.ref, state = side.state)
    }

    private fun missingPart(rule: RuleDef): RuleOutcome.Unverified =
        RuleOutcome.Unverified(
            ruleId = rule.ruleId,
            weakest = VerificationState.VERIFIED,
            missingField = null,
            reason = UnverifiedReason.MISSING_FIELD,
        )

    private fun conflictId(rule: RuleDef, left: FieldRef, right: FieldRef, index: Int?): String {
        val base = "${rule.ruleId}:${left.componentId}.${left.fieldPath}:${right.componentId}.${right.fieldPath}"
        return if (index == null) base else "$base#$index"
    }

    private fun weakest(a: VerificationState, b: VerificationState): VerificationState =
        if (a.ordinal >= b.ordinal) a else b

    private fun weakestOfSources(field: FieldValue): VerificationState =
        field.sources.minByOrNull { it.state.ordinal }?.state ?: field.state

    private fun formatNumber(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

    private sealed interface Side {
        data class Present(
            val ref: FieldRef,
            val field: FieldValue,
            val state: VerificationState,
        ) : Side

        data class MissingField(
            val ref: FieldRef,
            val state: VerificationState,
        ) : Side

        data object MissingPart : Side
    }

    private data class ConflictHit(val ref: FieldRef, val weakest: VerificationState)
    private data class MissingHit(val ref: FieldRef, val state: VerificationState)
}
