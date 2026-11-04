# E. 测试 oracle 与失败归因明细

## E.0 总原则

**三层裁决，LLM 不做法官：**

1. **确定性不变量 + 轻量参考模型** → 自动判“是不是 app bug”。
2. **定向 fixture + 规则表回归** → 自动判“语义是否被破坏”。
3. **LLM 断言只做报警候选** → 不直接判 bug；与参考模型矛盾时记 LLM 假阳性。

**失败归因五类（冻结）：** `SPEC_ORACLE_ERROR`、`LLM_ASSERTION_ERROR`、`FIXTURE_ERROR`、`DOMAIN_BUG`、`UI_STATE_BUG`；归不进去进 `UNCLASSIFIED`，人工仲裁。

**测试批次最终通过由人批准**；自动系统只负责跑、归因、出报告。人工不逐条看失败，只看批次报告 + `UNCLASSIFIED` + mutation 未杀死项。

---

## E.1 Oracle 分层

### E.1.1 硬不变量（每步、每次状态变化都查）

| ID | 不变量 | 违反归类 |
|---|---|---|
| INV-01 | `totalCny == Σ quantity × unitPriceCny` | DOMAIN_BUG / UI_STATE_BUG |
| INV-02 | 同 buildId 下 revision.id 严格递增 | DOMAIN_BUG |
| INV-03 | 旧诊断不得覆盖新 revision（`diagnosis.revisionId == currentRevisionId` 才可展示） | UI_STATE_BUG |
| INV-04 | 规则输入含 EMPTY/缺失 → 不得 PASSED | DOMAIN_BUG |
| INV-05 | 规则输入含来源冲突 → 必须 UNVERIFIED(SOURCE_CONFLICT) | DOMAIN_BUG |
| INV-06 | RuleOutcome.weakest = 输入字段最弱证据 | DOMAIN_BUG |
| INV-07 | Apply 前 baseRevisionId == currentRevisionId；Apply 后必须全量重检 | DOMAIN_BUG / UI_STATE_BUG |
| INV-08 | 无候选不得修改 build、不得超预算、不得放宽硬约束 | DOMAIN_BUG |
| INV-09 | 非法事件 → `Rejected(reason)` 且状态不变 | UI_STATE_BUG |
| INV-10 | Undo 生成新 revision（id 递增），内容等于历史快照 | DOMAIN_BUG |
| INV-11 | `isDemo=true` 记录不得进入生产 seed | FIXTURE_ERROR |
| INV-12 | 比较页过期诊断不得显示为当前 PASSED | UI_STATE_BUG |
| INV-13 | 推荐可行时 total ≤ budget；不可行时 `feasible=false` 且 shortfall > 0 | DOMAIN_BUG |
| INV-14 | 替换候选必须先过硬约束，再排序；排序键固定 | DOMAIN_BUG |
| INV-15 | 同一输入、同一目录版本，规则输出和候选顺序确定 | DOMAIN_BUG |

不变量由独立 `InvariantChecker` 实现，**不得调用被测模块的内部方法**；只读 `WorkspaceUiState`、`BuildRevision`、`Diagnosis`、`SuggestionResult`。

### E.1.2 轻量参考模型（只测可判定部分）

**范围：** 不变量 + 状态机转移合法性 + 三态传播 + 总价/预算 + 候选硬约束过滤。**不实现**推荐排序“最优”、替换排序“最优”、性能预测。

**参考模型规则：**
- 独立 Kotlin 模块 `test-oracle/`，不依赖生产 `domain/`。
- 规则三态用简化声明表（路径 + 比较 + 期望三态），只覆盖 B.3 的 15 条；可复用 B.4 fixture。
- 状态机只实现 C.1/C.2 转移表；非法转移返回 `Rejected`。
- 参考模型**不复制**生产排序算法；排序只断言“候选过硬约束 + tie-breaker 键有序”。

**自证禁令：** 参考模型不得 import 生产 `domain/rules`、`domain/replacement`、`domain/recommend`。CI 加依赖检查。

### E.1.3 定向 fixture（随机测试碰不到的场景）

必须存在、每次全跑：

- `fx_gpu_len_conflict_337_320`
- `fx_gpu_len_pass_300_320`
- `fx_bios_missing`
- `fx_source_conflict_gpu_len`
- `fx_psu_power_conflict`
- `fx_memory_gen_conflict`
- `fx_suggestion_300_plus_100`
- `fx_stale_diagnosis_drop`
- `fx_apply_stale_guard`
- `fx_no_candidate`
- `fx_recommend_infeasible`
- `fx_compare_stale`
- `fx_demo_isolation`
- `fx_import_invalid_schema`
- `fx_undo_revision_monotonic`

