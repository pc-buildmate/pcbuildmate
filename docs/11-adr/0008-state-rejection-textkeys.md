# ADR-0008 · 两个状态机拒绝用的 TextKey

- 状态：**已采纳**（2026-11-04）
- 影响：`:contracts`、`:domain:state`、`:feature`、`:ui`
- 相关：`docs/04 §4.3/§4.4`、`contracts/Workspace.kt`

## 背景

`Rejection(reason, textKey, detail)` 的 `textKey` 非空，但 `docs/04` 的状态表里有两类拒绝没有对应 key：

- `INVALID_STATE`：在 Idle 收到 CheckCompleted、在 Done 收到 CheckCompleted 等；
- `PREVIEW_NOT_READY`：Previewing 里 `previewRevisionId == null` 时点 Apply。

现有 `TextKey` 里没有这两个值，实现只能借用语义不相干的 key（例如 `diagnostics_failed`），UI 会显示错误文案。

## 决策

在 `TextKey` 追加两个值（只增不改）：

```kotlin
INVALID_STATE("invalid_state"),
PREVIEW_NOT_READY("preview_not_ready"),
```

`RejectionReason` 保持不变，仍由状态机决定 reason；UI 用 key 查文案。

## 后果

正面：

- 状态机可以严格按 docs/04 拒绝，不需要借 key；
- 文案 key 与 reason 一一对应，测试可以断言 key。

负面：

- 资源文件需要补两条中英文文案；
- 这是契约追加，需记录在 API diff。

## 替代方案

| 方案 | 为什么不选 |
|---|---|
| 借用 `DIAGNOSTICS_FAILED` | 文案错误，用户会以为检查失败 |
| 把 `textKey` 改成可空 | 破坏所有调用方，且 UI 仍要兜底 |
| 在 domain 里拼中文 | 违反文案走 TextKey 的约定 |

## 复核触发条件

- 状态机新增第三种“无文案”拒绝；
- 文案系统改成 reason → key 自动映射（届时可删掉显式 key）。
