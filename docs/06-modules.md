# 06 · 六模块契约卡

> 读者：每个 LLM 任务只读**自己那一张** + 02/03/04 的相关小节 + 07 的验收矩阵。
> 每张卡的六块内容缺一不可（目标 / 输入 / 输出 / 禁止 / 失败 / 验收），
> 这也是 prompt 模板的骨架（12 章）。

---

## 6.0 卡片模板

| 块 | 含义 | 写不好的典型症状 |
|---|---|---|
| **目标** | 一句话职责 + 不做什么 | 写成功能介绍，边界含糊 |
| **输入契约** | 接口签名 + 依赖的只读端口 | 直接写"调用 Repository" |
| **输出契约** | 数据类型 + 不变量 + 顺序 | 漏掉确定性要求 |
| **禁止事项** | 明确 MUST NOT（含跨模块禁令） | 只写"注意性能" |
| **失败模式** | 每种失败的结构化返回 | 写"抛出异常" |
| **验收用例** | Given/When/Then + 测试入口 | 写"功能正常" |

**统一约定**：

- 所有模块的失败都返回**结构化结果**，不抛异常给 UI（数据层 fail-fast 除外）。
- 所有模块的输出都必须**确定性**（同输入同输出含顺序）。
- 所有模块都必须能单独 `:module:test` 通过（05 章 L2）。

---

## D1 硬件数据库

**目标**：提供只读静态目录、字段级证据、带日期的 CNY 参考价、目录版本。**不判断兼容性。**

**模块**：`:data`（部分）+ `assets/catalogue/` ｜ **负责角色**：数据层成员

### 输入契约

```kotlin
interface CataloguePort {
    fun getById(componentId: String): CatalogueRecord?
    fun search(query: String, category: Category?): List<CatalogueRecord>
    fun listByCategory(category: Category): List<CatalogueRecord>
    fun catalogueVersion(): String
    fun priceDate(): String
}
```

### 输出契约

- `CatalogueRecord`，其 `attributes` 的 key 必须全部在 03 章 §3.3 表内；
- `search` 结果按 `componentId` 字典序（确定性）；
- `catalogueVersion` / `priceDate` 在整个进程生命周期内不变（一次加载，不可变）。

### 不变量

- INV-11：`isDemo=true` 的记录不得进入生产 seed；
- 每条记录的 `priceDate` 必须是合法 ISO 日期且不晚于 `catalogueVersion` 的发布日期；
- 同一 `componentId` 不得重复。

### 禁止事项

- 禁止网络请求、实时价格、库存、爬虫（范围外，见 09 章）；
- 禁止在数据层计算兼容性、推荐、价格差；
- 禁止把 demo 记录混进生产 seed；
- 禁止返回 null 以外的"缺省记录"（例如自动造一条 EMPTY 的占位记录）。

### 失败模式

| 场景 | 返回/行为 |
|---|---|
| `getById` 找不到 | `null`（不抛异常） |
| seed schema 非法（路径不在表内 / 重复 id / 类型错） | **启动即 fail-fast**，日志含 componentId 与位置 |
| 目录为空 | 返回空列表，`catalogueVersion` 仍有值；UI 显示 `catalogue_empty` |

### 验收用例

| Given | When | Then |
|---|---|---|
| demo 目录 | `getById("gpu-demo-337")` | 非空，`isDemo=true`，`lengthMm.numeric == 337.0` |
| 任意目录 | `getById("nope")` | `null`，不抛异常 |
| seed 含重复 id | 启动 | fail-fast，日志含重复 id |
| 任意记录 | 遍历 `attributes` | 每个 path 都在 §3.3 表内 |
| 目录 | `search("rtx", null)` vs `search("RTX", null)` | 结果集合相同 |

### 单元验证入口

`:data:test` → 继承 `CataloguePortContract`；seed 校验单测；demo 隔离单测（INV-11）。

### 独立性约束

