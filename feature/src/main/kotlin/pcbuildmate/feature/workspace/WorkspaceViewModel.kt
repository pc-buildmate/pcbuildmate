package pcbuildmate.feature.workspace

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import pcbuildmate.contracts.Build
import pcbuildmate.contracts.BuildItem
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.CataloguePort
import pcbuildmate.contracts.CompatibilityEvaluator
import pcbuildmate.contracts.Diagnosis
import pcbuildmate.contracts.DiagnosisEvent
import pcbuildmate.contracts.DiagnosisState
import pcbuildmate.contracts.Effect
import pcbuildmate.contracts.OverallStatus
import pcbuildmate.contracts.Preferences
import pcbuildmate.contracts.ReplacementAdvisorPort
import pcbuildmate.contracts.Rejection
import pcbuildmate.contracts.RejectionReason
import pcbuildmate.contracts.ReplacementEvent
import pcbuildmate.contracts.ReplacementState
import pcbuildmate.contracts.RiskLevel
import pcbuildmate.contracts.RuleOutcome
import pcbuildmate.contracts.Severity
import pcbuildmate.contracts.SuggestionResult
import pcbuildmate.contracts.TextKey
import pcbuildmate.contracts.UseCase
import pcbuildmate.contracts.WorkspaceIntent
import pcbuildmate.contracts.WorkspaceUiState
import pcbuildmate.domain.state.DiagnosisContext
import pcbuildmate.domain.state.DiagnosisMachine
import pcbuildmate.domain.state.ReplacementMachine
import pcbuildmate.domain.state.ReplacementGuard

/**
 * D2 工作区 ViewModel：MVI 编排器。
 *
 * - 状态机是纯函数；这里只负责执行 Effect、把结果回灌成 Event；
 * - 所有异步/耗时动作（检查、候选、预览、重检）目前同步执行，演示规模足够；
 * - 不把业务判定写在 VM 里：判定来自 [CompatibilityEvaluator] / [DefaultReplacementAdvisor]。
 */
