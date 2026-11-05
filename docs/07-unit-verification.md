# 07 · 单元验证设计

> 读者：测试负责人；每个 LLM 任务在**写代码之前**读一次。
> 本章回答：**每个设计单元，用什么手段、在哪一层、证明什么？**
> 核心立场：**测试不是"跑绿了"，而是"矩阵没有空格子 + 关键变异被杀掉"。**

---

## 7.1 什么是本项目的一个「单元」

单元不是"一个类"，而是**一个可以在毫秒级判定的设计决策**。按这个定义：

| 模块 | 单元 | 隔离手段 | Oracle | 环境 |
|---|---|---|---|---|
| `domain:revision` | 一次编辑产生的新快照（含总价、id） | 纯函数，无依赖 | 不变量 + 属性 | JVM |
| `domain:rules` | 一条规则对一组字段的判定 | 目录通过接口注入；fixture 自带数据 | fixture 期望 + 参考模型 | JVM |
| `domain:replacement` | 一次候选过滤 + 排序 | 引擎通过接口注入（Fake 返回固定结果） | 不变量 + 排序键 | JVM |
| `domain:recommend` | 一次预算分配 | 同上 | 不变量（预算/可行性） | JVM |
| `domain:compare` | 一次多列对齐 | 只读入参 | 字段/delta 断言 | JVM |
| `domain:state` | 一个 (state, event, guard) 转移 | 纯函数 | 转移表 | JVM |
| `data` | 一次端口调用的行为 | 内存实现 vs Room 实现 | 契约测试 | JVM/Robolectric |
| `feature` | 一串 Intent 造成的状态序列 + Effect 序列 | 全部端口用 Fake | 状态序列断言 | JVM + 协程测试 |
| `ui` | 一个 state 渲染出的可交互元素 | 静态 state | semantics 断言 | Robolectric |

**判定标准**：如果你需要 sleep、需要模拟器、需要网络才能测，那它就不是单元测试，应该下沉或拆分。

## 7.2 测试预算（不是覆盖率，是数量级）

| 层 | 数量级 | 单条耗时 | 失败意味着 |
|---|---|---|---|
| 规则 fixture | 60 + 8 | < 5 ms | 语义被破坏（最高优先级） |
| 状态机矩阵 | 76 | < 1 ms | 交互逻辑错 |
| 属性测试 | 10 组 × 100 次 | < 1 s | 隐藏边界 |
| 端口契约 | 每个端口 5–8 | < 10 ms | 实现不一致 |
| ViewModel 序列 | 15–20 | < 100 ms | 编排错 |
| UI semantics | 15–20 | < 200 ms | 展示错 |
| 端到端契约 | 10 | < 300 ms | 模块配合错 |

**反例**：1000 条 UI 测试、3 条规则测试。这说明测试写在了错误的地方。

## 7.3 确定性从哪来（A3 的落地）

| 不确定源 | 端口/手段 | 测试里 |
|---|---|---|
| 当前时间 | `ClockPort` | `FixedClock("2026-10-31T09:00:00+08:00")` |
| revision id | `IdPort` | `SequenceId(1,2,3...)` |
| buildId | `IdPort` | 同上 |
| 迭代顺序 | 全部输出显式排序 | 断言 `List` 相等（顺序敏感） |
| 随机 | 生产代码禁止 `Random` | 属性测试显式传入 seed |
| 语言/时区 | 禁止读 `Locale` / `TimeZone` | 固定 `Locale.CHINA` |
| 目录内容 | 版本化 JSON | fixture 自带记录 |

**规则**：任何 `java.util.Random` / `Math.random()` / `System.currentTimeMillis()` / `Instant.now()`
出现在 `src/main` 的非 app 模块里，视为 INV-18 违规，CI fail。

## 7.4 测试替身：Fake 优先，Mock 禁用

| 替身 | 用在哪 | 规则 |
|---|---|---|
| **Fake**（手写实现） | 端口、Repository、引擎 | 必须与真实实现同过契约测试（INV-17） |
| **Stub**（固定返回） | 一次性场景 | 简单值即可，不要做成"万能桩" |
| **Mock**（行为验证） | **禁止**用于 domain / feature | 会把"调用顺序"写进测试 |

为什么禁 mock：

```kotlin
// 反例：这条测试在保护"实现方式"，不是在保护"行为"
verify(engine, times(1)).evaluate(any())
// 一旦实现改成批量评估，测试假失败，而 bug 一个也没抓到
```

**Fake 的写法要求**：

