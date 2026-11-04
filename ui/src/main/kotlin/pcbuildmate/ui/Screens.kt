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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import pcbuildmate.contracts.BuildRevision
import pcbuildmate.contracts.Candidate
import pcbuildmate.contracts.Category
import pcbuildmate.contracts.Diagnosis
import pcbuildmate.contracts.DiagnosisState
import pcbuildmate.contracts.OverallStatus
import pcbuildmate.contracts.ReplacementState
import pcbuildmate.contracts.RiskLevel
import pcbuildmate.contracts.RuleOutcome
import pcbuildmate.contracts.Severity
import pcbuildmate.contracts.UnverifiedReason
import pcbuildmate.contracts.WorkspaceIntent
import pcbuildmate.contracts.WorkspaceUiState
import pcbuildmate.ui.theme.PcBuildmateTheme

private val categoryLabels: Map<Category, String> = mapOf(
    Category.CPU to "处理器",
    Category.MOTHERBOARD to "主板",
    Category.MEMORY to "内存",
    Category.GPU to "显卡",
    Category.PSU to "电源",
    Category.CASE to "机箱",
    Category.COOLER to "散热器",
    Category.STORAGE to "硬盘",
)

private val ruleLabels: Map<String, String> = mapOf(
    "CPU_SOCKET_MATCH" to "CPU 插槽",
    "CPU_MB_CHIPSET_SUPPORT" to "芯片组支持",
    "CPU_MB_BIOS_SUPPORT" to "BIOS 版本",
    "MEMORY_DDR_GENERATION" to "内存代际",
    "MEMORY_SLOT_COUNT" to "内存插槽",
    "MEMORY_CAPACITY_LIMIT" to "内存容量",
    "GPU_CASE_LENGTH" to "显卡长度",
    "GPU_CASE_THICKNESS" to "显卡厚度",
    "GPU_CASE_SLOT_WIDTH" to "显卡槽宽",
    "PSU_CONTINUOUS_POWER" to "电源功率",
    "PSU_CONNECTORS" to "电源接口",
    "COOLER_CASE_HEIGHT" to "散热器高度",
    "COOLER_CPU_SOCKET" to "散热器扣具",
    "MB_CASE_FORM_FACTOR" to "主板板型",
    "STORAGE_MB_INTERFACE" to "硬盘接口",
)

@Composable
fun PcBuildmateApp() {
    PcBuildmateTheme {
        Surface(color = MaterialTheme.colorScheme.background) {
            EntryScreen(onStartDemo = {})
        }
    }
}

@Composable
fun EntryScreen(onStartDemo: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("PC Buildmate", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "预算 → 配置 → 检查 → 替换 → 比较",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(20.dp))
        AssistChip(onClick = {}, label = { Text("演示数据 · 离线可用") })
        Spacer(Modifier.height(24.dp))
        Button(onClick = onStartDemo, modifier = Modifier.fillMaxWidth()) {
            Text("载入演示配置 · ¥7,800")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "8 类零件 · 预算 ¥8,000 · 含 1 个 BLOCKER",
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
    onIntent: (WorkspaceIntent) -> Unit,
    onCheck: () -> Unit,
    onBack: () -> Unit,
) {
    val revision = state.revision
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("配置工作区") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
                actions = { DemoBadge() },
            )
        },
    ) { padding ->
        if (revision == null) {
            EmptyState("还没有配置，先载入演示配置")
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
            if (state.isDiagnosisStale) {
                item {
                    Banner("配置已修改，请重新检查兼容性")
                }
            }
            item {
                Text(
                    "配置清单",
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
                    Text("检查兼容性")
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("诊断与替换") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
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
            when (val d = state.diagnosisState) {
                is DiagnosisState.Running -> item { LoadingCard("正在检查兼容性…") }
                is DiagnosisState.Failed -> item { ErrorCard("检查失败：${d.reason}") }
                else -> Unit
            }

            val diagnosis = state.currentDiagnosis
            if (diagnosis != null) {
                item { DiagnosisSummary(diagnosis) }
                val conflicts = diagnosis.outcomes.filterIsInstance<RuleOutcome.Conflict>()
                if (conflicts.isNotEmpty()) {
                    item { SectionTitle("冲突") }
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
                    item { SectionTitle("未验证") }
                    items(unverified, key = { it.ruleId + (it.missingField?.fieldPath ?: "") }) { u ->
                        UnverifiedCard(u)
                    }
                }
            }

            when (val r = state.replacementState) {
                is ReplacementState.Candidates -> {
                    val result = state.suggestions
                    if (result != null && result.candidates.isNotEmpty()) {
                        item { SectionTitle("替换候选 · 先过硬约束再排序") }
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
                            "当前目录内没有可选替换：${r.reason.blockingConstraint}\n" +
                                "建议：${r.reason.suggestedDirection}",
                        )
                    }
                    item {
                        OutlinedButton(
                            onClick = { onIntent(WorkspaceIntent.DismissNoCandidate) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("关闭") }
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

                is ReplacementState.Rechecking -> item { LoadingCard("正在全量重检…") }

                is ReplacementState.Applied -> {
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F6E9))) {
                            Column(Modifier.padding(16.dp)) {
                                Text("已应用并重检", fontWeight = FontWeight.Bold)
                                Text("总价已更新；修一个冲突 ≠ 全部兼容，未验证项仍然保留。")
                            }
                        }
                    }
                    item {
                        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                            Text("返回配置")
                        }
                    }
                }

                else -> Unit
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun CompareScreen(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("构建比较", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text("保存两份配置后，可在这里并排比较。", color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f))
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = onBack) { Text("返回") }
    }
}

