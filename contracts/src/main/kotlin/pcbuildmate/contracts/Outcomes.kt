package pcbuildmate.contracts

/**
 * 三层状态的第二层：一条规则的判定结果（01 章 §1.1）。
 *
 * 冻结语义：
 * - 任一输入缺失或为 EMPTY -> 只能是 [Unverified]；
 * - 任一输入 conflict=true -> 只能是 [Unverified] 且 reason=SOURCE_CONFLICT；
 * - [weakest] 是该规则所有输入字段中最弱的证据态；
 * - 输出按 ruleId 字典序稳定排序（由引擎保证）。
 */
sealed interface RuleOutcome {
    val ruleId: String
    val weakest: VerificationState

    data class Passed(
        override val ruleId: String,
        override val weakest: VerificationState,
    ) : RuleOutcome

    /**
     * 冲突必须自带左右引用与实际值：UI 不得自行拼接文案，
     * 替换建议必须能凭 [conflictId] 精确引用同一冲突。
     */
    data class Conflict(
        override val ruleId: String,
        override val weakest: VerificationState,
        val conflictId: String,
        val left: FieldRef,
        val leftValue: String,
        val right: FieldRef,
        val rightValue: String,
        val severity: Severity,
    ) : RuleOutcome

    data class Unverified(
        override val ruleId: String,
        override val weakest: VerificationState,
        val missingField: FieldRef?,
        val reason: UnverifiedReason,
    ) : RuleOutcome
}

/** 未验证原因。枚举冻结，新增走契约变更。 */
enum class UnverifiedReason {
    MISSING_FIELD,
    SOURCE_CONFLICT,
    UNSUPPORTED_RULE,
    INSUFFICIENT_DATA,
}

/** 三层状态的第三层：一份诊断的总览。判定顺序见 02 章 §2.5。 */
enum class OverallStatus { PASSED, CONFLICT, UNVERIFIED, MIXED }

/** 风险等级。只由 severity 决定，与价格、分数无关。 */
enum class RiskLevel { HIGH, MEDIUM, LOW, UNKNOWN }

/**
 * 一次检查的完整结果。
 *
 * [revisionId] 是它的身份证：与 build.currentRevisionId 不一致时，
 * 它不是"旧结果"，而是"不属于当前配置的结论"，不得展示为当前结论（INV-03）。
 *
 * [score] 仅用于展示；禁止参与排序、推荐、替换（01 章 §1.2）。
 */
data class Diagnosis(
    val revisionId: Long,
    val outcomes: List<RuleOutcome>,
    val overall: OverallStatus,
    val riskLevel: RiskLevel,
    val score: Int?,
    val computedAt: String,
)
