# 05 · 模块独立性（Independence by construction）

> 读者：架构负责人；每个 LLM 任务开工前必读一次。
> 本章回答一个问题：**当六个人（和若干 LLM）同时改代码时，凭什么保证 A 的模块坏不掉 B 的模块？**
> 答案不是"大家都小心点"，而是三条硬机制：**模块类型隔离、依赖矩阵、CI 边界检查**。

---

## 5.1 "独立"的可检验定义

| 级别 | 判据 | 怎么验 |
|---|---|---|
| L1 编译独立 | 单独 `:module:compileKotlin` 成功，不依赖其它模块的实现 | Gradle 多模块 |
| L2 测试独立 | 单独 `:module:test` 成功，不需要 Android 模拟器 | 模块类型 + 无 Android 依赖 |
| L3 替换独立 | 换掉一个实现（Room ↔ 内存）不影响调用方 | 端口 + 契约测试 |
| L4 演进独立 | 改内部实现（含重写）不触发其它模块改代码 | `internal` + 不导出实现类 |
| L5 错误不扩散 | 一个模块出错只在该模块被测出，不会伪装成别处失败 | Effect/Rejection 边界 + 归因 |

**本项目要求 L1–L5 全部成立**，其中 L1/L2 由构建强制，L3 由端口强制，L4 由可见性强制，L5 由 08 章归因规则强制。

## 5.2 模块清单（14 个；13 个进产物，1 个只用于测试）

| # | 模块 | 类型 | Android? | 依赖 | 单测环境 | 规模预估 |
|---|---|---|---|---|---|---|
| 1 | `:contracts` | Kotlin/JVM | 否 | 仅 stdlib | JVM | 已冻结 |
| 2 | `:contracts-test` | Kotlin/JVM | 否 | contracts + kotlin-test | JVM | 小 |
| 3 | `:domain:revision` | Kotlin/JVM | 否 | contracts | JVM | 小 |
| 4 | `:domain:rules` | Kotlin/JVM | 否 | contracts | JVM | 中 |
| 5 | `:domain:replacement` | Kotlin/JVM | 否 | contracts | JVM | 中 |
| 6 | `:domain:recommend` | Kotlin/JVM | 否 | contracts | JVM | 中 |
| 7 | `:domain:compare` | Kotlin/JVM | 否 | contracts | JVM | 小 |
| 8 | `:domain:state` | Kotlin/JVM | 否 | contracts | JVM | 小（但测试最多） |
| 9 | `:data` | Android Library | 是 | contracts + Room + assets | Robolectric / 内存 | 中 |
| 10 | `:feature` | Android Library | 是 | contracts + domain:state + domain:revision | JVM（协程测试） | 大 |
| 11 | `:ui` | Android Library | 是 | contracts + Compose | Robolectric（semantics） | 大 |
| 12 | `:app` | Android App | 是 | 全部 | Instrumented（少量） | 小 |
| 13 | `:testing` | Kotlin/JVM | 否 | contracts + kotlin-test | JVM | 小 |
| 14 | `:test-oracle` | Kotlin/JVM | 否 | contracts | JVM | 中（参考模型） |

**第 14 行 `:test-oracle` 不随 app 发布**：它是 08 章 §8.3 的独立参考模型，
只在测试 classpath 上存在；它禁止 import 生产 `domain.*`（矩阵最后一行）。

**注意第 9–11 行的方向**：`data` 和 `ui` 都只认识 `contracts`，彼此不知道对方存在。
`app` 是唯一同时认识所有人的模块——它只做装配（DI、导航、启动），**不含业务逻辑**。

## 5.3 依赖矩阵（唯一真相）

行 = 消费者，列 = 被依赖者。✔ = 允许，✘ = 禁止（CI 直接 fail）。

| 消费者 \ 提供者 | contracts | domain:revision | domain:rules | domain:repl | domain:recom | domain:comp | domain:state | data | feature | ui | testing |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **contracts** | — | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ |
| **domain:revision** | ✔ | — | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ |
| **domain:rules** | ✔ | ✘ | — | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ |
| **domain:replacement** | ✔ | ✘ | ✘ | — | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ |
| **domain:recommend** | ✔ | ✘ | ✘ | ✘ | — | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ |
| **domain:compare** | ✔ | ✘ | ✘ | ✘ | ✘ | — | ✘ | ✘ | ✘ | ✘ | ✘ |
| **domain:state** | ✔ | ✘ | ✘ | ✘ | ✘ | ✘ | — | ✘ | ✘ | ✘ | ✘ |
| **data** | ✔ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | — | ✘ | ✘ | ✘ |
| **feature** | ✔ | ✔ | ✘ | ✘ | ✘ | ✘ | ✔ | ✘ | — | ✘ | ✘ |
| **ui** | ✔ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | — | ✘ |
| **app** | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ | ✔ |
| **testing** | ✔ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | — |

