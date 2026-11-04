# PC Buildmate · 设计指南（单文件版）

> 版本 v1.0  ·  由 tools/build-single-md.mjs 从 docs/ 生成，请勿手改本文件。
> 源文件：README.md + docs/00..13 + docs/11-adr/*

## 目录

  - README
  - 00 概览
  - 01 术语
  - 02 契约语义
  - 03 规则表
  - 04 状态机
  - 05 模块独立性
  - 06 模块契约卡
  - 07 单元验证
  - 08 Oracle 与归因
  - 09 范围与 CI
  - 10 LLM 交付
  - 11 ADR 索引
  - ADR-0001
  - ADR-0002
  - ADR-0003
  - ADR-0004
  - ADR-0005
  - ADR-0006
  - 12 Prompt 模板
  - 13 风险与口径
  - 14 分工与里程碑


---

# PC Buildmate — 设计指南（LLM 可执行契约集）

> 版本 `v1.0`  ·  目录版本 `static-v1`  ·  规则版本 `rules-v1`  ·  文档状态见 `docs/VERSION`
>
> 本仓库不是产品说明书，而是**一组可执行契约 + 自动验收协议 + 变更控制规则**。
> 人类读它来对齐，LLM 读它来实现；两边都不能靠「理解精神」自由发挥。

---

## 1. 一句话

把一个「预算 + 用途」变成「一份花过钱、检查过、可比过的装机单」，
并且**每一条结论都带着它的证据**：规格字段有证据状态，检查结果继承最弱证据，
缺字段只能返回「未验证」，永远不能靠没人确认的字段宣布「兼容」。

## 2. 为什么要有这份文档

团队会用 LLM 写绝大部分代码。LLM 的失败模式不是「写不出代码」，而是：

1. **重新定义语义**——把「未验证」写成「通过」，把「来源冲突」降级成数值比较；
2. **跨越模块边界**——在 ViewModel 里算兼容性，在 UI 里读 DAO，在 domain 里 import Android；
3. **改契约而不说**——为了让自己编译通过，顺手改了字段名或可空性；
4. **用测试自证**——写一个断言自己实现的测试，绿了就算完成。

这份文档用四件东西把上面四条堵死：**冻结契约**（02 章 + `contracts/`）、
**单向依赖矩阵**（05 章）、**矩阵化单元验证**（07 章）、**CI 硬阻塞**（09 章）。

## 3. 目录地图

| 文件 | 内容 | 谁先读 |
|---|---|---|
| [docs/00-overview.md](docs/00-overview.md) | 定位、范围、六功能、五条设计公理、成功判据 | 所有人 |
| [docs/01-glossary.md](docs/01-glossary.md) | 术语与三态/三结果语义（**歧义出现时以本文为准**） | 所有人 |
| [docs/02-contracts.md](docs/02-contracts.md) | 契约语义、不变量、冻结规则、变更流程 | LLM 必读 |
| [docs/03-rules.md](docs/03-rules.md) | 规则 DSL、15 条规则明细、fixture 规范、conflictId | LLM 必读 |
| [docs/04-state-machines.md](docs/04-state-machines.md) | 诊断/替换两张状态机全转移表 + 导航 guard | LLM 必读 |
| [docs/05-module-independence.md](docs/05-module-independence.md) | **模块独立性**：分层、端口、依赖矩阵、隔离机制 | 架构负责人必读 |
| [docs/06-modules.md](docs/06-modules.md) | 六模块契约卡（职责/接口/禁止/失败/验收） | 分工后各读自己那张 |
| [docs/07-unit-verification.md](docs/07-unit-verification.md) | **单元验证设计**：单元定义、替身、矩阵、属性、变异、DoD | 测试负责人 + LLM |
| [docs/08-testing-and-triage.md](docs/08-testing-and-triage.md) | oracle 分层、失败归因五类、批次报告与人工批准 | 测试负责人 |
| [docs/09-ci-and-scope.md](docs/09-ci-and-scope.md) | 范围外清单、CI 三层检查、白名单、解除流程 | 全员 |
| [docs/10-llm-delivery.md](docs/10-llm-delivery.md) | 生成顺序、prompt 打包、闸门、评审清单、漂移检测 | 全员 |
| [docs/11-adr/](docs/11-adr/) | 6 条架构决策记录（为什么这么做，什么时候该推翻） | 答辩前 |
| [docs/12-prompt-templates.md](docs/12-prompt-templates.md) | 7 份可直接复制的模块 prompt 模板 | 开工第一天 |
| [docs/13-risks.md](docs/13-risks.md) | 已知风险、TBD、答辩口径（哪些话不能说） | 答辩前 |
| [docs/14-team-and-milestones.md](docs/14-team-and-milestones.md) | 角色与模块、四个里程碑、交接协议、砍功能顺序 | 组长 |
| [contracts/](contracts/) | Kotlin 契约源码（**只读区**） | 只读 |
| [contracts-test/](contracts-test/) | 端口契约测试基类（假实现与真实实现必须同过） | 测试负责人 |
| [assets/rules/rules-v1.json](assets/rules/rules-v1.json) | 规则表数据 |
| [assets/fixtures/](assets/fixtures/) | 60 条单规则 fixture + 8 条答辩锚点 fixture |
| [ci/](ci/) | 边界检查、关键词检查、模块矩阵 |
| [tools/check-docs.mjs](tools/check-docs.mjs) | 文档自检（链接 / ID / 矩阵完整性 / JSON 合法性） |

## 4. 三种阅读路径

**A. 人类新成员（约 60 分钟）**
`00 → 01 → 05 → 06 → 07`。读完应当能回答：为什么不能缺字段 PASS；为什么 domain 不能 import Android；
为什么测试矩阵空格子要 CI fail。

**B. LLM 开工（每个模块一次，约 1 个上下文窗口）**
[docs/12-prompt-templates.md](docs/12-prompt-templates.md) 里对应模板 → 模板会要求**只**加载：本模块卡（06）
+ 相关契约（02 / `contracts/`）+ 相关状态机（04）+ 相关规则与 fixture（03）+ 本模块验收（07）。
**不要喂全文档**，见 10 章 §10.3。

**C. 答辩前复核（约 30 分钟）**
`00 §0.5 范围` → `09 §9.1 范围外` → `13 风险与口径`。三处结论必须一致；
不一致就改文档，不要在台上临场解释。

## 5. 治理三规则（只有三条，别再发明）

| # | 规则 | 谁批 | CI 行为 |
|---|---|---|---|
| G1 | 契约变更走 git，默认批准自动合并；必须留 diff + 影响模块 + ADR | 无人（记录即可） | 记录并在批次报告列出，不阻塞 |
| G2 | 一个测试批次（milestone / release）的最终「通过」由人点批准；人不逐条看失败 | 人 | 只出报告，不代替人下结论 |
| G3 | 范围外功能是硬阻塞；解除必须提交 `scope-change` 文档并由人批准 | 人 | fail-closed，无白名单不放行 |

## 6. 自检

```bash
node tools/check-docs.mjs      # 文档链接 / ID 引用 / 矩阵完整性 / JSON 合法性
node ci/check-boundaries.mjs   # 模块依赖边界（读取 ci/modules.json 与源码 import）
node ci/check-keywords.mjs     # 范围外关键词与权限
node tools/check-matrix.mjs    # 验证矩阵完整性（规则 60 / 状态机 76 / 不变量 18）
```

四个脚本都是**零依赖 Node**，不需要 Android SDK、不需要网络。
任何一条不通过，视为文档/代码未完成，而不是「稍后修」。

## 7. 版本与冻结

- 文档版本见 `docs/VERSION`；契约版本由 `contracts/` 的 git 提交 + API dump 决定。
- 每个构建、每个诊断、每份测试报告都必须记录三元组：`catalogueVersion / rulesVersion / commit`。
- 失败报告额外记录：`seed / promptHash / modelVersion`（见 08 章 §8.5）。
- **契约只允许经 ADR + 单独提交变更**；实现 PR 里顺手改 `contracts/` 一律打回（见 02 章 §2.9）。


---

# 00 · 概览：范围、公理与判据

> 读者：所有人。本文只回答四个问题——做什么、不做什么、凭什么做对、怎么算做完。

---

## 0.1 文档定位

这是一份**规格文档（specification）**，不是方案文档。区别很实际：

| | 方案文档 | 规格文档（本文） |
|---|---|---|
| 判定标准 | 听起来合理 | 可被测试证伪 |
| 语义归属 | 作者解释 | 文档 + fixture 决定 |
| 冲突处理 | 讨论 | 查术语表 / 提 ADR |
| LLM 用法 | 参考 | 输入契约 |

因此全文用固定句式：**必须（MUST）/ 禁止（MUST NOT）/ 应当（SHOULD）/ 可以（MAY）**。
出现「最好」「尽量」「一般来说」的地方，视为文档缺陷，应提 issue 改成可判定表述。

## 0.2 产品一句话

PC Buildmate 是一个 **Android 原生、中文界面、离线可用** 的装机决策工具：
输入预算与用途（或已有零件），得到一份**带价、已检查、可比较**的配置单；
检查失败时不是报错，而是给出**已排序、带差价、可预览后确认**的替换方案。

它不做的事同样重要：不预测帧数、不做 3D 装配、不接实时库存、不做账号。
边界见 §0.5，硬阻塞见第 09 章。

## 0.3 用户与场景（首版只对两类人负责）

| 用户 | 场景 | 首版责任 |
|---|---|---|
| 首次装机者 | 预算 7000–10000 元，知道用途，不知道选什么 | **主要目标**：从预算走到一份自洽配置，并看懂每个冲突 |
| 预算敏感升级者 | 手里有整机，只想换一张显卡或加内存 | **主要目标**：把「新件 vs 旧件」的约束查清楚 |
| 发烧友 | 反复比较多个方案 | 次要：能用比较页，但不为其做性能分档 |
| 小店 / 装机商 | 复用配置、快速报价 | 次要：能保存与分享配置单即可 |

验收任务只针对前两类人设计。为后两类人增加的功能，属于范围变更（09 章 §9.5）。

## 0.4 六功能与它们的依赖顺序

六个功能不是六个页面，而是**一条环路上六个必须同时成立的环节**：

```
        预算/用途 ──┐
                    ├──> D4 预算推荐 ──┐
        直接选件 ───┘                   │
                                        v
   D1 硬件数据库 ──> D2 工作区（revision）──> D3 兼容性检查
                          ^                        │
                          │                        v
                          └──── D5 替换建议 <── 冲突
                                        │
                                        v
                                  D6 构建比较
```

**实现顺序必须与依赖顺序一致**（10 章 §10.2）：

```
D1 数据 → D3 规则 → D5 替换 → D2 工作区 → D4 推荐 → D6 比较 → D7 UI 绑定
```

理由：D4 要「先过滤硬约束再排序」，所以必须等 D3 与 D5 的过滤语义冻结；D2 是编排层，
要等状态机能独立测试后再接；D6 只读已有诊断，最后做。

## 0.5 首版范围（冻结）

### 做

- 静态目录（8 类 × 3–8 件，demo 数据，`isDemo=true` 与生产 seed 物理隔离）
- 六功能端到端：预算 → 编辑 → 检查 → 替换 → 应用 → 比较
- 三态证据模型（VERIFIED / REPORTED / EMPTY）与三结果（PASSED / CONFLICT / UNVERIFIED）
- 两张状态机（诊断、替换）+ 四条导航 guard
- CNY 参考价，**带日期**，可手动「重新载入内置参考价」
- MVI 单向下行数据流；domain 纯 JVM；中文 UI；完全离线

### 不做（硬阻塞，见 09 章）

- 性能预测：FPS、跑分、benchmark、CPU/GPU 瓶颈评分、performance band
- 3D 装配、AR 预览、模型渲染
- 实时库存 / 实时价格 / 电商 API / 爬虫 / 后台刷新 / 价格轮询
- 二手价、账号系统、云同步、社交
- 相机、条码扫描、推送、定位
- 目录版本对比、历史价格曲线

**范围陷阱**：提案原文里 D6 比较页出现过「an indicative performance band」。
本设计**明确裁掉**它（ADR-0002），因为它是性能预测，与公理 A1「缺证据不得断言」直接冲突。
答辩口径见 13 章 §13.4。

## 0.6 五条设计公理

后面所有章节都是这五条的推论。**任何实现若与公理冲突，是实现错，不是公理错。**

### A1 证据优先（Evidence before assertion）

一个值有三种证据状态；一条规则有三种结果。**最弱证据向上传播**。
缺字段、来源冲突、无法评估，一律 `UNVERIFIED`，**永远不得** `PASSED`。

> 反例（必须被 CI 杀死）：`biosGeneration` 为 `EMPTY` 时返回 `Passed`。

### A2 版本保护（Revision guards every claim）

一份配置的每次变化都产生**新 revision**；诊断、建议、预览、应用**都必须绑定 revision**。
旧结果不得覆盖新结果；过期 base 不得被应用。

> 反例：用户在诊断运行时改了 GPU，旧诊断返回后直接展示——它描述的是另一份配置。

### A3 确定性（Determinism over cleverness）

同输入、同目录版本、同规则版本 → **完全相同的输出（含顺序）**。
domain 层不得读时钟、不得用随机数、不得读系统语言/时区；时间与 ID 由端口注入（03 章 §3.2）。

> 理由：不确定的实现无法被单元测试固定，也无法复现线上问题。

### A4 模块独立（Independence by construction）

模块之间的耦合只能通过 `contracts` 中的**数据类型与端口接口**发生。
domain 不依赖 Android；feature 不依赖 Room；UI 不依赖 domain 实现。
独立性不靠自觉，靠**模块类型 + 依赖矩阵 + CI 检查**（05 章）。

### A5 范围即代码（Scope is executable）

「不做」必须能被机器检查，否则等于「说了但没做」。
范围外功能 = 关键词 + 权限 + 依赖坐标三层扫描，命中即 fail（09 章）。

## 0.7 用户旅程与状态映射

| 旅程阶段 | 屏幕 | 主要状态 | 状态机 |
|---|---|---|---|
| Plan | Entry（预算/用途） | `RecommendationResult` | 无（纯函数） |
| Customize | Workspace | `BuildRevision` | 无（每次编辑 = 新 revision） |
| Check | Diagnostics | `DiagnosisState` | 04 章 C.1 |
| Resolve | Diagnostics 内嵌面板 | `ReplacementState` | 04 章 C.2 |
| Compare | Compare | `ComparisonResult` | 无（只读） |

**注意**：Workspace 不是状态机。它的状态由 `BuildRevision` 完全决定，
「当前诊断是否过期」是**派生属性**（`diagnosis.revisionId != currentRevisionId`），不是一个状态。
把它做成状态是首版最常见的建模错误（04 章 §4.1）。

## 0.8 交付物形态

```
app/                      Android 应用（只做装配与导航）
contracts/                纯 Kotlin 模型 + 端口 + UI 契约（只读区）
domain/build/             纯 JVM：revision、总价、diff、草稿
domain/rules/             纯 JVM：规则引擎
domain/replacement/       纯 JVM：候选过滤与排序
domain/recommend/         纯 JVM：预算分配
domain/compare/           纯 JVM：比较（只读诊断）
domain/state/             纯 JVM：两张状态机转移函数
data/                     Room + assets 适配器（实现 contracts 端口）
feature/                  ViewModel 编排（Intent → Transition → Effect）
ui/                       Compose，只读 UiState，只发 Intent
testing/                  fixture、fake、oracle 工具（生产代码禁止依赖）
```

详见 05 章。**每个模块都必须能单独 `:module:test` 通过**；domain / contracts / testing 甚至不需要 Android SDK。

## 0.9 成功判据（分两层，不许混）

### 技术正确性（机器可判）

1. 15 条规则 × 4 类 fixture 全绿；矩阵无空格（07 章 §7.5）。
2. 18 条不变量（INV-01…INV-18）每条至少一个测试覆盖。
3. 12 条关键 mutation 全部被杀死（07 章 §7.7）。
4. 两张状态机全转移表覆盖；非法转移全部 `Rejected` 且状态不变。
5. 模块边界检查、范围关键词检查、API dump 检查全绿。

### 用户理解（人可判，样本很小）

1. 一位首次装机者能独立完成：预算 → 配置 → 看到冲突 → 选替换 → 应用 → 保存。
2. 被问「为什么这个显卡不行」时，能复述「337 mm 超过 320 mm」，而不是「红了」。
3. 被问「BIOS 那栏为什么是灰的」时，能说出「没有人确认过，所以不算通过」。

**不要**用「准确率」「完成度百分比」「用户数」当成功指标——没有数据支撑的百分比是答辩风险点（13 章）。

## 0.10 与前期草稿的对应关系

本仓库是把早期 A–F 六章草稿**重构为可交付规格**的结果。对应关系便于组内对照：

| 早期草稿 | 现在的位置 | 变化 |
|---|---|---|
| 0 粗略版（治理 + 骨架） | README §5 + 00 + 05 | 治理三规则保留；补充公理与独立性 |
| A 核心 schema | 02 + `contracts/` | 补 B.0 三个补丁；补端口 / UI 契约 / Effect |
| B 规则表 DSL | 03 + `assets/rules/` | 规则从表格落地为可校验 JSON；补 60 条 fixture 规格 |
| C 两张状态机 | 04 | **新增 Effect 与 Rejection**，把副作用移出转移函数 |
| D 六模块卡 | 06 | 补「单元验证入口」与「独立性约束」两栏 |
| E 测试 oracle | 07 + 08 | 拆成「单元验证设计」与「归因 / 批次」两章 |
| F 范围外 CI | 09 | 补可运行的检查脚本与模块矩阵 |

**新增的 05、07 两章是本版重点**，因为团队会用 LLM 大量生成代码：
05 保证 LLM 改不动别人的模块，07 保证 LLM 不能靠自证测试蒙混过关。


---

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


---

# 02 · 契约语义与不变量

> 读者：LLM 必读；评审必读。
> 源码在 [../contracts/](../contracts/)，本文解释**为什么长这样**与**怎样算违约**。
> 源码与本文冲突时：**源码是接口，本文是语义**；两者都要改，且必须一起提。

---

## 2.1 契约的三个层次

```
┌───────────────────────────── contracts/ ─────────────────────────────┐
│ L-A 数据契约   Build / BuildRevision / CatalogueRecord / FieldValue   │
│ L-B 端口契约   CataloguePort / CompatibilityEvaluator / ...           │
│ L-C UI 契约    WorkspaceIntent / WorkspaceUiState / Effect / TextKey  │
└──────────────────────────────────────────────────────────────────────┘
```

三层都是"跨边界的东西"，所以都放这里。**不在这里的，就不是契约**，任何模块都可以自由重构。

判断标准（一句话）：**如果两个模块都要认识它，它就在 contracts；只有一个模块认识它，它就在那个模块里。**

## 2.2 十八条不变量

不变量是**任何时刻都必须成立**的性质，与"功能是否实现"无关。
它们由独立的 `InvariantChecker` 校验（08 章 §8.2），**不得调用被测模块的内部方法**。

| ID | 不变量 | 违反归类 | 主要覆盖 |
|---|---|---|---|
| INV-01 | `totalCny == Σ(quantity × unitPriceCny)` | DOMAIN_BUG | 构造期 `require` + 属性测试 |
| INV-02 | 同一 `buildId` 下 revision id 严格递增 | DOMAIN_BUG | `domain:build` |
| INV-03 | 诊断只有 `revisionId == currentRevisionId` 才能作为当前结论展示 | UI_STATE_BUG | `currentDiagnosis` 访问器 |
| INV-04 | 规则输入含 `EMPTY` / 缺失 → 不得 `Passed` | DOMAIN_BUG | 15 条规则 × `missing` fixture |
| INV-05 | 规则输入含来源冲突 → 必须 `Unverified(SOURCE_CONFLICT)` | DOMAIN_BUG | `fx_source_conflict_*` |
| INV-06 | `weakest` = 输入字段最弱证据（`EMPTY > REPORTED > VERIFIED`） | DOMAIN_BUG | 属性测试 |
| INV-07 | Apply 前 `baseRevisionId == currentRevisionId`；Apply 后必须全量重检 | DOMAIN_BUG / UI_STATE_BUG | C.2 转移表 + `fx_apply_stale_guard` |
| INV-08 | 无候选不得改 build、不得超预算、不得放宽硬约束 | DOMAIN_BUG | `fx_no_candidate` |
| INV-09 | 非法事件 → `Rejected(reason)` 且状态不变 | UI_STATE_BUG | 状态机全矩阵 |
| INV-10 | Undo 生成**新** revision（id 递增），内容等于历史快照 | DOMAIN_BUG | `fx_undo_revision_monotonic` |
| INV-11 | `isDemo=true` 记录不得进入生产 seed | FIXTURE_ERROR | `fx_demo_isolation` |
| INV-12 | 比较页过期诊断不得显示为当前 `PASSED` | UI_STATE_BUG | `fx_compare_stale` |
| INV-13 | 推荐可行时 `total ≤ budget`；不可行时 `feasible=false` 且 `shortfall > 0` | DOMAIN_BUG | `fx_recommend_infeasible` |
| INV-14 | 候选先过硬约束、再排序；排序键固定 | DOMAIN_BUG | `fx_suggestion_*` |
| INV-15 | 同输入、同目录版本、同规则版本 → 输出（含顺序）完全相同 | DOMAIN_BUG | 双跑相等断言 |
| INV-16 | 模块依赖只出现在允许矩阵内 | 架构违规（CI） | `ci/check-boundaries.mjs` |
| INV-17 | 每个端口实现（含 Fake）通过同一份契约测试 | FIXTURE_ERROR | `contracts-test/` |
| INV-18 | domain 层无 I/O 副作用（不读时钟、不读写、不发网络） | 架构违规（CI + 单测） | `ci/check-boundaries.mjs` + Effect 断言 |

**INV-16/17/18 是本次新增**：它们把「模块独立」从口头约定变成可失败的检查（05 章）。

## 2.3 三层状态的判定规则（冻结）

### 2.3.1 单条规则：五步判定，先命中先生效

```
1. 部件缺失 / 类别不匹配            -> Unverified(MISSING_FIELD, missingField=null)
2. 任一输入字段 conflict == true     -> Unverified(SOURCE_CONFLICT, missingField=该字段)
3. 任一输入字段 value == null 或 EMPTY -> Unverified(MISSING_FIELD, missingField=第一个缺失字段)
4. 比较通过                          -> Passed(weakest)
5. 比较失败                          -> Conflict(..., weakest, severity, conflictId)
```

顺序不可交换。**第 2 步在第 3 步之前**，因为"来源冲突"比"字段缺失"信息量更大：
用户需要知道"两个来源打架"，而不是"某个字段是空的"。

### 2.3.2 总体状态：四步

| 序号 | 条件 | `overall` |
|---|---|---|
| 1 | 存在 `BLOCKER` 的 Conflict | `CONFLICT` |
| 2 | 存在任意 Conflict **且** 存在任意 Unverified | `MIXED` |
| 3 | 存在任意 Conflict（无 Unverified） | `CONFLICT` |
| 4 | 存在任意 Unverified（无 Conflict） | `UNVERIFIED` |
| 5 | 无 Conflict 且无 Unverified | `PASSED` |

> 早期草稿里"有 MAJOR Conflict 就 CONFLICT"与"混合冲突与未验证 → MIXED"互相覆盖，
> 会产生两种都说得通的实现。本版**只保留上表**：先看 BLOCKER，再看混合，再看单一类型。
> 这样任意输入只有一个答案，UI 才能写死映射。

**防御性约定**：`outcomes.isEmpty()` 时 `overall = UNVERIFIED`（正常路径不会走到：
空配置在导航 guard 就被拦下）。

### 2.3.3 风险等级

| 条件 | `riskLevel` |
|---|---|
| 有 BLOCKER | `HIGH` |
| 有 MAJOR（无 BLOCKER） | `MEDIUM` |
| 只有 MINOR 或 Unverified | `LOW` |
| 没有诊断 | `UNKNOWN` |

### 2.3.4 分数（只给眼睛看）

```
score = clamp(100 - 30*BLOCKER - 15*MAJOR - 5*MINOR - 3*UNVERIFIED, 0, 100)
```

**禁止**：用 score 排序候选、决定推荐、决定是否允许应用。理由见 01 章 §1.2。

## 2.4 null、EMPTY 与「缺失」的区别

三种"没有值"在语义上不同，实现上都要落到 `Unverified(MISSING_FIELD)`，但 `missingField` 不同：

| 情况 | 表示 | `missingField` |
|---|---|---|
| `attributes` 里没有这个 key | 字段未采集 | 该 `FieldRef` |
| 有 key 但 `value == null`（必须 `state == EMPTY`） | 有人查过，没查到 | 该 `FieldRef` |
| 部件本身不存在 | 记录缺失 | `null` |

UI 据此显示不同提示：前两者是"这条规格需要确认"，后者是"这件零件不在目录里"。
**禁止**把三者合并成一个空字符串或 0。

## 2.5 端口契约与 Fake 的一致性

端口是模块之间唯一的运行期耦合点。因此：

1. 每个端口在 [../contracts-test/](../contracts-test/) 有一份抽象契约测试；
2. **真实实现（Room / assets）与测试 Fake 都必须继承同一份并全绿**（INV-17）；
3. 契约测试只断言"行为"，不断言实现细节（不数调用次数、不检查 SQL）。

> 为什么必须这样：LLM 写测试时会自然地用 Fake。如果 Fake 与真实实现行为不同，
> 测试全绿而真机崩溃——这是最容易漏、也最难在答辩时解释的一类 bug。

**端口设计禁令**：

- 端口方法不得返回 `Result` 之外的可变对象；不得返回 Android/Room 类型。
- 端口不得暴露 `suspend` 之外的隐式线程语义；实现必须自己保证线程安全。
- 端口不得回调调用方（禁止双向依赖）。
- 一个端口只服务一个模块。禁止"大而全的 `Repository` 上帝接口"（05 章 §5.6 反例 7）。

## 2.6 把不变量编码进类型：`currentDiagnosis`

`WorkspaceUiState` 里同时有 `diagnosis` 与 `revision`。如果让 UI 直接读 `diagnosis`，
迟早会有人忘了比对 revision。因此契约提供两个**安全访问器**：

```kotlin
val currentDiagnosis: Diagnosis?
    get() = diagnosis?.takeIf { it.revisionId == revision?.id }

val isDiagnosisStale: Boolean
    get() = diagnosis != null && diagnosis.revisionId != revision?.id
```

规则：

- UI **只能**读 `currentDiagnosis`；读原始 `diagnosis` 必须在评审中给出理由（目前唯一允许的场景：
  显示"旧结果已过期"的横幅，且只能读 `isDiagnosisStale`）。
- 任何模块不得往 `WorkspaceUiState` 里再加一个 `isStale` 布尔——那是重复真相源。

同样的模式用在比较页：`ComparisonResult.diagnoses` 允许为 `null`，非空即代表"匹配当前 revision"。

## 2.7 字段路径：契约的第三个维度

字段路径（如 `gpu.lengthMm`）是**字符串契约**，编译器管不了，所以用三层保护：

1. 冻结表（03 章 §3.3）——唯一权威；
2. `rules-v1.json` 启动校验——路径不在表内即 fail-fast；
3. fixture 校验——期望里的 `left` / `right` 路径必须同时出现在规则与表内。

**新增路径**必须：改 §3.3 表 → 改 `rules-v1.json` → 加 fixture → 提契约变更。四步缺一不可。

## 2.8 编译期已经挡住的错误（别再用运行期检查重复一遍）

| 错误 | 挡住的位置 |
|---|---|
| 总价与条目不符 | `BuildRevision.init` |
| 数量 0 / 负价 | `BuildItem.init` |
| `value != null` 却是 `EMPTY` | `FieldValue.init` |
| `conflict = true` 但只有一个来源 | `FieldValue.init` |
| revision id = 0 | `BuildRevision.init` |

**评审口味**：能用 `require` 表达的约束，不要写成"调用方应当检查"。
类型和构造器是最便宜的测试。

## 2.9 契约变更流程（治理规则 G1 的具体化）

### 允许的变更（不需要人批，但必须留痕）

| 类型 | 例子 | 额外要求 |
|---|---|---|
| 追加枚举值 | 新增 `UnverifiedReason.LICENSE_RESTRICTED` | 全量 `when` 必须穷尽；CI 编译即发现 |
| 追加带默认值的字段 | `Candidate(val reason: String = "")` | 旧 fixture 不得受影响 |
| 追加端口方法 | `CataloguePort.countByCategory` | 所有实现同步；契约测试补一条 |
| 追加 `TextKey` | 新文案 | 必须同时在中英文资源里存在 |

### 禁止的变更（必须走 scope-change 或 ADR）

| 类型 | 为什么 |
|---|---|
| 改字段名 / 改可空性 | 破坏所有实现与 fixture，等价于重写 |
| 改单位（元 → 分） | 静默的 100 倍错误 |
| 把 `Conflict` 合并进 `Unverified` | 抹掉"两边数值都要展示"的能力 |
| 给 `RuleOutcome` 加第四种结果 | 三态是所有测试矩阵的维度，加一维等于重做验收 |
| 把 UI 状态移进 domain | 违反 05 章依赖矩阵 |

### 提交规范

```
contracts/…          <- 单独一个 commit，message 前缀 "contract:"
docs/02-…            <- 同一 commit 或紧随其后的 commit
docs/11-adr/00xx-…   <- 若属于"禁止的变更"，必须有 ADR
fixtures/…           <- 若影响语义，同一 PR
```

**实现 PR 里出现 `contracts/` 的 diff → 直接打回**（评审清单第 1 条，10 章 §10.6）。
这不是不信任，而是为了让"语义变了"这件事无法被混在一堆格式改动里。

### 影响面速查（改哪里要动谁）

| 改动 | 受影响模块 | 必须补的测试 |
|---|---|---|
| `RuleOutcome` 结构 | domain:rules、domain:replacement、feature、ui、全部 fixture | 规则 ×4、替换、UI 状态快照 |
| `WorkspaceUiState` | feature、ui | ViewModel 状态序列 + semantics |
| `CataloguePort` | data、testing/Fake、domain:rules | 契约测试 + 规则回归 |
| `TextKey` | ui、资源文件 | semantics key 断言 |
| 字段路径表 | data seed、rules JSON、fixtures、domain:rules | 该规则的 4 类 fixture |

## 2.10 契约漂移的六个真实场景（LLM 一定会犯）

| # | 现象 | 检测手段 |
|---|---|---|
| 1 | 为了让编译过，把 `Conflict.conflictId` 改成可空 | API dump diff + `git log contracts/` |
| 2 | 在 domain 里 `import android.util.Log` | `ci/check-boundaries.mjs` |
| 3 | 把 `REPORTED` 当 `VERIFIED` 用（!= EMPTY 就认为可信） | MUT-04 + `fx_*_reported` 用例 |
| 4 | 新增一个 `isCompatible: Boolean` 到 `Diagnosis` | 契约评审 + "三态不得压成布尔"检查条目 |
| 5 | 在 ViewModel 里内联一条兼容性 if | 边界检查（feature 不得 import domain:rules 实现类）+ 代码评审 |
| 6 | 给 `TextKey` 加 key 但不加资源 | 编译期：`TextKey` → 资源映射用 `when` 穷尽 + 资源存在性测试 |

## 2.11 自检清单（每个 PR 贴一次）

- [ ] 没有改 `contracts/`（或：改了，且是单独 commit + 本条说明 + 影响模块列表）
- [ ] 新增字段都有默认值，旧 fixture 未修改仍通过
- [ ] 新增枚举值后所有 `when` 仍穷尽（编译通过）
- [ ] 新增 `TextKey` 在 `strings.xml` 与 `values-en/strings.xml` 都存在
- [ ] 新增字段路径已进 §3.3 表、rules JSON、fixture 三处
- [ ] `:contracts:apiCheck` 通过


---

# 03 · 规则表：DSL、字段路径、15 条规则与 fixture

> 读者：LLM（实现 D3 / D5 前必读）、测试负责人。
> 数据源：[../assets/rules/rules-v1.json](../assets/rules/rules-v1.json)（生成物，勿手改）。
> 生成器：[../tools/gen-fixtures.mjs](../tools/gen-fixtures.mjs)。
> 规则是**数据**，不是代码分支：新增硬件代际 = 改数据 + 加 fixture，不改 UI（提案的核心承诺）。

---

## 3.1 为什么规则必须是数据

如果规则写成代码：

```kotlin
if (cpu.socket != mb.socket) return Conflict(...)
if (mb.biosGeneration < cpu.minBiosGeneration) return Conflict(...)
```

那么每一次新增检查都要改 domain、改测试、改 UI 提示，而且 LLM 每次都会写出略微不同的文案与判定顺序。
写成表格后：

| 变化 | 需要改什么 | 不需要改什么 |
|---|---|---|
| 新增一条检查 | rules JSON + 4 条 fixture | 引擎、状态机、UI |
| 新增一代硬件（DDR6） | 目录数据 + 规则参数 | 引擎 |
| 调整严重度 | rules JSON 一行 | 其它全部 |

**代价**：规则表达力受限于 DSL 的 7 种 kind。这是刻意的——表达力越强，LLM 越容易写出「只有它自己懂」的规则。

## 3.2 DSL schema

```json
{
  "ruleId": "GPU_CASE_LENGTH",
  "kind": "NUMERIC_LE",
  "left":  { "category": "GPU",  "path": "lengthMm" },
  "right": { "category": "CASE", "path": "maxGpuLengthMm" },
  "severity": "BLOCKER",
  "aggregation": "SINGLE",
  "formula": null,
  "fixtures": ["fx_gpu_case_length_pass", "..."]
}
```

| 字段 | 取值 | 语义 |
|---|---|---|
| `ruleId` | 大写蛇形，全局唯一 | 结果的稳定标识；测试 tag、文案 key、批次报告都用它 |
| `kind` | 见下表 7 种 | 比较方式 |
| `left` / `right` | `category + path` | 操作数；`path` 必须在 §3.3 表内 |
| `severity` | `BLOCKER / MAJOR / MINOR` | 只影响 overall 与风险，不改变是否 CONFLICT |
| `aggregation` | `SINGLE / PER_ITEM / COUNT / SUM` | 多件同类部件时如何聚合 |
| `formula` | 字符串或 null | 只用于 PSU 负载这类「先算中间量再比较」的规则 |
| `fixtures` | fixtureId 列表 | CI 用来验证四类齐全 |

### 7 种 kind（冻结，不增不减）

| kind | 语义 | 左/右约定 | 例 |
|---|---|---|---|
| `EQUALS` | `left == right`（字符串精确，先 trim） | 两个单值 | CPU socket / MB socket |
| `CSV_CONTAINS` | `right ∈ split(left)` | **左是集合**，右是单值 | `mb.chipset ∈ cpu.supportedChipsets` |
| `CSV_SUBSET` | `set(right) ⊆ set(left)` | **左是集合**，右是集合 | `gpu.requiredPsuConnectors ⊆ psu.connectors` |
| `NUMERIC_LE` | `left <= right` | 左是被约束量 | `gpu.lengthMm <= case.maxGpuLengthMm` |
| `NUMERIC_GE` | `left >= right` | 左是被约束量 | `psu.continuousW >= requiredW` |
| `COUNT_LE` | `Σ(qty × left) <= right` | 左是每件计数 | 内存条数 / 插槽 |
| `SUM_LE` | `Σ(qty × left) <= right` | 左是每件容量 | 内存总容量 |

`COUNT_LE` 与 `SUM_LE` 的公式相同；分开是为了让测试与文案能区分「数量超了」和「容量超了」。

## 3.3 字段路径冻结表

**唯一权威**。规则、fixture、seed 校验都引用这张表。新增路径必须走契约变更（02 章 §2.9）。

| 类别 | 路径 | 类型 | 单位/取值 |
|---|---|---|---|
| CPU | `socket` | string | `AM5 / AM4 / LGA1700 / LGA1851` |
| CPU | `supportedChipsets` | csv | 大写 token，如 `B650,X670` |
| CPU | `tdpW` | number | W |
| CPU | `minBiosGeneration` | version | `主.次`，如 `1.0` |
| MOTHERBOARD | `socket` | string | 同上 |
| MOTHERBOARD | `chipset` | string | 大写 token |
| MOTHERBOARD | `biosGeneration` | version | `主.次` |
| MOTHERBOARD | `memoryGeneration` | string | `DDR4 / DDR5` |
| MOTHERBOARD | `memorySlots` | number | 个 |
| MOTHERBOARD | `maxMemoryGb` | number | GB |
| MOTHERBOARD | `formFactor` | string | `ATX / MICRO_ATX / MINI_ITX` |
| MOTHERBOARD | `m2Slots` | number | 个 |
| MOTHERBOARD | `sataPorts` | number | 个 |
| MOTHERBOARD | `storageInterfaces` | csv | `M2_NVME,SATA` |
| MEMORY | `generation` | string | `DDR4 / DDR5` |
| MEMORY | `moduleCapacityGb` | number | GB |
| MEMORY | `kitCapacityGb` | number | GB（整套） |
| MEMORY | `moduleCount` | number | 每套条数 |
| GPU | `lengthMm` | number | mm |
| GPU | `thicknessMm` | number | mm |
| GPU | `slotWidth` | number | 槽 |
| GPU | `tdpW` | number | W |
| GPU | `recommendedPsuW` | number | W（仅展示，不参与判定） |
| GPU | `requiredPsuConnectors` | csv | `12VHPWR,8PIN_X2` |
| PSU | `continuousW` | number | W（**连续**功率，非峰值） |
| PSU | `connectors` | csv | 同上 |
| CASE | `maxGpuLengthMm` | number | mm |
| CASE | `maxGpuThicknessMm` | number | mm |
| CASE | `maxGpuSlots` | number | 槽 |
| CASE | `maxCoolerHeightMm` | number | mm |
| CASE | `supportedFormFactors` | csv | 同上 |
| CASE | `driveBays` | number | 个 |
| COOLER | `heightMm` | number | mm |
| COOLER | `socketMounts` | csv | `AM5,AM4,LGA1700` |
| STORAGE | `interface` | string | `M2_NVME / SATA` |

### CSV 解析规范（冻结）

1. 按 `,` 分割 → 去首尾空白 → 丢弃空 token；
2. token 必须匹配 `^[A-Z0-9_]+$`，否则 seed 校验失败（fail-fast）；
3. 比较**区分大小写**（因为 token 已规范化）；
4. 空集合是合法的（表示「没有」），但此时 `CSV_CONTAINS` 必为 CONFLICT：
   「CPU 支持列表为空」不是「不限制」，而是「不支持任何主板」——若想表达未知，用 `EMPTY`。

### 数值解析规范（冻结）

1. 优先用 `FieldValue.numeric`；
2. `numeric == null` 且 `value` 非空 → 用 `value.toDoubleOrNull()`；
3. 解析失败 → `Unverified(INSUFFICIENT_DATA, missingField=该字段)`，**不得**当成 0；
4. 比较使用 `Double` 但所有参数字段约定为整数或一位小数，禁止浮点累加误差敏感的比较。

### 版本号规范（已知限制，必须记住）

`biosGeneration` 采用 `主.次` 两段、比较时转数值（`1.2 -> 1.2`）。
**限制**：`1.10` 会被当成 1.1，小于 1.9。
**缓解**：seed 校验强制 `^\d+\.\d{1,2}$` 且次版本号 ≤ 9；超过就改用新的编码方案（走契约变更）。
**不要**在实现里偷偷改成字符串比较——那会让 `1.10 < 1.9` 变成「字典序正确」，但与数值语义不一致，fixture 会跨掉。

## 3.4 执行语义（冻结）

### 3.4.1 五步判定

见 02 章 §2.3.1。这里只补三个细节：

- **类别不匹配**：`items` 里声明 `category=GPU` 但目录记录是 `MEMORY` → 视为部件缺失 → `Unverified(MISSING_FIELD)`。
  数据层应当在 seed 校验就拦住这种不一致，运行期只是防御。
- **多实例（PER_ITEM）**：对每个 `BuildItem` 各生成一条结果；`ruleId` 相同，`conflictId` 不同（带 `#index`）。
- **聚合（COUNT / SUM）**：先求和再比较，只产生一条结果；`leftValue` 写求和后的值（如 `"6"`）。

### 3.4.2 weakest 传播

`weakest = min(所有参与比较的字段的 state)`，强弱顺序 `VERIFIED > REPORTED > EMPTY`。

| 场景 | weakest | 结果 |
|---|---|---|
| 全 VERIFIED，比较通过 | VERIFIED | PASSED |
| 含 REPORTED，比较通过 | REPORTED | PASSED（UI 加「基于报告数据」） |
| 含 EMPTY | EMPTY | UNVERIFIED |
| 含 conflict=true | 最弱来源 | UNVERIFIED(SOURCE_CONFLICT) |

**注意**：`weakest` 只看**参与该规则比较的字段**，不是整机最弱。这是为了让「某个零件数据差」不污染无关规则。

### 3.4.3 输出顺序

结果按 `ruleId` 字典序（`String.compareTo`，区分大小写）排序。**禁止**按 severity 排序后再交给 UI——
UI 可以自己分组展示，但引擎输出顺序必须稳定（INV-15）。

## 3.5 conflictId 生成（稳定标识）

```
conflictId = ruleId ":" left.componentId "." left.path ":" right.componentId "." right.path
多实例追加 "#" itemIndex
```

示例：

```
GPU_CASE_LENGTH:gpu-demo-337.lengthMm:case-demo-320.maxGpuLengthMm
MEMORY_DDR_GENERATION:mem-demo-ddr4-32.generation:mb-demo-b650.memoryGeneration#0
```

规则：

1. **同一冲突重复计算必须得到同一个 id**（用户点「重新检查」，候选列表不能因为 id 变了而清空）。
2. id 只由引用构成，**不含数值**——数值变了但仍是同一条冲突时，id 不变，UI 才能「原地更新」。
3. `SuggestionResult.conflictId` 用完整 id；`conflictRuleId` 用 `ruleId`。
4. 替换建议的过滤以 `ruleId` 为单位（换掉零件就能消除这类冲突），以 `conflictId` 定位是哪一个实例。

## 3.6 规则明细（15 条）

> 左/右列遵循 §3.2 的 kind 约定。severity 已冻结。
> 完整数据见 [../assets/rules/rules-v1.json](../assets/rules/rules-v1.json)。

| # | ruleId | kind | 左 | 右 | severity | 一句话 | 边界/坑 |
|---|---|---|---|---|---|---|---|
| 1 | `CPU_SOCKET_MATCH` | EQUALS | CPU.socket | MB.socket | BLOCKER | 插槽必须完全相等 | 大小写、空白差异不得影响 |
| 2 | `CPU_MB_CHIPSET_SUPPORT` | CSV_CONTAINS | CPU.supportedChipsets | MB.chipset | BLOCKER | 主板芯片组需被 CPU 支持 | 空集合 = 不支持任何主板（不是「不限」） |
| 3 | `CPU_MB_BIOS_SUPPORT` | NUMERIC_GE | MB.biosGeneration | CPU.minBiosGeneration | BLOCKER | 主板 BIOS 需够新 | 缺失即 UNVERIFIED（答辩锚点） |
| 4 | `MEMORY_DDR_GENERATION` | EQUALS | MEMORY.generation | MB.memoryGeneration | BLOCKER | 内存代际匹配 | PER_ITEM；两条内存各自一条结果 |
| 5 | `MEMORY_SLOT_COUNT` | COUNT_LE | MEMORY.moduleCount × qty | MB.memorySlots | MAJOR | 条数不超插槽 | 多套累加；qty=3 的套装 = 6 条 |
| 6 | `MEMORY_CAPACITY_LIMIT` | SUM_LE | MEMORY.kitCapacityGb × qty | MB.maxMemoryGb | MAJOR | 总容量不超上限 | 与 `moduleCapacityGb` 混用是常见错误 |
| 7 | `GPU_CASE_LENGTH` | NUMERIC_LE | GPU.lengthMm | CASE.maxGpuLengthMm | BLOCKER | 显卡长度 ≤ 机箱限长 | 答辩锚点 337 vs 320 |
| 8 | `GPU_CASE_THICKNESS` | NUMERIC_LE | GPU.thicknessMm | CASE.maxGpuThicknessMm | MAJOR | 厚度 | 61 vs 60 是「看起来没问题」的典型 |
| 9 | `GPU_CASE_SLOT_WIDTH` | NUMERIC_LE | GPU.slotWidth | CASE.maxGpuSlots | MAJOR | 槽宽 | 等号边界必须 PASSED |
| 10 | `PSU_CONTINUOUS_POWER` | NUMERIC_GE | PSU.continuousW | formula | BLOCKER | 连续功率 ≥ 估算负载 | 公式见 §3.7；向上取整；缺 CPU/GPU 即 UNVERIFIED |
| 11 | `PSU_CONNECTORS` | CSV_SUBSET | PSU.connectors | GPU.requiredPsuConnectors | BLOCKER | 接口集合包含 | GPU 无此字段 → UNVERIFIED，不得默认满足 |
| 12 | `COOLER_CASE_HEIGHT` | NUMERIC_LE | COOLER.heightMm | CASE.maxCoolerHeightMm | BLOCKER | 散热器高度 | 160 vs 160 必须 PASSED |
| 13 | `COOLER_CPU_SOCKET` | CSV_CONTAINS | COOLER.socketMounts | CPU.socket | BLOCKER | 扣具支持 | 与 #1 方向相反，别搞混 |
| 14 | `MB_CASE_FORM_FACTOR` | CSV_CONTAINS | CASE.supportedFormFactors | MB.formFactor | BLOCKER | 板型可装入 | ATX 板进 ITX 机箱 |
| 15 | `STORAGE_MB_INTERFACE` | CSV_CONTAINS | MB.storageInterfaces | STORAGE.interface | MAJOR | 主板支持该接口 | SATA 盘插纯 NVMe 板 → CONFLICT |

### 明确不做（写进范围外，见 09 章）

CPU/GPU 瓶颈评分、FPS 估算、跑分排序、机箱硬盘位数量校验、显卡功耗墙、内存时序兼容、
BIOS 具体版本号与 CPU 型号的对应表（需要厂商数据，首版不做——所以 BIOS 只有「够新 / 不够 / 未知」三态）。

## 3.7 PSU 负载公式（冻结）

```
requiredW = ceil( (cpu.tdpW + gpu.tdpW + 100) * 1.2 )
```

| 项 | 含义 | 缺失时 |
|---|---|---|
| `cpu.tdpW` | CPU 标称 TDP（W） | 该规则 `Unverified(MISSING_FIELD)` |
| `gpu.tdpW` | 显卡 TDP（W） | 同上（无独显配置首版不评估此规则，返回 UNVERIFIED 而不是 PASSED） |
| `+100` | 主板/存储/风扇等的固定余量 | 常量，改它走契约 |
| `*1.2` | 20% 余量 | 常量，改它走契约 |

**取整**：先求和、乘系数、再 `ceil`，**最后**与 `continuousW` 比较。
顺序错会差 1 W，而「差 1 W」正是 mutation MUT-03 要杀死的边界。

示例（答辩用）：`(105 + 285 + 100) × 1.2 = 588 → 750W 电源通过，550W 电源冲突`。

**禁止**：用 `gpu.recommendedPsuW` 直接比较（那是厂商建议值，不是规则）；
禁止把公式实现成「分段查表」（不可测且不可解释）。

## 3.8 fixture 规范

### 3.8.1 四类强制用例

每条 ruleId **必须**有且命名一致的四类：

| case | 构造 | 期望 outcome | 期望 weakest |
|---|---|---|---|
| `PASSED` | 两个值都 VERIFIED 且满足比较 | PASSED | VERIFIED |
| `CONFLICT` | 两个值都 VERIFIED 且不满足 | CONFLICT | VERIFIED |
| `UNVERIFIED` | 任一输入 EMPTY | UNVERIFIED(MISSING_FIELD) | EMPTY |
| `MISSING` | 缺一个部件（items 里没有） | UNVERIFIED(MISSING_FIELD) | 视剩余输入 |

`UNVERIFIED` 与 `MISSING` 的区别是**被测代码路径不同**：
前者走「字段为空」，后者走「部件不存在」。两条路径都必须覆盖，因为它们的 `missingField` 不同
（一个是具体 `FieldRef`，一个是 `null`）。

### 3.8.2 文件与命名

```
assets/fixtures/fixtures-rules-v1.json    # 60 条：fx_<ruleId 小写>_<case>
assets/fixtures/fixtures-anchor-v1.json   # 8 条答辩锚点（含跨规则、端到端）
```

fixture 只携带**本用例需要的目录记录**与**字段覆写**，不复制整个目录；
加载器 = 基础目录 → 覆写 → 构造 revision → 调引擎。这样 fixture 小、可读、diff 干净。

### 3.8.3 答辩锚点（必须存在、必须绿）

| fixtureId | 断言 | 为什么重要 |
|---|---|---|
| `fx_gpu_len_conflict_337_320` | 337 > 320 → CONFLICT/BLOCKER，conflictId 固定 | 提案里的标志性例子 |
| `fx_gpu_len_pass_300_320` | 300 ≤ 320 → PASSED | 杀死 `<=` 改 `<` 的变异 |
| `fx_gpu_len_equal_320_320` | 320 ≤ 320 → PASSED | 等号边界 |
| `fx_bios_missing` | BIOS EMPTY → UNVERIFIED，missingField 指向它 | 回答「数据不全怎么办」 |
| `fx_source_conflict_gpu_len` | 两来源 337/335 → UNVERIFIED(SOURCE_CONFLICT) | 回答「数据打架怎么办」 |
| `fx_psu_power_conflict` | requiredW 588 > 550 → CONFLICT | 公式与取整 |
| `fx_memory_gen_conflict` | DDR4 vs DDR5 → CONFLICT | 最常见装机错误 |
| `fx_suggestion_300_plus_100` | 7800 → 7900；GPU PASSED；BIOS 仍 UNVERIFIED | 回答「解决一个冲突就万事大吉了吗」 |

最后一条是**端到端**：它跨 D1→D3→D5→D2，任何一层出错都会让它失败。

### 3.8.4 fixture 也是代码

- 改 fixture 的期望值 = 改语义 → 必须与 ADR 一起提（评审清单第 2 条）。
- **禁止**为了让测试通过而放宽 fixture（例如把 CONFLICT 改成 UNVERIFIED）。见 07 章 §7.10 反模式。
- 生成器 `--check` 模式会比对「生成物 vs 源」，手改生成物会被 CI 抓到。

## 3.9 新增规则 / 新增硬件代际

### 新增一条规则（六步，顺序不可乱）

1. 确认路径已在 §3.3；不在就先走契约变更加路径。
2. 在 `tools/gen-fixtures.mjs` 的 `RULES` 与 `CASES` 里加条目（唯一真相源）。
3. 跑 `node tools/gen-fixtures.mjs`，生成 4 条 fixture，逐个确认期望值**人工正确**。
4. `:domain:rules` 不需要改代码——如果必须改代码，说明规则不是数据，回头改设计。
5. 补一条跨模块测试（若它影响替换建议的过滤）。
6. 更新本文 §3.6 表。

### 新增一代硬件（例：DDR6）

1. 目录加记录（生产 seed）；
2. `memoryGeneration` 的合法取值加 `DDR6`（枚举式字符串，不需要改 schema）；
3. 规则 4 自动生效（EQUALS 比较字符串）；
4. 新增 fixture：DDR6 内存 + DDR5 主板 → CONFLICT；
5. **不改** UI、不改状态机、不改引擎。

这就是提案里「rules live in a data table」的具体含义，答辩时用这条回答「以后怎么扩展」。

## 3.10 规则引擎自检清单

- [ ] `rules-v1.json` 中每个 `path` 都在 §3.3 表内
- [ ] 每个 `ruleId` 有 4 条 fixture 且 case 名正确
- [ ] 每条规则输出按 `ruleId` 字典序
- [ ] 两次同输入调用结果完全相等（`==` 比较）
- [ ] 空 revision 返回空列表且不抛异常
- [ ] 未知 ruleId / 未知路径降级为 `Unverified(UNSUPPORTED_RULE)` 且记日志，不 crash
- [ ] 缺部件只影响该规则，其它规则正常
- [ ] PSU 公式的等号 / 差 1 / 取整三个边界都有测试
- [ ] 多内存 PER_ITEM 的 conflictId 各自独立


---

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


---

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
| 3 | `:domain:build` | Kotlin/JVM | 否 | contracts | JVM | 小 |
| 4 | `:domain:rules` | Kotlin/JVM | 否 | contracts | JVM | 中 |
| 5 | `:domain:replacement` | Kotlin/JVM | 否 | contracts | JVM | 中 |
| 6 | `:domain:recommend` | Kotlin/JVM | 否 | contracts | JVM | 中 |
| 7 | `:domain:compare` | Kotlin/JVM | 否 | contracts | JVM | 小 |
| 8 | `:domain:state` | Kotlin/JVM | 否 | contracts | JVM | 小（但测试最多） |
| 9 | `:data` | Android Library | 是 | contracts + Room + assets | Robolectric / 内存 | 中 |
| 10 | `:feature` | Android Library | 是 | contracts + domain:state + domain:build | JVM（协程测试） | 大 |
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

| 消费者 \ 提供者 | contracts | domain:build | domain:rules | domain:repl | domain:recom | domain:comp | domain:state | data | feature | ui | testing |
|---|---|---|---|---|---|---|---|---|---|---|---|
| **contracts** | — | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ |
| **domain:build** | ✔ | — | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ | ✘ |
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
  │  1. 调 domain:build 生成新 BuildRevision（纯函数）
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


---

# 06 · 六模块契约卡

> 读者：每个 LLM 任务只读**自己那一张** + 02/03/04 的相关小节 + 07 的验收矩阵。
> 每张卡的六块内容缺一不可（目标 / 输入 / 输出 / 禁止 / 失败 / 验收），
> 这也是 prompt 模板的骨架（12 章）。

---

## 6.0 卡片模板

| 块 | 含义 | 写不好的典型症状 |
|---|---|---|
| **目标** | 一句话职责 + 不做什么 | 写成功能介绍，边界含糊 |
| **输入契约** | 接口签名 + 依赖的只读端口 | 直接写"调用 Repository" |
| **输出契约** | 数据类型 + 不变量 + 顺序 | 漏掉确定性要求 |
| **禁止事项** | 明确 MUST NOT（含跨模块禁令） | 只写"注意性能" |
| **失败模式** | 每种失败的结构化返回 | 写"抛出异常" |
| **验收用例** | Given/When/Then + 测试入口 | 写"功能正常" |

**统一约定**：

- 所有模块的失败都返回**结构化结果**，不抛异常给 UI（数据层 fail-fast 除外）。
- 所有模块的输出都必须**确定性**（同输入同输出含顺序）。
- 所有模块都必须能单独 `:module:test` 通过（05 章 L2）。

---

## D1 硬件数据库

**目标**：提供只读静态目录、字段级证据、带日期的 CNY 参考价、目录版本。**不判断兼容性。**

**模块**：`:data`（部分）+ `assets/catalogue/` ｜ **负责角色**：数据层成员

### 输入契约

```kotlin
interface CataloguePort {
    fun getById(componentId: String): CatalogueRecord?
    fun search(query: String, category: Category?): List<CatalogueRecord>
    fun listByCategory(category: Category): List<CatalogueRecord>
    fun catalogueVersion(): String
    fun priceDate(): String
}
```

### 输出契约

- `CatalogueRecord`，其 `attributes` 的 key 必须全部在 03 章 §3.3 表内；
- `search` 结果按 `componentId` 字典序（确定性）；
- `catalogueVersion` / `priceDate` 在整个进程生命周期内不变（一次加载，不可变）。

### 不变量

- INV-11：`isDemo=true` 的记录不得进入生产 seed；
- 每条记录的 `priceDate` 必须是合法 ISO 日期且不晚于 `catalogueVersion` 的发布日期；
- 同一 `componentId` 不得重复。

### 禁止事项

- 禁止网络请求、实时价格、库存、爬虫（范围外，见 09 章）；
- 禁止在数据层计算兼容性、推荐、价格差；
- 禁止把 demo 记录混进生产 seed；
- 禁止返回 null 以外的"缺省记录"（例如自动造一条 EMPTY 的占位记录）。

### 失败模式

| 场景 | 返回/行为 |
|---|---|
| `getById` 找不到 | `null`（不抛异常） |
| seed schema 非法（路径不在表内 / 重复 id / 类型错） | **启动即 fail-fast**，日志含 componentId 与位置 |
| 目录为空 | 返回空列表，`catalogueVersion` 仍有值；UI 显示 `catalogue_empty` |

### 验收用例

| Given | When | Then |
|---|---|---|
| demo 目录 | `getById("gpu-demo-337")` | 非空，`isDemo=true`，`lengthMm.numeric == 337.0` |
| 任意目录 | `getById("nope")` | `null`，不抛异常 |
| seed 含重复 id | 启动 | fail-fast，日志含重复 id |
| 任意记录 | 遍历 `attributes` | 每个 path 都在 §3.3 表内 |
| 目录 | `search("rtx", null)` vs `search("RTX", null)` | 结果集合相同 |

### 单元验证入口

`:data:test` → 继承 `CataloguePortContract`；seed 校验单测；demo 隔离单测（INV-11）。

### 独立性约束

只依赖 `:contracts` + Room + assets。**禁止** import 任何 `pcbuildmate.domain.*`。

---

## D2 DIY 配置工作区（ViewModel 编排）

**目标**：把 `Intent` 变成新 `BuildRevision`，维护两张状态机，执行 Effect，暴露唯一 `WorkspaceUiState`。
**不计算规则、不直接读数据库、不直接改 revision。**

**模块**：`:feature` ｜ **负责角色**：架构 + UI 负责人

### 输入契约

```kotlin
sealed interface WorkspaceIntent { /* 15 个意图，见 contracts/Workspace.kt */ }
```

依赖（只读端口）：`CataloguePort`、`BuildStorePort`、`DraftStorePort`、`IdPort`、`ClockPort`、
`CompatibilityEvaluator`、`ReplacementAdvisorPort`、`BuildComparatorPort`。

### 输出契约

```kotlin
val state: StateFlow<WorkspaceUiState>
```

不变量：

- 每次 Add/Remove/Replace/SetQuantity/Undo 产生新 revision，`totalCny` 同步（INV-01/02/10）；
- 编辑后 `diagnosisState == Idle` 且 `currentDiagnosis == null`，**不自动** StartCheck；
- 只有经 `Rechecking` 才能 `Applied`（INV-07）；
- 被拒绝的意图不改变 state，只写 `rejection`。

### 禁止事项

- 禁止在 ViewModel 写兼容性 if（只能调 `CompatibilityEvaluator`）；
- 禁止 UI 直接构造/修改 `BuildRevision`；
- 禁止编辑后自动重检；
- 禁止把草稿混进 `savedBuilds`；
- 禁止 `GlobalScope` / 不受控的并发写 state。

### 失败模式

| 场景 | 结果 |
|---|---|
| Add/Replace 目标不存在 | 不产生 revision，`rejection = CATALOGUE_MISS` |
| qty < 1 | `rejection = QUANTITY_INVALID` |
| apply 时 base 过期 | `rejection = APPLY_REVISION_STALE`，build 不变 |
| plan card JSON 非法 | `rejection = IMPORT_INVALID_SCHEMA`，**不部分导入** |
| 状态机拒绝 | 透传 `Rejection`，state 不变 |

### 验收用例

| Given | When | Then |
|---|---|---|
| 空 build | Add CPU | 新 revision、total 更新、`diagnosisState = Idle` |
| 已诊断 rev1 | SetQuantity | `currentDiagnosis == null`、banner 出现、**不** Running |
| Running(rev1) | 编辑生成 rev2 后收到 rev1 的 CheckCompleted | 丢弃，state 仍 Idle |
| 任意 | Undo | 新 revision id > 旧 id，内容等于上一快照 |
| Previewing 且 base 过期 | Apply | `Rejected(APPLY_REVISION_STALE)`，build 不变 |
| 非法 JSON | ImportPlanCard | 拒绝，`savedBuilds` 不变 |

### 单元验证入口

`:feature:test` → 状态序列断言（Turbine 收集）、虚拟时间、交错 revision 用例、Effect 执行次数断言。

### 独立性约束

允许依赖：`contracts`、`domain:state`、`domain:build`。
**禁止** import `domain:rules` / `domain:replacement` / `domain:recommend` / `domain:compare` 的**实现类**（只能经接口）。

---

## D3 兼容性检查（规则引擎）

**目标**：输入 `BuildRevision`，输出按 `ruleId` 排序的 `List<RuleOutcome>`。纯函数。

**模块**：`:domain:rules` ｜ **负责角色**：领域层成员

### 输入契约

```kotlin
interface CompatibilityEvaluator {
    fun evaluate(revision: BuildRevision): List<RuleOutcome>
}