- 记录"被调用了什么"可以（用于断言 Effect 执行次数），但**不要**在断言里依赖调用顺序；
- 必须能被构造失败（`FakeCatalogue.failOn("gpu-x")`），用于测错误路径；
- 必须支持"迟到的结果"（返回顺序可配置），用于测 revision 保护。

## 7.5 验证矩阵：让"覆盖"变成可判定的

### 7.5.1 三个矩阵

| 矩阵 | 维度 | 规模 | 空格后果 |
|---|---|---|---|
| 规则矩阵 | 15 ruleId × 4 case | 60 | 某条规则的某种语义从未被测 |
| 状态机矩阵 | 诊断 4×4 + 替换 6×10 | 76 | 某个交互分支存在但没人验证 |
| 不变量矩阵 | 18 INV × ≥1 测试 | 18 | 某条"永远不能发生"的性质没人守 |

### 7.5.2 用标签把测试挂到矩阵上

```kotlin
@Test
@Tag("rule:GPU_CASE_LENGTH")
@Tag("case:CONFLICT")
@Tag("inv:INV-04")
fun `gpu length conflict 337 vs 320`() { ... }
```

标签是**唯一的机器可读接口**。CI 从 JUnit XML 里抽出全部 `rule:*` / `case:*` / `inv:*` 标签，
与期望矩阵比对，**缺一格即 fail**（不是"警告"）。

### 7.5.3 矩阵检查器行为

```bash
node tools/check-matrix.mjs
# 读取 build/test-results/**/*.xml
# 1) 期望：rules-v1.json 的 15 条 × 4 case；状态机 76 格；INV-01..18
# 2) 实际：XML 里出现的 tag 组合（且必须 passed）
# 3) 差集非空 -> 退出码 1，并打印缺失清单
```

**为什么不用行覆盖率**：行覆盖率 90% 的规则引擎，可能漏掉"来源冲突"整条分支，因为那一行被别的用例覆盖到了。
矩阵覆盖看的是**语义维度**，行覆盖看的是**代码维度**——前者才是我们要的。

**`MISSING` 的映射**：fixture id 以 `_missing` 结尾（RuleOutcome 仍是 `UNVERIFIED`），
不是第四种 outcome；生成器从 id 后缀取 case，避免把 45 格误报成 60 格。

## 7.6 端口契约测试（Fake 与真实实现的一致性）

每个端口一份抽象测试（已提供 `CataloguePortContract` 作为样例）：

```kotlin
abstract class BuildStorePortContract {
    abstract fun newStore(): BuildStorePort
    @Test fun `save then load returns equal revision`() { ... }
    @Test fun `listRevisions is ordered by id ascending`() { ... }
    @Test fun `deleting build removes its revisions`() { ... }
}

class InMemoryBuildStoreTest : BuildStorePortContract() { override fun newStore() = InMemoryBuildStore() }
class RoomBuildStoreTest : BuildStorePortContract() { override fun newStore() = roomStore() }   // Robolectric
```

**没有这一层**，用 Fake 写的所有 feature 测试都只在证明"Fake 自己很自洽"。

## 7.7 属性测试（10 组，覆盖不变量）

| # | 属性 | 覆盖 |
|---|---|---|
| P1 | `totalCny == Σ(qty × price)` 对任意 items 成立 | INV-01 |
| P2 | 任意编辑序列，新 revision id 严格大于旧 id | INV-02 |
| P3 | 任意规则输入的 `weakest` 等于最弱证据 | INV-06 |
| P4 | 同输入两次 evaluate 完全相等 | INV-15 |
| P5 | Undo 后内容等于上一快照，id 更大 | INV-10 |
| P6 | 任一候选应用后，目标冲突消除且无新 BLOCKER | INV-14 |
| P7 | 候选列表满足排序键单调 | INV-14 |
| P8 | `feasible=true ⇒ total ≤ budget` | INV-13 |
| P9 | `diagnoses[i] == null ⇔ 该 build 诊断过期` | INV-12 |
| P10 | 任意 (state, event)：要么有定义转移，要么 Rejected 且状态不变 | INV-09 |

生成器约束（否则属性会测到无意义输入）：

```kotlin
val arbItem = Arb.bind(arbComponentId, arbCategory, Arb.int(1..4), Arb.long(0..5000)) { id, c, q, p -> BuildItem(id, c, q, p) }
val arbRevision = Arb.list(arbItem, 0..8).map { items -> BuildRevision(1, "b", items, BuildRevision.computeTotal(items), "static-v1", "2026-10-31", false) }
```

