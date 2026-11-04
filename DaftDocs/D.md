# D. 六模块契约卡

## D.0 模块边界与依赖规则（先锁死）

**生成顺序（LLM 必须按此顺序，后一模块只能依赖前一模块已冻结的接口）：**

```
D1 数据库 → D3 规则引擎 → D5 替换建议 → D2 工作区/ViewModel → D4 预算推荐 → D6 比较 → D7 UI 绑定
```

说明：D4 依赖 D3 和 D5 才能“过滤+排序”；D2 是 ViewModel 编排，放后面是为了让状态机先可用；D6 只读 saved builds 和诊断。

**依赖方向（CI 硬检查）：**

| 模块 | 可依赖 | 禁止依赖 |
|---|---|---|
| D1 数据 | Room、contracts、JSON schema | Domain 规则、UI、Compose |
| D3 规则 | contracts、D1 的只读接口 | Room 实现、Compose、Android |
| D5 替换 | contracts、D3 接口、D1 只读接口 | Compose、直接读 DAO |
| D2 工作区 | contracts、D1/D3/D5 接口 | 直接算规则、直接写规则表 |
| D4 推荐 | contracts、D3/D5/D1 接口 | UI 状态、Compose |
| D6 比较 | contracts、D2 输出的 saved revisions/diagnoses | 重新算规则（只能读已有诊断） |
| D7 UI | Compose、D2 的 `BuildUIState` | 直接调 DAO、直接算规则 |

**跨模块唯一通信方式：** 通过 `contracts` 里的数据类型和接口；禁止模块间共享可变全局状态；禁止 UI 直接读 Room。

---

## D.1 硬件数据库模块

**职责：** 提供只读静态 catalogue；字段级证据状态；带日期参考价；目录版本。不参与兼容性判断。

**输入契约：**

```kotlin
interface CatalogueRepository {
    fun getById(componentId: String): CatalogueRecord?
    fun search(query: String, category: Category?): List<CatalogueRecord>
    fun listByCategory(category: Category): List<CatalogueRecord>
    fun catalogueVersion(): String
    fun priceDate(): String
}
```

**输出契约：** `CatalogueRecord`（A.3 + B.0 补丁），所有字段路径符合 A.5 + B.0。

**失败模式：**
- `NotFound(componentId)`：返回 null，不抛异常；调用方转 `Unverified(MISSING_FIELD)`。
- `EmptyCatalogue`：启动时 seed 失败，返回空列表 + `catalogueVersion` 仍存在；UI 显示“目录为空”，不得崩溃。
- `SchemaViolation`：seed 校验失败，启动即 fail-fast，不进入主界面。
- `DuplicateComponentId`：seed 校验失败，fail-fast。

**禁止事项：**
- 禁止网络请求、实时价格、库存、爬虫。
- 禁止在数据层计算兼容性、推荐、价格差。
- 禁止把 `isDemo=true` 的记录放进生产 seed；demo 只在 fixture/debug 目录。
- 禁止 LLM 编造字段路径；新增路径走契约 diff。

**验收用例：**
- Given 静态 seed，When `getById("gpu-demo-337")`，Then 返回 `isDemo=true`、`lengthMm.numeric=337`。
- Given 无此 ID，When `getById("nope")`，Then null，不抛异常。
- Given seed 含重复 ID，When 启动，Then fail-fast，日志含重复 ID。
- Given `search("RTX")`，When 执行，Then 只返回 model/partNumber 匹配项，大小写不敏感。
- Given 任意记录，Then 所有 `attributes` 路径在 A.5/B.0 路径表内；否则 CI fail。

**放置：** `data/`，Room + `assets/catalogue/static-v1.json` + seed 校验。

---

## D.2 DIY 配置工作区模块（ViewModel 编排）

**职责：** 接收编辑 Intent，生成新 `BuildRevision`，立即重算总价，维护诊断/替换子状态，执行导航 guard。不直接算规则，不直接读 DAO。

**输入契约：**

```kotlin
sealed interface WorkspaceIntent {
    data class AddComponent(val componentId: String) : WorkspaceIntent
    data class RemoveComponent(val componentId: String) : WorkspaceIntent
    data class ReplaceComponent(val oldId: String, val newId: String) : WorkspaceIntent
    data class SetQuantity(val componentId: String, val qty: Int) : WorkspaceIntent
    data object Undo : WorkspaceIntent
    data object StartCheck : WorkspaceIntent
    data class ApplySuggestion(val candidateId: String) : WorkspaceIntent
    data object CancelPreview : WorkspaceIntent
    data object SaveBuild : WorkspaceIntent
    data object ImportPlanCard(val json: String) : WorkspaceIntent
}
```