三条要点：

1. **domain 之间零依赖**。`domain:replacement` 需要"评估一份配置是否还冲突"，
   但它不 import `domain:rules`——它通过 `CompatibilityEvaluator` 端口拿到结果（§5.4）。
2. **feature 只认识两个 domain 模块**：`build`（造 revision）与 `state`（转移函数）。
   规则、替换、推荐、比较四个**策略引擎**全部经 ports 调用，因此在 feature 的编译期，它们的实现类根本不可见。
3. **ui 连 feature 都不认识**。它只认识 `contracts` 里的 `WorkspaceUiState` 与 `WorkspaceIntent`。
   好处：Compose 预览可以喂静态 state，不需要 ViewModel、不需要 Hilt、不需要数据库。

## 5.4 端口与适配器：谁实现谁

```
            ┌──────────────── :contracts ────────────────┐
            │  Ports.kt（10 个接口） Model/Outcome/…        │
            └───────▲──────────────▲─────────────▲────────┘
                    │ 实现          │ 实现         │ 实现
        ┌───────────┴───┐   ┌──────┴──────┐  ┌───┴──────────┐
        │    :data      │   │  :domain:*  │  │  :testing    │
        │ Room/adapter  │   │ 纯算法实现   │  │ Fake/Stub    │
        └───────┬───────┘   └──────┬──────┘  └───┬──────────┘
                │                  │             │
                └──────────┬───────┴─────────────┘
                           ▼
                        :app（DI 装配，唯一知道全部）
```

| 端口 | 真实实现 | Fake（测试用） | 契约测试 |
|---|---|---|---|
| `CataloguePort` | `data: AssetCatalogueAdapter` | `testing: InMemoryCatalogue` | `CataloguePortContract` |
| `BuildStorePort` | `data: RoomBuildStore` | `testing: InMemoryBuildStore` | `BuildStorePortContract` |
| `DraftStorePort` | `data: RoomDraftStore` | `testing: InMemoryDraftStore` | 同上模式 |
| `ClockPort` | `app: SystemClockAdapter` | `testing: FixedClock` | 无（一行） |
| `IdPort` | `app: MonotonicIdAdapter` | `testing: SequenceId` | `INV-02 属性测试` |
| `CompatibilityEvaluator` | `domain:rules: DefaultRuleEngine` | `testing: ScriptedEvaluator` | 规则 fixture |
| `ReplacementAdvisorPort` | `domain:replacement` | `testing: ScriptedAdvisor` | 替换 fixture |
| `BudgetRecommenderPort` | `domain:recommend` | `testing: ScriptedRecommender` | 推荐 fixture |
| `BuildComparatorPort` | `domain:compare` | `testing: ScriptedComparator` | 比较 fixture |

**关键设计**：`domain:rules` 不认识 JSON。规则文件的解析在 `data`，
解析后把 `List<RuleDef>` 传给引擎构造函数。这样 domain 不需要序列化库，也不需要 assets。

```kotlin
// domain:rules 的入口，只吃已解析好的数据
class DefaultRuleEngine(private val rules: List<RuleDef>) : CompatibilityEvaluator {
    override fun evaluate(revision: BuildRevision): List<RuleOutcome> = ...
}
```

## 5.5 编译期隔离机制（四道闸）

### 闸 1：模块类型

```kotlin
// domain 模块只能这样声明——它根本没有 Android 插件，也就 import 不到 android.*
plugins { id("org.jetbrains.kotlin.jvm") }
dependencies { implementation(project(":contracts")) }
```

这不是风格问题：Android 类型不在 classpath 上，**写错就编译不过**。

### 闸 2：`api` vs `implementation`

```kotlin
// data/build.gradle.kts
dependencies {
    api(project(":contracts"))        // 调用方需要看到 contracts 类型
    implementation("androidx.room:room-runtime:...")  // Room 不暴露出去
}
```

规则：**依赖库一律 `implementation`**，除非它的类型出现在公开签名里。
否则 Room 会顺着依赖链泄露到 feature / ui 的 classpath 上，"UI 直接读 DAO"就从不可能变成可能。