class WorkspaceViewModel(
    private val catalogue: CataloguePort,
    private val evaluator: CompatibilityEvaluator,
    private val advisor: ReplacementAdvisorPort,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WorkspaceUiState())
    val uiState: StateFlow<WorkspaceUiState> = _uiState.asStateFlow()

    private var build: Build? = null
    private var revision: BuildRevision? = null
    private var diagnosis: Diagnosis? = null
    private var diagnosisState: DiagnosisState = DiagnosisState.Idle
    private var replacementState: ReplacementState = ReplacementState.Idle
    private var suggestions: SuggestionResult? = null
    private var previewRevision: BuildRevision? = null
    private var revisionSeq: Long = 1L
    private var lastRejection: Rejection? = null
    private var lastBanner: TextKey? = null
    private var isSaved: Boolean = false

    /** Entry 页“载入演示配置”。演示锚点：8 类零件，总价 7800。 */
    fun startDemo() {
        val ids = listOf(
            "cpu-demo-7600x" to 1500L,
            "mb-demo-b650" to 900L,
            "mem-demo-ddr5-32" to 500L,
            "gpu-demo-337" to 3400L,
            "psu-demo-750" to 600L,
            "case-demo-320" to 400L,
            "cooler-demo-160" to 300L,
            "storage-demo-nvme" to 200L,
        )
        val items = ids.mapNotNull { (id, price) ->
            val record = catalogue.getById(id) ?: return@mapNotNull null
            BuildItem(
                componentId = record.componentId,
                category = record.category,
                quantity = 1,
                unitPriceCny = price,
            )
        }
        val rev = BuildRevision(
            id = 1L,
            buildId = "demo-build",
            items = items,
            totalCny = BuildRevision.computeTotal(items),
            catalogueVersion = catalogue.catalogueVersion(),
            priceDate = catalogue.priceDate(),
            userModified = false,
        )
        val b = Build(
            buildId = "demo-build",
            budgetCny = 8000L,
            useCase = UseCase.GAMING,
            preferences = Preferences(),
            currentRevisionId = rev.id,
            userModified = false,
            createdAt = catalogue.priceDate(),
            updatedAt = catalogue.priceDate(),
        )
        revisionSeq = 1L
        build = b
        revision = rev
        diagnosis = null
        diagnosisState = DiagnosisState.Idle
        replacementState = ReplacementState.Idle
        suggestions = null
        previewRevision = null
        lastRejection = null
        lastBanner = null
        isSaved = false
        publish()
    }

    fun onIntent(intent: WorkspaceIntent) {
        lastRejection = null
        lastBanner = null
        when (intent) {
            is WorkspaceIntent.StartCheck -> startCheck()
            is WorkspaceIntent.SelectConflict -> selectConflict(intent.conflictId)
            is WorkspaceIntent.PreviewCandidate -> previewCandidate(intent.candidateId)
            is WorkspaceIntent.ApplySuggestion -> applySuggestion()
            is WorkspaceIntent.CancelPreview -> cancelPreview()
            is WorkspaceIntent.DismissNoCandidate -> dismissNoCandidate()
            is WorkspaceIntent.SetQuantity -> setQuantity(intent.componentId, intent.quantity)
            is WorkspaceIntent.SaveBuild -> {
                isSaved = true
                publish()
            }

            else -> Unit
        }
    }

    // ------------------------------------------------------------ diagnosis

    private fun startCheck() {
        val rev = revision
        val ctx = DiagnosisContext(build = build, revision = rev, diagnosis = diagnosis)
        val result = DiagnosisMachine.transition(diagnosisState, DiagnosisEvent.StartCheck, ctx)
        applyDiagnosis(result)
    }

    private fun applyDiagnosis(result: pcbuildmate.contracts.TransitionResult<DiagnosisState>) {
        diagnosisState = result.state
        result.rejection?.let {
            lastRejection = it
            lastBanner = it.textKey
        }
        result.effects.forEach(::executeDiagnosisEffect)
        publish()
    }

    private fun executeDiagnosisEffect(effect: Effect) {
        when (effect) {
            is Effect.RunDiagnosis -> {
                val rev = revision ?: return
                val diag = diagnose(rev)
                diagnosis = diag
                val ctx = DiagnosisContext(build = build, revision = rev, diagnosis = diag)
                applyDiagnosis(
                    DiagnosisMachine.transition(
                        diagnosisState,
                        DiagnosisEvent.CheckCompleted(rev.id, diag),
                        ctx,
                    ),
                )
            }

            Effect.CancelDiagnosis -> Unit
            is Effect.SaveDraft -> Unit
            is Effect.ShowText -> lastBanner = effect.key
            else -> Unit
        }
    }

    private fun diagnose(rev: BuildRevision): Diagnosis {
        val outcomes = evaluator.evaluate(rev)
        val conflicts = outcomes.filterIsInstance<RuleOutcome.Conflict>()
        val unverified = outcomes.filterIsInstance<RuleOutcome.Unverified>()
        val hasBlocker = conflicts.any { it.severity == Severity.BLOCKER }
        val hasMajor = conflicts.any { it.severity == Severity.MAJOR }

        val overall = when {
            hasBlocker -> OverallStatus.CONFLICT
            conflicts.isNotEmpty() && unverified.isNotEmpty() -> OverallStatus.MIXED
            conflicts.isNotEmpty() -> OverallStatus.CONFLICT
            unverified.isNotEmpty() -> OverallStatus.UNVERIFIED
            else -> OverallStatus.PASSED
        }
        val risk = when {
            hasBlocker -> RiskLevel.HIGH
            hasMajor -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }
        val score = (
            100 -
                30 * conflicts.count { it.severity == Severity.BLOCKER } -
                15 * conflicts.count { it.severity == Severity.MAJOR } -
                5 * conflicts.count { it.severity == Severity.MINOR } -
                3 * unverified.size
            ).coerceIn(0, 100)

        return Diagnosis(
            revisionId = rev.id,
            outcomes = outcomes,
            overall = overall,
            riskLevel = risk,
            score = score,
            computedAt = catalogue.priceDate(),
        )
    }

    // ------------------------------------------------------------ replacement

    private fun selectConflict(conflictId: String) {
        val rev = revision ?: return
        val conflict = diagnosis?.outcomes
            ?.filterIsInstance<RuleOutcome.Conflict>()
            ?.firstOrNull { it.conflictId == conflictId }
        val event = ReplacementEvent.ConflictSelected(
            conflictId = conflictId,
            ruleId = conflict?.ruleId ?: "",
        )
        applyReplacement(
            ReplacementMachine.transition(
                replacementState,
                event,
                replacementGuard(),
            ),
        )
    }

    private fun previewCandidate(candidateId: String) {
        applyReplacement(
            ReplacementMachine.transition(
                replacementState,
                ReplacementEvent.PreviewCandidate(candidateId),
                replacementGuard(),
            ),
        )
    }

    private fun applySuggestion() {
        applyReplacement(
            ReplacementMachine.transition(
                replacementState,
                ReplacementEvent.Apply,
                replacementGuard(),
            ),
        )
    }

    private fun cancelPreview() {
        applyReplacement(
            ReplacementMachine.transition(
                replacementState,
                ReplacementEvent.Cancel,
                replacementGuard(),
            ),
        )
    }

    private fun dismissNoCandidate() {
        applyReplacement(
            ReplacementMachine.transition(
                replacementState,
                ReplacementEvent.Dismiss,
                replacementGuard(),
            ),
        )
    }

    private fun applyReplacement(result: pcbuildmate.contracts.TransitionResult<ReplacementState>) {
        replacementState = result.state
        result.rejection?.let {
            lastRejection = it
            lastBanner = it.textKey
        }
        result.effects.forEach(::executeReplacementEffect)
        publish()
    }

    private fun executeReplacementEffect(effect: Effect) {
        when (effect) {
            is Effect.LoadCandidates -> {
                val rev = revision ?: return
                val conflict = diagnosis?.outcomes
                    ?.filterIsInstance<RuleOutcome.Conflict>()
                    ?.firstOrNull { it.conflictId == effect.conflictId }
                    ?: return
                val result = advisor.suggest(rev, conflict)
                suggestions = result
                applyReplacement(
                    ReplacementMachine.transition(
                        replacementState,
                        ReplacementEvent.CandidatesLoaded(result.baseRevisionId, result),
                        replacementGuard(),
                    ),
                )
            }

            is Effect.BuildPreview -> {
                val rev = revision ?: return
                val record = catalogue.getById(effect.componentId) ?: return
                // effect.componentId 是候选件；被替换的是同一类目的当前件
                val item = rev.items.firstOrNull { it.category == record.category } ?: return

                val items = rev.items.map {
                    if (it.componentId == item.componentId) {
                        it.copy(componentId = record.componentId, unitPriceCny = record.priceCny)
                    } else {
                        it
                    }
                }
                val preview = rev.copy(
                    id = ++revisionSeq,
                    items = items,
                    totalCny = BuildRevision.computeTotal(items),
                    userModified = true,
                )
                previewRevision = preview
                applyReplacement(
                    ReplacementMachine.transition(
                        replacementState,
                        ReplacementEvent.PreviewBuilt(preview),
                        replacementGuard(),
                    ),
                )
            }

            is Effect.RecheckRevision -> {
                val preview = previewRevision ?: return
                val diag = diagnose(preview)
                diagnosis = diag
                applyReplacement(
                    ReplacementMachine.transition(
                        replacementState,
                        ReplacementEvent.RecheckCompleted(preview, diag),
                        replacementGuard(),
                    ),
                )
            }

            is Effect.PersistApplied -> {
                val applied = effect.revision
                build = build?.copy(
                    currentRevisionId = applied.id,
                    updatedAt = catalogue.priceDate(),
                )
                revision = applied
                previewRevision = applied
                diagnosisState = DiagnosisState.Done(applied.id)
                isSaved = false
            }

            is Effect.SaveDraft -> Unit
            is Effect.ShowText -> lastBanner = effect.key
            else -> Unit
        }
    }

    private fun replacementGuard(): ReplacementGuard {
        val currentRevisionId = revision?.id ?: 0L
        return ReplacementGuard(
            currentRevisionId = currentRevisionId,
            diagnosis = diagnosis,
            candidateIds = suggestions?.candidates?.map { it.componentId }?.toSet().orEmpty(),
        )
    }

    // ------------------------------------------------------------ edit

    private fun setQuantity(componentId: String, quantity: Int) {
        if (quantity < 1) {
            lastRejection = Rejection(RejectionReason.QUANTITY_INVALID, TextKey.QUANTITY_INVALID)
            lastBanner = TextKey.QUANTITY_INVALID
            publish()
            return
        }
        val rev = revision ?: return
        val items = rev.items.map {
            if (it.componentId == componentId) it.copy(quantity = quantity) else it
        }
        val next = rev.copy(
            id = ++revisionSeq,
            items = items,
            totalCny = BuildRevision.computeTotal(items),
            userModified = true,
        )
        build = build?.copy(currentRevisionId = next.id, userModified = true)
        revision = next
        diagnosisState = DiagnosisState.Idle
        replacementState = ReplacementState.Idle
        suggestions = null
        previewRevision = null
        isSaved = false
        // diagnosis 保留，用于 UI 的 stale banner；revisionId 不匹配即过期
        publish()
    }

    // ------------------------------------------------------------ publish

    private fun publish() {
        _uiState.value = WorkspaceUiState(
            build = build,
            revision = revision,
            diagnosisState = diagnosisState,
            diagnosis = diagnosis,
            replacementState = replacementState,
            suggestions = suggestions,
            previewRevision = previewRevision,
            banner = lastBanner,
            rejection = lastRejection,
            isSaved = isSaved,
            isBusy = diagnosisState is DiagnosisState.Running ||
                replacementState is ReplacementState.Rechecking,
        )
    }
}
