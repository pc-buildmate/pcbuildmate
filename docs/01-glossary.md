# 01 · 术语与语义（歧义出现时以本文为准）

> 读者：所有人，尤其 LLM。
> 本文只有一个目的：**让同一个词在文档、代码、测试、UI 里指同一件事**。
> 发现两处用法不同，不要就地解释——改本文或提 issue。

---

## 1.1 三层「状态」必须分开（最高频错误）

初学者最容易把三层状态混成一个布尔值。本项目**强制三分**：

| 层 | 类型 | 取值 | 作用域 | 谁产生 |
|---|---|---|---|---|
| 证据层 | `VerificationState` | `VERIFIED / REPORTED / EMPTY` | **一个字段** | D1 数据层 |
| 结果层 | `RuleOutcome` | `Passed / Conflict / Unverified` | **一条规则** | D3 规则引擎 |
| 总览层 | `OverallStatus` | `PASSED / CONFLICT / UNVERIFIED / MIXED` | **一份诊断** | D3 汇总 |

**禁止**用结果层表示证据（例如用 `Unverified` 表示某字段是 `REPORTED`）；
**禁止**用总览层驱动单条规则判定。三层只能单向推导：证据 → 结果 → 总览。

```
字段: lengthMm = 337 (VERIFIED)     字段: maxGpuLengthMm = 320 (VERIFIED)
                 └──────────────┬──────────────────┘
                        规则 GPU_CASE_LENGTH
                                │  weakest = VERIFIED
                                v
                     Conflict(severity=BLOCKER, 337 > 320)
                                │
                                v
                 Diagnosis.overall = CONFLICT, risk = HIGH
```

## 1.2 核心名词

### Build / BuildRevision

- **Build**：一份「配置工程」，有 `buildId`、预算、用途、偏好、指向当前 revision 的指针。可保存、可比较。
- **BuildRevision**：某一刻配置的**不可变快照**，有单调递增的 `id`、条目列表、总价、目录版本、价格日期。
- **每一个改变配置的动作都产生新 revision**：新增、删除、替换、改数量、Undo、应用替换建议。
- **revision id 单调递增且不回退**：Undo 产生的是"内容像旧版本"的**新** revision，id 仍递增。

> 不是什么：revision 不是「草稿版本」，不是 git commit，不是可变的草稿对象。

### Stale（过期）

**派生属性，不是状态**：`diagnosis.revisionId != build.currentRevisionId`。
它不占 `DiagnosisState` 的枚举位；它不由事件触发；它随 revision 变化自动成立或消失。

> 反例（禁止）：新增 `Stale` 状态，或给诊断加一个 `isStale` 布尔后再由事件维护。

### CatalogueRecord / componentId

一条硬件记录。`componentId` 全局唯一且稳定（如 `gpu-demo-337`），是对外引用的**唯一键**；
`manufacturer + model` 不保证唯一，**禁止**用它们当键。

### FieldValue / FieldSource

- `FieldValue`：某字段的合并值 + 证据态 + 多来源 + 冲突标记 + 可选数值。
- `FieldSource`：一个来源（厂商页 / 零售商 / 社区）给出的原始值与它自己的证据态。
- `conflict = true` 表示**同一字段的多个来源数值不一致**。此时 `value` 只是展示值，
  **不得**参与数值比较（见 03 章 §3.4 判定顺序第 2 步）。

### VerificationState

| 值 | 含义 | 允许出现在 | 不允许 |
|---|---|---|---|
| `VERIFIED` | 厂商规格页给出的值 | 任何字段 | 用于"看起来可信"的社区数据 |
| `REPORTED` | 零售商 / 社区给出、未被厂商确认 | 任何字段 | 被升格成 `VERIFIED` |
| `EMPTY` | 没有人确认 | 任何字段 | 被当成"默认值""0""无限制" |

**EMPTY 不是"未知但大概可以"**。EMPTY 是"我们不知道，所以不下结论"。

### RuleOutcome / weakest

- `Passed`：比较成立，且所有输入都是可评估的。
- `Conflict`：比较不成立，带左右 `FieldRef`、左右实际值、`severity`、`conflictId`。
- `Unverified`：无法判定，带 `missingField` 与 `reason`。
- `weakest`：该规则**所有输入字段**中最弱的证据态，顺序 `EMPTY > REPORTED > VERIFIED`。
  `REPORTED` 可以产生 `Passed`，但 `weakest = REPORTED`，UI 必须显示「基于报告数据」。
  只有 `EMPTY`、缺失、来源冲突才强制 `Unverified`。

### conflictId

一条冲突的**稳定标识**，供替换建议精确引用：
`"{ruleId}:{left.componentId}.{left.path}:{right.componentId}.{right.path}"`，
多实例规则（如每条内存）在末尾追加 `#itemIndex`。详情见 03 章 §3.5。

> 不是什么：`conflictId` 不是随机 UUID，也不是显示文本；同一冲突重算必须得到同一个 id。

### UnverifiedReason

`MISSING_FIELD / SOURCE_CONFLICT / UNSUPPORTED_RULE / INSUFFICIENT_DATA`。
**枚举冻结**：新增原因必须走契约变更；不得用自由文本代替。

### OverallStatus / RiskLevel / score