**注意**：`arbRevision` 用 `computeTotal` 造总价，会掩盖 INV-01 的实现错误。
因此 INV-01 必须**另有**一条"手工构造不一致总价应抛异常"的测试。

### 变形测试（metamorphic，三条便宜的强断言）

| # | 变换 | 断言 |
|---|---|---|
| M1 | 多插一条内存 | total 增加 `qty × price`；插槽占用不减 |
| M2 | 换一张更长的显卡 | `GPU_CASE_LENGTH` 不得从 CONFLICT 变 PASSED |
| M3 | 把某输入设为 EMPTY | 结果不得从 UNVERIFIED/PASSED 变 PASSED |

M2/M3 不需要知道"正确答案"，只需要知道"方向"——非常适合作为 LLM 生成代码的回归网。

## 7.8 变异测试（合并闸门）

工具：本项目用 `tools/run-mutations.mjs`（12 条手工补丁，改源码→跑目标测试→恢复）为主；
PIT 可作为后续全量扫描。**mutation score 不是 KPI**；**关键 mutation 未杀死 = 阻塞合并**。

| ID | 变异 | 应杀死它的测试 |
|---|---|---|
| MUT-01 | EMPTY 输入返回 PASSED | 15 条 `missing` fixture、INV-04 |
| MUT-02 | 来源冲突改为按数值比较 | `fx_source_conflict_gpu_len`、INV-05 |
| MUT-03 | `<=` 改 `<` | `fx_gpu_len_pass_300_320`、等号边界 |
| MUT-04 | weakest 取最强 | P3、INV-06 |
| MUT-05 | 旧诊断覆盖新 revision | `e2e_stale_diagnosis`、INV-03 |
| MUT-06 | Apply 跳过 Rechecking | 04 章 R-16/R-20、INV-07 |
| MUT-07 | 替换先排序后过滤 | P6、`e2e_no_candidate` |
| MUT-08 | 无候选时静默放宽硬约束 | INV-08、`fx_no_candidate` |
| MUT-09 | Undo 回退 revision id | P5、`e2e_undo_revision` |
| MUT-10 | 推荐超预算不报告 | P8、`e2e_recommend_infeasible` |
| MUT-11 | demo 数据进入生产 seed | INV-11、`e2e_demo_isolation` |
| MUT-12 | 比较页显示过期诊断 | INV-12、`e2e_compare_stale` |

PIT 配置要点：

```kotlin
pitest {
    targetClasses.set(listOf("pcbuildmate.domain.*"))
    mutationThreshold.set(0)        // 不设全量阈值
    testStrengthThreshold.set(0)
    features.set(listOf("-FINFIELD_MUTATION"))   // 减少噪声
}
```

然后由 `tools/check-matrix.mjs --mutations` 读 `build/reports/pitest/mutations.xml`，
断言 12 条关键变异全部 `KILLED`。

## 7.9 ViewModel 单元测试（最容易写错的一层）

### 7.9.1 三条纪律

1. **不 sleep**：用 `runTest` 的虚拟时间 + 手动推进 Fake 的 Deferred。
2. **断言状态序列，不断言单点**：用 Turbine `test { }` 收集 state 流。
3. **交错场景必须显式构造**：不要指望"跑 100 次碰巧出问题"。

### 7.9.2 交错 revision 的标准写法

```kotlin
@Test
fun `慢检查返回时用户已改配置则丢弃`() = runTest {
    val engine = ControllableEvaluator()          // 返回 CompletableDeferred
    val vm = WorkspaceViewModel(fakeDeps(engine), ...)
    vm.onIntent(StartCheck)                       // Running(rev1)
    vm.onIntent(SetQuantity("gpu-demo-337", 2))   // 生成 rev2 -> Idle
    engine.complete(rev1, diagnosisFor(rev1))     // 迟到结果
    vm.state.test {
        assertEquals(DiagnosisState.Idle, awaitItem().diagnosisState)   // 仍 Idle
        assertTrue(vm.state.value.currentDiagnosis == null)
    }
}
```

这条测试同时守住 INV-03、R-07/D-07，以及"编辑不自动重检"的产品语义。

### 7.9.3 必测的七种时序

| # | 时序 | 断言 |
|---|---|---|
| T1 | 慢检查 + 编辑 | 旧结果丢弃 |
| T2 | 双击"检查" | 只跑一次（Running 忽略重复） |
| T3 | 预览中编辑 | 回 Idle，preview 作废 |
| T4 | 应用时 base 过期 | Rejected，build 不变 |
| T5 | 重检发现新冲突 | 停在 Candidates，不 Applied |
| T6 | 迟到候选（旧 base） | 静默丢弃，不报错 |
| T7 | 切换配置时有未保存编辑 | 自动存草稿，不阻断 |

