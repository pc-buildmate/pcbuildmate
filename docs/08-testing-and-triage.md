# 08 · Oracle、失败归因与测试批次

> 读者：测试负责人；答辩前所有人。
> 本章回答：**结果不对时，怎么知道是谁的错、要不要改、由谁拍板。**

---

## 8.1 三层 oracle（LLM 不做法官）

| 层 | 内容 | 判定力 | 谁维护 |
|---|---|---|---|
| L1 不变量 + 参考模型 | 确定性、可复算 | **最终裁决** | 测试负责人 |
| L2 定向 fixture | 语义锁定 | **最终裁决** | 领域成员 |
| L3 LLM 断言 | 自然语言/意图生成的候选断言 | **只报警，不判 bug** | 离线批量生成 |

```
        失败
          │
          ▼
   L1 不变量违反？ ──是──> DOMAIN_BUG / UI_STATE_BUG（定案）
          │否
          ▼
   L2 fixture 失败？ ──是──> 检查 fixture 合法性 → DOMAIN_BUG 或 FIXTURE_ERROR
          │否
          ▼
   L3 LLM 断言失败？ ──是──> LLM_ASSERTION_ERROR（默认假阳性，需人工看）
          │否
          ▼
     UNCLASSIFIED → 人工仲裁
```

**L3 永远不能推翻 L1/L2**。LLM 说"这里应该通过"，而 fixture 说冲突——fixture 赢。

## 8.2 InvariantChecker（独立实现）

三条硬约束：

1. 只读**公开**产物：`WorkspaceUiState`、`BuildRevision`、`Diagnosis`、`SuggestionResult`、`ComparisonResult`；
2. **不得**调用被测模块的内部方法、不得 import 生产 `domain.*` 实现（CI 检查）；
3. 每条检查返回 `InvariantViolation(invId, message, evidence)`，而不是抛异常。

```kotlin
object InvariantChecker {
    fun check(state: WorkspaceUiState): List<InvariantViolation> = buildList {
        state.revision?.let { r ->
            if (r.totalCny != BuildRevision.computeTotal(r.items)) add(v("INV-01", ...))
        }
        if (state.diagnosis != null && state.revision != null &&
            state.diagnosis.revisionId != state.revision.id &&
            state.currentDiagnosis != null) add(v("INV-03", ...))
        val d = state.currentDiagnosis
        if (d != null && d.outcomes.any { it is RuleOutcome.Passed && it.weakest == VerificationState.EMPTY })
            add(v("INV-04", ...))
    }
}
```

**为什么放在独立模块**：如果检查器和被测代码共享逻辑，"用同一套错误逻辑验证错误逻辑"就会全绿。

## 8.3 轻量参考模型（只覆盖可判定部分）

| 覆盖 | 不覆盖 |
|---|---|
| 三态传播（15 条规则的声明式表） | 推荐"最优" |
| 状态机转移合法性 | 替换排序"最优" |
| 总价 / 预算算术 | 性能预测（范围外） |
| 候选硬约束过滤（过滤后是否消除冲突） | 候选体验好坏 |

实现要求：

- 独立模块 `test-oracle/`，**禁止** import `domain:rules` / `domain:replacement` / `domain:recommend`；
- 规则表用简化声明（路径 + 比较 + 期望三态），不复用生产 DSL 解析器；
- 状态机只实现 04 章的转移表；
- 与生产实现同一份 fixture，结果必须一致。

**自证禁令**由 `ci/check-boundaries.mjs` 强制（test-oracle 的 forbiddenImports）。

## 8.4 失败归因五类（冻结）

| 类 | 含义 | 典型 |
|---|---|---|
| `SPEC_ORACLE_ERROR` | 断言引用了不存在路径；两条不变量可推出矛盾；文档未覆盖该行为 | "诊断应该有 isStale 字段" |
| `LLM_ASSERTION_ERROR` | 生产与参考模型一致，但 LLM 断言失败 | "我认为 300mm 应该冲突" |
| `FIXTURE_ERROR` | fixture schema/路径/期望非法；demo 混入生产 | 期望里 `left` 指向不存在的字段 |
| `DOMAIN_BUG` | 引擎/替换/推荐/revision 计算错 | 缺字段返回 PASSED |
| `UI_STATE_BUG` | 状态编排/展示错 | 旧诊断覆盖新 revision |
| `UNCLASSIFIED` | 以上都不是 | 需要人看 |

### 自动判定顺序（先命中先归，不可跳步）

1. 断言引用的状态路径不存在 → `SPEC_ORACLE_ERROR`
2. 同一 `randomSeed` + 同 fixture，参考模型与生产不一致，且参考模型自洽 → 按失败层归 `DOMAIN_BUG`（domain 接口）或 `UI_STATE_BUG`（`WorkspaceUiState`）
3. 生产与参考模型一致，LLM 断言失败 → `LLM_ASSERTION_ERROR`
4. fixture 自身非法（路径不在表内、期望值自相矛盾、`isDemo` 混入生产） → `FIXTURE_ERROR`
5. 规格歧义（两条规则/不变量可推出不同结果，或文档没写） → `SPEC_ORACLE_ERROR`
6. 其余 → `UNCLASSIFIED`