- `OverallStatus`：由全部 `RuleOutcome` 汇总（规则见 02 章 §2.5）。
- `RiskLevel`：`HIGH / MEDIUM / LOW / UNKNOWN`，由是否存在 BLOCKER/MAJOR 决定。
- `score`：0–100 的**展示用**数值（公式见 03 章 §3.7）。**禁止**参与排序、推荐、替换决策。

> 为什么禁止：分数会把「两个 BLOCKER」和「一个 BLOCKER + 若干 MINOR」压成一个数，
> 从而掩盖用户真正需要知道的那一条冲突。

### Diagnosis

一次检查的完整结果：`revisionId + outcomes + overall + riskLevel + score + computedAt`。
**revisionId 是它的身份证**：与当前 revision 不一致时，它不是「旧结果」，而是「不属于当前的结论」。

### Candidate / SuggestionResult / NoCandidateReason

- `Candidate`：一个可替换部件，带 `priceDeltaCny`、`specDelta`、`affectsOtherRules`、`rank`。
- `SuggestionResult`：绑定 `baseRevisionId` 的候选集合，或一个结构化的「无候选」原因。
- `NoCandidateReason`：`blockingConstraint`（指向具体 ruleId + fieldPath）+ `suggestedDirection`。

`suggestedDirection` 是**方向建议**，不是承诺：它不说「加 100 元就好」，只说「需要更高预算或放宽 X」。

### Intent / Effect / Rejection / TransitionResult

- `Intent`：UI 发出的**意图**（用户做了什么），不含结果。
- `Effect`：状态机产出的**副作用指令**（去跑规则、去取候选、去落库）。由边界执行，domain 不执行。
- `Rejection`：被拒绝的事件，带 `RejectionReason` 与文案 key。**状态不变**。
- `TransitionResult`：`state + effects + rejection` 三元组。纯函数输出。

> 这是本版相对早期草稿的最大结构改动：**转移函数不产生副作用，只产生"副作用的描述"**。
> 好处：状态机可以在没有 Android、没有协程、没有数据库的情况下被穷举测试。

### Port / Adapter / Fake

- **Port（端口）**：`contracts` 里的接口，定义「我需要什么能力」，不定义「怎么实现」。
- **Adapter（适配器）**：`data/` 或 `app/` 里的实现，如 Room 实现 `CataloguePort`。
- **Fake**：测试里的手写实现，行为必须与真实实现**同过一份契约测试**（`contracts-test/`）。

> 项目**禁止**对 domain 使用 mock 框架：mock 会把「实现怎么被调用」写进测试，
> 一旦实现重排调用顺序，测试就假失败。用 Fake + 状态断言。

### Fixture / Oracle / Invariant / Mutation

- **Fixture**：一组固定的输入 + 期望输出，用于锁定语义。本项目按 规则 × 四类 组织。
- **Oracle**：判定"结果对不对"的依据。本项目 oracle 分三层（08 章 §8.1）。
- **Invariant（不变量）**：任何时刻都必须成立的性质，如 `totalCny == Σ qty×unitPrice`。
- **Mutation（变异）**：故意改坏一处实现，检查测试是否失败；没失败就是测试有洞。

### demo 数据 / isDemo

`isDemo=true` 表示**概念数据**（如 337 mm 显卡、320 mm 机箱、7800→7900 元那个例子）。
demo 数据**禁止**进入生产 seed；生产 seed 的校验在启动时 fail-fast（06 章 D1）。

### seed（两个意思，必须区分）

| 写法 | 含义 |
|---|---|
| `randomSeed` / `seed`（测试报告里） | 随机测试的种子，用于复现 |
| `catalogue seed`（数据层） | 启动时灌入的静态目录数据 |

写文档时一律写全：`randomSeed` 或 `catalogueSeed`。

### batch（测试批次）

一个 milestone / release 级别的测试集合（如 `m1-data-layer`、`release-v1`）。
**批次通过由人批准**（治理规则 G2），自动化只负责跑、归因、出报告。

### DoD（Definition of Done）

一个模块"做完"的机器可检查清单，见 07 章 §7.9。**没有 DoD 就不算交付**，口头完成无效。

## 1.3 禁用词表（写了就等于没写）

| 禁用表述 | 改成 |
|---|---|
| 「智能推荐最优配置」 | 「在硬约束内按固定键排序的启发式候选」 |
| 「大致兼容」「应该能用」 | 必须是三态之一 |
| 「实时价格」 | 「带日期的参考价」 |
| 「性能提升 20%」 | 删掉（范围外） |
| 「AI 判断兼容性」 | 「确定性规则表判定」 |
| 「用户会觉得更快」 | 「可测量的任务完成时间 / 复述正确率」（若真测了） |

## 1.4 中英对照（写代码时用英文标识符）

| 中文 | 代码标识符 |
|---|---|
| 配置 / 配置版本 | `Build` / `BuildRevision` |
| 条目 | `BuildItem` |
| 字段证据态 | `VerificationState` |
| 规则结果 | `RuleOutcome` |
| 冲突 | `Conflict` |
| 未验证 | `Unverified` |
| 最弱证据 | `weakest` |
| 冲突标识 | `conflictId` |
| 替换候选 | `Candidate` |
| 无候选原因 | `NoCandidateReason` |
| 副作用的描述 | `Effect` |
| 拒绝 | `Rejection` |
| 端口 | `*Port` / `*Evaluator` / `*Advisor` |
