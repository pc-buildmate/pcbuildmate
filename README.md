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
| [COLLABORATION.md](COLLABORATION.md) | **Git / 分支 / PR / 任务包 / CI / 人工复核 / 排期**（现行协作协议） | 所有人 + AI |
| [AGENTS.md](AGENTS.md) | 给 AI 代理的硬规则与开工步骤 | 所有 AI |
| [docs/tasks/TEMPLATE.md](docs/tasks/TEMPLATE.md) | AI 可执行任务包的九块模板 | 派任务的人 |
| [materials/](materials/README.md) | 提案 / 讲稿 / Q&A 的 md 转写（**历史材料，非规格**） | 答辩前 |
| [site/](site/) | **交互原型**：可点击的四屏流程 + 状态机 + 不变量面板（演示数据） | 想核对 UI 逻辑时 |
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
| G1 | 契约变更走**独立 PR**；必须留 diff + 影响模块 + ADR；**Captain 批准后合并** | Captain | 记录 diff；不因契约变更阻塞其它检查 |
| G2 | 一个测试批次（milestone / release）的最终「通过」由人点批准；人不逐条看失败 | 人 | 只出报告，不代替人下结论 |
| G3 | 范围外功能是硬阻塞；解除必须提交 `scope-change` 文档并由人批准 | 人 | fail-closed，无白名单不放行 |

## 6. 交互原型（site/）

一份可点击的静态原型，用来核对 **UI 逻辑**（不是真机 App，也不需要构建）：

```bash
# 方式一：直接打开 site/index.html
# 方式二：
node tools/serve-site.mjs        # http://127.0.0.1:4173/
```

它把文档里的两张状态机、三态证据、revision 保护都实现成了可点的流程：
载入 ¥7,800 演示配置 → 检查（1 BLOCKER + 1 MAJOR + BIOS 未验证）→ 替换 300mm（+¥100）→
预览 ¥7,900 → 全量重检 → 应用。右侧面板可以实时看状态、不变量、事件日志，并模拟字段证据。

原型的逻辑**不是另写一套**：`site/engine.js` 直接跑本仓库的 60 条 fixture 与 8 条锚点，
`node tools/test-demo-engine.mjs` 会逐条比对。说明见 [site/README.md](site/README.md)。

## 7. 自检

```bash
node tools/check-docs.mjs      # 文档链接 / ID 引用 / 矩阵完整性 / JSON 合法性
node ci/check-boundaries.mjs   # 模块依赖边界（读取 ci/modules.json 与源码 import）
node ci/check-keywords.mjs     # 范围外关键词与权限
node tools/check-matrix.mjs    # 验证矩阵完整性（规则 60 / 状态机 76 / 不变量 18）

# 原型的自检（与上面同一套语义）
node tools/test-demo-engine.mjs  # 原型引擎 × 60 fixture + 8 锚点
node tools/test-demo-flow.mjs    # 无头跑完整用户旅程
node tools/test-demo-ui.mjs      # 全部界面的渲染冒烟测试
```

四个脚本都是**零依赖 Node**，不需要 Android SDK、不需要网络。
任何一条不通过，视为文档/代码未完成，而不是「稍后修」。

## 8. 版本与冻结

- 文档版本见 `docs/VERSION`；契约版本由 `contracts/` 的 git 提交 + API dump 决定。
- 每个构建、每个诊断、每份测试报告都必须记录三元组：`catalogueVersion / rulesVersion / commit`。
- 失败报告额外记录：`seed / promptHash / modelVersion`（见 08 章 §8.5）。
- **契约只允许经 ADR + 单独提交变更**；实现 PR 里顺手改 `contracts/` 一律打回（见 02 章 §2.9）。

## 9. 当前状态（2026-11-04）

- 规格、契约、规则、fixture、交互原型：**已完成**，四个自检脚本全绿。
- Android 工程：**S1 骨架已建**（14 模块，`compileSdk 37` / `minSdk 31`），`:app:assembleDebug` 通过，产出 debug APK。
- D1 数据层：**已完成**（目录解析 + 路径/CSV/版本校验 + `CataloguePort` 实现 + 26 条单测）。
- D3 规则引擎：**已完成**（15 条规则 + PSU 公式；60 条 fixture 全绿）。
- D5 替换建议：**已完成**（先过滤 BLOCKER，再按价格/影响/ID 排序；5 条测试，含 337→300 锚点）。
- D2 工作区：**已完成**（76 格状态机 + ViewModel；Entry/Workspace/Diagnostics 三屏真机跑通，7800→7900）。
- Git：已推送到 <https://github.com/pc-buildmate/pcbuildmate>；`main` 是文档基线，S1 在 `chore/s1-scaffold`，待 PR 合并。
- 答辩材料：提案 / 讲稿 / Q&A 已转写为 md，见 [materials/README.md](materials/README.md)。

## 10. 原始材料

`materials/source/` 下是提案 docx、15 分钟讲稿 PDF、Q&A PDF 的 md 转写，
**只用于答辩口径与历史对照，不是规格**。与本仓库冲突时以 `docs/` + `contracts/` 为准；
五处已知差异列在 [materials/README.md](materials/README.md)。

## 11. 工程与构建（S1 起）

- 模块矩阵与 `ci/modules.json` 一致：`:contracts`、`:contracts-test`、`:domain:{build,rules,replacement,recommend,compare,state}`、`:data`、`:feature`、`:ui`、`:app`、`:testing`、`:test-oracle`。
- `compileSdk = 37`，`minSdk = 31`（Android 12+），`arm64-v8a` 为发布基线。
- 安装身份：`applicationId = cn.org.this.pcbuildmate`（反向域名，与 GitHub 邮箱 `pc-buildmate@this.org.cn` 同域）。
  代码 namespace 保留 `pcbuildmate.*`：`this` 是 Java/Kotlin 保留字，不能作为包名段；两者不必相同。
- 依赖仓库在 `settings.gradle.kts`：阿里云镜像优先，官方仓库回退；**不提交任何私人代理地址**。
- 本机 SDK 写在 `local.properties`（已 gitignore）：

```bash
export ANDROID_HOME=/Volumes/SA500/Library/Android/sdk
./gradlew :app:assembleDebug
./gradlew :data:testDebugUnitTest
```

首次构建需要 JDK 17+ 与 Android SDK Platform 37。若本机网络需要代理，用 `GRADLE_OPTS` 注入，不要写进仓库：

```bash
export GRADLE_OPTS="-Dhttps.proxyHost=127.0.0.1 -Dhttps.proxyPort=1080 \
  -Dhttp.proxyHost=127.0.0.1 -Dhttp.proxyPort=1080"
```
