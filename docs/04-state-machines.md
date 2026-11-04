# 04 · 状态机：诊断与替换

> 读者：LLM（实现 D2 / domain:state 前必读）、测试负责人。
> 类型定义：[../contracts/src/main/kotlin/pcbuildmate/contracts/Workspace.kt](../contracts/src/main/kotlin/pcbuildmate/contracts/Workspace.kt)
> 本版相对早期草稿的关键改动：**转移函数不再执行副作用，只产出 `Effect` 描述**（§4.2）。

---

## 4.1 三条建模前提

### 前提 1：Stale 不是状态

`stale = (diagnosis != null && diagnosis.revisionId != currentRevisionId)`。
它由数据派生，**不占枚举位、不被事件驱动、不需要转移**。
把它做成状态，就会出现「Stale 状态下收到 StartCheck 怎么办」这种本不该存在的问题。

### 前提 2：状态机是纯函数

`transition(state, event) -> TransitionResult`：
无 I/O、无协程、无时钟、无随机数。因此 60 个格子可以在毫秒级穷举。

### 前提 3：状态里只放「判定所需」，不放「展示所需」

`Candidates` 里放的是 `ReplacementContext`（base、ruleId、conflictId），
不放候选列表本身——候选列表在 `WorkspaceUiState.suggestions`。
理由：候选列表可能很大，且它的正确性由 baseRevisionId 一个字段就能验证；塞进状态会让状态比较变得昂贵且易错。

## 4.2 Effect：把副作用变成数据

这是本版最重要的结构决定。早期写法：

```kotlin
// 反例：转移函数里直接干活
fun onStartCheck(state: DiagnosisState): DiagnosisState {
    viewModelScope.launch { engine.evaluate(...) }   // 副作用
    return state.copy(...)
}
```

这样的函数**无法被穷举测试**：它需要协程调度器、需要 fake 引擎、需要等待。
本版写法：

```kotlin
data class TransitionResult<S : Any>(
    val state: S,
    val effects: List<Effect> = emptyList(),
    val rejection: Rejection? = null,
)
```

规则：

| 规则 | 说明 |
|---|---|
| E1 | 转移函数**只**返回 `TransitionResult`，不得调用任何端口 |
| E2 | 每个 `Effect` 必须由边界（`feature/` 的 ViewModel）**恰好执行一次** |
| E3 | Effect 必须可序列化、可比较（`data class` / `data object`），便于断言 |
| E4 | 被拒绝的事件**不得**产生 Effect（否则「拒绝」就有副作用了） |
| E5 | Effect 里只带 ID 与不可变对象，不带 lambda、不带 Context |

### Effect 清单

| Effect | 谁执行 | 失败时 |
|---|---|---|
| `RunDiagnosis(revisionId)` | ViewModel → `CompatibilityEvaluator` | 发 `CheckFailed` |
| `CancelDiagnosis` | ViewModel（取消 job） | 忽略 |
| `LoadCandidates(base, ruleId, conflictId)` | ViewModel → `ReplacementAdvisorPort` | 发 `CandidatesLoaded(空)` |
| `BuildPreview(base, componentId)` | ViewModel → `domain:build` | 不产生 `PreviewBuilt`（保持 `previewRevisionId = null`） |
| `RecheckRevision(revisionId)` | ViewModel → 引擎全量重检 | 发 `RecheckRejected` |
| `PersistRevision(revision)` | ViewModel → `BuildStorePort` | 保留内存态 + 提示 |
| `PersistApplied(revision)` | 同上（仅在重检通过后） | 同上 |
| `SaveDraft(revision)` | ViewModel → `DraftStorePort` | 静默失败 + 日志（不阻断） |
| `ShowText(key)` | UI | 无 |
| `Navigate(target)` | UI（NavController） | 无 |

## 4.3 C.1 诊断状态机

### 4.3.1 状态与事件