只依赖 `:contracts` + Room + assets。**禁止** import 任何 `pcbuildmate.domain.*`。

---

## D2 DIY 配置工作区（ViewModel 编排）

**目标**：把 `Intent` 变成新 `BuildRevision`，维护两张状态机，执行 Effect，暴露唯一 `WorkspaceUiState`。
**不计算规则、不直接读数据库、不直接改 revision。**

**模块**：`:feature` ｜ **负责角色**：架构 + UI 负责人

### 输入契约

```kotlin
sealed interface WorkspaceIntent { /* 15 个意图，见 contracts/Workspace.kt */ }
```

依赖（只读端口）：`CataloguePort`、`BuildStorePort`、`DraftStorePort`、`IdPort`、`ClockPort`、
`CompatibilityEvaluator`、`ReplacementAdvisorPort`、`BuildComparatorPort`。

### 输出契约

```kotlin
val state: StateFlow<WorkspaceUiState>
```

不变量：

- 每次 Add/Remove/Replace/SetQuantity/Undo 产生新 revision，`totalCny` 同步（INV-01/02/10）；
- 编辑后 `diagnosisState == Idle` 且 `currentDiagnosis == null`，**不自动** StartCheck；
- 只有经 `Rechecking` 才能 `Applied`（INV-07）；
- 被拒绝的意图不改变 state，只写 `rejection`。

### 禁止事项

- 禁止在 ViewModel 写兼容性 if（只能调 `CompatibilityEvaluator`）；
- 禁止 UI 直接构造/修改 `BuildRevision`；
- 禁止编辑后自动重检；
- 禁止把草稿混进 `savedBuilds`；
- 禁止 `GlobalScope` / 不受控的并发写 state。

### 失败模式

| 场景 | 结果 |
|---|---|
| Add/Replace 目标不存在 | 不产生 revision，`rejection = CATALOGUE_MISS` |
| qty < 1 | `rejection = QUANTITY_INVALID` |
| apply 时 base 过期 | `rejection = APPLY_REVISION_STALE`，build 不变 |
| plan card JSON 非法 | `rejection = IMPORT_INVALID_SCHEMA`，**不部分导入** |
| 状态机拒绝 | 透传 `Rejection`，state 不变 |

### 验收用例

| Given | When | Then |
|---|---|---|
| 空 build | Add CPU | 新 revision、total 更新、`diagnosisState = Idle` |
| 已诊断 rev1 | SetQuantity | `currentDiagnosis == null`、banner 出现、**不** Running |
| Running(rev1) | 编辑生成 rev2 后收到 rev1 的 CheckCompleted | 丢弃，state 仍 Idle |
| 任意 | Undo | 新 revision id > 旧 id，内容等于上一快照 |
| Previewing 且 base 过期 | Apply | `Rejected(APPLY_REVISION_STALE)`，build 不变 |
| 非法 JSON | ImportPlanCard | 拒绝，`savedBuilds` 不变 |

### 单元验证入口

`:feature:test` → 状态序列断言（Turbine 收集）、虚拟时间、交错 revision 用例、Effect 执行次数断言。

### 独立性约束

允许依赖：`contracts`、`domain:state`、`domain:build`。
**禁止** import `domain:rules` / `domain:replacement` / `domain:recommend` / `domain:compare` 的**实现类**（只能经接口）。

---

## D3 兼容性检查（规则引擎）

**目标**：输入 `BuildRevision`，输出按 `ruleId` 排序的 `List<RuleOutcome>`。纯函数。

**模块**：`:domain:rules` ｜ **负责角色**：领域层成员

### 输入契约

```kotlin
interface CompatibilityEvaluator {
    fun evaluate(revision: BuildRevision): List<RuleOutcome>
}

class DefaultRuleEngine(
    private val rules: List<RuleDef>,          // 由 data 层解析 JSON 后注入
    private val catalogue: CataloguePort,      // 只读
) : CompatibilityEvaluator
```

### 输出契约

