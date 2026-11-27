package pcbuildmate.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import android.content.res.Resources
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.Candidate
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.Diagnosis
import pcbuildmate.contracts.DiagnosisState
import pcbuildmate.contracts.OverallStatus
import pcbuildmate.contracts.QuantityRules
import pcbuildmate.contracts.ReplacementState
import pcbuildmate.contracts.RiskLevel
import pcbuildmate.contracts.RuleOutcome
import pcbuildmate.contracts.Severity
import pcbuildmate.contracts.TextKey
import pcbuildmate.contracts.UnverifiedReason
import pcbuildmate.contracts.UseCase
import pcbuildmate.contracts.WorkspaceIntent
import pcbuildmate.contracts.WorkspaceUiState
import pcbuildmate.ui.theme.PcBuildmateTheme

private fun categoryLabel(res: Resources, category: Category): String = when (category) {
    Category.CPU -> res.getString(R.string.category_cpu)
    Category.MOTHERBOARD -> res.getString(R.string.category_motherboard)
    Category.MEMORY -> res.getString(R.string.category_memory)
    Category.GPU -> res.getString(R.string.category_gpu)
    Category.PSU -> res.getString(R.string.category_psu)
    Category.CASE -> res.getString(R.string.category_case)
    Category.COOLER -> res.getString(R.string.category_cooler)
    Category.STORAGE -> res.getString(R.string.category_storage)
}

private fun ruleLabel(res: Resources, ruleId: String): String = when (ruleId) {
    "CPU_SOCKET_MATCH" -> res.getString(R.string.rule_cpu_socket_match)
    "CPU_MB_CHIPSET_SUPPORT" -> res.getString(R.string.rule_cpu_mb_chipset_support)
    "CPU_MB_BIOS_SUPPORT" -> res.getString(R.string.rule_cpu_mb_bios_support)
    "MEMORY_DDR_GENERATION" -> res.getString(R.string.rule_memory_ddr_generation)
    "MEMORY_SLOT_COUNT" -> res.getString(R.string.rule_memory_slot_count)
    "MEMORY_CAPACITY_LIMIT" -> res.getString(R.string.rule_memory_capacity_limit)
    "GPU_CASE_LENGTH" -> res.getString(R.string.rule_gpu_case_length)
    "GPU_CASE_THICKNESS" -> res.getString(R.string.rule_gpu_case_thickness)
    "GPU_CASE_SLOT_WIDTH" -> res.getString(R.string.rule_gpu_case_slot_width)
    "PSU_CONTINUOUS_POWER" -> res.getString(R.string.rule_psu_continuous_power)
    "PSU_CONNECTORS" -> res.getString(R.string.rule_psu_connectors)
    "COOLER_CASE_HEIGHT" -> res.getString(R.string.rule_cooler_case_height)
    "COOLER_CPU_SOCKET" -> res.getString(R.string.rule_cooler_cpu_socket)
    "MB_CASE_FORM_FACTOR" -> res.getString(R.string.rule_mb_case_form_factor)
    "STORAGE_MB_INTERFACE" -> res.getString(R.string.rule_storage_mb_interface)
    else -> ruleId
}

private fun useCaseLabel(res: Resources, uc: UseCase): String = when (uc) {
    UseCase.GAMING -> res.getString(R.string.usecase_gaming)
    UseCase.STUDY_OFFICE -> res.getString(R.string.usecase_study_office)
    UseCase.CONTENT_CREATION -> res.getString(R.string.usecase_content_creation)
    UseCase.LOCAL_AI -> res.getString(R.string.usecase_local_ai)
}