```kotlin
sealed interface DiagnosisState {
    data object Idle
    data class Running(val revisionId: Long)
    data class Done(val revisionId: Long)
    data class Failed(val revisionId: Long, val reason: String)
}
sealed interface DiagnosisEvent {
    data object StartCheck
    data class CheckCompleted(val revisionId: Long, val diagnosis: Diagnosis)
    data class CheckFailed(val revisionId: Long, val reason: String)
    data class BuildEdited(val revision: BuildRevision)
}
```

### 4.3.2 转移矩阵（4 × 4，完整）

`R` = 拒绝，状态不变；`Running*` = 忽略（状态不变、无 Effect）。

| 当前 \ 事件 | StartCheck | CheckCompleted | CheckFailed | BuildEdited |
|---|---|---|---|---|
| **Idle** | Running | R | R | Idle |
| **Running** | Running* | Done / Running | Failed / Running | Idle |
| **Done** | Running | R | R | Idle |
| **Failed** | Running | R | R | Idle |

斜杠表示按 guard 分叉：「rev 匹配 / rev 不匹配」。

### 4.3.3 转移明细

| # | 当前 | 事件 | Guard | 下一 | Effects | 断言 |
|---|---|---|---|---|---|---|
| D-01 | Idle | StartCheck | `build != null && revision != null` | Running(rev.id) | [RunDiagnosis(rev.id)] | 状态携带**当前** revision id |
| D-02 | Idle | StartCheck | 否则 | Idle | [] | Rejected(DIAGNOSTICS_REQUIRED)，无 Effect |
| D-03 | Idle | CheckCompleted / CheckFailed | — | Idle | [] | Rejected(INVALID_STATE) |
| D-04 | Idle | BuildEdited(r) | — | Idle | [SaveDraft(r)] | 不自动重检 |
| D-05 | Running | StartCheck | — | Running | [] | 忽略重复，**不**新建任务 |
| D-06 | Running | CheckCompleted(r,d) | `r == 当前 revision` | Done(r) | [] | 诊断进入可展示态 |
| D-07 | Running | CheckCompleted(r,d) | `r != 当前 revision` | Running | [] | **丢弃**，不覆盖、不报错（INV-03） |
| D-08 | Running | CheckFailed(r,why) | `r == 当前` | Failed(r,why) | [] | UI 显示 `diagnostics_failed` + 重试入口 |
| D-09 | Running | CheckFailed(r,why) | `r != 当前` | Running | [] | 丢弃 |
| D-10 | Running | BuildEdited(r) | — | Idle | [CancelDiagnosis, SaveDraft(r)] | 旧任务取消；结果作废 |
| D-11 | Done | StartCheck | `revision != null` | Running(current) | [RunDiagnosis(current)] | **总是**用当前 revision 重跑，即使 Done.rev 已旧 |
| D-12 | Done | CheckCompleted / CheckFailed | — | Done | [] | Rejected(INVALID_STATE) |
| D-13 | Done | BuildEdited(r) | — | Idle | [SaveDraft(r)] | 旧诊断自动变 stale（派生，不改状态） |
| D-14 | Failed | StartCheck | `revision != null` | Running(current) | [RunDiagnosis(current)] | 重试 |
| D-15 | Failed | CheckCompleted / CheckFailed | — | Failed | [] | Rejected(INVALID_STATE) |
| D-16 | Failed | BuildEdited(r) | — | Idle | [SaveDraft(r)] | — |

### 4.3.4 禁止转移（必须写测试）

| 禁止 | 为什么 |
|---|---|
| `Done → Done` 直接覆盖新结果 | 结果必须经 Running，否则无法区分「同一次检查」与「另一次检查」 |
| `Failed → Done` 无完成事件 | 失败不能自愈 |
| `Idle → Done` 无完成事件 | 没有开始就没有完成 |
| `CheckCompleted` 在 Idle 被接受 | 说明有幽灵任务在跑 |
| `BuildEdited` 触发自动重检 | 用户没点，凭什么跑；也破坏「编辑后由用户决定」的产品语义 |

