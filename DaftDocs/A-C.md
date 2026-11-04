下面按 A → C 展开。这两章的目标是：**LLM 读完只能实现，不能重新定义语义。**

---

# A. 核心 schema 与枚举（Kotlin 契约）

放置目录：`contracts/src/main/kotlin/pcbuildmate/contracts/`。  
规则：此目录只允许追加枚举值或经 git 记录的 diff；LLM 不得改字段名、可空性、单位、语义。

## A.1 基础枚举

```kotlin
enum class Category {
    CPU, MOTHERBOARD, MEMORY, GPU, PSU, CASE, COOLER, STORAGE
}

enum class VerificationState {
    VERIFIED,   // 厂商规格页
    REPORTED,   // 零售商/社区，未证实
    EMPTY       // 无人确认
}

enum class Severity { BLOCKER, MAJOR, MINOR }

enum class UseCase { GAMING, STUDY_OFFICE, CONTENT_CREATION, LOCAL_AI }

enum class RiskLevel { HIGH, MEDIUM, LOW, UNKNOWN }

enum class OverallStatus { PASSED, CONFLICT, UNVERIFIED, MIXED }
```

## A.2 字段值与引用

```kotlin
data class FieldValue(
    val value: String?,          // 统一字符串存储，数值在规则层解析
    val state: VerificationState,
    val source: String?,         // 厂商页/零售商/社区，可空
    val numeric: Double? = null  // 可选，数值字段预解析
)

data class FieldRef(
    val componentId: String,
    val fieldPath: String        // 见 A.5 命名规范
)
```

## A.3 目录记录

```kotlin
data class CatalogueRecord(
    val componentId: String,     // 全局唯一，如 "cpu-amd-7600"
    val category: Category,
    val manufacturer: String,
    val model: String,
    val partNumber: String,
    val attributes: Map<String, FieldValue>,
    val priceCny: Long,
    val priceDate: String,       // ISO-8601，如 "2026-10-31"
    val catalogueVersion: String,// 如 "static-v1"
    val isDemo: Boolean          // 337/320/300/CNY100 fixture 必须 true
)
```

## A.4 Build、BuildRevision、诊断与建议

```kotlin
data class Build(
    val buildId: String,         // UUID
    val budgetCny: Long,
    val useCase: UseCase,
    val preferences: Preferences,
    val currentRevisionId: Long,
    val userModified: Boolean,
    val createdAt: String,
    val updatedAt: String
)

data class Preferences(
    val caseSize: String? = null,
    val noiseLevel: String? = null,
    val upgradeRoom: Boolean = false
)

data class BuildItem(
    val componentId: String,
    val category: Category,
    val quantity: Int,           // >= 1
    val unitPriceCny: Long
)

data class BuildRevision(
    val id: Long,                // 单调递增，全局
    val buildId: String,
    val items: List<BuildItem>,
    val totalCny: Long,          // 必须等于 Σ quantity * unitPriceCny
    val catalogueVersion: String,
    val priceDate: String,
    val userModified: Boolean
)
```

**不变量（测试必须覆盖）：**
- `totalCny == items.sumOf { it.quantity * it.unitPriceCny }`
- 同一 `buildId` 下 `revision.id` 严格递增
- 每个 `item.componentId` 必须存在于同 `catalogueVersion` 的目录
- `quantity >= 1`

## A.5 字段路径命名规范（冻结）