// ------------------------------------------------------------------ pieces

@Composable
private fun DemoBadge() {
    AssistChip(onClick = {}, label = { Text("演示数据") })
}

@Composable
private fun BudgetCard(revision: BuildRevision, state: WorkspaceUiState) {
    val budget = state.build?.budgetCny ?: 0L
    val remaining = budget - revision.totalCny
    Card {
        Column(Modifier.padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("预算", style = MaterialTheme.typography.labelLarge)
                Text(money(budget), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("已用", style = MaterialTheme.typography.labelLarge)
                Text(money(revision.totalCny), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("剩余", style = MaterialTheme.typography.labelLarge)
                Text(
                    money(remaining),
                    fontWeight = FontWeight.Bold,
                    color = if (remaining >= 0) Color(0xFF1B7F3A) else MaterialTheme.colorScheme.error,
                )
            }
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            Text(
                "价格快照 ${revision.priceDate} · ${revision.catalogueVersion}",
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
    Card {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(categoryLabels[item.category] ?: item.category.name, style = MaterialTheme.typography.labelSmall)
                Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            TextButton(onClick = onMinus, enabled = item.quantity > 1) { Text("−") }
            Text("${item.quantity}", fontWeight = FontWeight.Bold)
            TextButton(onClick = onPlus) { Text("+") }
            Spacer(Modifier.size(8.dp))
            Text(money(item.unitPriceCny * item.quantity), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun DiagnosisSummary(diagnosis: Diagnosis) {
    val (title, color) = when (diagnosis.overall) {
        OverallStatus.PASSED -> "全部通过" to Color(0xFF1B7F3A)
        OverallStatus.CONFLICT -> "存在冲突" to MaterialTheme.colorScheme.error
        OverallStatus.MIXED -> "冲突 + 未验证" to Color(0xFF9A6700)
        OverallStatus.UNVERIFIED -> "有未验证项" to Color(0xFF9A6700)
    }
    Card(colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.08f))) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = color)
            Spacer(Modifier.height(4.dp))
            Text(
                "风险 ${riskLabel(diagnosis.riskLevel)} · 分数 ${diagnosis.score ?: "—"} · " +
                    "revision #${diagnosis.revisionId}",
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
    Card {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(ruleLabels[conflict.ruleId] ?: conflict.ruleId, fontWeight = FontWeight.Bold)
                Text(
                    severityLabel(conflict.severity),
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
                "conflictId ${conflict.conflictId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f),
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onSelect, modifier = Modifier.fillMaxWidth()) {
                Text("看看能换成什么")
            }
        }
    }
}

@Composable
private fun UnverifiedCard(u: RuleOutcome.Unverified) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF6E0))) {
        Column(Modifier.padding(14.dp)) {
            Text(ruleLabels[u.ruleId] ?: u.ruleId, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                when (u.reason) {
                    UnverifiedReason.MISSING_FIELD -> "未验证 —— 缺少 ${u.missingField?.fieldPath ?: "部件"}"
                    UnverifiedReason.SOURCE_CONFLICT -> "未验证 —— 来源冲突 ${u.missingField?.fieldPath ?: ""}"
                    UnverifiedReason.UNSUPPORTED_RULE -> "未验证 —— 规则暂不支持"
                    UnverifiedReason.INSUFFICIENT_DATA -> "未验证 —— 数据不足"
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
                    "会影响：${candidate.affectsOtherRules.joinToString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9A6700),
                )
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onPreview, modifier = Modifier.fillMaxWidth()) {
                Text("预览")
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
            Text("替换预览", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("新总价 ${preview?.let { money(it.totalCny) } ?: "—"}")
            Text(
                "应用前会做一次全量重检；旧诊断不会覆盖新配置。",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(10.dp))
            Button(onClick = onApply, modifier = Modifier.fillMaxWidth()) { Text("应用并全量重检") }
            Spacer(Modifier.height(6.dp))
            OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("取消") }
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

private fun money(value: Long): String = "¥" + "%,d".format(value)

private fun riskLabel(risk: RiskLevel): String = when (risk) {
    RiskLevel.HIGH -> "高"
    RiskLevel.MEDIUM -> "中"
    RiskLevel.LOW -> "低"
    RiskLevel.UNKNOWN -> "未知"
}

private fun severityLabel(severity: Severity): String = when (severity) {
    Severity.BLOCKER -> "BLOCKER"
    Severity.MAJOR -> "MAJOR"
    Severity.MINOR -> "MINOR"
}