## 4.4 C.2 替换状态机

### 4.4.1 状态与事件

```kotlin
data class ReplacementContext(val baseRevisionId: Long, val conflictRuleId: String, val conflictId: String)

sealed interface ReplacementState {
    data object Idle
    data class Candidates(val ctx: ReplacementContext)
    data class Previewing(val ctx: ReplacementContext, val candidateId: String, val previewRevisionId: Long?)
    data class Rechecking(val ctx: ReplacementContext, val previewRevisionId: Long)
    data class Applied(val newRevisionId: Long)
    data class NoCandidate(val ctx: ReplacementContext?, val reason: NoCandidateReason)
}

sealed interface ReplacementEvent {
    data class ConflictSelected(val conflictId: String, val ruleId: String)
    data class CandidatesLoaded(val baseRevisionId: Long, val result: SuggestionResult)
    data class PreviewCandidate(val candidateId: String)
    data class PreviewBuilt(val previewRevision: BuildRevision)
    data object Apply
    data object Cancel
    data class RecheckCompleted(val revision: BuildRevision, val diagnosis: Diagnosis)
    data class RecheckRejected(val reason: String)
    data class BuildEdited(val revision: BuildRevision)
    data object Dismiss
}
```

### 4.4.2 转移矩阵（6 × 10，完整）

记法：`Cand` = Candidates，`Prev` = Previewing，`Rech` = Rechecking，`Appl` = Applied，`NoC` = NoCandidate，`R` = 拒绝（状态不变）。
列名：ConfSel = ConflictSelected，CandLoaded = CandidatesLoaded，PreviewCand = PreviewCandidate，PreviewBuilt = PreviewBuilt，
Apply = Apply，Cancel = Cancel，RechDone = RecheckCompleted，RechRejected = RecheckRejected，BuildEdited = BuildEdited，Dismiss = Dismiss。

| 当前 \ 事件 | ConfSel | CandLoaded | PreviewCand | PreviewBuilt | Apply | Cancel | RechDone | RechRej | Edited | Dismiss |
|---|---|---|---|---|---|---|---|---|---|---|
| **Idle** | Cand / R | R | R | R | R | R | R | R | Idle | R |
| **Cand** | Cand / Cand | Cand / NoC / Idle | Prev / Cand / Idle | R | R | Idle | R | R | Idle | Idle |
| **Prev** | R | R | Prev / R | Prev | Rech / Idle / Prev | Cand | R | R | Idle | Idle |
| **Rech** | R | R | R | R | R | Cand | Appl / Cand / Idle | Cand | Idle | Idle |
| **Appl** | Cand / R | R | R | R | R | Idle | R | R | Idle | Idle |
| **NoC** | Cand / R | R | R | R | R | Idle | R | R | Idle | Idle |

斜杠按 guard 顺序分叉，**顺序固定，先命中先生效**。这张表没有空格：60 格全部有定义。

### 4.4.3 转移明细（非 R 的 30 条）