- 顺序：`ruleId` 字典序；
- `Conflict` 必须带 `conflictId` / 左右 `FieldRef` / 左右值 / `severity`；
- `Unverified` 必须带 `reason`，能定位时带 `missingField`；
- 同一输入两次调用结果完全相等。

### 禁止事项

- 禁止缺字段 PASS（INV-04）；
- 禁止把 `REPORTED` 当 `VERIFIED`；
- 禁止读价格、预算、UI 文案、时间；
- 禁止做替换排序或推荐；
- 禁止改 §3.7 公式而不走契约变更；
- 禁止抛异常给调用方（未知规则降级为 `UNSUPPORTED_RULE`）。

### 失败模式

| 场景 | 结果 |
|---|---|
| 未知 ruleId / 未知路径 | `Unverified(UNSUPPORTED_RULE)` + 日志，不 crash |
| 缺部件 | 该规则 `Unverified(MISSING_FIELD)`，其它规则正常 |
| 来源冲突 | `Unverified(SOURCE_CONFLICT)`，优先于数值比较 |
| 空 revision | 空列表 |

### 验收用例

| Given | When | Then |
|---|---|---|
| 60 条单规则 fixture | 逐条 evaluate | 与 `expected` 全等 |
| 337 / 320 | evaluate | CONFLICT，conflictId 固定，leftValue=337 |
| BIOS EMPTY | evaluate | UNVERIFIED，missingField 指向 mb.biosGeneration |
| 两来源 337/335 | evaluate | UNVERIFIED(SOURCE_CONFLICT) |
| 同输入跑两次 | 比较 | 结果列表 `==` |
| requiredW 边界 | 等号 / 差 1 / 取整 | 恰好翻转 |

### 单元验证入口

`:domain:rules:test` → 15 × 4 fixture 参数化 + 属性（weakest）+ 边界。

### 独立性约束

只依赖 `:contracts`。**禁止** Room / Android / 序列化库 / 时钟。

---

## D4 预算规划与推荐

**目标**：`(预算, 用途, 偏好) -> 可编辑的起始配置`；权重可见；不可行必须报告。

**模块**：`:domain:recommend` ｜ **负责角色**：领域层成员

### 输入契约

```kotlin
interface BudgetRecommenderPort {
    fun recommend(request: RecommendationRequest): RecommendationResult
}
```

内部可用：`CataloguePort`、`CompatibilityEvaluator`（通过构造注入）。

### 输出契约

- `feasible=true` ⇒ `totalCny <= budgetCny` 且所有可评估硬约束通过（INV-13）；
- `feasible=false` ⇒ `shortfallCny > 0` 且 `build` 可为 null，且 `relaxedPreferences` 非空（若放宽了偏好）；
- `weightsVisible` 非空，且与实际分配一致；
- 同输入同目录版本，结果确定。

### 禁止事项

- 禁止声称"最优"；
- 禁止用性能分数 / FPS / 跑分排序（范围外）；
- 禁止超预算或放宽硬约束而不报告；
- 禁止隐藏权重；
- 禁止读 UI 状态、写 saved builds。

### 失败模式

| 场景 | 结果 |
|---|---|
| 空目录 | `feasible=false`，`shortfall=budget`，`build=null` |
| 预算过低 | 最接近配置 + shortfall + 放宽列表 |
| 偏好无法满足 | 同上，`relaxedPreferences` 列出被放宽项 |

### 验收用例

| Given | When | Then |
|---|---|---|
| 8000 / GAMING | recommend | total ≤ 8000，硬约束无 BLOCKER Conflict |
| 1000 / GAMING | recommend | `feasible=false`，shortfall > 0 |
| GAMING vs STUDY_OFFICE | recommend | GPU 权重 GAMING > STUDY_OFFICE，且两者都可展示 |
| 推荐结果再跑引擎 | evaluate | 无新 BLOCKER Conflict（最多 UNVERIFIED） |

### 单元验证入口