@Composable
fun EntryScreen(
    onStartDemo: () -> Unit,
    onRecommend: (Long, UseCase) -> Unit,
    error: String? = null,
    savedBuilds: List<pcbuildmate.contracts.Build> = emptyList(),
    onOpenSaved: (String) -> Unit = {},
) {
    var budgetText by rememberSaveable { mutableStateOf("8000") }
    var useCase by rememberSaveable { mutableStateOf(UseCase.GAMING) }
    val budget = budgetText.toLongOrNull() ?: 0L
    val res = LocalContext.current.resources

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.home_title), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.home_subtitle),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(20.dp))
        AssistChip(onClick = {}, label = { Text(stringResource(R.string.entry_reference_badge)) })
        Spacer(Modifier.height(18.dp))

        OutlinedTextField(
            value = budgetText,
            onValueChange = { budgetText = it.filter { c -> c.isDigit() }.take(7) },
            label = { Text(stringResource(R.string.entry_budget_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            UseCase.entries.forEach { uc ->
                FilterChip(
                    selected = useCase == uc,
                    onClick = { useCase = uc },
                    label = { Text(useCaseLabel(res, uc), style = MaterialTheme.typography.labelSmall) },
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Button(
            onClick = { onRecommend(budget, useCase) },
            modifier = Modifier.fillMaxWidth(),
            enabled = budget > 0,
        ) {
            Text(stringResource(R.string.entry_generate))
        }
        if (error != null) {
            Spacer(Modifier.height(8.dp))
            Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(18.dp))
        OutlinedButton(onClick = onStartDemo, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.entry_load_demo))
        }
        if (savedBuilds.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { onOpenSaved(savedBuilds.first().buildId) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.entry_continue_saved, savedBuilds.size))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.entry_demo_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkspaceScreen(
    state: WorkspaceUiState,
    labels: Map<String, String>,
    weights: Map<Category, Double> = emptyMap(),
    onIntent: (WorkspaceIntent) -> Unit,
    onCheck: () -> Unit,
    onCompare: () -> Unit,
    onPreview3d: () -> Unit = {},
    onBack: () -> Unit,
) {
    val revision = state.revision
    val res = LocalContext.current.resources
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.workspace_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } },
                actions = { DemoBadge() },
            )
        },
    ) { padding ->
        if (revision == null) {
            EmptyState(stringResource(R.string.workspace_empty))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { BudgetCard(revision, state) }
            if (weights.isNotEmpty()) {
                item { WeightCard(weights) }
            }
            state.banner?.let { key ->
                item { Banner(bannerText(res, key)) }
            }
            if (state.isDiagnosisStale) {
                item {
                    Banner(stringResource(R.string.workspace_stale_banner))
                }
            }
            item {
                Text(
                    stringResource(R.string.workspace_items_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(revision.items, key = { it.componentId }) { item ->
                ItemRow(
                    item = item,
                    label = labels[item.componentId] ?: item.componentId,
                    onMinus = { onIntent(WorkspaceIntent.SetQuantity(item.componentId, item.quantity - 1)) },
                    onPlus = { onIntent(WorkspaceIntent.SetQuantity(item.componentId, item.quantity + 1)) },
                )
            }
            item {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onCheck,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isBusy,
                ) {
                    if (state.diagnosisState is DiagnosisState.Running) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.size(8.dp))
                    }
                    Text(stringResource(R.string.action_check))
                }
                if (state.canUndo) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { onIntent(WorkspaceIntent.Undo) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.action_undo))
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { onIntent(WorkspaceIntent.SaveBuild) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.isSaved) stringResource(R.string.action_saved) else stringResource(R.string.action_save))
                }
                if (state.savedBuilds.size >= 2) {
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onCompare, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.action_compare_saved, state.savedBuilds.size))
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = onPreview3d, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_preview_3d))
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    state: WorkspaceUiState,
    labels: Map<String, String>,
    onIntent: (WorkspaceIntent) -> Unit,
    onBack: () -> Unit,
) {
    val res = LocalContext.current.resources
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.diagnostics_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } },
                actions = { DemoBadge() },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            state.banner?.let { key ->
                item { Banner(bannerText(res, key)) }
            }
            when (val d = state.diagnosisState) {
                is DiagnosisState.Running -> item { LoadingCard(stringResource(R.string.loading_checking)) }
                is DiagnosisState.Failed -> item { ErrorCard(stringResource(R.string.error_check_failed, d.reason)) }
                else -> Unit
            }

            val diagnosis = state.currentDiagnosis
            if (diagnosis != null) {
                item { DiagnosisSummary(diagnosis) }
                val conflicts = diagnosis.outcomes.filterIsInstance<RuleOutcome.Conflict>()
                if (conflicts.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.section_conflicts)) }
                    items(conflicts, key = { it.conflictId }) { conflict ->
                        ConflictCard(
                            conflict = conflict,
                            labels = labels,
                            onSelect = {
                                onIntent(WorkspaceIntent.SelectConflict(conflict.conflictId))
                            },
                        )
                    }
                }

                val unverified = diagnosis.outcomes.filterIsInstance<RuleOutcome.Unverified>()
                if (unverified.isNotEmpty()) {
                    item { SectionTitle(stringResource(R.string.section_unverified)) }
                    items(unverified, key = { it.ruleId + (it.missingField?.fieldPath ?: "") }) { u ->
                        UnverifiedCard(u)
                    }
                }
            }

            when (val r = state.replacementState) {
                is ReplacementState.Candidates -> {
                    val result = state.suggestions
                    if (result != null && result.candidates.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.section_candidates)) }
                        items(result.candidates, key = { it.componentId }) { candidate ->
                            CandidateCard(
                                candidate = candidate,
                                label = labels[candidate.componentId] ?: candidate.componentId,
                                onPreview = {
                                    onIntent(WorkspaceIntent.PreviewCandidate(candidate.componentId))
                                },
                            )
                        }
                    }
                }

                is ReplacementState.NoCandidate -> {
                    item {
                        ErrorCard(
                            stringResource(
                                R.string.no_candidate_message,
                                r.reason.blockingConstraint,
                                r.reason.suggestedDirection,
                            ),
                        )
                    }
                    item {
                        OutlinedButton(
                            onClick = { onIntent(WorkspaceIntent.DismissNoCandidate) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(stringResource(R.string.common_close)) }
                    }
                }

                is ReplacementState.Previewing -> {
                    val preview = state.previewRevision
                    item {
                        PreviewCard(
                            preview = preview,
                            onApply = { onIntent(WorkspaceIntent.ApplySuggestion) },
                            onCancel = { onIntent(WorkspaceIntent.CancelPreview) },
                        )
                    }
                }

                is ReplacementState.Rechecking -> item { LoadingCard(stringResource(R.string.loading_rechecking)) }

                is ReplacementState.Applied -> {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F6E9))) {
                            Column(Modifier.padding(16.dp)) {
                                Text(stringResource(R.string.applied_title), fontWeight = FontWeight.Bold)
                                Text(stringResource(R.string.applied_note))
                            }
                        }
                    }
                    item {
                        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.action_back_to_workspace))
                        }
                    }
                }

                else -> Unit
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    result: pcbuildmate.contracts.ComparisonResult,
    labels: Map<String, String>,
    onBack: () -> Unit,
) {
    val res = LocalContext.current.resources
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.compare_title)) },
                navigationIcon = { TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } },
                actions = { DemoBadge() },
            )
        },
    ) { padding ->
        if (result.revisions.size < 2) {
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.compare_need_two))
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.compare_need_two_hint),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
                }
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (TextKey.COMPARE_DIAGNOSTICS_STALE in result.warnings) {
                item { Banner(stringResource(R.string.compare_stale_banner)) }
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    result.revisions.forEachIndexed { index, rev ->
                        Card(modifier = Modifier.weight(1f)) {
                            Column(Modifier.padding(10.dp)) {
                                Text(
                                    if (index == 0) stringResource(R.string.compare_config_a) else stringResource(R.string.compare_config_b),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                                Text(
                                    money(rev.totalCny),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    stringResource(R.string.compare_revision, rev.id),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                val diag = result.diagnoses.getOrNull(index)
                                Text(
                                    diag?.let { statusLabel(res, it.overall.name) } ?: stringResource(R.string.compare_status_recheck),
                                    color = if (diag == null) {
                                        Color(0xFF9A6700)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }

            item { SectionTitle(stringResource(R.string.section_diff)) }
            items(result.deltas, key = { it.field }) { delta ->
                DeltaRow(delta, labels)
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DeltaRow(delta: pcbuildmate.contracts.FieldDelta, labels: Map<String, String>) {
    val res = LocalContext.current.resources
    val fieldLabel = when (delta.field) {
        "totalCny" -> stringResource(R.string.compare_total)
        "status" -> stringResource(R.string.compare_status)
        else -> categoryLabelByName(res, delta.field)
    }
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (delta.highlighted) {
                Color(0xFFFFF6E0)
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
        ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(fieldLabel, style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                delta.values.forEach { value ->
                    Text(
                        formatDeltaValue(res, delta.field, value),
                        modifier = Modifier.weight(1f),
                        fontWeight = if (delta.highlighted) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

private fun formatDeltaValue(res: Resources, field: String, value: String): String =
    if (field == "totalCny" || field in Category.entries.map { it.name }) {
        value.toLongOrNull()?.let { money(it) } ?: value
    } else {
        statusLabel(res, value)
    }

private fun statusLabel(res: Resources, value: String): String = when (value) {
    "PASSED" -> res.getString(R.string.status_passed)
    "CONFLICT" -> res.getString(R.string.status_conflict)
    "MIXED" -> res.getString(R.string.status_mixed)
    "UNVERIFIED" -> res.getString(R.string.status_unverified)
    "UNCHECKED" -> res.getString(R.string.status_unchecked)
    else -> value
}

private fun categoryLabelByName(res: Resources, name: String): String =
    Category.entries.firstOrNull { it.name == name }?.let { categoryLabel(res, it) } ?: name

// ------------------------------------------------------------------ pieces

@Composable
private fun DemoBadge() {
    AssistChip(onClick = {}, label = { Text(stringResource(R.string.demo_badge)) })
}

@Composable
private fun WeightCard(weights: Map<Category, Double>) {
    val res = LocalContext.current.resources
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.weights_title), style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                weights.entries
                    .sortedByDescending { it.value }
                    .joinToString(" · ") { (category, w) ->
                        "${categoryLabel(res, category)} ${(w * 100).toInt()}%"
                    },
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun BudgetCard(revision: BuildRevision, state: WorkspaceUiState) {
    val budget = state.build?.budgetCny ?: 0L
    val remaining = budget - revision.totalCny
    Card {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.budget_label), style = MaterialTheme.typography.labelLarge)
                Text(money(budget), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.spent_label), style = MaterialTheme.typography.labelLarge)
                Text(money(revision.totalCny), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.remaining_label), style = MaterialTheme.typography.labelLarge)
                Text(
                    money(remaining),
                    fontWeight = FontWeight.Bold,
                    color = if (remaining >= 0) Color(0xFF1B7F3A) else MaterialTheme.colorScheme.error,
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Text(
                stringResource(R.string.price_snapshot, revision.priceDate, revision.catalogueVersion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
    }
}

@Composable
private fun ItemRow(
    item: pcbuildmate.contracts.BuildItem,
    label: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
) {
    val editable = QuantityRules.allowsQuantityAboveOne(item.category)
    val res = LocalContext.current.resources
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(categoryLabel(res, item.category), style = MaterialTheme.typography.labelSmall)
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (editable) {
                TextButton(onClick = onMinus, enabled = item.quantity > 1) { Text("−") }
                Text("${item.quantity}", fontWeight = FontWeight.Bold)
                TextButton(onClick = onPlus) { Text("+") }
            } else {
                Text(
                    stringResource(R.string.quantity_fixed),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.size(8.dp))
            Text(money(item.unitPriceCny * item.quantity), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DiagnosisSummary(diagnosis: Diagnosis) {
    val res = LocalContext.current.resources
    val (title, color) = when (diagnosis.overall) {
        OverallStatus.PASSED -> stringResource(R.string.diagnosis_passed) to Color(0xFF1B7F3A)
        OverallStatus.CONFLICT -> stringResource(R.string.diagnosis_conflict) to MaterialTheme.colorScheme.error
        OverallStatus.MIXED -> stringResource(R.string.diagnosis_mixed) to Color(0xFF9A6700)
        OverallStatus.UNVERIFIED -> stringResource(R.string.diagnosis_unverified) to Color(0xFF9A6700)
    }
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = color)
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(
                    R.string.diagnosis_risk_score,
                    riskLabel(res, diagnosis.riskLevel),
                    diagnosis.score?.toString() ?: "—",
                    diagnosis.revisionId,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun ConflictCard(
    conflict: RuleOutcome.Conflict,
    labels: Map<String, String>,
    onSelect: () -> Unit,
) {
    val res = LocalContext.current.resources
    Card {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(ruleLabel(res, conflict.ruleId), fontWeight = FontWeight.Bold)
                Text(
                    severityLabel(res, conflict.severity),
                    color = if (conflict.severity == Severity.BLOCKER) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color(0xFF9A6700)
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${labels[conflict.left.componentId] ?: conflict.left.componentId} " +
                    "${conflict.leftValue} vs ${labels[conflict.right.componentId] ?: conflict.right.componentId} " +
                    "${conflict.rightValue}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.conflict_id_label, conflict.conflictId),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onSelect, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_find_replacement))
            }
        }
    }
}

@Composable
private fun UnverifiedCard(u: RuleOutcome.Unverified) {
    val res = LocalContext.current.resources
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF6E0))) {
        Column(Modifier.padding(14.dp)) {
            Text(ruleLabel(res, u.ruleId), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                when (u.reason) {
                    UnverifiedReason.MISSING_FIELD -> stringResource(
                        R.string.unverified_missing_field,
                        u.missingField?.fieldPath ?: "部件",
                    )
                    UnverifiedReason.SOURCE_CONFLICT -> stringResource(
                        R.string.unverified_source_conflict,
                        u.missingField?.fieldPath ?: "",
                    )
                    UnverifiedReason.UNSUPPORTED_RULE -> stringResource(R.string.unverified_unsupported_rule)
                    UnverifiedReason.INSUFFICIENT_DATA -> stringResource(R.string.unverified_insufficient_data)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun CandidateCard(candidate: Candidate, label: String, onPreview: () -> Unit) {
    Card {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("#${candidate.rank} $label", fontWeight = FontWeight.Bold)
                Text(
                    if (candidate.priceDeltaCny >= 0) "+${money(candidate.priceDeltaCny)}" else money(candidate.priceDeltaCny),
                    color = if (candidate.priceDeltaCny > 0) MaterialTheme.colorScheme.error else Color(0xFF1B7F3A),
                    fontWeight = FontWeight.Bold,
                )
            }
            if (candidate.specDelta.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    candidate.specDelta.entries.take(3).joinToString(" · ") { "${it.key}: ${it.value}" },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (candidate.affectsOtherRules.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.candidate_affects, candidate.affectsOtherRules.joinToString()),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9A6700),
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onPreview, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_preview))
            }
        }
    }
}

@Composable
private fun PreviewCard(
    preview: BuildRevision?,
    onApply: () -> Unit,
    onCancel: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF1FF))) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.preview_title), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.preview_total, preview?.let { money(it.totalCny) } ?: "—"))
            Text(
                stringResource(R.string.preview_note),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_apply)) }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}

@Composable
private fun Banner(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF6E0))) {
        Text(text, modifier = Modifier.padding(12.dp), color = Color(0xFF7A4F00))
    }
}

@Composable
private fun LoadingCard(text: String) {
    Card {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            Spacer(Modifier.size(10.dp))
            Text(text)
        }
    }
}