| # | 当前 | 事件 | Guard | 下一 | Effects | 断言 |
|---|---|---|---|---|---|---|
| R-01 | Idle | ConflictSelected(cid,rule) | 当前诊断含该 conflictId 且为 Conflict | Candidates(ctx) | [LoadCandidates(cur,rule,cid)] | ctx.baseRevisionId = 当前 revision |
| R-02 | Idle | ConflictSelected | 否则 | Idle | [] | Rejected(NO_CONFLICT_SELECTED) |
| R-03 | Idle | BuildEdited(r) | — | Idle | [SaveDraft(r)] | — |
| R-04 | Cand | ConflictSelected | 新冲突存在 | Candidates(新 ctx) | [LoadCandidates] | 允许直接切换冲突，不要求先关闭 |
| R-05 | Cand | ConflictSelected | 不存在 | Candidates | [] | Rejected(NO_CONFLICT_SELECTED) |
| R-06 | Cand | CandidatesLoaded(b,res) | `b == 当前` 且 res 非空 | Candidates | [] | 展示候选，state 不变 |
| R-07 | Cand | CandidatesLoaded(b,res) | `b == 当前` 且 res 空 | NoCandidate(ctx, res.noCandidate) | [] | **结构化**原因，不得只给空列表 |
| R-08 | Cand | CandidatesLoaded(b,res) | `b != 当前` | Idle | [] | 静默丢弃（**不**算拒绝，用户不该看到报错） |
| R-09 | Cand | PreviewCandidate(c) | 候选存在 且 base 未过期 | Previewing(ctx,c,null) | [BuildPreview(base,c)] | previewRevisionId 先为 null |
| R-10 | Cand | PreviewCandidate(c) | 候选不存在 | Candidates | [] | Rejected(CANDIDATE_NOT_FOUND) |
| R-11 | Cand | PreviewCandidate(c) | base 过期 | Idle | [] | Rejected(APPLY_REVISION_STALE) |
| R-12 | Cand | Cancel / Dismiss | — | Idle | [] | 不修改 build |
| R-13 | Cand | BuildEdited(r) | — | Idle | [SaveDraft(r)] | 候选失效 |
| R-14 | Prev | PreviewCandidate(c2) | 候选存在 且 base 未过期 | Previewing(ctx,c2,null) | [BuildPreview(base,c2)] | 换一个候选 = 重建 preview |
| R-15 | Prev | PreviewBuilt(rev) | — | Previewing(ctx,c,rev.id) | [] | **只有**这一步写入 previewRevisionId |
| R-16 | Prev | Apply | base 未过期 且 previewRevisionId != null | Rechecking(ctx,previewRev) | [RecheckRevision(previewRev)] | 进入全量重检 |
| R-17 | Prev | Apply | base 已过期 | Idle | [] | Rejected(APPLY_REVISION_STALE)，**不应用** |
| R-18 | Prev | Apply | previewRevisionId == null | Prev | [] | Rejected(PREVIEW_NOT_READY) |
| R-19 | Prev | Cancel | — | Candidates(ctx) | [] | 清 preview，build 不变 |
| R-20 | Rech | RecheckCompleted(rev,diag) | `rev.id == 当前` 且无新 Conflict | Applied(rev.id) | [PersistApplied(rev)] | **只有这里**才落库 |
| R-21 | Rech | RecheckCompleted | `rev.id == 当前` 且有新 Conflict | Candidates(新 ctx) | [LoadCandidates(新冲突)] | 自动为**新**冲突找候选，不 Applied |
| R-22 | Rech | RecheckCompleted | `rev.id != 当前` | Idle | [] | 丢弃 |
| R-23 | Rech | RecheckRejected(reason) | — | Candidates(ctx) | [] | 回候选，build 不变 |
| R-24 | Rech | Cancel | — | Candidates(ctx) | [] | 中断重检 |
| R-25 | Appl | ConflictSelected | 新冲突存在 | Candidates(新 ctx) | [LoadCandidates] | 允许连续修复多个冲突 |
| R-26 | Appl | ConflictSelected | 不存在 | Appl | [] | Rejected(NO_CONFLICT_SELECTED) |
| R-27 | Appl | BuildEdited(r) | — | Idle | [SaveDraft(r)] | 已应用结果不受影响（revision 保护） |
| R-28 | NoC | ConflictSelected | 新冲突存在 | Candidates | [LoadCandidates] | 换一条冲突再试 |
| R-29 | NoC | Dismiss / Cancel | — | Idle | [] | 关闭原因 |
| R-30 | NoC | BuildEdited(r) | — | Idle | [SaveDraft(r)] | 改配置后再看 |

> 「新 Conflict」的判定：`diagnosis.outcomes` 中第一条 `Conflict`（按 ruleId 字典序）；
> 若存在 BLOCKER Conflict 优先取它。若没有 Conflict（只剩 Unverified），视为「无新冲突」。

