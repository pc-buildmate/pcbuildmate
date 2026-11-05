package pcbuildmate.contracts

/**
 * 所有跨边界的文案都用 key 引用，禁止在 domain / feature 里写中文句子。
 * key 字符串本身是冻结的（可以加，不可以改）。
 */
enum class TextKey(val key: String) {
    DIAGNOSTICS_STALE_BANNER("diagnostics_stale_banner"),
    DIAGNOSTICS_RECHECK_ACTION("diagnostics_recheck_action"),
    DIAGNOSTICS_RUNNING("diagnostics_running"),
    DIAGNOSTICS_FAILED("diagnostics_failed"),
    DIAGNOSTICS_REQUIRED("diagnostics_required"),
    PRICE_SNAPSHOT_LABEL("price_snapshot_label"),
    PRICE_RELOAD_DEBUG_ACTION("price_reload_debug_action"),
    COMPARE_DIAGNOSTICS_STALE("compare_diagnostics_stale"),
    COMPARE_INSUFFICIENT_BUILDS("compare_insufficient_builds"),
    APPLY_REVISION_STALE("apply_revision_stale"),
    REPLACEMENT_NO_CANDIDATE("replacement_no_candidate"),
    CATALOGUE_EMPTY("catalogue_empty"),
    CATALOGUE_MISS("catalogue_miss"),
    QUANTITY_INVALID("quantity_invalid"),
    IMPORT_INVALID_SCHEMA("import_invalid_schema"),
    CONFLICT_SELECTED_NONE("conflict_selected_none"),
    CANDIDATE_NOT_FOUND("candidate_not_found"),
    REPORTED_EVIDENCE_BADGE("reported_evidence_badge"),
    UNVERIFIED_MISSING_FIELD("unverified_missing_field"),
    SOURCE_CONFLICT_BADGE("source_conflict_badge"),
    RECOMMENDATION_INFEASIBLE("recommendation_infeasible"),
    UNDO_APPLIED("undo_applied"),
    SAVED("saved"),
    INVALID_STATE("invalid_state"),
    PREVIEW_NOT_READY("preview_not_ready"),
    NO_DISPLAY_OUTPUT("no_display_output"),
}

/** 拒绝原因。枚举冻结；新增走契约变更。 */
enum class RejectionReason {
    INVALID_STATE,
    DIAGNOSTICS_REQUIRED,
    APPLY_REVISION_STALE,
    COMPARE_INSUFFICIENT_BUILDS,
    CATALOGUE_MISS,
    QUANTITY_INVALID,
    IMPORT_INVALID_SCHEMA,
    NO_CONFLICT_SELECTED,
    CANDIDATE_NOT_FOUND,
    PREVIEW_NOT_READY,
}

/** 被拒绝的事件。状态不变，UI 只负责展示。 */
data class Rejection(
    val reason: RejectionReason,
    val textKey: TextKey,
    val detail: String? = null,
)

/**
 * 副作用的描述。转移函数产出它，边界（feature / app）执行它。
 * 禁止在转移函数内做 I/O、启动协程、写数据库（04 章 §4.2）。
 */
sealed interface Effect {
    data class RunDiagnosis(val revisionId: Long) : Effect
    data object CancelDiagnosis : Effect
    data class LoadCandidates(val baseRevisionId: Long, val conflictRuleId: String, val conflictId: String) : Effect
    data class BuildPreview(val baseRevisionId: Long, val componentId: String) : Effect
    data class RecheckRevision(val revisionId: Long) : Effect
    data class PersistRevision(val revision: BuildRevision) : Effect
    data class PersistApplied(val revision: BuildRevision) : Effect
    data class SaveDraft(val revision: BuildRevision) : Effect
    data class ShowText(val key: TextKey) : Effect
    data class Navigate(val target: NavTarget) : Effect
}

enum class NavTarget { ENTRY, WORKSPACE, DIAGNOSTICS, COMPARE, SAVED_BUILDS }

/** 转移函数输出：新状态 + 要执行的副作用 + 可选的拒绝原因。 */
data class TransitionResult<S : Any>(
    val state: S,
    val effects: List<Effect> = emptyList(),
    val rejection: Rejection? = null,
)

// ---------------------------------------------------------------- 诊断状态机

sealed interface DiagnosisState {
    data object Idle : DiagnosisState
    data class Running(val revisionId: Long) : DiagnosisState
    data class Done(val revisionId: Long) : DiagnosisState
    data class Failed(val revisionId: Long, val reason: String) : DiagnosisState
}