## 7.10 UI 单元测试（semantics，不测文案全文）

**只断言三样**：元素存在（by testTag）、可交互（performClick）、状态映射（enabled/visible/text key）。

```kotlin
@Test
fun `过期诊断显示横幅且不显示通过徽标`() {
    composeRule.setContent {
        WorkspaceScreen(
            state = WorkspaceUiState(revision = rev2, diagnosis = diagOf(rev1)),
            onIntent = {},
        )
    }
    composeRule.onNodeWithTag("diagnostics_stale_banner").assertIsDisplayed()
    composeRule.onAllNodesWithTag("diagnostic_badge_PASSED").assertCountEquals(0)
}
```

**禁止**：`assertTextContains("该显卡长度 337mm 超过机箱限长 320mm")`。
文案会改，断言会碎，而且它测的是翻译，不是逻辑。要测"左右值都显示"，就断言两个数值 tag 存在。

## 7.11 DoD：模块"做完"的机器可检查清单

一个模块只有在**全部 8 条**为真时才算交付。前 6 条可脚本化，后 2 条靠评审。

| # | 条件 | 检查方式 |
|---|---|---|
| 1 | 单模块测试全绿 | `./gradlew :module:test` |
| 2 | 规则/状态机/不变量矩阵无空格 | `node tools/check-matrix.mjs` |
| 3 | 关键 mutation 全杀死 | `node tools/check-matrix.mjs --mutations` |
| 4 | 边界/关键词检查通过 | `node ci/check-boundaries.mjs && node ci/check-keywords.mjs` |
| 5 | 公开 API 未变（或已走契约流程） | `./gradlew :contracts:apiCheck` |
| 6 | 无 `TODO` / `FIXME` / `println` 在 `src/main` | `ci/check-keywords.mjs` 附带的卫生检查 |
| 7 | 评审：没有把业务逻辑写进 ViewModel/UI | 评审清单（10 章 §10.6） |
| 8 | 评审：新增行为都有对应文档章节更新 | 提交说明里贴章节号 |

**"口头完成"不算完成**。这是治理规则 G2 的下沉：自动化只负责把"没做完"变成红色。

## 7.12 LLM 测试反模式（十条，逐条对照检查）

| # | 反模式 | 长什么样 | 检出方式 |
|---|---|---|---|
| 1 | 改断言凑绿 | 把 `assertEquals(CONFLICT, ...)` 改成 `UNVERIFIED` | PR diff 里出现 `fixtures/` 或期望值变更 |
| 2 | 放宽 fixture | 把 337 改成 320 让测试通过 | 生成器 `--check` + 人工确认 |
| 3 | mock 被测对象 | `mockk<DefaultRuleEngine>` | 静态扫描 `mockk<` / `Mockito` 用在 domain |
| 4 | 断言实现细节 | `verify(engine).evaluate(any())` | 评审 |
| 5 | 用 sleep 等异步 | `Thread.sleep(500)` | 静态扫描 |
| 6 | 只测 happy path | 60 条 fixture 只有 pass 类 | 矩阵空格即 fail |
| 7 | 断言文案全文 | `onNodeWithText("...")` | 扫描测试代码里的中文长串 |
| 8 | 用真实时钟/随机 | `Instant.now()` 在测试断言里 | 评审 + 扫描 |
| 9 | 把实现抄进 oracle | 参考模型 import 生产 `domain:rules` | `ci/check-boundaries.mjs`（test-oracle 规则） |
| 10 | 跳过 missing case | ```Ignore` 掉 missing 用例 | 矩阵空格 + skip 即 fail |

**评审口令**（写进 10 章清单）："这个测试如果实现写错了，它会红吗？"
如果答不上来，这个测试就是装饰品。

## 7.13 命名与目录

```
domain/rules/src/test/kotlin/pcbuildmate/domain/rules/
├─ fixture/RuleFixtureTest.kt        # 60 条参数化
├─ property/WeakestPropertyTest.kt   # P3/P4
├─ boundary/PsuFormulaTest.kt        # 等号 / 差 1 / 取整
└─ contract/EngineContractTest.kt    # 空 revision / 未知规则 / 确定性
```

测试方法名用**中文可读句**（Kotlin 反引号支持），例如
`fun 缺失 BIOS 字段时返回未验证并指向该字段`。
理由：失败报告直接可读，人在批次报告里一眼能懂——这是 G2「人只看报告」的前提。
