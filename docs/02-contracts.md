# 02 · 契约语义与不变量

> 读者：LLM 必读；评审必读。
> 源码在 [../contracts/](../contracts/)，本文解释**为什么长这样**与**怎样算违约**。
> 源码与本文冲突时：**源码是接口，本文是语义**；两者都要改，且必须一起提。

---

## 2.1 契约的三个层次

```
┌───────────────────────────── contracts/ ─────────────────────────────┐
│ L-A 数据契约   Build / BuildRevision / CatalogueRecord / FieldValue   │
│ L-B 端口契约   CataloguePort / CompatibilityEvaluator / ...           │
│ L-C UI 契约    WorkspaceIntent / WorkspaceUiState / Effect / TextKey  │
└──────────────────────────────────────────────────────────────────────┘
```

三层都是"跨边界的东西"，所以都放这里。**不在这里的，就不是契约**，任何模块都可以自由重构。

判断标准（一句话）：**如果两个模块都要认识它，它就在 contracts；只有一个模块认识它，它就在那个模块里。**

## 2.2 十八条不变量

不变量是**任何时刻都必须成立**的性质，与"功能是否实现"无关。
它们由独立的 `InvariantChecker` 校验（08 章 §8.2），**不得调用被测模块的内部方法**。

| ID | 不变量 | 违反归类 | 主要覆盖 |
|---|---|---|---|
| INV-01 | `totalCny == Σ(quantity × unitPriceCny)` | DOMAIN_BUG | 构造期 `require` + 属性测试 |
| INV-02 | 同一 `buildId` 下 revision id 严格递增 | DOMAIN_BUG | `domain:revision` |
| INV-03 | 诊断只有 `revisionId == currentRevisionId` 才能作为当前结论展示 | UI_STATE_BUG | `currentDiagnosis` 访问器 |
| INV-04 | 规则输入含 `EMPTY` / 缺失 → 不得 `Passed` | DOMAIN_BUG | 15 条规则 × `missing` fixture |
| INV-05 | 规则输入含来源冲突 → 必须 `Unverified(SOURCE_CONFLICT)` | DOMAIN_BUG | `fx_source_conflict_*` |
| INV-06 | `weakest` = 输入字段最弱证据（`EMPTY > REPORTED > VERIFIED`） | DOMAIN_BUG | 属性测试 |
| INV-07 | Apply 前 `baseRevisionId == currentRevisionId`；Apply 后必须全量重检 | DOMAIN_BUG / UI_STATE_BUG | C.2 转移表 + `fx_apply_stale_guard` |
| INV-08 | 无候选不得改 build、不得超预算、不得放宽硬约束 | DOMAIN_BUG | `fx_no_candidate` |
| INV-09 | 非法事件 → `Rejected(reason)` 且状态不变 | UI_STATE_BUG | 状态机全矩阵 |
| INV-10 | Undo 生成**新** revision（id 递增），内容等于历史快照 | DOMAIN_BUG | `fx_undo_revision_monotonic` |
| INV-11 | `isDemo=true` 记录不得进入生产 seed | FIXTURE_ERROR | `fx_demo_isolation` |
| INV-12 | 比较页过期诊断不得显示为当前 `PASSED` | UI_STATE_BUG | `fx_compare_stale` |
| INV-13 | 推荐可行时 `total ≤ budget`；不可行时 `feasible=false` 且 `shortfall > 0` | DOMAIN_BUG | `fx_recommend_infeasible` |
| INV-14 | 候选先过硬约束、再排序；排序键固定 | DOMAIN_BUG | `fx_suggestion_*` |
| INV-15 | 同输入、同目录版本、同规则版本 → 输出（含顺序）完全相同 | DOMAIN_BUG | 双跑相等断言 |
| INV-16 | 模块依赖只出现在允许矩阵内 | 架构违规（CI） | `ci/check-boundaries.mjs` |
| INV-17 | 每个端口实现（含 Fake）通过同一份契约测试 | FIXTURE_ERROR | `contracts-test/` |
| INV-18 | domain 层无 I/O 副作用（不读时钟、不读写、不发网络） | 架构违规（CI + 单测） | `ci/check-boundaries.mjs` + Effect 断言 |

**INV-16/17/18 是本次新增**：它们把「模块独立」从口头约定变成可失败的检查（05 章）。

## 2.3 三层状态的判定规则（冻结）

### 2.3.1 单条规则：五步判定，先命中先生效

```
1. 部件缺失 / 类别不匹配            -> Unverified(MISSING_FIELD, missingField=null)
2. 任一输入字段 conflict == true     -> Unverified(SOURCE_CONFLICT, missingField=该字段)
3. 任一输入字段 value == null 或 EMPTY -> Unverified(MISSING_FIELD, missingField=第一个缺失字段)
4. 比较通过                          -> Passed(weakest)
5. 比较失败                          -> Conflict(..., weakest, severity, conflictId)
```

