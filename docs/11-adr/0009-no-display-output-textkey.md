# ADR-0009 · `NO_DISPLAY_OUTPUT` TextKey

- 状态：**已采纳**（2026-11-05）
- 影响：`:contracts`、`:domain:revision`、`:feature`、`:ui`

## 背景

`DisplayOutputGuard` 在“无 GPU 且 CPU 无核显”时阻止检查/应用。
`Rejection` 需要 `TextKey`，但现有枚举没有对应文案；借用
`DIAGNOSTICS_FAILED` 会误导用户以为检查失败。

## 决策

追加 `TextKey.NO_DISPLAY_OUTPUT("no_display_output")`，只增不改。
Guard 返回 `Boolean`，不新增 `RejectionReason`；UI 用
`WorkspaceUiState.banner` 展示，不伪造 `RuleOutcome`。

## 后果

- 无 GPU/无核显时用户看到“没有显示输出”，而不是“检查失败”；
- 不把 build-level guard 混进 15 条 pairs 规则；
- 文案资源后续补中英文。

## 替代方案

| 方案 | 为什么不选 |
|---|---|
| 新增第 16 条规则 | 规则是成对兼容，显示输出是整机条件；会动 60 条 fixture |
| 借 `DIAGNOSTICS_FAILED` | 文案错误 |
| 静默失败 | 用户不知道为什么不检查 |
