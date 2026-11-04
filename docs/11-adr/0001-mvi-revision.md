# ADR-0001 · 用 MVI + BuildRevision 管理状态

- 状态：已采纳（2026-10）
- 影响：feature、ui、domain:build、domain:state
- 相关：docs/04-state-machines.md、docs/05-module-independence.md

## 背景

一次更换显卡会同时改变：条目、总价、功耗余量、物理间隙、诊断结果、候选列表。
传统 MVVM 用多个独立 `LiveData` 时，用户可能短暂看到"显卡已变、总价未变"的碎片状态；
更严重的是，异步返回的诊断可能描述的是**上一份配置**。

## 决策

1. 一个屏幕一个不可变 `WorkspaceUiState`，由 `StateFlow` 下发；
2. 每次配置变化生成新的 `BuildRevision`（id 单调递增），**所有结论绑定 revision**；
3. 诊断/候选/预览都携带 `revisionId` 或 `baseRevisionId`，不匹配即作废；
4. 状态转移是纯函数，副作用以 `Effect` 描述，由 ViewModel 执行。

## 后果

正面：

- 不存在"半更新"状态；UI 是 state 的纯函数；
- 旧结果覆盖新结果在类型层面被阻断（`currentDiagnosis` 访问器）；
- 状态机可穷举测试，不需要协程与数据库；
- Undo/比较/草稿都建立在 revision 上，语义统一。

负面：

- 每次编辑都要复制整个列表（首版 ≤ 8 条，可接受）；
- 需要额外维护 `intent → transition → effect` 三层，代码量比直接改 state 多约 30%；
- 初学者容易把"派生属性"（stale）做成状态，需要文档反复强调。

## 替代方案

| 方案 | 为什么不选 |
|---|---|
| MVVM + 多 LiveData | 碎片状态无法在类型层面排除 |
| Redux 式全局 store | 对 4 个屏幕过重；状态寿命不清晰 |
| 直接把 domain 结果写回 build | 混淆"事实"与"结论"，无法表达过期 |

## 复核触发条件

- 单次配置条目 > 50（复制成本显著）→ 考虑持久化不可变结构或 structural sharing；
- 需要跨进程/跨设备恢复 → 引入序列化版本的 state。

## 答辩口径

"我们选 MVI 不是为了时髦，是因为**一次编辑会影响多个输出**，
而诊断是异步的、可能迟到。用单一不可变状态 + revision 绑定，可以让'迟到的旧结果'在类型上就进不来。"