**SLA**：`UNCLASSIFIED` 不阻塞日常合并，**阻塞批次通过**；同一失败出现两次必须升级为规格问题
（改文档/ADR，并补一条回归）。

## 8.5 失败报告必须包含什么

```json
{
  "batchId": "release-v1",
  "commit": "abc1234",
  "catalogueVersion": "static-v1",
  "rulesVersion": "rules-v1",
  "randomSeed": 20261031,
  "promptHash": "sha256:...",
  "modelVersion": "android-studio-llm-...",
  "failure": {
    "testId": "rules.GPU_CASE_LENGTH.边界_320_等于_320",
    "tags": ["rule:GPU_CASE_LENGTH", "case:PASSED", "inv:INV-04"],
    "expected": { "outcome": "PASSED" },
    "actual": { "outcome": "CONFLICT" },
    "evidence": {
      "items": ["gpu-demo-300#1"],
      "fields": { "gpu.lengthMm": 320, "case.maxGpuLengthMm": 320 }
    },
    "attribution": "DOMAIN_BUG",
    "attributionReason": "参考模型判定 PASSED；生产返回 CONFLICT；fixture 合法"
  },
  "replay": "gradlew :domain:rules:test --tests '*GPU_CASE_LENGTH*' -Dseed=20261031"
}
```

**没有 `replay` 行的失败报告不算报告**——不能复现就没法修。

## 8.6 批次定义与报告结构

批次 = 一个 milestone / release（`m1-data`、`m2-rules`、`m3-workspace`、`release-v1`）。

```json
{
  "batchId": "release-v1",
  "summary": {
    "total": 0, "passed": 0,
    "failedByClass": {
      "SPEC_ORACLE_ERROR": 0, "LLM_ASSERTION_ERROR": 0, "FIXTURE_ERROR": 0,
      "DOMAIN_BUG": 0, "UI_STATE_BUG": 0, "UNCLASSIFIED": 0
    },
    "mutationKilled": "12/12",
    "matrix": { "rules": "60/60", "stateMachine": "76/76", "invariants": "18/18" },
    "llmAssertionFalsePositiveRate": 0.0,
    "intentExecutableRate": 0.0
  },
  "unclassified": [],
  "uncoveredInvariants": [],
  "uncoveredRules": [],
  "artifacts": ["failure-bundles/", "mutation-report/", "random-seeds/"]
}
```

## 8.7 人工批准（治理规则 G2 的具体条件）

人有且只有一个动作：**点"通过"或"打回"**。人不逐条看失败用例。

批准条件（全部满足）：

| # | 条件 |
|---|---|
| 1 | `UNCLASSIFIED == 0`，或每条已仲裁并记录结论 |
| 2 | 12 条关键 mutation 全部 `KILLED` |
| 3 | 矩阵三块无空格（60/76/18） |
| 4 | 所有 ruleId 四类 fixture 通过 |
| 5 | 随机测试至少 N 个 `randomSeed` 通过（N 由团队定，建议 50） |
| 6 | `LLM_ASSERTION_ERROR` 已统计，且**未被用来判 app bug** |

人只看四个数字 + 一个清单：汇总、`UNCLASSIFIED` 列表、未杀死 mutation、未覆盖项。

## 8.8 CI 与 LLM 的边界

| 项 | CI（确定性） | 离线（LLM） |
|---|---|---|
| 运行时机 | 每次 push | 每批次一次，或手动 |
| 依赖模型 | **不依赖** | 依赖 |
| 结果用途 | 阻塞合并 | 生成候选断言/解释 |
| 存档 | 测试报告 | `promptHash + modelVersion + 原始输出` |
| 可复现 | 是 | 通过存档的断言 JSON 复现 |

**关键**：CI **不得**调用在线模型。否则一次模型故障会让整个流水线红，且无法复现。

## 8.9 LLM 断言（L3）的正确用法

```json
{ "assertionId": "a-001", "statePath": "revision.totalCny", "op": "EQ", "expected": "7900", "sourceIntentId": "intent-42" }
```

规则：

- `statePath` 必须在 `docs/08` 定义的**可访问路径白名单**内（`revision.totalCny`、`diagnosis.overall`、`replacementState` 等）；
- 路径不存在 → `SPEC_ORACLE_ERROR`，不算 app bug；
- 断言失败但参考模型自洽 → `LLM_ASSERTION_ERROR`，记入假阳性率；
- 假阳性率用于**评估 prompt 质量**，不用于调整通过/失败判定。

## 8.10 复现与归档

| 产物 | 内容 | 保存期 |
|---|---|---|
| failure bundle | 输入、state diff、日志、seed、commit | 整个学期 |
| mutation report | 未杀死清单 | 当前批次 |
| random seeds | 通过的 seed 列表 | 当前批次 |
| LLM 断言 JSON | promptHash + 输出 | 整个学期（答辩材料） |

**复现命令模板**（贴进每份报告）：

```bash
./gradlew :domain:rules:test --tests '*' -Dseed=20261031
node tools/check-matrix.mjs
```