**输出契约：** `StateFlow<WorkspaceUiState>`，包含：

```kotlin
data class WorkspaceUiState(
    val build: Build?,
    val revision: BuildRevision?,
    val diagnosisState: DiagnosisState,
    val diagnosis: Diagnosis?,        // 仅当 revisionId 匹配才非空
    val replacementState: ReplacementState,
    val suggestions: SuggestionResult?,
    val savedBuilds: List<Build>,
    val banner: BannerKey?,           // diagnostics_stale_banner 等
    val rejectedReason: String?       // 非法事件
)
```

**关键不变量：**
- 每次 Add/Remove/Replace/SetQuantity 生成新 revision；`totalCny` 同步重算。
- 编辑使 `diagnosis` 置 null（或派生 stale），`diagnosisState` 回 `Idle`；不自动 StartCheck。
- Undo 生成新 revision，内容回退，id 递增；不回退 id。
- ApplySuggestion 只能经替换状态机；baseRevision 不匹配则 `Rejected(apply_revision_stale)`。
- 导入 plan card 新建 `buildId`，不混入当前 build；确认前不保存。
- 切换 build 时未保存编辑自动存草稿，不阻断。

**失败模式：**
- `Rejected(reason)`：非法事件/guard 失败；状态不变，UI toast。
- `CatalogueMiss(componentId)`：Add/Replace 目标不存在；不生成 revision，返回原因。
- `QuantityInvalid`：qty < 1；拒绝。
- `RevisionConflict`：apply 时 baseRevision 不匹配；拒绝并提示重新检查。
- `ImportInvalidSchema`：plan card JSON 不合法；拒绝，不部分导入。

**禁止事项：**
- 禁止在 ViewModel 里写兼容性 if；只能调 D3 接口。
- 禁止 UI 直接改 `BuildRevision`；只能发 Intent。
- 禁止编辑后自动重跑诊断。
- 禁止 Apply 跳过 Rechecking。
- 禁止把草稿混进 saved builds。

**验收用例：**
- Given 空 build，When Add CPU，Then 新 revision、total 更新、诊断 Idle。
- Given 已诊断 revision，When SetQuantity，Then 旧诊断不再展示，banner 出现，不自动 Running。
- Given Running(rev1)，When 编辑生成 rev2，Then 旧结果返回被丢弃，状态 Idle。
- Given Undo，Then 新 revision id > 旧 id，内容等于上一快照。
- Given Apply 时 baseRev != current，Then `Rejected(apply_revision_stale)`，build 不变。
- Given 非法 JSON plan card，Then 拒绝，savedBuilds 不变。

**放置：** `ui/workspace/WorkspaceViewModel.kt` + `domain/build/RevisionService.kt`。

---

## D.3 兼容性检查模块（规则引擎）

**职责：** 输入 `BuildRevision` + catalogue，输出 `List<RuleOutcome>`；执行 B.3 规则表；不读价格、不排序、不依赖 UI。

**输入契约：**

```kotlin
interface CompatibilityRuleEngine {
    fun evaluate(revision: BuildRevision, catalogue: CatalogueRepository): List<RuleOutcome>
}
```

**输出契约：** 按 `ruleId` 字典序稳定排序的 `List<RuleOutcome>`；冲突带 `conflictId`、左右 `FieldRef` 和值；`Unverified` 带 `missingField`/`reason`。

**失败模式：**
- 未知 ruleId/路径 → 运行时 `Unverified(UNSUPPORTED_RULE)`，不 crash；CI 应提前拦截。
- 缺部件 → 该规则 `Unverified(MISSING_FIELD)`，不影响其他规则。
- 来源冲突 → `Unverified(SOURCE_CONFLICT)`，优先于数值比较。
- 输入为空 build → 返回空列表，不 crash；调用方判“无配置”。

**禁止事项：**
- 禁止缺字段 PASS；禁止把 `REPORTED` 当 `VERIFIED`。
- 禁止读价格、预算、UI 文案。
- 禁止在引擎里做替换排序或推荐。
- 禁止规则硬编码在 UI/ViewModel。
- 禁止修改 B.3 公式（PSU requiredW）而不走契约。

