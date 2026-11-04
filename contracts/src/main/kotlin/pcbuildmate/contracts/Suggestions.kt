package pcbuildmate.contracts

/**
 * 一个替换候选。
 *
 * [affectsOtherRules] 列出这个候选项会影响到的其他 ruleId（不含被解决的冲突本身），
 * 用于 UI 预先提示"换了它还会动到哪里"。它不改变可行性判定。
 */
data class Candidate(
    val componentId: String,
    val priceDeltaCny: Long,
    val specDelta: Map<String, String>,
    val affectsOtherRules: List<String>,
    val rank: Int,
)

/**
 * 无候选时的结构化原因。不得用空列表 + 文案代替。
 *
 * [blockingConstraint] 形如 "PSU_CONTINUOUS_POWER:psu.continuousW"；
 * [suggestedDirection] 是方向（加预算 / 放宽偏好 / 换类别），不是承诺。
 */
data class NoCandidateReason(
    val blockingConstraint: String,
    val suggestedDirection: String,
)

/** 替换建议结果。绑定 [baseRevisionId]；base 过期时整体作废。 */
data class SuggestionResult(
    val baseRevisionId: Long,
    val conflictRuleId: String,
    val conflictId: String,
    val candidates: List<Candidate>,
    val noCandidate: NoCandidateReason?,
)

/** 预算推荐请求。 */
data class RecommendationRequest(
    val budgetCny: Long,
    val useCase: UseCase,
    val preferences: Preferences,
)

/**
 * 预算推荐结果。
 *
 * 不变量（INV-13）：
 * - feasible=true 时必须 totalCny <= budgetCny；
 * - feasible=false 时必须 shortfallCny > 0 且 build 可为 null；
 * - [weightsVisible] 必须非空且与实际分配一致（可展示、可调）。
 */
data class RecommendationResult(
    val feasible: Boolean,
    val build: BuildRevision?,
    val shortfallCny: Long,
    val relaxedPreferences: List<String>,
    val weightsVisible: Map<Category, Double>,
)

/** 比较页的一格差异。highlighted 由模块给出，UI 只负责渲染。 */
data class FieldDelta(
    val field: String,
    val values: List<String>,
    val highlighted: Boolean,
)

/**
 * 比较结果。
 *
 * 不变量（INV-12）：diagnoses[i] 非空 <=> 该 build 的诊断与它的 currentRevisionId 匹配。
 * 过期诊断一律以 null + warnings 表达，不得回退成旧结论。
 */
data class ComparisonResult(
    val buildIds: List<String>,
    val revisions: List<BuildRevision>,
    val diagnoses: List<Diagnosis?>,
    val deltas: List<FieldDelta>,
    val warnings: List<TextKey>,
)