@Composable
private fun ErrorCard(text: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Text(text, modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 6.dp))
}

private fun bannerText(res: Resources, key: TextKey): String = when (key) {
    TextKey.NO_DISPLAY_OUTPUT -> res.getString(R.string.banner_no_display_output)
    TextKey.QUANTITY_NOT_APPLICABLE -> res.getString(R.string.banner_quantity_not_applicable)
    TextKey.QUANTITY_INVALID -> res.getString(R.string.banner_quantity_invalid)
    TextKey.DIAGNOSTICS_REQUIRED -> res.getString(R.string.banner_diagnostics_required)
    TextKey.APPLY_REVISION_STALE -> res.getString(R.string.banner_apply_revision_stale)
    TextKey.CANDIDATE_NOT_FOUND -> res.getString(R.string.banner_candidate_not_found)
    TextKey.CONFLICT_SELECTED_NONE -> res.getString(R.string.banner_conflict_selected_none)
    else -> key.key
}

private fun money(value: Long): String = "¥" + "%,d".format(value)

private fun riskLabel(res: Resources, risk: RiskLevel): String = when (risk) {
    RiskLevel.HIGH -> res.getString(R.string.risk_high)
    RiskLevel.MEDIUM -> res.getString(R.string.risk_medium)
    RiskLevel.LOW -> res.getString(R.string.risk_low)
    RiskLevel.UNKNOWN -> res.getString(R.string.risk_unknown)
}

private fun severityLabel(res: Resources, severity: Severity): String = when (severity) {
    Severity.BLOCKER -> res.getString(R.string.severity_blocker)
    Severity.MAJOR -> res.getString(R.string.severity_major)
    Severity.MINOR -> res.getString(R.string.severity_minor)
}