| 类别 | 字段路径 | 类型 |
|---|---|---|
| CPU | `socket` | string |
| CPU | `supportedChipsets` | csv |
| CPU | `tdpW` | number |
| MOTHERBOARD | `socket` | string |
| MOTHERBOARD | `chipset` | string |
| MOTHERBOARD | `biosGeneration` | string |
| MOTHERBOARD | `memoryGeneration` | string |
| MOTHERBOARD | `memorySlots` | number |
| MOTHERBOARD | `maxMemoryGb` | number |
| MOTHERBOARD | `formFactor` | string |
| MOTHERBOARD | `m2Slots` | number |
| MOTHERBOARD | `sataPorts` | number |
| MEMORY | `generation` | string |
| MEMORY | `moduleCapacityGb` | number |
| MEMORY | `kitCapacityGb` | number |
| GPU | `lengthMm` | number |
| GPU | `thicknessMm` | number |
| GPU | `slotWidth` | number |
| GPU | `tdpW` | number |
| GPU | `recommendedPsuW` | number |
| PSU | `continuousW` | number |
| PSU | `connectors` | csv |
| CASE | `maxGpuLengthMm` | number |
| CASE | `maxGpuThicknessMm` | number |
| CASE | `maxCoolerHeightMm` | number |
| CASE | `supportedFormFactors` | csv |
| CASE | `driveBays` | number |
| COOLER | `heightMm` | number |
| COOLER | `socketMounts` | csv |
| STORAGE | `interface` | string |

规则和 fixture 只能引用表中路径；新增路径必须走契约 diff。

## A.6 规则结果

```kotlin
sealed interface RuleOutcome {
    val ruleId: String
    val weakest: VerificationState

    data class Passed(
        override val ruleId: String,
        override val weakest: VerificationState
    ) : RuleOutcome

    data class Conflict(
        override val ruleId: String,
        override val weakest: VerificationState,
        val left: FieldRef,
        val leftValue: String,
        val right: FieldRef,
        val rightValue: String,
        val severity: Severity
    ) : RuleOutcome

    data class Unverified(
        override val ruleId: String,
        override val weakest: VerificationState,
        val missingField: FieldRef?,
        val reason: UnverifiedReason
    ) : RuleOutcome
}

enum class UnverifiedReason {
    MISSING_FIELD,
    SOURCE_CONFLICT,
    UNSUPPORTED_RULE,
    INSUFFICIENT_DATA
}
```

**传播规则（冻结）：**
- `weakest` = 该规则所有输入字段中最弱证据；顺序 `EMPTY > REPORTED > VERIFIED`。
- 任一输入为 `EMPTY` 或缺失 → 必须 `Unverified(MISSING_FIELD)`，不得 Passed。
- 同一字段多来源数值不一致 → `Unverified(SOURCE_CONFLICT)`，不新增枚举。
- `Conflict` 必须带左右 `FieldRef` 和实际值，UI 不自行拼字符串。

## A.7 诊断

```kotlin
data class Diagnosis(
    val revisionId: Long,
    val outcomes: List<RuleOutcome>,
    val overall: OverallStatus,
    val riskLevel: RiskLevel,
    val score: Int?,             // 0–100，展示用，不参与排序
    val computedAt: String
)
```

**overall 判定（冻结）：**
- 有 `BLOCKER` Conflict → `CONFLICT`
- 否则有 `MAJOR`/`MINOR` Conflict → `CONFLICT`
- 否则有 `Unverified` → `UNVERIFIED`
- 否则 → `PASSED`
- 混合冲突与未验证 → `MIXED`

**riskLevel（冻结）：**
- 有 BLOCKER → `HIGH`
- 有 MAJOR → `MEDIUM`
- 仅 MINOR/Unverified → `LOW`
- 无诊断 → `UNKNOWN`

**score（冻结，展示用）：** `100 - 30*BLOCKER数 - 15*MAJOR数 - 5*MINOR数 - 3*Unverified数`，下限 0。不得用于替换排序。

## A.8 替换建议

```kotlin
data class SuggestionResult(
    val baseRevisionId: Long,
    val conflictRuleId: String,
    val candidates: List<Candidate>,
    val noCandidate: NoCandidateReason?
)

data class Candidate(
    val componentId: String,
    val priceDeltaCny: Long,          // 相对当前部件
    val specDelta: Map<String, String>,
    val affectsOtherRules: List<String>,
    val rank: Int
)

data class NoCandidateReason(
    val blockingConstraint: String,   // ruleId + fieldPath
    val suggestedDirection: String    // 加预算/换类别/放宽偏好，不承诺全局最小变更
)
```