### 闸 3：`internal` 可见性

每个模块的实现类一律标 `internal`：

```kotlin
internal class DefaultRuleEngine(...) : CompatibilityEvaluator
internal object RuleDslParser
```

跨模块只能用 `contracts` 里的接口。想共享实现？那就说明它该进 contracts（而 contracts 只放数据与接口）。

### 闸 4：API dump

```bash
./gradlew :contracts:apiDump    # 生成 api/contracts.api
./gradlew :contracts:apiCheck   # CI 里比对，任何签名变化 fail
```

`api/contracts.api` 是**人类可读的契约快照**。评审时看这个文件 diff，比看 Kotlin diff 快十倍。

## 5.6 运行时隔离

| 规则 | 说明 | 违反的后果 |
|---|---|---|
| R1 无可变全局状态 | 禁止 `object { var ... }`、`companion object` 里的可变缓存 | 测试间互相污染，随机测试不可复现 |
| R2 单例只允许 DI 提供 | 不在代码里写 `getInstance()` | 隐藏依赖，无法替换 |
| R3 模块间只传不可变数据 | 全部 `data class` / 只读集合 | 一个模块改了对象，另一个模块默默受影响 |
| R4 副作用只在边界执行 | domain 产出 Effect，feature 执行 | 业务逻辑藏在协程里，测不到 |
| R5 不跨模块扩展函数 | 禁止给别的模块的类型加 extension | 隐式耦合，重构时找不到调用点 |
| R6 不共享 ViewModel | 一个屏幕一个 VM，跨屏共享走 Repository | 状态生命周期混乱 |

## 5.7 一次编辑的完整数据流（把所有机制串起来）

```
[UI] SetQuantity(gpu, 2)
  │  WorkspaceIntent
  ▼
[feature] WorkspaceViewModel
  │  1. 调 domain:revision 生成新 BuildRevision（纯函数）
  │  2. 把 BuildEdited(rev) 喂给两个状态机
  ▼
[domain:state] transition(Done(rev1), BuildEdited(rev2))
  │  => TransitionResult(state=Idle, effects=[SaveDraft(rev2)], rejection=null)
  ▼
[feature] 执行 effects
  ├─ SaveDraft -> DraftStorePort -> [data] RoomDraftStore
  └─ 更新 StateFlow<WorkspaceUiState>
  ▼
[ui] 重组：banner=diagnostics_stale_banner，currentDiagnosis=null
```

注意三件事：

1. 状态机**没有**调用数据库——它只产出 `SaveDraft`；
2. UI **没有**算任何东西——它只读 state；
3. 引擎**没有**被调用——因为用户没点"检查"。

## 5.8 十条禁止（都是"看起来无害"的真实事故）

| # | 禁止 | 真实后果 | 检测 |
|---|---|---|---|
| 1 | ViewModel 里写 `if (gpu.length > case.max)` | 规则表外的第二套真相源 | 评审 + 边界检查（feature 不得 import domain:rules） |
| 2 | UI 里 `collectAsState` 后自己 filter 诊断 | 展示与状态不一致 | semantics 测试断言 state 映射 |
| 3 | ```Repository` 返回 Room `Entity` | Room 注解渗入 domain | 边界检查（Room 类型不在 contracts） |
| 4 | domain 里 `System.currentTimeMillis()` | 测试不可复现 | 边界检查（禁 `System.`）+ 评审 |
| 5 | 给 `BuildRevision` 加 extension `fun BuildRevision.hasGpu()` 放在 ui 模块 | 业务语义散落 | 评审清单 |
| 6 | 两个 feature 共享一个 `object AppState` | 状态寿命混乱 | 边界检查（禁顶层 object） |
| 7 | 一个 `AppRepository` 接口塞 20 个方法 | 任何人改任何事都要动它 | 评审：端口 > 8 个方法即拆 |
| 8 | UI 直接 `context.getSharedPreferences` | 绕过端口，测试无法注入 | 边界检查（ui 禁 android.content） |
| 9 | feature 里 `GlobalScope.launch` | 泄漏 + 不可测 | 静态扫描 `GlobalScope` |
| 10 | 在 `contracts` 里放工具函数 | 契约膨胀，变成"什么都往里塞" | 评审：contracts 只允许数据 + 接口 + 纯算术 |

## 5.9 怎么验证独立性（四层，全部自动化）