class DefaultRuleEngine(
    private val rules: List<RuleDef>,          // 由 data 层解析 JSON 后注入
    private val catalogue: CataloguePort,      // 只读
) : CompatibilityEvaluator
```

### 输出契约

- 顺序：`ruleId` 字典序；
- `Conflict` 必须带 `conflictId` / 左右 `FieldRef` / 左右值 / `severity`；
- `Unverified` 必须带 `reason`，能定位时带 `missingField`；
- 同一输入两次调用结果完全相等。

### 禁止事项

- 禁止缺字段 PASS（INV-04）；
- 禁止把 `REPORTED` 当 `VERIFIED`；
- 禁止读价格、预算、UI 文案、时间；
- 禁止做替换排序或推荐；
- 禁止改 §3.7 公式而不走契约变更；
- 禁止抛异常给调用方（未知规则降级为 `UNSUPPORTED_RULE`）。

### 失败模式

| 场景 | 结果 |
|---|---|
| 未知 ruleId / 未知路径 | `Unverified(UNSUPPORTED_RULE)` + 日志，不 crash |
| 缺部件 | 该规则 `Unverified(MISSING_FIELD)`，其它规则正常 |
| 来源冲突 | `Unverified(SOURCE_CONFLICT)`，优先于数值比较 |
| 空 revision | 空列表 |

### 验收用例

| Given | When | Then |
|---|---|---|
| 60 条单规则 fixture | 逐条 evaluate | 与 `expected` 全等 |
| 337 / 320 | evaluate | CONFLICT，conflictId 固定，leftValue=337 |
| BIOS EMPTY | evaluate | UNVERIFIED，missingField 指向 mb.biosGeneration |
| 两来源 337/335 | evaluate | UNVERIFIED(SOURCE_CONFLICT) |
| 同输入跑两次 | 比较 | 结果列表 `==` |
| requiredW 边界 | 等号 / 差 1 / 取整 | 恰好翻转 |

### 单元验证入口

`:domain:rules:test` → 15 × 4 fixture 参数化 + 属性（weakest）+ 边界。

### 独立性约束

只依赖 `:contracts`。**禁止** Room / Android / 序列化库 / 时钟。

---

## D4 预算规划与推荐

**目标**：`(预算, 用途, 偏好) -> 可编辑的起始配置`；权重可见；不可行必须报告。

**模块**：`:domain:recommend` ｜ **负责角色**：领域层成员

### 输入契约

```kotlin
interface BudgetRecommenderPort {
    fun recommend(request: RecommendationRequest): RecommendationResult
}
```

内部可用：`CataloguePort`、`CompatibilityEvaluator`（通过构造注入）。

### 输出契约

- `feasible=true` ⇒ `totalCny <= budgetCny` 且所有可评估硬约束通过（INV-13）；
- `feasible=false` ⇒ `shortfallCny > 0` 且 `build` 可为 null，且 `relaxedPreferences` 非空（若放宽了偏好）；
- `weightsVisible` 非空，且与实际分配一致；
- 同输入同目录版本，结果确定。

### 禁止事项

- 禁止声称"最优"；
- 禁止用性能分数 / FPS / 跑分排序（范围外）；
- 禁止超预算或放宽硬约束而不报告；
- 禁止隐藏权重；
- 禁止读 UI 状态、写 saved builds。

### 失败模式

| 场景 | 结果 |
|---|---|
| 空目录 | `feasible=false`，`shortfall=budget`，`build=null` |
| 预算过低 | 最接近配置 + shortfall + 放宽列表 |
| 偏好无法满足 | 同上，`relaxedPreferences` 列出被放宽项 |

### 验收用例

| Given | When | Then |
|---|---|---|
| 8000 / GAMING | recommend | total ≤ 8000，硬约束无 BLOCKER Conflict |
| 1000 / GAMING | recommend | `feasible=false`，shortfall > 0 |
| GAMING vs STUDY_OFFICE | recommend | GPU 权重 GAMING > STUDY_OFFICE，且两者都可展示 |
| 推荐结果再跑引擎 | evaluate | 无新 BLOCKER Conflict（最多 UNVERIFIED） |

### 单元验证入口

`:domain:recommend:test` → 预算/不可行/权重可见/确定性/硬约束。

### 独立性约束

只依赖 `:contracts`；引擎经 `CompatibilityEvaluator` 接口。

---

## D5 智能替换建议

**目标**：给定一条冲突，返回**先过硬约束、再排序**的候选；无候选时给结构化原因。

**模块**：`:domain:replacement` ｜ **负责角色**：领域层成员

### 输入契约

```kotlin
interface ReplacementAdvisorPort {
    fun suggest(base: BuildRevision, conflict: RuleOutcome.Conflict): SuggestionResult
}
```

### 输出契约

- 每个候选**消除**该 `conflictId`（替换后该规则不再 Conflict）；
- 候选必须通过其它所有**可评估**规则；引入 BLOCKER 新冲突的候选被过滤；
- 排序键固定：价格差升序 → 对剩余余量影响小 → `componentId` 字典序（INV-14）；
- 无候选 ⇒ `candidates = []` 且 `noCandidate != null`。

### 禁止事项

- 禁止先排序再过滤；
- 禁止用"综合分"抵消不兼容；
- 禁止隐藏会引入新冲突的候选而不标注（`affectsOtherRules`）；
- 禁止直接修改 build；
- 禁止声称"最优替换"。

### 失败模式

| 场景 | 结果 |
|---|---|
| 无候选 | `noCandidate.blockingConstraint` 指向具体 ruleId + fieldPath |
| base 过期 | 空结果 + `noCandidate(blockingConstraint="STALE_REVISION")` |
| 目录空 | 同上 |

### 验收用例

| Given | When | Then |
|---|---|---|
| 337 / 320 | suggest | 含 300mm 候选，`priceDeltaCny = 100`，rank=1 |
| 候选会引入 PSU 不足 | suggest | 被过滤，或 `affectsOtherRules` 含 PSU 规则 |
| 无候选 | suggest | 结构化原因，build 不变 |
| 同输入两次 | suggest | 顺序一致 |
| 应用 300mm | 全量重检 | total 7900，GPU PASSED，BIOS 仍 UNVERIFIED（`fx_suggestion_300_plus_100`） |

### 单元验证入口

`:domain:replacement:test` → 过滤顺序、排序稳定性、无候选结构、跨规则锚点。

### 独立性约束

只依赖 `:contracts`；引擎经接口。

---

## D6 构建比较

**目标**：2–3 个已保存配置并排比较；**只读**已有 revision 与诊断；过期诊断不显示旧绿。

**模块**：`:domain:compare`（+ `:ui` 展示）｜ **负责角色**：领域层 + UI

### 输入契约

```kotlin
interface BuildComparatorPort {
    fun compare(buildIds: List<String>): ComparisonResult
}
```

输入来源：`BuildStorePort.listRevisions` + 已存诊断。**不调用**引擎。

### 输出契约

字段冻结（首版）：总价、兼容状态、分类花费、功耗余量、升级余量（内存槽 / 存储位）、物理尺寸。
差异高亮由模块给 `highlighted`，UI 只渲染。

不变量：`diagnoses[i] != null <=> 该 build 的 diagnosis.revisionId == build.currentRevisionId`（INV-12）。

### 禁止事项

- 禁止重新计算规则（只能读已有诊断）；
- 禁止把过期诊断显示为当前结论；
- 禁止 performance band / FPS / 跑分（范围外）；
- 禁止修改 build；
- 禁止把不同目录版本的价格差说成确定结论（必须进 warnings）。

### 失败模式

| 场景 | 结果 |
|---|---|
| <2 个 build | `Rejected(COMPARE_INSUFFICIENT_BUILDS)` |
| 某 build 诊断过期 | 该列 `diagnoses[i]=null`，warnings 加 `compare_diagnostics_stale` |
| 目录版本不同 | warnings 加提示，仍比较 |
| build 不存在 | 整体错误，不部分比较 |

### 验收用例

| Given | When | Then |
|---|---|---|
| 2 个 saved build | compare | 字段对齐、delta 正确、单位一致 |
| 某 build 诊断过期 | compare | 该列显示"请重新检查"，不显示旧 PASSED |
| 1 个 build | compare | 被拒绝并给 key |
| 目录版本不同 | compare | warning 出现，仍比较 |

### 单元验证入口

`:domain:compare:test` → 对齐、delta、过期诊断、版本不一致。

### 独立性约束

只依赖 `:contracts`。**禁止**调用 `CompatibilityEvaluator`。

---

## D7 UI 绑定

**目标**：Compose 只读 `WorkspaceUiState`、只发 `WorkspaceIntent`；四屏线性流程。

**模块**：`:ui` ｜ **负责角色**：UI 实现成员

### 输入契约

- `state: WorkspaceUiState`（只读）
- `onIntent: (WorkspaceIntent) -> Unit`

### 输出契约

- 每个可见元素有稳定 `testTag`（key 来自 `TextKey` 或语义 id）；
- 相同 state 渲染出相同 UI（无内部可变状态）；
- 小屏（360dp）不截断关键操作。

### 禁止事项

- 禁止 import DAO / Room / 具体 Repository 实现；
- 禁止在 Composable 里计算兼容性、价格、delta；
- 禁止自行拼接冲突文案（用 `Conflict.leftValue/rightValue` + 资源模板）；
- 禁止断言/依赖自然语言全文（测试只断言 key 与状态）。

### 失败模式

- 无 build 点"检查"→ 提示 `diagnostics_required`，不跳转；
- 少于 2 个配置点"比较"→ 提示 `compare_insufficient_builds`；
- 过期诊断 → 显示 banner 而非旧结论。

### 验收用例

| Given | When | Then |
|---|---|---|
| 有冲突 state | 渲染诊断页 | 可见 `conflict_<ruleId>` tag、左右值均显示 |
| stale state | 渲染 | 可见 `diagnostics_stale_banner`，不可见 PASSED 徽标 |
| 空 build | 点检查 | 出现提示且未导航 |
| 360dp 宽 | 滚动 | 关键按钮可达 |

### 单元验证入口

`:ui:test`（Robolectric + Compose semantics）→ 用**静态 state** 渲染，不启动 ViewModel。

### 独立性约束

只依赖 `:contracts` + Compose。**禁止**依赖 `feature` / `data` / `domain:*`。

---

## 6.9 模块间配合契约测试（10 张，跨模块唯一集成手段）

| 测试 | 涉及模块 | 断言 |
|---|---|---|
| `e2e_gpu_337_to_300` | D1→D3→D5→D2 | 冲突 → 候选 +100 → apply 后 7900 → GPU PASSED、BIOS UNVERIFIED |
| `e2e_stale_diagnosis` | D2→D3 | rev1 Running，编辑 rev2，rev1 结果被丢弃 |
| `e2e_apply_guard` | D2→D5 | base 过期 apply → Rejected，build 不变 |
| `e2e_no_candidate` | D3→D5 | 结构化原因，不超预算、不放宽硬约束 |
| `e2e_recommend_feasible` | D1→D3→D4 | 预算内、硬约束通过、权重可见 |
| `e2e_recommend_infeasible` | D4 | `feasible=false`，shortfall 非零 |
| `e2e_compare_stale` | D2→D6 | 过期诊断不显示旧绿 |
| `e2e_demo_isolation` | D1 | `isDemo=true` 不进生产 seed |
| `e2e_import_plan_card` | D2 | 新 buildId，不混入当前 |
| `e2e_undo_revision` | D2 | 新 revision id 递增，内容回退 |

**每张卡开工时的 prompt 必须附带**：本卡 + 02 相关契约 + 04 相关状态机 + 03 相关规则/fixture + 本节对应测试。
少一个，LLM 就会自己补语义（10 章 §10.3）。


---

# 07 · 单元验证设计

> 读者：测试负责人；每个 LLM 任务在**写代码之前**读一次。
> 本章回答：**每个设计单元，用什么手段、在哪一层、证明什么？**
> 核心立场：**测试不是"跑绿了"，而是"矩阵没有空格子 + 关键变异被杀掉"。**

---

## 7.1 什么是本项目的一个「单元」

单元不是"一个类"，而是**一个可以在毫秒级判定的设计决策**。按这个定义：

| 模块 | 单元 | 隔离手段 | Oracle | 环境 |
|---|---|---|---|---|
| `domain:build` | 一次编辑产生的新快照（含总价、id） | 纯函数，无依赖 | 不变量 + 属性 | JVM |
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

工具：PIT（JVM 模块）。**mutation score 不是 KPI**；**关键 mutation 未杀死 = 阻塞合并**。

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


---

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


---

# 09 · 范围外控制与 CI

> 读者：全员。
> 本章把"不做"变成**可执行的失败**。三项治理规则中的 G3（范围外硬阻塞）在这里落地。

---

## 9.1 范围外清单（出现即 fail）

| 类别 | 禁止项 |
|---|---|
| 性能预测 | FPS、跑分、benchmark prediction、CPU/GPU 瓶颈评分、performance band |
| 3D / 可视化 | 3D 装配、AR 预览、模型渲染 |
| 实时数据 | 实时库存、实时价格、电商 API、爬虫、后台自动刷新、价格轮询 |
| 二手 / 账号 | 二手价、账号系统、云同步、社交 |
| 设备能力 | 相机、条码扫描、推送、定位 |
| 越界比较 | 目录版本对比、历史价格曲线 |
| 架构越界 | UI 直读 DAO、domain 依赖 Android/Room/Compose、模块间共享可变全局状态 |

**为什么"性能分档"必须裁掉**：它不是"暂时没数据"，而是**结构上无法满足公理 A1**
（没有证据就不能断言）。一半成品会逼着前端显示一个没有来源的数字。

## 9.2 CI 三层检查

### 第 1 层：依赖边界（硬 fail，INV-16）

- `:domain:*` 不得依赖 `android.*` / `androidx.room.*` / `androidx.compose.*` / 网络库；
- `:ui` 不得依赖 `:feature` / `:data` / `:domain:*`；
- `:feature` 不得依赖 `:domain:rules|replacement|recommend|compare`；
- `test-oracle` 不得依赖生产 `domain.*`；
- `:contracts` 不得依赖任何实现模块。

执行：`node ci/check-boundaries.mjs`（读 `ci/modules.json`）。

### 第 2 层：关键词 / 权限 / 依赖坐标（硬 fail）

扫描范围：`src/main/**` 的标识符、字符串资源、`AndroidManifest.xml` 权限、`build.gradle.kts` 依赖坐标。
**不扫描**：注释、`docs/`、测试 fixture、`debug/` 下显式 `OUT_OF_SCOPE_DEMO` 文件。

| 检查项 | 命中即 fail |
|---|---|
| 标识符 | `FPS`、`fps`、`BenchmarkPrediction`、`PerformanceBand`、`LiveInventory`、`SecondHand`、`RealtimePrice`、`PricePolling`、`AutoRefresh`、`Camera`、`ARPreview`、`PushNotification` |
| 权限 | `INTERNET`、`CAMERA`、`POST_NOTIFICATIONS`、`ACCESS_FINE_LOCATION`、`FOREGROUND_SERVICE` |
| 依赖坐标 | 网络库（okhttp/retrofit/ktor）、推送 SDK、AR/相机库、爬虫库 |
| 文案 | 「实时价格」「库存同步」「帧率预测」「跑分」 |
| 卫生 | `TODO` / `FIXME` / `println` 在 `src/main` |

执行：`node ci/check-keywords.mjs`。

**例外面**：文档、注释、fixture 里讨论这些词是允许的；生产标识符不允许。
plan card 分享用应用内 JSON + 系统分享 Intent，**不需要** `INTERNET` 权限。

### 第 3 层：主流程隔离

- 主流程（Entry → Workspace → Diagnostics → Compare）不得出现范围外入口；
- debug / 答辩入口必须：显式开关、位于 `debug/`、标识 `OUT_OF_SCOPE_DEMO`、默认关闭、不进 release；
- 现有 debug 入口**只允许**"重新载入内置参考价"（纯本地重读 assets），不得扩展成网络刷新。

## 9.3 白名单

`ci/allowlist.txt`，每条一行：

```
# 文件路径 | 关键词 | 理由 | 批准人 | 日期
app/src/test/.../FpsRegressionTest.kt | FPS | 反例测试：断言范围外字段不会出现在 UI | 组长 | 2026-10-20
```

规则：

- 白名单只允许出现在**测试 / 文档 / 注释**，不允许生产标识符或权限；
- 白名单变更走 git（默认批准），但 CI 在批次报告中列出；
- **频繁新增白名单 = 范围漂移信号**，评审时点名。

## 9.4 解除范围阻塞（G3）

```
1. 写 scope-change 文档：功能描述 / 为什么首版需要 / 影响模块 / 测试计划 / 答辩口径 / 风险
2. 人批准（默认不批；批准需说明理由）
3. 批准后：更新 09 章 §9.1、§9.2 → 移除/加白名单 → 补 fixture 与验收用例 → 更新 00 章 §0.5
4. 未批准前：只能独立分支实验，不得进 main、不得进 release
```

**scope-change 模板**（六节，缺一不批）：

| 节 | 内容 |
|---|---|
| 1 功能 | 一句话 + 用户场景 |
| 2 必要性 | 不做会怎样；能否用现有功能替代 |
| 3 影响模块 | 依赖矩阵哪几格会变 |
| 4 测试计划 | 新增哪些 fixture / 矩阵哪些格子 |
| 5 答辩口径 | 一句话怎么讲，不能说什么 |
| 6 风险 | 工期、数据来源、合规 |

## 9.5 关键词表是怎么维护的

关键词表在 `ci/keywords.json`，与本文同步。加词的判断标准：

- 该词**只可能**出现在范围外功能里（如 `PricePolling`）；
- 或该词是范围外功能的**唯一标识**（如 `PerformanceBand`）。

**不要**加泛词（`AI`、`Score`、`Market`）——误报会把白名单撑爆，白名单一多就没人看了。

## 9.6 检查脚本的失败输出长什么样

```
$ node ci/check-boundaries.mjs
✗ feature/src/main/kotlin/.../WorkspaceViewModel.kt:42
    import pcbuildmate.domain.rules.DefaultRuleEngine
    违规：:feature 不得依赖 :domain:rules 的实现类
    依据：docs/05-module-independence.md §5.3
    修复：改为注入 CompatibilityEvaluator 接口

$ node ci/check-keywords.mjs
✗ app/src/main/AndroidManifest.xml
    权限 INTERNET 属于范围外（docs/09-ci-scope.md §9.2）
    若确需，请提 scope-change 并由人批准
```

**报错必须带文档链接**，否则 LLM 会自己猜怎么修，然后猜错。

## 9.7 与其它章节的对应

| 本文条目 | 对应 |
|---|---|
| §9.1 范围外 | 00 章 §0.5；13 章 §13.4 答辩口径 |
| §9.2 三层检查 | 05 章 §5.5（编译期闸）；07 章 DoD 第 4 条 |
| §9.3 白名单 | 10 章 §10.6 评审清单第 6 条 |
| §9.4 解除流程 | README §5 治理规则 G3 |


---

# 10 · 用 LLM 交付：顺序、打包、闸门

> 读者：全员。本章是**工作流**，不是原则。
> 一句话：**LLM 负责写，契约负责管，矩阵负责验，人负责放行。**

---

## 10.1 LLM 能做与不能做（先对齐预期）

| 能 | 不能（重复喂也没用） |
|---|---|
| 按冻结契约写实现 | 自己发明语义并保持前后一致 |
| 按 fixture 写测试 | 判断"这个 fixture 期望值对不对" |
| 按转移表写状态机 | 决定"要不要加一个状态" |
| 按模块卡重构 | 知道两个模块之间的隐性耦合 |
| 批量改命名、补 null 检查 | 评估"改这个会不会影响答辩" |

**因此**：契约、fixture 期望值、状态表由人定；实现与测试由 LLM 写；
"对不对"由矩阵与变异测试判；"能不能交"由人批。

## 10.2 生成顺序与逐步闸门

```
S0 契约与文档（人）         ✅ 本仓库
S1 D1 数据层                → 闸门 G1：契约测试 + seed 校验 + demo 隔离
S2 D3 规则引擎              → 闸门 G2：60 条 fixture 全绿
S3 D5 替换建议              → 闸门 G3：过滤顺序 + 无候选结构 + 排序稳定
S4 D2 工作区                → 闸门 G4：76 格状态机 + 7 种时序
S5 D4 预算推荐              → 闸门 G5：预算/不可行/权重可见
S6 D6 比较                  → 闸门 G6：过期诊断 + 版本警告
S7 D7 UI 绑定               → 闸门 G7：semantics + 无业务计算
```

**顺序不能换**，理由在 00 章 §0.4。每一步的闸门 = 该模块 DoD（07 章 §7.11）。

**不过闸门不许进下一步**。这不是形式主义：S4 建立在 S2/S3 的语义之上，
如果 S2 的 fixture 还有空格，S4 的失败会以"状态机错了"的形式出现，归因会全错。

## 10.3 上下文打包（决定成败的一步）

每个任务只给**六块**，不要给全仓库：

| # | 块 | 来源 | 大致篇幅 |
|---|---|---|---|
| 1 | 本模块卡 | 06 章对应一节 | 60–80 行 |
| 2 | 相关契约 | 02 章相关小节 + `contracts/*.kt` 相关文件 | 100–200 行 |
| 3 | 相关状态机 | 04 章相关表 | 50–120 行 |
| 4 | 相关规则与 fixture | 03 章相关行 + `assets/fixtures/*` 相关条目 | 50–150 行 |
| 5 | 验收矩阵 | 07 章 §7.5 + 06 章 §6.9 对应行 | 20 行 |
| 6 | 禁止清单 | 本卡"禁止事项" + 09 章相关项 | 20 行 |

**为什么不能喂全文档**：注意力会被稀释，LLM 会开始"综合理解"，
而综合理解正是语义漂移的来源。宁可多开几轮，也不要一次性塞 8000 行。

### 打包示例（D3 规则引擎）

```
给：
  docs/06-modules.md 的 D3 一节
  docs/02-contracts.md §2.2 INV-04~06、§2.3 五步判定
  contracts/Model.kt、Outcomes.kt、Ports.kt（CataloguePort 部分）
  docs/03-rules.md §3.2–§3.4、§3.7
  assets/rules/rules-v1.json
  assets/fixtures/fixtures-rules-v1.json 的前 4 条（作为格式样例）
  docs/07-unit-verification.md §7.5.2 标签规范

不给：
  05 章、08 章、09 章、10 章、13 章、UI 相关、推荐/比较相关
```

## 10.4 一次任务的十个步骤

```
1. 选模块 → 复制 12 章的模板
2. 填六个块（从上面六处摘）
3. 声明"本轮只做 X，不做 Y"（防止顺手扩展）
4. 让 LLM 先输出"实现计划 + 会改哪些文件"，人确认（30 秒）
5. 生成实现 + 测试
6. 本机跑：:module:test → check-boundaries → check-keywords → check-matrix
7. 全绿后人工评审（§10.6 清单）
8. 提交：一个模块一个 PR，message 含模块名 + 章节号
9. 记录 promptHash / modelVersion（写进 PR 描述）
10. 更新对应文档表格（若实现过程中发现了文档缺口）
```

第 4 步最容易被跳过，也最值钱：让 LLM 先说要改哪些文件，能提前发现"它想改契约"。

## 10.5 prompt 里必须写死的四句话

1. **"只实现本卡范围，不要顺手改其它模块。"**
2. **"不得修改 `contracts/`、不得改 fixture 期望值；若需要，停下来说明原因。"**
3. **"先写测试再写实现，测试必须能被'实现写错'弄红。"**
4. **"遇到规格空白，列出 2–3 个选项并等我选择，不要自行决定。"**

第 4 句是关键：LLM 最危险的行为不是写错，而是**在空白处自行补语义且不告诉你**。

## 10.6 评审清单（12 条，逐条打勾）

| # | 检查 | 不通过的例子 |
|---|---|---|
| 1 | 没有改 `contracts/`（或已单独提） | PR 里出现 Model.kt diff |
| 2 | 没有改 fixture 期望值 | 把 CONFLICT 改成 UNVERIFIED |
| 3 | 没有跨越依赖矩阵 | feature import 了 DefaultRuleEngine |
| 4 | 业务逻辑不在 VM/UI | VM 里写 `if (length > max)` |
| 5 | 新行为有测试，且测试能失败 | 只断言"不抛异常" |
| 6 | 没有新增白名单 | allowlist.txt 多了一行 |
| 7 | 没有范围外标识符/权限 | 出现 `PerformanceBand` |
| 8 | 错误路径返回结构化结果 | 抛异常给 UI |
| 9 | 输出确定性（排序/集合转列表） | 用 `Set` 输出 |
| 10 | 文案走 TextKey | 硬编码中文句子 |
| 11 | 没有 TODO/println | `// TODO: 以后再说` |
| 12 | 文档表格已更新 | 新增规则但 §3.6 没加行 |

## 10.7 六种漂移模式与检测手段

| # | 漂移 | 长什么样 | 检测 |
|---|---|---|---|
| 1 | 语义降级 | EMPTY → PASSED；SOURCE_CONFLICT → 数值比较 | MUT-01/02 + 矩阵 |
| 2 | 契约偷改 | 字段可空化、加默认值"顺手" | API dump + 清单 1 |
| 3 | 边界渗透 | VM 里算规则、UI 里读 DAO | check-boundaries |
| 4 | 测试自证 | 断言自己实现的中间结果 | 参考模型 + 清单 5 |
| 5 | 范围蔓延 | "顺便加个 FPS 估算" | check-keywords |
| 6 | 确定性丢失 | `HashSet` 输出、`Instant.now()` | 双跑相等 + 扫描 |

## 10.8 契约变更后怎么重跑历史 prompt

契约一旦变更（例如新增 `PREVIEW_NOT_READY`），已有实现可能仍然编译通过但语义过时。
处理方式：

1. 跑全量矩阵——空格会立刻暴露；
2. 跑 key mutation——语义降级会被抓到；
3. **不要**批量重发历史 prompt：上下文已变，重发会产生"看起来对但和现在不一致"的实现；
4. 只对**失败模块**用**最新**六块重开一轮。

## 10.9 失败后怎么办（不许"再试一次"）

| 现象 | 正确动作 |
|---|---|
| 编译错 | 把完整报错贴回去，只修编译 |
| 测试红 | 先判断是"实现错"还是"测试错"（用参考模型）；不许两个一起改 |
| 矩阵空格 | 补测试，不补实现 |
| 边界违规 | 改设计（注入接口），不许加白名单 |
| LLM 反复改不对（≥3 轮） | 停下来：说明规格有洞。补文档 → 重开一轮 |

**"再试一次"是最贵的行为**：它掩盖了规格缺陷，而且每次产出的实现都不一样。

## 10.10 留痕

每个 PR 描述包含：

```
module: :domain:rules
spec: docs/06-modules.md#d3, docs/03-rules.md#34
promptHash: sha256:abc...
modelVersion: android-studio-llm / 2026-10
matrix: rules 60/60, fsm -, inv 4/18
mutation: 12/12 killed
notes: 规格空白 2 处已按 §10.5 询问后确定（记录在 PR 评论）
```

留痕的用途不是审计，而是**复盘**：当某个模块反复出错时，能看出是 prompt 的问题还是规格的问题。


---

# 11 · 架构决策记录（ADR）

> 每条 ADR 记录：为什么这样决策、拒绝了哪些替代方案、什么条件下应该推翻它。
> **推翻一条 ADR 必须新写一条 ADR**，不能悄悄改实现。

| # | 决策 | 一句话 | 复核触发 |
|---|---|---|---|
| [0001](0001-mvi-revision.md) | MVI + BuildRevision | 一次编辑影响多个输出，必须单一不可变状态 + revision 绑定 | 单配置条目 > 50 |
| [0002](0002-static-catalogue.md) | 静态目录 + 日期化参考价 | 没有数据源就不给数字；裁掉 performance band | 拿到可授权数据源 |
| [0003](0003-no-runtime-llm.md) | 运行时无 LLM | 兼容是规则问题，不是语言问题；LLM 只做开发工具 | 需要解析自然语言输入 |
| [0004](0004-pure-jvm-domain.md) | 纯 JVM domain + 端口 | 用编译期隔离代替纪律 | 模块构建 > 3 分钟 |
| [0005](0005-rules-as-data.md) | 规则数据化 | 新增代际只改数据与 fixture | 出现第 3 条 formula 规则 |
| [0006](0006-three-state-evidence.md) | 三态证据模型 | 三种"责任"，最弱传播，缺字段不通过 | 需要连续置信度 |

## 写 ADR 的格式

```
# ADR-000X · 标题
- 状态：提议 / 已采纳 / 已废弃（被 000Y 取代）
- 影响：哪些模块
- 相关：哪些章节

## 背景      为什么现在要决定
## 决策      做了什么（编号列出，可执行）
## 后果      正面 / 负面（都要写）
## 替代方案  表格：方案 / 为什么不选
## 复核触发条件  什么情况下应该重新讨论
## 答辩口径  一句话怎么说
```


---

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


---

# ADR-0002 · 静态目录 + 日期化参考价，裁掉 performance band

- 状态：已采纳（2026-10）
- 影响：data、domain:compare、ui、09 章范围外清单
- 相关：docs/00-overview.md §0.5、docs/09-ci-and-scope.md §9.1

## 背景

提案原文在比较页写了 "an indicative performance band"，并期望"实时价格/库存"作为长期能力。
但团队没有数据源、没有授权、也没有评测集。若保留：

1. 性能分档必然要"猜"，与公理 A1（缺证据不得断言）冲突；
2. 实时价格需要网络权限，与"完全离线"和范围外清单冲突；
3. 任何未经验证的数字都会在答辩时被追问来源。

## 决策

1. 首版目录为**静态、版本化、可离线**的 JSON（`catalogueVersion = static-v1`）；
2. 价格是**带日期的参考价**（`priceDate`），UI 必须显示日期与"参考"字样；
3. **裁掉** performance band、FPS、跑分、实时库存、价格轮询；
4. 比较页只比较可验证字段（价格、兼容状态、分类花费、功耗余量、升级余量、尺寸）。

## 后果

正面：

- 完全离线，无网络权限，测试可复现；
- 每个数字都能回答"从哪来、什么时候"；
- 范围收缩后，六功能端到端可以在学期内完成。

负面：

- 产品卖点弱于"实时"竞品，需要在答辩中主动说明这是**证据优先的取舍**；
- 用户可能误以为价格是最新的 → 必须靠 UI 文案与日期标签缓解（`price_snapshot_label`）；
- 数据更新是手工流程（重新载入内置参考价）。

## 替代方案

| 方案 | 为什么不选 |
|---|---|
| 接电商 API | 授权、稳定性、合规都不成立；且破坏离线可测性 |
| 保留分档但标"估算" | 仍是无来源数字；答辩风险大于收益 |
| 允许用户手动填价 | 引入"用户数据"语义，首版无账号体系承载 |

## 复核触发条件

- 拿到可授权、可缓存、可标注日期的数据源 → 提 scope-change；
- 评测集与可解释模型同时具备 → 再讨论性能分档。

## 答辩口径

"我们把'性能分档'从首版拿掉了：没有数据来源的分档就是猜测，
而我们的核心主张是**每个结论都带证据**。所以我们宁可少一个卖点，也不给一个说不清来源的数字。"


---

# ADR-0003 · 运行时确定性；LLM 只在开发期，且不做最终 oracle

- 状态：已采纳（2026-10）
- 影响：全部模块、测试体系、10 章工作流
- 相关：docs/08-testing-and-triage.md、docs/10-llm-delivery.md

## 背景

团队会用 LLM 写大部分代码；同时组内讨论过"让 LLM 判断兼容性/给建议"。
两种用法风险完全不同：

- **开发期**：LLM 生成代码与测试 → 风险是语义漂移，可用契约 + 矩阵控制；
- **运行期**：LLM 参与判定 → 风险是不可复现、不可解释、不可测。

## 决策

1. **首版运行时不含任何模型调用**。兼容性由确定性规则表判定，推荐/替换由确定性算法给出；
2. LLM 只出现在开发期：代码生成、测试意图生成、断言候选、失败解释；
3. LLM 断言**只做报警候选**，永远不能判定 app 有 bug；
4. CI 不调用在线模型，模型输出必须存档（`promptHash + modelVersion`）才能复现。

## 后果

正面：

- 同一配置永远得到同一结论，可被 fixture 锁定；
- 失败可归因（五类），不会被"模型这次不一样"掩盖；
- 答辩时能回答"为什么不用 AI 判断兼容"——因为兼容是**规则问题**，不是**语言问题**。

负面：

- 无法处理规则表未覆盖的"软"约束（例如"这个牌子口碑差"）；
- 规则覆盖度靠人工维护；
- 与"AI 装机助手"的市场叙事相比不够性感，需要主动解释。

## 替代方案

| 方案 | 为什么不选 |
|---|---|
| LLM 直接判定兼容 | 不可复现；缺字段时会"自信地猜" |
| LLM 只做解释文案 | 可接受但非必需；首版用模板 + TextKey 更稳 |
| 混合（规则 + LLM 兜底） | 兜底路径无法测试，等于给自己埋随机 bug |

## 复核触发条件

- 需要处理自然语言输入（"我想玩 2K 光追"）→ 用 LLM 解析成 `IntentSpec`，**再交给确定性 planner**；
- 该路径必须离线可重放（存档模型输出），且不得直接产出结论。

## 答辩口径

"LLM 在我们这里是**开发工具**，不是**运行时裁判**。
兼容性判断必须能被复现和解释，所以它是规则表；LLM 负责帮我们更快写出这些规则和测试。"


---

# ADR-0004 · 纯 JVM domain + 端口适配器

- 状态：已采纳（2026-10）
- 影响：全部模块划分
- 相关：docs/05-module-independence.md

## 背景

六个人并行开发，其中大量代码由 LLM 生成。最大的风险不是写不出来，而是
**模块互相渗透**：ViewModel 里算规则、UI 里读数据库、domain 里 import Android。
一旦发生，任何一处的改动都会引发连锁修改，且无法单独测试。

## 决策

1. 所有 `domain:*` 与 `contracts`、`testing` 使用 `kotlin("jvm")`，**无 Android 插件**；
2. 模块之间只能通过 `contracts` 中的数据类型与端口接口通信；
3. 实现类一律 `internal`，跨模块不可见；
4. 依赖矩阵（05 章 §5.3）由 `ci/check-boundaries.mjs` 强制执行；
5. 第三方库（Room、Compose）一律 `implementation`，不随依赖链泄露。

## 后果

正面：

- "UI 读 DAO" 在编译期就不可能（DAO 类型不在 ui 的 classpath）；
- domain 测试 5 秒跑完，不需要模拟器；
- 替换实现（Room ↔ 内存）不影响任何调用方；
- LLM 看不见的东西，它写不出来——这是最有效的约束。

负面：

- 模块数 13 个，构建配置成本上升；
- 需要写端口与 Fake，前期代码量 +20%；
- 对初学者而言"为什么不能直接调用"需要解释。

## 替代方案

| 方案 | 为什么不选 |
|---|---|
| 单模块 + 包结构约定 | 靠自觉，LLM 一定违反；且无法单独测试 |
| 只分 app/data/domain | UI 与 feature 仍可互相 import 实现 |
| 使用 DI 框架约束依赖 | 运行期才报错，太晚 |

## 复核触发条件

- 模块构建时间 > 3 分钟 → 合并过细模块（优先合并 recommend/compare）；
- 端口方法数 > 8 → 拆端口（05 章 §5.6 反例 7）。

## 答辩口径

"我们把 domain 做成**纯 Kotlin**，所以它连 Android 的类都 import 不到。
这不是纪律问题，是编译问题——写错就编译不过。"


---

# ADR-0005 · 规则数据化（DSL 7 种 kind）

- 状态：已采纳（2026-10）
- 影响：domain:rules、data（解析）、fixtures
- 相关：docs/03-rules.md

## 背景

兼容性规则如果写成代码分支，会带来三个问题：

1. 每条规则的判定顺序、文案、边界都由实现者决定，LLM 每次写得都不太一样；
2. 新增硬件代际要改代码、改测试、改 UI；
3. 规则之间无法用统一的方式做"四类用例"覆盖。

## 决策

1. 规则存为 JSON（`assets/rules/rules-v1.json`），运行时解析为 `RuleDef`；
2. DSL 只支持 7 种 kind：`EQUALS / CSV_CONTAINS / CSV_SUBSET / NUMERIC_LE / NUMERIC_GE / COUNT_LE / SUM_LE`；
3. 判定顺序五步（部件缺失 → 来源冲突 → 缺字段 → 通过 → 冲突）冻结；
4. 每条规则强制 4 类 fixture，由生成器保证；
5. 解析在 `data`，引擎只接收 `List<RuleDef>`（domain 不认识 JSON）。

## 后果

正面：

- 新增代际 = 加数据 + 加 fixture，不改引擎与 UI；
- 判定顺序统一，测试矩阵可枚举；
- 规则可被非程序员审阅（一行 JSON 就是一条规则）。

负面：

- 表达力受限：需要"先算再比"的规则只能靠 `formula` 特例（目前仅 PSU）；
- 解析错误会变成运行期问题（用启动校验缓解）；
- 复杂规则会被硬塞进 7 种 kind，需要评审阻止。

## 替代方案

| 方案 | 为什么不选 |
|---|---|
| 规则写代码 | 无法矩阵化覆盖；LLM 产出不一致 |
| 通用表达式引擎（如 SpEL） | 表达力太强，等于允许写代码；不可审计 |
| 硬编码 15 条 + 枚举 | 新增代际要改代码，违背提案承诺 |

## 复核触发条件

- 出现第 3 条需要 `formula` 的规则 → 说明 DSL 不足，评估加 kind（走契约变更）；
- 规则数 > 40 且出现相互依赖 → 考虑引入分组/优先级（当前无此需求）。

## 答辩口径

"规则不是写在代码里的 if，而是**一张表**。
新增一代内存或一张显卡，我们改的是数据与测试用例，不是界面。"


---

# ADR-0006 · 三态证据模型，不新增第四种结果

- 状态：已采纳（2026-10）
- 影响：contracts、domain:rules、ui 文案
- 相关：docs/01-glossary.md §1.1、docs/02-contracts.md §2.3

## 背景

真实数据的质量是分级的：厂商规格页、零售商页面、社区帖子，可信度不同。
最初的模型只有"通过/冲突"，于是所有"数据不够"的情况都被压成了"通过"——
这正好是提案要解决的问题（"the app cannot claim compatibility on the strength of a field nobody has verified"）。

## 决策

1. 字段级证据三态：`VERIFIED / REPORTED / EMPTY`（强弱顺序即此）；
2. 规则级结果三态：`Passed / Conflict / Unverified`；
3. **不新增**第四种结果：来源冲突（同字段多来源数值不一致）表达为
   `Unverified(reason = SOURCE_CONFLICT)`，而不是新的 `Conflict` 或 `Unknown`；
4. 最弱证据向上传播：`weakest` 必须是参与比较的字段中最弱的那一个；
5. `REPORTED` 可以产生 `Passed`，但 UI 必须显示"基于报告数据"。

## 后果

正面：

- "缺证据"与"冲突"在类型上不可混淆；
- 测试矩阵维度固定（3 状态 × 3 结果），不会随需求膨胀；
- 用户看到的是"未验证：缺少 BIOS 信息"，而不是一个红叉。

负面：

- 来源冲突丢失了"左右值"的展示能力（因为不是 Conflict）→ 需在 `missingField` 上指路；
- UI 需要额外解释三态，文案成本上升；
- 数据录入必须标注来源，录入工作量增加。

## 替代方案

| 方案 | 为什么不选 |
|---|---|
| 加第四种结果 `Unknown` | 与 Unverified 语义重叠，矩阵维度翻倍 |
| 把来源冲突做成 Conflict | 会诱导用户"选一个数值"，而正确做法是去确认 |
| 用布尔 `verified` | 丢失 REPORTED 与 EMPTY 的区别 |

## 复核触发条件

- 出现"合法但互相矛盾"的规格（例如厂商自己两版文档冲突）→ 仍归 SOURCE_CONFLICT，并附来源列表；
- 若未来需要"部分可信"的连续置信度 → 那是另一个模型，必须新 ADR。

## 答辩口径

"三态不是三个枚举值，是三种**责任**：
厂商说的、别人说的、没人说的。规则继承最弱的那一个——
所以只要有一项没人确认，我们就不会说'兼容'。"


---

# 12 · Prompt 模板（可直接复制）

> 用法：复制对应模板 → 把 `{{...}}` 替换为实际内容 → 连同 §0 的"通用头"一起发给 LLM。
> 每个模板都遵守 06 章的六块结构；**不要删减块**，删了就会漂移。

---

## 12.0 通用头（每个任务都要带）

```
你是 PC Buildmate 项目的实现者。项目规格见 docs/（我已给你相关章节）。

硬性约束（违反即作废）：
1. 只实现本任务描述的范围；不要修改其它模块，不要"顺手优化"。
2. 不得修改 contracts/ 下的任何文件；不得修改 assets/fixtures/ 的期望值。
   如果确实需要，停止生成，列出原因与建议的契约 diff。
3. 先写测试，再写实现。每个测试都必须在"实现写错"时失败。
4. 输出必须确定性：所有集合在输出前显式排序，不使用随机、时钟、Locale。
5. 遇到规格未覆盖的情况，列出 2–3 个选项并等待我选择，不要自行决定。
6. 不引入新依赖，除非我明确批准。不使用 Android API（除本模块声明允许的）。
7. 不要生成 TODO / FIXME / println。
8. 完成后输出：改了哪些文件、跑了哪些命令、矩阵覆盖情况（ruleId/case 或 fsm 格子）。
```

---

## 12.1 D1 硬件数据库

```
【目标】
实现 :data 的目录加载与查询端口，使 D3 能在无 Android 依赖的情况下读到只读目录。
不做兼容性判断，不做价格计算。

【输入契约】
- contracts/Model.kt 的 CatalogueRecord / FieldValue / FieldSource
- contracts/Ports.kt 的 CataloguePort
- assets/catalogue/demo-v1.json（格式样例；生产 seed 另给）
- contracts-test/.../CataloguePortContract.kt（必须继承并通过）

【输出契约】
- AssetCatalogueAdapter : CataloguePort
- SeedValidator：启动时校验（路径在 docs/03-rules.md §3.3 表内、id 唯一、isDemo 与目录分离、数值可解析）
- 查询结果按 componentId 字典序

【禁止事项】
- 禁止网络、实时价、库存（docs/09-ci-scope.md §9.2）
- 禁止在数据层计算兼容性
- 禁止把 isDemo=true 的记录放进生产 seed
- 禁止新增字段路径

【验收用例】
1. getById 命中返回完全相等的记录
2. getById 未命中返回 null 不抛异常
3. search 大小写不敏感、结果确定
4. 重复 id -> 启动 fail-fast
5. 非法路径 -> 启动 fail-fast
6. demo id 出现在生产 seed -> fail-fast

【失败模式】
NotFound -> null；SchemaViolation -> 启动即抛（数据层允许 fail-fast）；EmptyCatalogue -> 空列表
```

---

## 12.2 D3 兼容性检查（规则引擎）

```
【目标】
实现 :domain:rules 的 CompatibilityEvaluator：输入 BuildRevision，输出按 ruleId 字典序的 List<RuleOutcome>。
纯 Kotlin，无 Android、无 I/O、无时钟。

【输入契约】
- contracts/Outcomes.kt（RuleOutcome / UnverifiedReason / Severity）
- contracts/Ports.kt（CataloguePort / CompatibilityEvaluator）
- docs/03-rules.md §3.2–§3.4（DSL 与五步判定）、§3.7（PSU 公式）
- assets/rules/rules-v1.json（15 条规则）
- assets/fixtures/fixtures-rules-v1.json（60 条，作为验收数据）

【输出契约】
- evaluate 结果按 ruleId 字典序
- Conflict 带 conflictId / 左右 FieldRef / 左右值 / severity
- Unverified 带 reason，能定位时带 missingField
- 同输入两次调用结果 == 相等

【禁止事项】
- 禁止缺字段 PASS（INV-04）
- 禁止把 REPORTED 当 VERIFIED
- 禁止读价格/预算/时间/UI 文案
- 禁止抛异常（未知规则降级为 UNSUPPORTED_RULE 并记日志）
- 禁止改 §3.7 公式

【验收用例】
1. 60 条 fixture 全绿（参数化，@Tag("rule:*") @Tag("case:*")）
2. 337/320 -> CONFLICT，conflictId 固定
3. BIOS EMPTY -> UNVERIFIED，missingField 指向 mb.biosGeneration
4. 两来源 337/335 -> UNVERIFIED(SOURCE_CONFLICT)
5. 空 revision -> 空列表
6. PSU 等号/差 1/取整三个边界

【失败模式】
未知 ruleId/路径 -> Unverified(UNSUPPORTED_RULE)；缺部件 -> 该规则 Unverified(MISSING_FIELD)
```

---

## 12.3 D5 替换建议

```
【目标】
实现 :domain:replacement 的 ReplacementAdvisorPort：先过硬约束过滤，再按固定键排序，无候选给结构化原因。
不修改 build，不调用 UI。

【输入契约】
- contracts/Suggestions.kt（Candidate / SuggestionResult / NoCandidateReason）
- contracts/Outcomes.kt（RuleOutcome.Conflict）
- contracts/Ports.kt（CataloguePort / CompatibilityEvaluator）
- docs/06-modules.md#d5（算法契约）
- assets/fixtures/fixtures-anchor-v1.json 的 fx_suggestion_300_plus_100

【输出契约】
- 每个候选替换后该 conflictId 消失
- 候选不得引入新的 BLOCKER Conflict（引入 MAJOR/MINOR 的保留并写入 affectsOtherRules）
- 排序：priceDeltaCny 升序 -> 对剩余余量影响小 -> componentId 字典序
- 无候选：candidates=[] 且 noCandidate 非空

【禁止事项】
- 禁止先排序再过滤
- 禁止用综合分抵消不兼容
- 禁止隐藏会影响其它规则的候选而不标注
- 禁止声称"最优替换"

【验收用例】
1. 337/320 -> 含 300mm，priceDelta=100，rank=1
2. 会引入 PSU 不足的候选被过滤或标注
3. 无候选 -> blockingConstraint 指向 ruleId+fieldPath
4. 同输入两次顺序一致
5. base 过期 -> noCandidate(blockingConstraint="STALE_REVISION")

【失败模式】
无候选/目录空/陈旧 base -> 结构化返回，不抛异常
```

---

## 12.4 D2 工作区（ViewModel 编排）

```
【目标】
实现 :feature 的 WorkspaceViewModel：Intent -> 新 revision -> 驱动两张状态机 -> 执行 Effect -> 单一 UiState。
不计算规则、不直接访问 Room、不自动重检。

【输入契约】
- contracts/Workspace.kt（WorkspaceIntent / WorkspaceUiState / Effect / 两张状态机类型）
- docs/04-state-machines.md（全部转移表，这是唯一权威）
- docs/06-modules.md#d2（不变量与失败模式）
- :domain:state 的 transition 函数（纯函数，直接调用）

【输出契约】
- StateFlow<WorkspaceUiState>
- 每次编辑产生新 revision，totalCny 同步
- 编辑后 diagnosisState == Idle 且 currentDiagnosis == null
- 只有经 Rechecking 才能 Applied
- 被拒绝的意图不改 state，只写 rejection

【禁止事项】
- 禁止写兼容性 if / 直接调 DefaultRuleEngine
- 禁止编辑后自动 StartCheck
- 禁止把草稿混入 savedBuilds
- 禁止 GlobalScope；协程必须在 viewModelScope 且可取消

【验收用例】
1. 空 build + Add CPU -> 新 revision、total 更新、Idle
2. 已诊断 + SetQuantity -> currentDiagnosis=null、banner 出现、无 Running
3. 慢检查迟到 -> 丢弃（交错用例，用可控 Deferred，不用 sleep）
4. Undo -> 新 id 更大、内容等于上一快照
5. preview 中 base 过期 -> Rejected(APPLY_REVISION_STALE)，build 不变
6. 非法 JSON plan card -> 拒绝，savedBuilds 不变

【失败模式】
全部通过 Rejection 表达（CATALOGUE_MISS / QUANTITY_INVALID / APPLY_REVISION_STALE / IMPORT_INVALID_SCHEMA）
```

---

## 12.5 D4 预算推荐

```
【目标】
实现 :domain:recommend 的 BudgetRecommenderPort：预算+用途+偏好 -> 可编辑起始配置；权重可见；不可行必报。

【输入契约】
- contracts/Suggestions.kt（RecommendationRequest / RecommendationResult）
- contracts/Ports.kt（CataloguePort / CompatibilityEvaluator）
- docs/06-modules.md#d4
- docs/03-rules.md §3.6（硬约束清单）

【输出契约】
- feasible=true => total <= budget，且无 BLOCKER Conflict
- feasible=false => shortfall > 0，relaxedPreferences 非空（若放宽）
- weightsVisible 非空且与实际分配一致
- 同输入同目录版本结果确定

【禁止事项】
- 禁止声称最优
- 禁止性能分数/FPS/跑分排序
- 禁止超预算或放宽硬约束而不报告
- 禁止隐藏权重

【验收用例】
1. 8000/GAMING -> total<=8000，硬约束无 BLOCKER
2. 1000/GAMING -> feasible=false，shortfall>0
3. GAMING vs STUDY_OFFICE -> GPU 权重前者大，且均可展示
4. 推荐结果再跑引擎 -> 无新 BLOCKER

【失败模式】
空目录/预算过低/偏好不可满足 -> 结构化不可行结果
```

---

## 12.6 D6 比较

```
【目标】
实现 :domain:compare 的 BuildComparatorPort：2–3 个 saved build 并排；只读已有诊断；过期不显示旧结论。

【输入契约】
- contracts/Suggestions.kt（ComparisonResult / FieldDelta）
- contracts/Ports.kt（BuildComparatorPort / BuildStorePort）
- docs/06-modules.md#d6（字段冻结清单）

【输出契约】
- diagnoses[i] 非空 <=> 该 build 诊断 revision 匹配
- warnings 用 TextKey
- 字段：总价、兼容状态、分类花费、功耗余量、升级余量、物理尺寸
- 不做 performance band

【禁止事项】
- 禁止调用规则引擎
- 禁止把过期诊断当当前结论
- 禁止修改 build

【验收用例】
1. 2 个 build -> 字段对齐、delta 正确
2. 某 build 诊断过期 -> 该列为 null + warning
3. <2 个 build -> 拒绝
4. 目录版本不同 -> warning，仍比较

【失败模式】
InsufficientBuilds -> Rejected；DiagnosisStale -> null + warning；BuildNotFound -> 整体错误
```

---

## 12.7 D7 UI 绑定

```
【目标】
实现 :ui 的四屏 Compose 界面：只读 WorkspaceUiState，只发 WorkspaceIntent，不含业务计算。

【输入契约】
- contracts/Workspace.kt（WorkspaceUiState / WorkspaceIntent / TextKey）
- docs/06-modules.md#d7
- docs/13-risks.md §13.4（哪些话不能出现在 UI）

【输出契约】
- 每个可见元素有稳定 testTag（用 TextKey.key 或语义 id）
- 相同 state 渲染相同 UI（无内部可变状态）
- 360dp 小屏关键操作可达

【禁止事项】
- 禁止 import data / feature 实现 / domain
- 禁止在 Composable 里算兼容性、价格、delta
- 禁止自行拼接冲突文案（用 leftValue/rightValue + 资源模板）
- 禁止用 onNodeWithText 断言长句

【验收用例】
1. 有冲突 state -> 可见 conflict_<ruleId> tag，左右值均显示
2. stale state -> 可见 diagnostics_stale_banner，不可见 PASSED 徽标
3. 空 build 点检查 -> 提示且未导航
4. 360dp -> 关键按钮可达

【失败模式】
guard 失败只提示不导航；过期诊断只显示 banner
```

---

## 12.8 收尾模板（每次任务结束贴一次）

```
请按以下格式汇报，不要写散文：
1. 改动文件清单（按模块）
2. 新增/修改的测试（列出 tag：rule/case/inv/fsm）
3. 已运行的命令与结果
4. 矩阵覆盖：rules __/60，fsm __/76，inv __/18
5. 遗留问题（含规格空白，用 §10.5 格式）
6. 是否触碰 contracts/ 或 fixtures/（应为"否"）
```


---

# 13 · 风险、TBD 与答辩口径

> 读者：全员（尤其答辩前）。
> 原则：**不假装解决了没有解决的问题**。承认边界比夸大能力安全。

---

## 13.1 已知风险

| # | 风险 | 影响 | 缓解 | 残余风险 |
|---|---|---|---|---|
| R1 | 需求未做用户验证 | 产品价值是推断 | 用 2 位真实用户做任务测试（07 章 §0.9） | 样本小，不能外推 |
| R2 | 静态目录不等于真实市场 | 结论只在目录内成立 | UI 保留"未验证"；价格标日期 | 用户可能误解为"全网最全" |
| R3 | 参考模型只覆盖可判定部分 | 推荐/替换"最优"无人守 | 明确不对"最优"做断言 | 排序争议只能人判 |
| R4 | LLM 断言假阳性 | 误报消耗人力 | 只报警不判 bug + 统计假阳性率 | 假阳性率高时报警会被忽略 |
| R5 | 13 个模块对课程偏多 | 构建与同步成本 | 合并路径已预留（05 章 §5.11） | 前期搭建慢 |
| R6 | BIOS 版本号语义不完整 | 1.10 vs 1.9 误判 | 正则限制次版本 ≤ 9 | 真实 BIOS 命名不遵循此约定 |
| R7 | demo 数据与生产数据混淆 | 答辩现场"这数据哪来的" | isDemo 物理隔离 + 启动校验 | 演示时口误 |
| R8 | 六人分工与模块数不匹配 | 有人空转/有人过载 | 一人可负责 2–3 个 domain 模块 | 集成期拥堵 |
| R9 | 工期：六功能端到端 | 做不完 | 严格按 S1–S7 顺序，每步可交付 | 后期 UI 打磨不足 |
| R10 | "AI/智能"叙事与实现不符 | 被问"哪里智能" | 统一口径见 §13.4 | 听众预期落差 |

## 13.2 仍待决定的最小 TBD（不影响开工）

| # | TBD | 建议值 | 何时必须定 |
|---|---|---|---|
| T1 | 最低 Android API | 26（Compose 要求） | S1 前 |
| T2 | 每批随机 seed 数 N | 50 | S4 前 |
| T3 | 「复制配置 / 另存为」是否在首版 | **建议在**（复用 SaveBuild + 新 buildId，不加新 Intent） | S4 前 |
| T4 | 静态目录条目数 | 8 类 × 3–8 件 | S1 前 |
| T5 | 推荐权重具体数值 | 只要求可见可调，测试只测不变量 | S5 前 |
| T6 | 草稿保留策略（几个） | 每个 build 1 个 | S4 前 |

## 13.3 三份材料的一致性检查（答辩前必做）

仓库里同时存在：提案 docx、15 分钟讲稿 pdf、Q&A 备忘录 pdf。
它们与本设计文档有 **5 处需要统一**，不一致会被追问：

| # | 材料原文 | 本设计 | 建议处理 |
|---|---|---|---|
| C1 | 提案 D6 比较页含 "an indicative performance band" | **已裁掉**（ADR-0002，范围外） | 以本设计为准；PPT 若仍有该词，删掉或改为"可比较的字段" |
| C2 | 提案 "Why Android" 列举相机/AR/推送 | 范围外（09 章 §9.1） | 讲成"平台**可能性**"，明确"首版不做" |
| C3 | 提案 "duplicate a build" | 首版无独立 Intent（TBD-3） | 统一说"另存为一份配置" |
| C4 | 讲稿 Slide 16：7800 → 7900、BIOS 未验证 | 与 fixture `fx_suggestion_300_plus_100` 完全一致 | **保持**，这是最好的演示锚点 |
| C5 | Q&A Q16："界面是概念原型，数据是演示数据" | 与 `isDemo` 隔离一致 | 保持；演示前确认屏幕上显示"演示数据"标识 |

**检查方法**：把 PPT 每页的"承诺句"抄成一张表，逐条对照 00 章 §0.5 与 09 章 §9.1。
凡是对不上的一句，要么改 PPT，要么提 scope-change——**不要在台上临场解释**。

## 13.4 答辩口径：能说 / 不能说

| 场景 | 能说 | 不能说 |
|---|---|---|
| 兼容性 | "我们对 15 条可判定的规则给出通过/冲突/未验证" | "我们能保证整机一定兼容" |
| 数据 | "静态目录 + 带日期的参考价，字段有证据等级" | "价格是实时的" |
| 智能 | "用当前配置与冲突来筛选候选，先过硬约束再排序" | "我们有 AI/机器学习" |
| 推荐 | "启发式，权重可见可调，不承诺全局最优" | "最优配置" |
| 比较 | "按价格、兼容状态、功耗与升级余量并排比较" | "预测游戏帧数" |
| 进度 | "已完成 X，正在做 Y"（按仓库实际） | 百分比、完成率、"基本做完" |
| 用户验证 | "我们计划用 2 位目标用户做任务测试" | "用户都很喜欢" |
| 竞品 | "PCPartPicker 目录更深，BuildCores 展示更强；我们聚焦手机上的闭环" | "他们没有这个功能"（要加"据我们了解"） |

## 13.5 演示脚本（与讲稿 Slide 16 对齐）

```
1. 打开 Entry：预算 8000，用途 GAMING。
2. 载入起始配置：总价 7800。
3. 进入 Workspace：展示 337mm 显卡与 320mm 机箱同屏。
4. 点"检查兼容性"：诊断页出现 BLOCKER 冲突（337 > 320），
   同时 BIOS 一行显示"未验证——缺少该字段"。
5. 点冲突 → 候选列表出现 300mm（+100 元，rank 1）。
6. 点预览：总价变 7900，仍在 8000 内；冲突消失，BIOS 仍"未验证"。
7. 点应用：进入重检，通过后返回工作区。
8. 保存，再建一份更便宜的配置，进入比较页：两列并排，差异高亮。
9. 关键一句话："解决一个冲突，不代表全部兼容——这一行仍然是'未验证'。"
```

演示前检查：

- [ ] 屏幕上"演示数据"标识可见（`isDemo`）
- [ ] 价格旁显示日期（`price_snapshot_label`）
- [ ] 旧诊断不会在编辑后仍然显示（stale banner 正常）
- [ ] 没有网络权限（设置里确认）

## 13.6 被问"现在做到哪了"怎么答

模板（30 秒）：

> "仓库里现在是：契约、规则表、60 条 fixture、两张状态机与转移表已经冻结并可通过自检脚本；
> 数据层/规则引擎/工作区/UI 按 S1–S7 顺序推进，目前完成到第 X 步。
> 界面里的零件与价格是**演示数据**，用来验证流程与交互，不是市场数据。"

**禁止**：报百分比、报"准确率"、报用户数、说"基本完成"。
如果实际进度落后，就说落后在哪一步、下一步是什么——这比模糊表述可信。

## 13.7 如果你只有 5 分钟准备

按顺序记三件事：

1. **证据三态**：厂商 / 报告 / 没人 → 最弱传播 → 缺字段绝不通过。
2. **revision 保护**：每次编辑新快照，旧结论不得覆盖新配置。
3. **替换闭环**：先过滤硬约束 → 再排序 → 预览 → 全量重检 → 才应用。

这三条覆盖了评委最可能追问的 Q5、Q6、Q7、Q9、Q11（见 Q&A 备忘录）。


---

# 14 · 分工、里程碑与交接

> 读者：组长 / 全体成员。
> 本章只解决一件事：**13 个模块、6 个人、若干个 LLM，怎么排才不会互相堵住。**

---

## 14.1 角色与模块（一人可带多个 domain 模块）

| 角色 | 负责 | 决定的边界 | 不能单独决定 |
|---|---|---|---|
| P1 架构 / 契约守护 | `:contracts`、`:domain:state`、`ci/` | 契约变更的**第一道**批准；模块矩阵 | 规则语义（需 P3） |
| P2 数据层 | D1、`:data`、`assets/catalogue` | 目录字段填充、证据来源标注 | 字段路径（属契约） |
| P3 规则引擎 | D3、`:domain:rules`、fixture 期望值 | 规则语义、conflictId 形态 | 新增 ruleId（需契约变更） |
| P4 策略 | D5、D4、`:domain:replacement` / `recommend` | 过滤与排序键、无候选原因 | 新增端口 |
| P5 工作区 | D2、`:feature` | Intent 的编排细节 | 状态表（属 04 章） |
| P6 UI / 比较 | D7、D6、`:ui` / `domain:compare` | 布局、testTag、可访问性 | 状态与文案 key |

**共同职责**：每人对自己模块的**矩阵格子**负责——格子空着就是没做完（07 章 §7.5）。

## 14.2 四个里程碑（相对周，按课程日历对齐）

| 里程碑 | 目标 | 退出条件（可脚本判定） |
|---|---|---|
| **M1 契约与数据** | 契约冻结、目录可查、fixture 生成器可跑 | `check-docs` 绿；`gen-fixtures --check` 绿；`CataloguePortContract` 在两实现上通过 |
| **M2 规则与替换** | 15 条规则 + 60 fixture + 替换闭环 | rules 60/60；MUT-01..04/07 被杀；`e2e_gpu_337_to_300` 通过 |
| **M3 工作区与 UI** | 四屏流程跑通，状态机 76 格 | fsm 76/76；7 种时序用例通过；UI semantics 通过 |
| **M4 打磨与答辩** | 边界/关键词全绿、演示脚本可复现 | 四个脚本全绿；演示脚本 §13.5 一次跑通；三份材料口径一致（§13.3） |

**里程碑不是"做完功能"，而是"这一层的闸门全绿"**。功能做多了但矩阵空着，等于 M 没到。

## 14.3 每周节奏（15 分钟）

```
1. 看四个数字：rules __/60、fsm __/76、inv __/18、mutation __/12
2. 看 UNCLASSIFIED 清单（有就当场定人）
3. 看新增白名单（有就当场问理由）
4. 看本周是否有模块要"交接"（换人做）
```

不汇报"进度百分比"。数字之外的话，一律记成"下一步做什么、谁做、什么时候"。

## 14.4 交接协议

模块换人时，交接的不是代码，是**四样东西**：

1. 该模块的卡（06 章那一节）是否还准确——不准确就先改；
2. 该模块的矩阵覆盖（哪几格绿了、哪几格没做）；
3. 已知的坑（在 PR 描述或 `docs/13-risks.md` 里加一行）；
4. 未决的规格空白（按 §10.5 格式，不能口头交接）。

**代码可以重叠，职责不能重叠**：同一个模块同一周只能有一个"owner"，否则两种实现会同时长出来。

## 14.5 集成纪律

- `main` 每天必须全绿（四个脚本），红了当天修，不许"明天再说"；
- 契约变更单独 PR，**不与功能混**（否则 blast radius 看不见）；
- 每个人的分支生命周期 ≤ 3 天，超了就拆小步；
- 合并前必须 rebase 到最新 `main` 并重跑脚本（矩阵是快照，不是增量）。

## 14.6 时间不够时，按这个顺序砍

```
先砍：D6 比较（保留单列详情，去掉并排）
再砍：D4 推荐（保留"手动从目录选"）
再砍：D5 候选的排序（只按价格升序，去掉余量影响）
绝不砍：三态证据模型、revision 保护、替换前的全量重检
```

理由：前三个砍掉后，产品仍然**说真话**；后三个砍掉后，产品会**说假话**。
答辩时被问"为什么不完整"，答"我们优先保证不说错"，比答"时间不够"有力。

## 14.7 与其它章节的对应

| 本文 | 对应 |
|---|---|
| §14.1 角色 | 06 章每卡的"负责角色" |
| §14.2 里程碑 | 10 章 §10.2 的 S1–S7 与闸门 |
| §14.3 数字 | 07 章 §7.11 DoD、08 章 §8.7 批准条件 |
| §14.6 砍的顺序 | 09 章范围外、13 章答辩口径 |