`:domain:recommend:test` → 预算/不可行/权重可见/确定性/硬约束。

### 独立性约束

只依赖 `:contracts`；引擎经 `CompatibilityEvaluator` 接口。

---

## D5 智能替换建议

**目标**：给定一条冲突，返回**先过硬约束、再排序**的候选；无候选时给结构化原因。

**模块**：`:domain:replacement` ｜ **负责角色**：领域层成员

### 输入契约

```kotlin
interface ReplacementAdvisorPort {
    fun suggest(base: BuildRevision, conflict: RuleOutcome.Conflict): SuggestionResult
}
```

### 输出契约

- 每个候选**消除**该 `conflictId`（替换后该规则不再 Conflict）；
- 候选必须通过其它所有**可评估**规则；引入 BLOCKER 新冲突的候选被过滤；
- 排序键固定：价格差升序 → 对剩余余量影响小 → `componentId` 字典序（INV-14）；
- 无候选 ⇒ `candidates = []` 且 `noCandidate != null`。

### 禁止事项

- 禁止先排序再过滤；
- 禁止用"综合分"抵消不兼容；
- 禁止隐藏会引入新冲突的候选而不标注（`affectsOtherRules`）；
- 禁止直接修改 build；
- 禁止声称"最优替换"。

### 失败模式

| 场景 | 结果 |
|---|---|
| 无候选 | `noCandidate.blockingConstraint` 指向具体 ruleId + fieldPath |
| base 过期 | 空结果 + `noCandidate(blockingConstraint="STALE_REVISION")` |
| 目录空 | 同上 |

### 验收用例

| Given | When | Then |
|---|---|---|
| 337 / 320 | suggest | 含 300mm 候选，`priceDeltaCny = 100`，rank=1 |
| 候选会引入 PSU 不足 | suggest | 被过滤，或 `affectsOtherRules` 含 PSU 规则 |
| 无候选 | suggest | 结构化原因，build 不变 |
| 同输入两次 | suggest | 顺序一致 |
| 应用 300mm | 全量重检 | total 7900，GPU PASSED，BIOS 仍 UNVERIFIED（`fx_suggestion_300_plus_100`） |

### 单元验证入口

`:domain:replacement:test` → 过滤顺序、排序稳定性、无候选结构、跨规则锚点。

### 独立性约束

只依赖 `:contracts`；引擎经接口。

---

## D6 构建比较

**目标**：2–3 个已保存配置并排比较；**只读**已有 revision 与诊断；过期诊断不显示旧绿。

**模块**：`:domain:compare`（+ `:ui` 展示）｜ **负责角色**：领域层 + UI

### 输入契约

```kotlin
interface BuildComparatorPort {
    fun compare(buildIds: List<String>): ComparisonResult
}
```

输入来源：`BuildStorePort.listRevisions` + 已存诊断。**不调用**引擎。

### 输出契约

字段冻结（首版）：总价、兼容状态、分类花费、功耗余量、升级余量（内存槽 / 存储位）、物理尺寸。
差异高亮由模块给 `highlighted`，UI 只渲染。

不变量：`diagnoses[i] != null <=> 该 build 的 diagnosis.revisionId == build.currentRevisionId`（INV-12）。

### 禁止事项

- 禁止重新计算规则（只能读已有诊断）；
- 禁止把过期诊断显示为当前结论；
- 禁止 performance band / FPS / 跑分（范围外）；
- 禁止修改 build；
- 禁止把不同目录版本的价格差说成确定结论（必须进 warnings）。

### 失败模式

| 场景 | 结果 |
|---|---|
| <2 个 build | `Rejected(COMPARE_INSUFFICIENT_BUILDS)` |
| 某 build 诊断过期 | 该列 `diagnoses[i]=null`，warnings 加 `compare_diagnostics_stale` |
| 目录版本不同 | warnings 加提示，仍比较 |
| build 不存在 | 整体错误，不部分比较 |