---

## E.2 随机测试两方案的 oracle 分工

### 方案 A：语义树引导的随机游走

**生成器：** 从 Compose semantics tree 枚举 `isDisplayed && isEnabled && hasClickAction` 节点；列表先 `scrollUntilVisible`；导航/弹窗后重新枚举。动作 DSL 固定（Add/Remove/Replace/SetQuantity/Check/Preview/Apply/Cancel/Undo/Save/Compare/Back/Scroll）。

**Oracle：**
- 每步后 `InvariantChecker` 全跑（E.1.1）。
- 状态机转移与 C.1/C.2 表比对；非法事件断言 `Rejected + state 不变`。
- 不逐步骤调 LLM；LLM 只在失败后做差异解释候选。

**覆盖引导：** 优先选能产生新状态、新规则三态、新冲突类型、revision 变化、预算边界的动作；纯重复动作降权。

**记录：** seed、操作轨迹、每步 state diff、semantics tree、失败 bundle。

### 方案 B：LLM 意图 → planner → 操作

**生成器：** LLM 输出受限 `IntentSpec`；确定性 planner 转操作序列；不可执行意图丢弃并统计“意图可执行率”（生成器质量指标，不设硬阈值）。

```kotlin
data class IntentSpec(
    val intentId: String,
    val budgetCny: Long,
    val useCase: UseCase,
    val preferences: Preferences,
    val seededComponents: List<String>,
    val resolutionStrategy: ResolutionStrategy, // ACCEPT_SUGGESTION / REJECT / RAISE_BUDGET / SWAP_COMPONENT
    val expectedCoverage: List<String>          // ruleId / state / invariant id
)

enum class ResolutionStrategy { ACCEPT_SUGGESTION, REJECT, RAISE_BUDGET, SWAP_COMPONENT }
```

**Oracle：**
- 每步 postcondition 用 `InvariantChecker` + 状态机表。
- LLM 可离线预生成 `LLMAssertion`（JSON，引用状态路径），执行框架验证。
- LLM 断言失败先归 `LLM_ASSERTION_ERROR`；与参考模型矛盾且参考模型自洽时，**不判 app bug**。
- 最终裁决永远是不变量 + 参考模型 + fixture。

```kotlin
data class LLMAssertion(
    val assertionId: String,
    val statePath: String,       // 如 "revision.totalCny"
    val op: AssertOp,            // EQ / NEQ / LTE / GTE / CONTAINS
    val expected: String,
    val sourceIntentId: String
)
```

**禁止：** LLM 生成 Kotlin 测试代码直接改生产断言；LLM 读生产实现生成断言；LLM 断言引用不存在路径却算 app bug。

---

## E.3 Mutation testing（合并闸门）

**对象：** 规则引擎、三态传播、状态机 guard、替换过滤、revision 保护。

**关键 mutation（必须被杀死，否则不合并）：**

| ID | Mutation | 应杀死它的测试 |
|---|---|---|
| MUT-01 | `EMPTY` 输入改为 `PASSED` | `fx_bios_missing`、INV-04 |
| MUT-02 | `SOURCE_CONFLICT` 改为按数值比较 | `fx_source_conflict_gpu_len`、INV-05 |
| MUT-03 | `NUMERIC_LE` 的 `<=` 改 `<` | `fx_gpu_len_pass_300_320` 边界 |
| MUT-04 | weakest 取最强而非最弱 | INV-06 属性测试 |
| MUT-05 | 旧诊断覆盖新 revision | `fx_stale_diagnosis_drop`、INV-03 |
| MUT-06 | Apply 跳过 Rechecking | C.2 非法转移测试、INV-07 |
| MUT-07 | 替换先排序后过滤 | INV-14、`e2e_no_candidate` |
| MUT-08 | 无候选时静默放宽硬约束 | INV-08、`fx_no_candidate` |
| MUT-09 | Undo 回退 revision id | INV-10、`fx_undo_revision_monotonic` |
| MUT-10 | 推荐超预算不报告 | INV-13、`fx_recommend_infeasible` |
| MUT-11 | demo 数据进入生产 seed | INV-11、`fx_demo_isolation` |
| MUT-12 | 比较页显示过期诊断 | INV-12、`fx_compare_stale` |

**报告：** mutation score 不作唯一 KPI；**未杀死关键 mutation 直接阻塞**。非关键 mutation 记录趋势。

---

## E.4 失败归因自动规则

失败报告必须含：`seed`、`commit`、`catalogueVersion`、`promptHash/modelVersion`、操作轨迹、每步 state diff、失败断言、自动归因标签、`fixtureId`/`ruleId`（如适用）。

**自动判定顺序（先命中先归类）：**