### 4.4.4 五个禁止转移（必须写测试）

| 禁止 | 为什么 | 对应 mutation |
|---|---|---|
| `Previewing → Applied` | 跳过重检 = 用未验证的替换改用户的钱包 | MUT-06 |
| `Candidates → Applied` | 没预览就没有确认半径 | MUT-06 |
| `NoCandidate → Applied` | 没有候选却应用了东西 | MUT-08 |
| 编辑后继续 Apply 旧 preview | baseRevision 已失效 | MUT-05 |
| `Rechecking` 期间接受旧 rev 的 `RecheckCompleted` | 旧结果覆盖新结果 | MUT-05 |

## 4.5 导航 guard（不是状态机）

| Guard | 条件 | 失败文案 key | 测试 |
|---|---|---|---|
| 进入诊断 | `build != null && revision != null` | `diagnostics_required` | 无 build 点检查 → 提示且不跳转 |
| 进入比较 | `savedBuilds.size >= 2` | `compare_insufficient_builds` | 1 个配置点比较 → 提示 |
| 比较页展示诊断 | `diagnosis.revisionId == build.currentRevisionId` | `compare_diagnostics_stale` | 过期只显示「请重新检查」 |
| 应用替换 | `baseRevisionId == build.currentRevisionId` | `apply_revision_stale` | 过期 base 应用 → 拒绝 |

guard 是**纯判定**，写成独立函数并单测；不要塞进 Composable 的 `LaunchedEffect` 里。

## 4.6 全矩阵覆盖要求

1. 每个 (state, event) 组合必须有一个参数化用例（诊断 16 格 + 替换 60 格）；
2. 期望断言三项：`nextState`、`effects`（精确列表，含顺序）、`rejection?.reason`；
3. 表格顺序即 Effects 顺序，不得依赖无序集合；
4. 用一个 `StateMachineContract` 抽象测试，真实实现与参考模型跑同一份表。

参数化骨架：

```kotlin
data class Case<S : Any>(
    val name: String,
    val from: S,
    val event: Any,
    val guard: GuardContext,
    val expectState: S,
    val expectEffects: List<Effect> = emptyList(),
    val expectRejection: RejectionReason? = null,
)

@TestFactory
fun `diagnosis full matrix`() = DIAGNOSIS_CASES.map { c ->
    dynamicTest(c.name) {
        val r = transition(c.from, c.event, c.guard)
        assertEquals(c.expectState, r.state)
        assertEquals(c.expectEffects, r.effects)
        assertEquals(c.expectRejection, r.rejection?.reason)
    }
}
```

## 4.7 状态机能被测到什么程度

| 能测 | 不能测（靠别的层） |
|---|---|
| 每个转移的下一状态 | 协程取消是否真的发生 |
| Effect 的精确列表与顺序 | 引擎算得对不对（靠规则测试） |
| 拒绝原因与「状态不变」 | 文案好不好（靠人工） |
| guard 分支 | 动画与过渡 |

**因此**：状态机测试不需要协程、不需要 fake 引擎、不需要 Android，在 `:domain:state:test` 里毫秒级完成。

## 4.8 五个常见建模错误

| # | 错误 | 症状 | 正确做法 |
|---|---|---|---|
| 1 | 加 `Stale` 状态 | 「Stale 下能编辑吗」这类怪问题 | 用派生属性 |
| 2 | 在状态里存候选列表 | 状态比较昂贵、fixture 巨大 | 只存 ctx，列表放 UiState |
| 3 | 用 `Boolean loading` 代替 Running | 无法区分「谁的检查」 | 状态带 revisionId |
| 4 | 转移函数里 launch 协程 | 测试要 sleep | 产出 Effect |
| 5 | 出错时把状态重置为 Idle | 用户输入被吞 | 拒绝且状态不变 |