**排序 tie-breaker（冻结）：** 先按“解决冲突”过滤，再按价格差升序 → 对剩余余量影响小 → `componentId` 字典序。LLM 不得改排序键。

## A.9 预算推荐

```kotlin
data class RecommendationRequest(
    val budgetCny: Long,
    val useCase: UseCase,
    val preferences: Preferences
)

data class RecommendationResult(
    val feasible: Boolean,
    val build: BuildRevision?,        // 不可行时也给最接近配置
    val shortfallCny: Long,
    val relaxedPreferences: List<String>,
    val weightsVisible: Map<Category, Double>
)
```

**不变量：** 可行时 total ≤ budget；所有硬约束通过；不可行必须 `feasible=false` 并给 shortfall；权重必须可见可调；不承诺全局最优。

## A.10 比较

```kotlin
data class ComparisonResult(
    val buildIds: List<String>,
    val revisions: List<BuildRevision>,
    val diagnoses: List<Diagnosis?>,
    val deltas: List<FieldDelta>,
    val warnings: List<String>        // 如诊断过期、目录版本不同
)

data class FieldDelta(
    val field: String,
    val values: List<String>,
    val highlighted: Boolean
)
```

首版比较字段：总价、兼容状态、分类花费、功耗余量、升级余量（内存槽/存储位）、物理尺寸。**不做 performance band。**

---

# C. 两张状态机转移表

实现方式：`sealed interface` 状态 + 纯函数 `transition(state, event): TransitionResult`；非法事件返回 `Rejected(reason)` 且状态不变。`Stale` 不设状态，派生自 `diagnosis.revisionId != build.currentRevisionId`。

## C.1 诊断状态机

```kotlin
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
    data object EditBuild : DiagnosisEvent
}
```

| 当前 | 事件 | Guard | 下一 | 副作用 / 测试断言 |
|---|---|---|---|---|
| Idle | StartCheck | `currentRevisionId != null` | Running(rev) | 启动任务；断言 Running.rev=当前 |
| Idle | StartCheck | 无 build | Idle | `Rejected(diagnostics_required)`；状态不变 |
| Running | StartCheck | 任意 | Running | 忽略重复；不新建任务 |
| Running | CheckCompleted(rev) | `rev == current` | Done(rev) | 展示当前诊断 |
| Running | CheckCompleted(rev) | `rev != current` | Running | 丢弃旧结果；记 stale；不覆盖 |
| Running | CheckFailed(rev) | `rev == current` | Failed(rev) | 显示 `diagnostics_failed` |
| Running | CheckFailed(rev) | `rev != current` | Running | 丢弃 |
| Running | EditBuild | 任意 | Idle | 新 revision；旧任务取消或标记 stale |
| Done | EditBuild | 任意 | Idle | 旧诊断派生 stale；banner 出现 |
| Done | StartCheck | 任意 | Running | 重新检查 |
| Failed | StartCheck | 任意 | Running | 重试 |
| Failed | EditBuild | 任意 | Idle | 新 revision |

**禁止转移：** `Done → Done` 直接覆盖（必须经 Running）；`Failed → Done` 无完成事件；`Idle → Done` 无完成事件。  
**测试用例：** 每个合法转移一条；每个非法事件断言 `Rejected + state unchanged`；延迟返回用两个 revision 交错验证丢弃。

## C.2 替换状态机