顺序不可交换。**第 2 步在第 3 步之前**，因为"来源冲突"比"字段缺失"信息量更大：
用户需要知道"两个来源打架"，而不是"某个字段是空的"。

### 2.3.2 总体状态：四步

| 序号 | 条件 | `overall` |
|---|---|---|
| 1 | 存在 `BLOCKER` 的 Conflict | `CONFLICT` |
| 2 | 存在任意 Conflict **且** 存在任意 Unverified | `MIXED` |
| 3 | 存在任意 Conflict（无 Unverified） | `CONFLICT` |
| 4 | 存在任意 Unverified（无 Conflict） | `UNVERIFIED` |
| 5 | 无 Conflict 且无 Unverified | `PASSED` |

> 早期草稿里"有 MAJOR Conflict 就 CONFLICT"与"混合冲突与未验证 → MIXED"互相覆盖，
> 会产生两种都说得通的实现。本版**只保留上表**：先看 BLOCKER，再看混合，再看单一类型。
> 这样任意输入只有一个答案，UI 才能写死映射。

**防御性约定**：`outcomes.isEmpty()` 时 `overall = UNVERIFIED`（正常路径不会走到：
空配置在导航 guard 就被拦下）。

### 2.3.3 风险等级

| 条件 | `riskLevel` |
|---|---|
| 有 BLOCKER | `HIGH` |
| 有 MAJOR（无 BLOCKER） | `MEDIUM` |
| 只有 MINOR 或 Unverified | `LOW` |
| 没有诊断 | `UNKNOWN` |

### 2.3.4 分数（只给眼睛看）

```
score = clamp(100 - 30*BLOCKER - 15*MAJOR - 5*MINOR - 3*UNVERIFIED, 0, 100)
```

**禁止**：用 score 排序候选、决定推荐、决定是否允许应用。理由见 01 章 §1.2。

## 2.4 null、EMPTY 与「缺失」的区别

三种"没有值"在语义上不同，实现上都要落到 `Unverified(MISSING_FIELD)`，但 `missingField` 不同：

| 情况 | 表示 | `missingField` |
|---|---|---|
| `attributes` 里没有这个 key | 字段未采集 | 该 `FieldRef` |
| 有 key 但 `value == null`（必须 `state == EMPTY`） | 有人查过，没查到 | 该 `FieldRef` |
| 部件本身不存在 | 记录缺失 | `null` |

UI 据此显示不同提示：前两者是"这条规格需要确认"，后者是"这件零件不在目录里"。
**禁止**把三者合并成一个空字符串或 0。

## 2.5 端口契约与 Fake 的一致性

端口是模块之间唯一的运行期耦合点。因此：

1. 每个端口在 [../contracts-test/](../contracts-test/) 有一份抽象契约测试；
2. **真实实现（Room / assets）与测试 Fake 都必须继承同一份并全绿**（INV-17）；
3. 契约测试只断言"行为"，不断言实现细节（不数调用次数、不检查 SQL）。

> 为什么必须这样：LLM 写测试时会自然地用 Fake。如果 Fake 与真实实现行为不同，
> 测试全绿而真机崩溃——这是最容易漏、也最难在答辩时解释的一类 bug。

**端口设计禁令**：

- 端口方法不得返回 `Result` 之外的可变对象；不得返回 Android/Room 类型。
- 端口不得暴露 `suspend` 之外的隐式线程语义；实现必须自己保证线程安全。
- 端口不得回调调用方（禁止双向依赖）。
- 一个端口只服务一个模块。禁止"大而全的 `Repository` 上帝接口"（05 章 §5.6 反例 7）。

## 2.6 把不变量编码进类型：`currentDiagnosis`

`WorkspaceUiState` 里同时有 `diagnosis` 与 `revision`。如果让 UI 直接读 `diagnosis`，
迟早会有人忘了比对 revision。因此契约提供两个**安全访问器**：

```kotlin
val currentDiagnosis: Diagnosis?
    get() = diagnosis?.takeIf { it.revisionId == revision?.id }

val isDiagnosisStale: Boolean
    get() = diagnosis != null && diagnosis.revisionId != revision?.id
```

规则：

- UI **只能**读 `currentDiagnosis`；读原始 `diagnosis` 必须在评审中给出理由（目前唯一允许的场景：
  显示"旧结果已过期"的横幅，且只能读 `isDiagnosisStale`）。
- 任何模块不得往 `WorkspaceUiState` 里再加一个 `isStale` 布尔——那是重复真相源。

同样的模式用在比较页：`ComparisonResult.diagnoses` 允许为 `null`，非空即代表"匹配当前 revision"。

## 2.7 字段路径：契约的第三个维度

字段路径（如 `gpu.lengthMm`）是**字符串契约**，编译器管不了，所以用三层保护：

