# contracts/ — 只读契约区

> 这个目录是**唯一**允许跨模块引用的代码。改它的门槛最高，理由见 [../docs/02-contracts.md](../docs/02-contracts.md)。

## 文件

| 文件 | 内容 | 谁能改 |
|---|---|---|
| `Model.kt` | 类目、证据态、严重度、`FieldValue`、`CatalogueRecord`、`Build`、`BuildRevision` | 契约变更 |
| `Outcomes.kt` | `RuleOutcome`、`UnverifiedReason`、`OverallStatus`、`RiskLevel`、`Diagnosis` | 契约变更 |
| `Suggestions.kt` | `Candidate`、`NoCandidateReason`、`SuggestionResult`、推荐/比较结果 | 契约变更 |
| `Ports.kt` | 十个端口接口 + 分享卡类型 | 契约变更 |
| `Workspace.kt` | `TextKey`、`Rejection`、`Effect`、两张状态机的状态与事件、`WorkspaceUiState` | 契约变更 |

## 三条硬规则

1. **本模块的依赖只有一个：Kotlin 标准库。** 禁止 Android、Room、Compose、网络库、协程库。
   这条由模块类型保证（纯 `kotlin("jvm")`），不是靠自觉。
2. **字段名、可空性、单位、语义都冻结。** 允许的变更只有三种：
   追加枚举值、追加数据类的**带默认值**字段、追加端口方法（且同步改所有实现）。
3. **公开 API 由 `api/contracts.api` 锁定。** 任何 diff 必须与 ADR 一起单独提交（`git log -- contracts/` 可审计）。

## 编译期能挡什么

| 错误 | 什么时候被挡住 |
|---|---|
| `totalCny` 与条目不一致 | 构造 `BuildRevision` 时 `require` 抛异常 |
| 条目数量为 0 | 构造 `BuildItem` 时抛异常 |
| 字段有值却是 `EMPTY` | 构造 `FieldValue` 时抛异常 |
| 标了 conflict 却只有一个来源 | 构造 `FieldValue` 时抛异常 |
| 展示旧诊断 | 编译期拿不到原文（只能走 `currentDiagnosis`），由评审 + INV-03 测试兜底 |

## 不支持的东西（别加）

- 不支持可变集合、`var`、懒加载字段：状态必须可安全跨线程传递、可整体比较。
- 不支持把 Android `Context` / 资源 ID 塞进契约。
- 不支持在契约里写业务逻辑（唯一例外是 `BuildRevision.computeTotal` 这种纯算术，且必须有测试）。