```kotlin
sealed interface ReplacementState {
    data object Idle : ReplacementState
    data class Candidates(val baseRevisionId: Long, val conflictRuleId: String) : ReplacementState
    data class Previewing(val baseRevisionId: Long, val candidateId: String) : ReplacementState
    data class Rechecking(val baseRevisionId: Long, val previewRevisionId: Long) : ReplacementState
    data class Applied(val newRevisionId: Long) : ReplacementState
    data class NoCandidate(val reason: NoCandidateReason) : ReplacementState
}

sealed interface ReplacementEvent {
    data class ConflictSelected(val ruleId: String) : ReplacementEvent
    data class CandidatesLoaded(val baseRevisionId: Long, val result: SuggestionResult) : ReplacementEvent
    data class PreviewCandidate(val candidateId: String) : ReplacementEvent
    data object Apply : ReplacementEvent
    data object Cancel : ReplacementEvent
    data class RecheckCompleted(val newRevisionId: Long, val diagnosis: Diagnosis) : ReplacementEvent
    data class RecheckConflict(val conflictRuleId: String) : ReplacementEvent
    data object EditBuild : ReplacementEvent
    data object Dismiss : ReplacementEvent
}
```

| 当前 | 事件 | Guard | 下一 | 副作用 / 测试断言 |
|---|---|---|---|---|
| Idle | ConflictSelected | 诊断有 CONFLICT | Candidates(baseRev) | 请求候选；baseRev=当前 |
| Idle | ConflictSelected | 无冲突 | Idle | `Rejected(invalid_state)` |
| Candidates | CandidatesLoaded | `baseRev == current` 且非空 | Candidates | 展示候选 |
| Candidates | CandidatesLoaded | `baseRev == current` 且空 | NoCandidate | 返回结构化原因 |
| Candidates | CandidatesLoaded | `baseRev != current` | Idle | 丢弃旧候选 |
| Candidates | PreviewCandidate | 候选存在且 `baseRev==current` | Previewing | 生成只读 preview revision；不写历史 |
| Candidates | EditBuild | 任意 | Idle | 候选失效；触发诊断 |
| Previewing | Apply | `baseRev == current` | Rechecking | 生成新 revision；全量重检 |
| Previewing | Apply | `baseRev != current` | Idle | `Rejected(apply_revision_stale)`；不应用 |
| Previewing | Cancel | 任意 | Candidates | 清 preview；build 不变 |
| Previewing | EditBuild | 任意 | Idle | preview 失效 |
| Rechecking | RecheckCompleted | `newRev == current` 且无新冲突 | Applied(newRev) | 保存并展示 |
| Rechecking | RecheckCompleted | `newRev == current` 且有新冲突 | Candidates | 不 Applied；提示新冲突 |
| Rechecking | RecheckCompleted | `newRev != current` | Idle | 丢弃 |
| Rechecking | RecheckConflict | 任意 | Candidates | 回候选；可转 NoCandidate |
| Rechecking | EditBuild | 任意 | Idle | 保护 revision |
| Applied | EditBuild | 任意 | Idle | 进入新编辑周期 |
| NoCandidate | Dismiss | 任意 | Idle | 关闭原因 |
| NoCandidate | EditBuild | 任意 | Idle | 新 revision |

**禁止转移：** `Previewing → Applied`（必须经 Rechecking）；`Candidates → Applied`；`NoCandidate → Applied`；任何编辑后继续 Apply 旧候选。  
**测试用例：** 每个合法转移；非法转移；编辑使 preview 失效；旧 candidates 返回不覆盖；无候选不超预算不放宽硬约束；apply 后全量重检。

## C.3 导航 guard（非 FSM）

| Guard | 条件 | 失败文案 key |
|---|---|---|
| 进诊断 | `build != null && currentRevision != null` | `diagnostics_required` |
| 进比较 | `savedBuilds.size >= 2` | `compare_insufficient_builds` |
| 比较页诊断 | `diagnosis.revisionId == build.currentRevisionId` | `compare_diagnostics_stale` |
| 应用替换 | `baseRevisionId == build.currentRevisionId` | `apply_revision_stale` |

---

A 和 C 到此。下一步我建议展开 **B：规则表 DSL + 10–15 条规则明细**，因为规则 ID、输入路径、severity 和 fixture 是随机测试和 LLM 断言的引用锚点。要继续 B 吗？