package pcbuildmate.contracts

/**
 * 规则表的类型形状（ADR-0007）。
 *
 * 这里只有数据，没有解析、没有判定：
 * - `:data` 把 `assets/rules/rules-v1.json` 解析成 `List<RuleDef>`；
 * - `:domain:rules` 只吃 `List<RuleDef>`，不 import 序列化库、不读 assets。
 */

/** 7 种比较方式，冻结（docs/03 §3.2）。新增走契约变更。 */
enum class RuleKind {
    EQUALS,
    CSV_CONTAINS,
    CSV_SUBSET,
    NUMERIC_LE,
    NUMERIC_GE,
    COUNT_LE,
    SUM_LE,
}

/** 多件同类部件时如何聚合（docs/03 §3.2）。 */
enum class Aggregation { SINGLE, PER_ITEM, COUNT, SUM }

/**
 * 规则的一个操作数。
 *
 * [path] 必须在 `docs/03 §3.3` 的冻结表内，否则 seed 校验 fail-fast；
 * 引擎本身不校验路径，只按 [Category] 取记录、按 [path] 取字段。
 */
data class RuleOperand(
    val category: Category,
    val path: String,
) {
    init {
        require(path.isNotBlank()) { "RuleOperand.path 不能为空" }
    }
}

/**
 * 一条规则。
 *
 * [formula] 只用于 PSU 负载这类「先算中间量再比较」的规则；
 * 首版只允许 null 或 `docs/03 §3.7` 冻结的公式名。
 */
data class RuleDef(
    val ruleId: String,
    val kind: RuleKind,
    val left: RuleOperand,
    val right: RuleOperand,
    val severity: Severity,
    val aggregation: Aggregation = Aggregation.SINGLE,
    val formula: String? = null,
) {
    init {
        require(ruleId.isNotBlank()) { "ruleId 不能为空" }
        require(ruleId.matches(Regex("^[A-Z][A-Z0-9_]*$"))) {
            "ruleId 必须是大写蛇形：$ruleId"
        }
    }
}