1. 冻结表（03 章 §3.3）——唯一权威；
2. `rules-v1.json` 启动校验——路径不在表内即 fail-fast；
3. fixture 校验——期望里的 `left` / `right` 路径必须同时出现在规则与表内。

**新增路径**必须：改 §3.3 表 → 改 `rules-v1.json` → 加 fixture → 提契约变更。四步缺一不可。

## 2.8 编译期已经挡住的错误（别再用运行期检查重复一遍）

| 错误 | 挡住的位置 |
|---|---|
| 总价与条目不符 | `BuildRevision.init` |
| 数量 0 / 负价 | `BuildItem.init` |
| `value != null` 却是 `EMPTY` | `FieldValue.init` |
| `conflict = true` 但只有一个来源 | `FieldValue.init` |
| revision id = 0 | `BuildRevision.init` |

**评审口味**：能用 `require` 表达的约束，不要写成"调用方应当检查"。
类型和构造器是最便宜的测试。

## 2.9 契约变更流程（治理规则 G1 的具体化）

### 允许的变更（不需要人批，但必须留痕）

| 类型 | 例子 | 额外要求 |
|---|---|---|
| 追加枚举值 | 新增 `UnverifiedReason.LICENSE_RESTRICTED` | 全量 `when` 必须穷尽；CI 编译即发现 |
| 追加带默认值的字段 | `Candidate(val reason: String = "")` | 旧 fixture 不得受影响 |
| 追加端口方法 | `CataloguePort.countByCategory` | 所有实现同步；契约测试补一条 |
| 追加 `TextKey` | 新文案 | 必须同时在中英文资源里存在 |

### 禁止的变更（必须走 scope-change 或 ADR）

| 类型 | 为什么 |
|---|---|
| 改字段名 / 改可空性 | 破坏所有实现与 fixture，等价于重写 |
| 改单位（元 → 分） | 静默的 100 倍错误 |
| 把 `Conflict` 合并进 `Unverified` | 抹掉"两边数值都要展示"的能力 |
| 给 `RuleOutcome` 加第四种结果 | 三态是所有测试矩阵的维度，加一维等于重做验收 |
| 把 UI 状态移进 domain | 违反 05 章依赖矩阵 |

### 提交规范

```
contracts/…          <- 单独一个 commit，message 前缀 "contract:"
docs/02-…            <- 同一 commit 或紧随其后的 commit
docs/11-adr/00xx-…   <- 若属于"禁止的变更"，必须有 ADR
fixtures/…           <- 若影响语义，同一 PR
```

**实现 PR 里出现 `contracts/` 的 diff → 直接打回**（评审清单第 1 条，10 章 §10.6）。
这不是不信任，而是为了让"语义变了"这件事无法被混在一堆格式改动里。

### 影响面速查（改哪里要动谁）

| 改动 | 受影响模块 | 必须补的测试 |
|---|---|---|
| `RuleOutcome` 结构 | domain:rules、domain:replacement、feature、ui、全部 fixture | 规则 ×4、替换、UI 状态快照 |
| `WorkspaceUiState` | feature、ui | ViewModel 状态序列 + semantics |
| `CataloguePort` | data、testing/Fake、domain:rules | 契约测试 + 规则回归 |
| `TextKey` | ui、资源文件 | semantics key 断言 |
| 字段路径表 | data seed、rules JSON、fixtures、domain:rules | 该规则的 4 类 fixture |

## 2.10 契约漂移的六个真实场景（LLM 一定会犯）

| # | 现象 | 检测手段 |
|---|---|---|
| 1 | 为了让编译过，把 `Conflict.conflictId` 改成可空 | API dump diff + `git log contracts/` |
| 2 | 在 domain 里 `import android.util.Log` | `ci/check-boundaries.mjs` |
| 3 | 把 `REPORTED` 当 `VERIFIED` 用（!= EMPTY 就认为可信） | MUT-04 + `fx_*_reported` 用例 |
| 4 | 新增一个 `isCompatible: Boolean` 到 `Diagnosis` | 契约评审 + "三态不得压成布尔"检查条目 |
| 5 | 在 ViewModel 里内联一条兼容性 if | 边界检查（feature 不得 import domain:rules 实现类）+ 代码评审 |
| 6 | 给 `TextKey` 加 key 但不加资源 | 编译期：`TextKey` → 资源映射用 `when` 穷尽 + 资源存在性测试 |

## 2.11 自检清单（每个 PR 贴一次）

- [ ] 没有改 `contracts/`（或：改了，且是单独 commit + 本条说明 + 影响模块列表）
- [ ] 新增字段都有默认值，旧 fixture 未修改仍通过
- [ ] 新增枚举值后所有 `when` 仍穷尽（编译通过）
- [ ] 新增 `TextKey` 在 `strings.xml` 与 `values-en/strings.xml` 都存在
- [ ] 新增字段路径已进 §3.3 表、rules JSON、fixture 三处
- [ ] `:contracts:apiCheck` 通过
