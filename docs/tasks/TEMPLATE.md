# 任务包模板

> 复制本文件为 `docs/tasks/<task-id>.md`，九块填满才允许标 `status:ready`。
> 任何一块空着，AI 必须拒绝开工。

---

## 1. 元信息

| 项 | 值 |
|---|---|
| 任务 ID | `D3-02` |
| 模块 | D3 规则引擎 |
| Owner | 待填 |
| Reviewer | Captain |
| 依赖 | `D1-01` 已合并 |
| 预计 | 1.5 天 |
| 截止 | 2026-11-15 |
| 分支 | `feat/d3-rule-engine` |

## 2. 目标（一句话）

实现 `CompatibilityEvaluator`，从 `assets/rules/rules-v1.json` 读取 15 条规则，对给定 `BuildRevision` 输出按 `ruleId` 字典序排序的 `RuleOutcome` 列表。

**不做**：不实现替换建议；不改字段路径；不做 UI。

## 3. 允许修改的路径

```
domain/rules/**
domain/rules/src/test/**
```

禁止：`contracts/**`、`assets/**`、`feature/**`、`ui/**`、`app/**`、`docs/01`–`04`。

## 4. 只读输入

- 模块卡：`docs/06-modules.md` §D3
- 契约：`contracts/src/main/kotlin/pcbuildmate/contracts/Model.kt`、`Outcomes.kt`、`Ports.kt`（CompatibilityEvaluator）
- 规则：`docs/03-rules.md` §3.2–§3.4、§3.6
- fixture：`assets/fixtures/fixtures-rules-v1.json` 全部 60 条

## 5. 输出契约

- `evaluate(revision)` 纯函数；同输入同输出（含顺序）。
- 结果按 `ruleId` 字典序；`RuleOutcome` 的 `state` 为 `PASSED | CONFLICT | UNVERIFIED`。
- 任一输入字段 `EMPTY` → 该规则 `UNVERIFIED`，不得 `PASSED`。
- `conflictId` 生成规则见 `docs/03 §3.5`。

## 6. 验收命令

```bash
./gradlew :domain:rules:test
node tools/check-matrix.mjs
```

期望：60/60 fixture 通过；矩阵 `rules` 60 格满。

## 7. 反例（必须能红）

- 把 `EMPTY` 当 `0.0` 参与比较 → `fx_missing_bios_*` 必须红。
- 把两个 `REPORTED` 来源的数值冲突降级为取平均 → `fx_source_conflict_*` 必须红。
- 结果用 `Set` 输出 → 顺序断言必须红。

## 8. 范围外

- 不做 `GPU_CASE_THICKNESS` 之外的新规则。
- 不做推荐/替换/比较。
- 不引入新依赖。
- 不修改 fixture 期望值。

## 9. 规格空白（必须提问）

- `Q1`：`numeric` 解析失败时返回 `null` 还是 `0.0`？契约已写 `null`，若发现不一致，停下。
- `Q2`：`biosGeneration` 的次版本号 > 9 如何处理？见 `docs/03 §3.3`，若 fixture 未覆盖，提 issue，不自行扩展。

## 10. 交付

- commit 前缀：`feat(d3):`
- PR 描述附：60/60 输出、矩阵截图、未完成项
- 更新 `docs/tasks/D3-02.md` 状态为 `review`
