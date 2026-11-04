package pcbuildmate.contracts

/**
 * 端口（port）：定义"我需要什么能力"，不定义"怎么实现"。
 *
 * 规则（05 章 §5.4）：
 * - 所有端口只出现在本文件，实现分散在 data/ 或 app/；
 * - 端口方法必须同步、可重入、无隐藏 I/O 顺序依赖；
 * - 端口不得返回 Android 类型、Room Entity、Compose 类型；
 * - 端口实现不得回调调用方（避免双向耦合）。
 */

/** D1：只读静态目录。NotFound 返回 null，不抛异常。 */
interface CataloguePort {
    fun getById(componentId: String): CatalogueRecord?
    fun search(query: String, category: Category?): List<CatalogueRecord>
    fun listByCategory(category: Category): List<CatalogueRecord>
    fun catalogueVersion(): String
    fun priceDate(): String
}

/** 已保存配置的持久化。只存不可变快照，不做增量更新。 */
interface BuildStorePort {
    fun loadBuild(buildId: String): Build?
    fun listSavedBuilds(): List<Build>
    fun listRevisions(buildId: String): List<BuildRevision>
    fun saveRevision(build: Build, revision: BuildRevision)
    fun deleteBuild(buildId: String)
}

/** 未保存编辑的草稿（切换配置时不阻断用户）。草稿不进入 savedBuilds。 */
interface DraftStorePort {
    fun saveDraft(buildId: String, revision: BuildRevision)
    fun loadDraft(buildId: String): BuildRevision?
    fun clearDraft(buildId: String)
}

/**
 * 时间端口。domain 不得直接读系统时钟（公理 A3）。
 * 返回 ISO-8601 字符串，例如 "2026-10-31"。
 */
interface ClockPort {
    fun todayIso(): String
    fun nowIso(): String
}

/**
 * 标识端口。revision id 必须单调递增且全局唯一（INV-02）。
 * 由实现保证单调；domain 不得自己算 id。
 */
interface IdPort {
    fun nextRevisionId(): Long
    fun newBuildId(): String
}

/**
 * D3：兼容性判定。输入一份快照，输出按 ruleId 字典序排序的结果。
 * 纯函数语义：同输入、同目录版本，输出逐项相同（INV-15）。
 */
interface CompatibilityEvaluator {
    fun evaluate(revision: BuildRevision): List<RuleOutcome>
}

/** D5：替换建议。必须先过硬约束过滤，再排序（INV-14）。 */
interface ReplacementAdvisorPort {
    fun suggest(base: BuildRevision, conflict: RuleOutcome.Conflict): SuggestionResult
}

/** D4：预算推荐。不承诺全局最优；不可行必须结构化上报（INV-13）。 */
interface BudgetRecommenderPort {
    fun recommend(request: RecommendationRequest): RecommendationResult
}

/** D6：比较。只读已有 revision 与诊断，禁止重算规则（06 章 D6）。 */
interface BuildComparatorPort {
    fun compare(buildIds: List<String>): ComparisonResult
}

/** 配置单分享卡的编解码。首版只做应用内 JSON + 系统分享 Intent，不联网。 */
interface PlanCardCodec {
    fun encode(revision: BuildRevision): String
    fun decode(json: String): PlanCardResult
}

/** 分享卡内容。导入必然产生新 buildId，不并入当前配置（06 章 D2）。 */
data class PlanCard(
    val schemaVersion: Int,
    val name: String,
    val items: List<PlanCardItem>,
    val totalCny: Long,
    val catalogueVersion: String,
    val priceDate: String,
)

data class PlanCardItem(
    val componentId: String,
    val category: Category,
    val quantity: Int,
    val unitPriceCny: Long,
)

sealed interface PlanCardResult {
    data class Ok(val card: PlanCard) : PlanCardResult
    data class Invalid(val reason: String) : PlanCardResult
}