### 验收用例

| Given | When | Then |
|---|---|---|
| 2 个 saved build | compare | 字段对齐、delta 正确、单位一致 |
| 某 build 诊断过期 | compare | 该列显示"请重新检查"，不显示旧 PASSED |
| 1 个 build | compare | 被拒绝并给 key |
| 目录版本不同 | compare | warning 出现，仍比较 |

### 单元验证入口

`:domain:compare:test` → 对齐、delta、过期诊断、版本不一致。

### 独立性约束

只依赖 `:contracts`。**禁止**调用 `CompatibilityEvaluator`。

---

## D7 UI 绑定

**目标**：Compose 只读 `WorkspaceUiState`、只发 `WorkspaceIntent`；四屏线性流程。

**模块**：`:ui` ｜ **负责角色**：UI 实现成员

### 输入契约

- `state: WorkspaceUiState`（只读）
- `onIntent: (WorkspaceIntent) -> Unit`

### 输出契约

- 每个可见元素有稳定 `testTag`（key 来自 `TextKey` 或语义 id）；
- 相同 state 渲染出相同 UI（无内部可变状态）；
- 小屏（360dp）不截断关键操作。

### 禁止事项

- 禁止 import DAO / Room / 具体 Repository 实现；
- 禁止在 Composable 里计算兼容性、价格、delta；
- 禁止自行拼接冲突文案（用 `Conflict.leftValue/rightValue` + 资源模板）；
- 禁止断言/依赖自然语言全文（测试只断言 key 与状态）。

### 失败模式

- 无 build 点"检查"→ 提示 `diagnostics_required`，不跳转；
- 少于 2 个配置点"比较"→ 提示 `compare_insufficient_builds`；
- 过期诊断 → 显示 banner 而非旧结论。

### 验收用例

| Given | When | Then |
|---|---|---|
| 有冲突 state | 渲染诊断页 | 可见 `conflict_<ruleId>` tag、左右值均显示 |
| stale state | 渲染 | 可见 `diagnostics_stale_banner`，不可见 PASSED 徽标 |
| 空 build | 点检查 | 出现提示且未导航 |
| 360dp 宽 | 滚动 | 关键按钮可达 |

### 单元验证入口

`:ui:test`（Robolectric + Compose semantics）→ 用**静态 state** 渲染，不启动 ViewModel。

### 独立性约束

只依赖 `:contracts` + Compose。**禁止**依赖 `feature` / `data` / `domain:*`。

---

## 6.9 模块间配合契约测试（10 张，跨模块唯一集成手段）

| 测试 | 涉及模块 | 断言 |
|---|---|---|
| `e2e_gpu_337_to_300` | D1→D3→D5→D2 | 冲突 → 候选 +100 → apply 后 7900 → GPU PASSED、BIOS UNVERIFIED |
| `e2e_stale_diagnosis` | D2→D3 | rev1 Running，编辑 rev2，rev1 结果被丢弃 |
| `e2e_apply_guard` | D2→D5 | base 过期 apply → Rejected，build 不变 |
| `e2e_no_candidate` | D3→D5 | 结构化原因，不超预算、不放宽硬约束 |
| `e2e_recommend_feasible` | D1→D3→D4 | 预算内、硬约束通过、权重可见 |
| `e2e_recommend_infeasible` | D4 | `feasible=false`，shortfall 非零 |
| `e2e_compare_stale` | D2→D6 | 过期诊断不显示旧绿 |
| `e2e_demo_isolation` | D1 | `isDemo=true` 不进生产 seed |
| `e2e_import_plan_card` | D2 | 新 buildId，不混入当前 |
| `e2e_undo_revision` | D2 | 新 revision id 递增，内容回退 |

**每张卡开工时的 prompt 必须附带**：本卡 + 02 相关契约 + 04 相关状态机 + 03 相关规则/fixture + 本节对应测试。
少一个，LLM 就会自己补语义（10 章 §10.3）。