### 层 1：Gradle 结构

模块拆开本身就是最强的隔离。`:domain:rules` 的 classpath 里没有 Android，写不出 `Log.d`。

### 层 2：边界脚本

```bash
node ci/check-boundaries.mjs
```

读取 `ci/modules.json`（模块 → 允许的 project 依赖 + 禁止的 import 前缀），
扫描每个模块的 `*.kt` 与 `build.gradle.kts`，命中即 fail。
这是 INV-16 的执行者。

### 层 3：ArchUnit / Konsist（语义规则）

```kotlin
// domain 模块的测试里
@ArchTest
val `domain 不依赖 android` =
    noClasses().that().resideInAnyPackage("..domain..")
        .should().dependOnClassesThat().resideInAnyPackage("android..", "androidx..")

@ArchTest
val `state 模块无 I-O` =
    noClasses().that().resideInAnyPackage("..state..")
        .should().accessClassesThat().resideInAnyPackage("java.io..", "java.nio..")
```

```kotlin
// Konsist（对 Kotlin 更顺手）
Konsist.scopeFromModule("feature")
    .classes()
    .assertFalse { it.hasImport("pcbuildmate.domain.rules.") }
```

### 层 4：单模块测试

```bash
./gradlew :domain:rules:test :domain:state:test :domain:replacement:test
```

这条命令**不需要 Android SDK、不需要模拟器、5 秒内跑完**。
如果它跑不动，说明有人把 Android 依赖塞进了 domain——这比任何评审都灵敏。

## 5.10 变更影响分析（改之前先看这张表）

| 你要改的东西 | 会波及 | 不会波及 | 必须同步更新 |
|---|---|---|---|
| 规则表 JSON | D3、fixture、批次报告 | 状态机、UI 布局 | §3.6 表 + 4 条 fixture |
| `RuleOutcome` | 全部下游 | 数据层 schema | 契约 PR + ADR + 全量 fixture |
| 状态机转移 | feature、UI 文案映射 | 规则引擎 | 04 章表 + 参数化用例 |
| 目录字段 | data seed、rules、fixture | 状态机 | §3.3 路径表 |
| UI 文案 | ui、资源文件 | domain | TextKey + 双语资源 |

**经验法则**：改动越"靠内"（contracts > domain > feature > ui），波及越大。
所以契约变更必须单独 PR——不是为了流程好看，是为了让 blast radius 可见。

## 5.11 务实取舍：为什么不是更多模块

14 个模块对一个课程项目已经偏多。为什么不合并？

- 合并 `domain:rules` 与 `domain:replacement` → 替换建议就能直接调用引擎内部方法，
  "先过滤再排序"的语义就无法在类型层面被保护。
- 合并 `feature` 与 `ui` → Compose 里出现 ViewModel 调用，semantics 测试必须启动完整依赖图。

为什么不拆更细（每个 feature 一个模块）？

- 课程周期内，模块数 > 人数 × 2 时，构建与同步成本会超过收益。
- 拆分可以延后：因为边界已经是**端口 + contracts**，将来把 `:feature` 拆成 3 个模块是纯机械操作。

**结论**：按"是否共用同一份状态"划模块，而不是按"屏幕上长什么样"划。

## 5.12 常见问题

**Q：domain 不能读时钟，那"诊断时间"谁给？**
A：`ClockPort.nowIso()` 由 feature 调用后放进 `Diagnosis.computedAt`；
或者诊断的 `computedAt` 由引擎从入参 `ClockPort` 拿——**但引擎必须把它当参数**，不能自己 `System.currentTimeMillis()`。
两种都可以，二选一并在 06 章 D3 写明。

**Q：feature 需要知道"当前冲突是哪条"，这算不算依赖规则？**
A：不算。它只读 `Diagnosis.outcomes` 里 `RuleOutcome.Conflict` 的 `ruleId` / `conflictId` 字符串，
这是 contracts 类型，不含任何规则语义。

**Q：如果两个模块真的要共享工具函数怎么办？**
A：先问它是不是"跨边界的数据操作"。是 → 进 contracts（纯函数，有测试）；
不是 → 复制 5 行代码，比建一个 `:shared` 模块便宜。

**Q：LLM 老是往 feature 里塞 domain 逻辑，怎么防？**
A：三道：编译（策略引擎在 feature 的 classpath 上不存在）→ 脚本（import 检查）→ 评审清单。
其中第一道最有效：看不见就写不出来。