1. 断言引用状态路径不存在 → `SPEC_ORACLE_ERROR`。
2. 同一 seed 在参考模型与生产实现结果不一致，且参考模型自洽、fixture 通过 → `DOMAIN_BUG` 或 `UI_STATE_BUG`（按失败层：domain 接口 vs `WorkspaceUiState`）。
3. 生产实现与参考模型一致，但 LLM 断言失败 → `LLM_ASSERTION_ERROR`。
4. fixture 自身 schema/路径/期望值不合法，或 `isDemo` 混入生产 → `FIXTURE_ERROR`。
5. 规格歧义：两条不变量/规则可推出矛盾结果，或文档未覆盖该行为 → `SPEC_ORACLE_ERROR`。
6. 其余 → `UNCLASSIFIED`。

**人工仲裁只处理 `UNCLASSIFIED` 和“同一失败出现两次”的升级项。** 仲裁三选一：改规格、改 oracle、标已知问题；结果回写文档/ADR，并新增回归用例。

**SLA：** `UNCLASSIFIED` 不阻塞日常合并，阻塞批次通过；同一失败两次必须升级为规格问题。

---

## E.5 测试批次报告与人工批准

**批次定义：** 一个 milestone/release 级别（如 `m1-data-layer`、`m2-rule-engine`、`release-v1`）。日常 PR 可自动合并，批次通过由人批。

**报告结构：**

```json
{
  "batchId": "release-v1",
  "commit": "...",
  "catalogueVersion": "static-v1",
  "summary": {
    "total": 0,
    "passed": 0,
    "failedByClass": {
      "SPEC_ORACLE_ERROR": 0,
      "LLM_ASSERTION_ERROR": 0,
      "FIXTURE_ERROR": 0,
      "DOMAIN_BUG": 0,
      "UI_STATE_BUG": 0,
      "UNCLASSIFIED": 0
    },
    "mutationKilled": "12/12",
    "llmAssertionFalsePositiveRate": 0.0,
    "intentExecutableRate": 0.0
  },
  "unclassified": [],
  "uncoveredInvariants": [],
  "uncoveredRules": [],
  "artifacts": ["failure-bundles/", "mutation-report/", "random-seeds/"]
}
```

**人工批准条件（建议）：**
- `UNCLASSIFIED = 0`，或已逐条仲裁并记录。
- 关键 mutation 全部杀死。
- 所有 ruleId 四类 fixture 通过。
- 所有 INV 至少一个测试覆盖。
- 随机测试至少 N 个 seed 通过（N 团队定，建议 50；不报“覆盖率百分比”当成功）。
- LLM 假阳性率已统计，且不用于判 app bug。

**人工只看：** 汇总、`UNCLASSIFIED`、未杀死 mutation、未覆盖不变量/规则。不逐条看通过用例。

---

## E.6 CI 与 LLM 的边界

- **CI 只跑确定性测试**：fixture、属性、状态机、参考模型、mutation、Compose semantics。不跑在线 LLM。
- **LLM 测试离线跑**：带 seed、promptHash、modelVersion 存档；结果进失败 bundle。CI 不依赖模型可用性。
- **成本控制：** LLM 断言生成离线批量；在线只用于失败后差异解释候选；设 token/调用上限，超限跳过并记录。
- **复现：** 失败 bundle 必须能离线重放，不依赖当时模型输出；LLM 断言作为静态 JSON 存档。
- **依赖检查：** CI 禁止 `test-oracle` 依赖生产 `domain`；禁止 domain 依赖 Android/Room/Compose；禁止生产代码出现范围外关键词/权限。

---

## E.7 测试清单（按模块）

| 模块 | 必测 |
|---|---|
| D1 数据 | seed schema、重复 ID、`isDemo` 隔离、字段路径合法、搜索大小写 |
| D3 规则 | 15 条 × 四类 fixture、weakest、来源冲突、多内存 PER_ITEM、PSU 边界、输出稳定 |
| D5 替换 | 337→300、候选硬约束、无候选结构、排序 tie-breaker、apply 后重检 |
| D2 工作区 | revision 单调、总价同步、stale 丢弃、非法事件、Undo、导入、apply guard |
| D4 推荐 | 预算内、不可行 shortfall、权重可见、硬约束通过、确定性 |
| D6 比较 | 字段对齐、过期诊断、版本不同 warning、少于 2 个拒绝 |
| D7 UI | semantics 可见/可点/滚动/返回/小屏；只断言 key 和状态，不断言自然语言全文 |
| 跨模块 | D.7 十张配合契约测试 + B.4 答辩锚点 |

---

E 到此。最后是 **F：范围外 CI 检查清单 + 文档消费方式 + 粗略文档收口**。继续 F？