**验收用例：**
- 每条规则四类 fixture（B.4）。
- 337 vs 320 → CONFLICT，conflictId 稳定，leftValue/rightValue 正确。
- BIOS EMPTY → UNVERIFIED，missingField 指向 `mb.biosGeneration`。
- 两个 reported 来源 337/335 → UNVERIFIED(SOURCE_CONFLICT)。
- 同输入两次输出完全相等；顺序稳定。
- PSU 边界：requiredW 等于/差 1/向上取整。

**放置：** `domain/rules/`，纯 Kotlin，无 Android 依赖。

---

## D.4 预算规划与推荐模块

**职责：** 预算 + 用途 + 偏好 → 可编辑起始 build；权重可见；不可行必须报告；不承诺全局最优。

**输入契约：**

```kotlin
interface BudgetRecommender {
    fun recommend(request: RecommendationRequest, catalogue: CatalogueRepository, engine: CompatibilityRuleEngine): RecommendationResult
}
```

**输出契约：** `RecommendationResult`（A.9）：`feasible`、`build`（不可行也给最接近）、`shortfallCny`、`relaxedPreferences`、`weightsVisible`。

**算法契约（不变量，不规定实现）：**
- 可行时 total ≤ budget。
- 推荐 build 必须通过所有可评估硬约束；不可评估字段保持 Unverified，不得伪装 Passed。
- 用途决定分类权重：GAMING 偏 GPU；STUDY_OFFICE 保护 CPU/内存/存储；CONTENT_CREATION 偏 CPU 核/内存/存储；LOCAL_AI 偏 GPU 显存/内存。
- 不可行：`feasible=false`，给 shortfall 和 relaxedPreferences，不得静默超预算或放宽硬约束。
- 权重必须可展示、可调；不承诺全局最优、不做 FPS 预测。

**失败模式：**
- `EmptyCatalogue`：返回 `feasible=false`，shortfall=budget，build=null。
- `NoFeasibleUnderHardConstraints`：返回最接近配置 + shortfall + 放宽偏好列表。
- `BudgetTooLow`：同不可行，不 crash。

**禁止事项：**
- 禁止声称“最优配置”。
- 禁止用性能分数/FPS/跑分排序。
- 禁止超预算或放宽硬约束而不报告。
- 禁止把权重藏起来不可见。
- 禁止读 UI 状态或写 saved builds。

**验收用例：**
- Given budget 8000、GAMING，When recommend，Then total ≤ 8000，硬约束全通过或 Unverified。
- Given budget 1000、GAMING，Then `feasible=false`，shortfall > 0，build 非空但标注不可行。
- Given 用途切换，Then 权重变化可见，GPU 份额 GAMING > STUDY_OFFICE。
- Given 推荐 build 再跑引擎，Then 无新 BLOCKER Conflict（除非数据缺失→Unverified）。
- 同输入同目录版本，推荐结果确定（tie-breaker 固定）。

**放置：** `domain/recommend/`，纯 Kotlin。

---

## D.5 智能替换建议模块

**职责：** 冲突 → 候选；先过硬约束，再排序；无候选结构化返回；Apply 前由 D2 调引擎全量重检。

**输入契约：**

```kotlin
interface ReplacementAdvisor {
    fun suggest(
        baseRevision: BuildRevision,
        conflict: RuleOutcome.Conflict,
        catalogue: CatalogueRepository,
        engine: CompatibilityRuleEngine
    ): SuggestionResult
}
```

**输出契约：** `SuggestionResult`（A.8）：`baseRevisionId`、`conflictRuleId`、`candidates`、`noCandidate`。候选含 `priceDeltaCny`、`specDelta`、`affectsOtherRules`、`rank`。

**算法契约（不变量）：**
- 候选必须替换冲突部件类别，且替换后该冲突消除。
- 候选必须通过其他所有可评估规则；引入新冲突的候选不得排第一，可标注 `affectsOtherRules` 后保留或过滤（首版建议过滤 BLOCKER 新冲突，保留 MAJOR/MINOR 标注）。
- 排序：价格差升序 → 对剩余余量影响小 → `componentId` 字典序。
- 无候选：`candidates=[]`，`noCandidate` 非空，含 `blockingConstraint` 和 `suggestedDirection`；不承诺全局最小变更。
- 不得静默超预算或放宽硬约束。

**失败模式：**
- `NoCandidate`：结构化返回，不抛异常。
- `BaseRevisionStale`：调用方 D2 已 guard；若仍传入旧 revision，返回空 + `noCandidate(blockingConstraint="STALE_REVISION")`。
- `CatalogueEmpty`：同上。