sealed interface DiagnosisEvent {
    data object StartCheck : DiagnosisEvent
    data class CheckCompleted(val revisionId: Long, val diagnosis: Diagnosis) : DiagnosisEvent
    data class CheckFailed(val revisionId: Long, val reason: String) : DiagnosisEvent
    /** 编辑产生的新 revision（含快照本体，便于副作用落草稿）。 */
    data class BuildEdited(val revision: BuildRevision) : DiagnosisEvent
}

// ---------------------------------------------------------------- 替换状态机

/** 三个状态共用的定位上下文：基础 revision + 哪条冲突。 */
data class ReplacementContext(
    val baseRevisionId: Long,
    val conflictRuleId: String,
    val conflictId: String,
)

sealed interface ReplacementState {
    data object Idle : ReplacementState
    data class Candidates(val ctx: ReplacementContext) : ReplacementState
    data class Previewing(
        val ctx: ReplacementContext,
        val candidateId: String,
        val previewRevisionId: Long?,
    ) : ReplacementState

    data class Rechecking(
        val ctx: ReplacementContext,
        val previewRevisionId: Long,
    ) : ReplacementState

    data class Applied(val newRevisionId: Long) : ReplacementState
    data class NoCandidate(
        val ctx: ReplacementContext?,
        val reason: NoCandidateReason,
    ) : ReplacementState
}

sealed interface ReplacementEvent {
    data class ConflictSelected(val conflictId: String, val ruleId: String) : ReplacementEvent
    data class CandidatesLoaded(val baseRevisionId: Long, val result: SuggestionResult) : ReplacementEvent
    data class PreviewCandidate(val candidateId: String) : ReplacementEvent
    data class PreviewBuilt(val previewRevision: BuildRevision) : ReplacementEvent
    data object Apply : ReplacementEvent
    data object Cancel : ReplacementEvent
    data class RecheckCompleted(val revision: BuildRevision, val diagnosis: Diagnosis) : ReplacementEvent
    data class RecheckRejected(val reason: String) : ReplacementEvent
    data class BuildEdited(val revision: BuildRevision) : ReplacementEvent
    data object Dismiss : ReplacementEvent
}

// ---------------------------------------------------------------- UI 契约

/** UI 只能发这些意图，不能直接改状态。 */
sealed interface WorkspaceIntent {
    data class AddComponent(val componentId: String) : WorkspaceIntent
    data class RemoveComponent(val componentId: String) : WorkspaceIntent
    data class ReplaceComponent(val oldComponentId: String, val newComponentId: String) : WorkspaceIntent
    data class SetQuantity(val componentId: String, val quantity: Int) : WorkspaceIntent
    data object Undo : WorkspaceIntent
    data object StartCheck : WorkspaceIntent
    data class SelectConflict(val conflictId: String) : WorkspaceIntent
    data class PreviewCandidate(val candidateId: String) : WorkspaceIntent
    data object ApplySuggestion : WorkspaceIntent
    data object CancelPreview : WorkspaceIntent
    data object DismissNoCandidate : WorkspaceIntent
    data object SaveBuild : WorkspaceIntent
    data class SwitchBuild(val buildId: String) : WorkspaceIntent
    data class ImportPlanCard(val json: String) : WorkspaceIntent
    data object ReloadReferencePrice : WorkspaceIntent
}

/**
 * 工作区对外暴露的唯一状态。UI 是它的纯函数。
 *
 * [currentDiagnosis] 与 [isDiagnosisStale] 是**安全访问器**：
 * 把 INV-03 编码进类型，调用方拿不到「不属于当前 revision 的诊断原文」。
 */
data class WorkspaceUiState(
    val build: Build? = null,
    val revision: BuildRevision? = null,
    val diagnosisState: DiagnosisState = DiagnosisState.Idle,
    val diagnosis: Diagnosis? = null,
    val replacementState: ReplacementState = ReplacementState.Idle,
    val suggestions: SuggestionResult? = null,
    val previewRevision: BuildRevision? = null,
    val savedBuilds: List<Build> = emptyList(),
    val banner: TextKey? = null,
    val rejection: Rejection? = null,
    val isSaved: Boolean = false,
    val isBusy: Boolean = false,
    val canUndo: Boolean = false,
) {
    /** 只有当诊断的 revisionId 等于当前 revision 时才返回，否则 null。 */
    val currentDiagnosis: Diagnosis?
        get() = diagnosis?.takeIf { it.revisionId == revision?.id }

    /** 曾经有诊断、但对应的是旧 revision。 */
    val isDiagnosisStale: Boolean
        get() = diagnosis != null && diagnosis.revisionId != revision?.id
}