**禁止事项：**
- 禁止先排序再过滤；必须先硬约束过滤。
- 禁止用“高分抵消不兼容”。
- 禁止隐藏会引入新冲突的候选而不标注。
- 禁止直接修改 build；只返回候选。
- 禁止声称“最优替换”。

**验收用例：**
- Given 337 vs 320，When suggest，Then 300mm 候选在列，priceDelta=100，rank 稳定。
- Given 候选会引入 PSU 不足，Then 被过滤或 `affectsOtherRules` 含 PSU 规则。
- Given 无候选，Then `noCandidate.blockingConstraint` 指向具体 ruleId/字段，build 不变。
- Given 同输入，Then 候选顺序确定。
- Given apply 300mm，Then 新总价 7900，GPU_CASE_LENGTH PASSED，BIOS 仍 UNVERIFIED。

**放置：** `domain/replacement/`，纯 Kotlin。

---

## D.6 构建比较模块

**职责：** 2–3 个 saved build 并排比较；只读已有 revision 和诊断；诊断过期不显示旧绿；不做 performance band。

**输入契约：**

```kotlin
interface BuildComparator {
    fun compare(buildIds: List<String>, saved: SavedBuildRepository): ComparisonResult
}
```

**输出契约：** `ComparisonResult`（A.10）：`buildIds`、`revisions`、`diagnoses`（可空）、`deltas`、`warnings`。

**比较字段（冻结）：** 总价、兼容状态、分类花费、功耗余量、升级余量（内存槽/存储位）、物理尺寸。差异高亮由 UI 做，模块只给 `highlighted`。

**失败模式：**
- `InsufficientBuilds`：<2 → `Rejected(compare_insufficient_builds)`。
- `DiagnosisStale`：某 build 诊断 revision 不匹配 → 该 build `diagnoses[i]=null`，`warnings` 加 `compare_diagnostics_stale`；不显示旧绿。
- `CatalogueVersionMismatch`：build 间目录版本不同 → warning，仍比较，标注“价格版本不同”。
- `BuildNotFound`：返回错误，不部分比较。

**禁止事项：**
- 禁止重新算规则；只能读已有诊断。
- 禁止显示过期诊断为当前结果。
- 禁止 performance band/FPS/跑分。
- 禁止修改 build。
- 禁止把不同目录版本的价格差说成确定结论。

**验收用例：**
- Given 2 个 saved build，When compare，Then 字段对齐、delta 正确、单位一致。
- Given 某 build 诊断过期，Then 该列显示“请重新检查”，不显示旧 PASSED。
- Given 1 个 build，Then `Rejected(compare_insufficient_builds)`。
- Given 目录版本不同，Then warning 出现，仍比较。
- 无 performance band 字段。

**放置：** `domain/compare/` + `ui/compare/`。

---

## D.7 模块间配合契约测试矩阵

| 测试 | 涉及模块 | 断言 |
|---|---|---|
| `e2e_gpu_337_to_300` | D1→D3→D5→D2 | 冲突 337/320 → 候选 300/CNY100 → apply 后 7900 → GPU PASSED、BIOS UNVERIFIED |
| `e2e_stale_diagnosis` | D2→D3 | rev1 Running，编辑 rev2，rev1 结果被丢弃 |
| `e2e_apply_guard` | D2→D5 | baseRev 过期 apply → Rejected，build 不变 |
| `e2e_no_candidate` | D3→D5 | 无候选结构化原因，不超预算 |
| `e2e_recommend_feasible` | D1→D3→D4 | 预算内、硬约束通过、权重可见 |
| `e2e_recommend_infeasible` | D4 | `feasible=false`，shortfall 非零 |
| `e2e_compare_stale` | D2→D6 | 过期诊断不显示旧绿 |
| `e2e_demo_isolation` | D1 | `isDemo=true` 不进入生产 seed |
| `e2e_import_plan_card` | D2 | 新 buildId，不混入当前 |
| `e2e_undo_revision` | D2 | 新 revision id 递增，内容回退 |

**每张模块卡的 prompt 必须附带：** 本卡 + A 章相关 schema + C 章相关状态机 + B 章相关规则/fixture + D.7 对应测试。少一个，LLM 就会自己补语义。

---

D 到此。下一步是 **E：测试 oracle 与失败归因明细**，然后是 **F：范围外 CI 检查清单**。继续 E？