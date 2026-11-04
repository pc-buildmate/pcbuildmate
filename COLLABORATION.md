# COLLABORATION · 协作与交付协议

> 版本 `v0.1-draft` · 2026-11-04 · 组长确认后冻结为 `v1.0`
>
> 读者：**6 名组员 + 所有 AI 代理**。本文件管「怎么做」，不管「做什么」；
> 「做什么」以 [docs/](docs/) 与 [contracts/](contracts/) 为准。
>
> 一句话：**`main` 永远可构建；每个任务是一份可执行的规格包；AI 只写被允许的文件；人只做四类复核。**

---

## 0. 一页速览

### 0.1 五条铁律

| # | 规则 | 违反的后果 |
|---|---|---|
| L1 | `main` 永远可构建、可跑四个自检脚本 | 当天回滚，不许「明天修」 |
| L2 | 一个任务 = 一个分支 = 一个模块 | PR 打回拆小 |
| L3 | `contracts/`、`assets/fixtures/`、`docs/01`–`04` 只读 | 变更必须单独 PR + ADR + 组长批准 |
| L4 | AI 只改任务包 `allowed paths` 里的文件 | PR 打回；不得「顺手重构」 |
| L5 | 契约、UI、APK、范围变更必须人工复核 | AI 不得自行宣布完成 |

### 0.2 三条命令（所有 PR 的底线）

```bash
node tools/check-docs.mjs
node ci/check-boundaries.mjs && node ci/check-keywords.mjs
./gradlew :domain:test :data:test        # S1 建工程后生效
```

### 0.3 当前状态（2026-11-04）

- 规格、契约、规则、fixture、原型：**已完成并自检全绿**。
- Android 工程：**S1 骨架已建**（14 模块，`compileSdk 37` / `minSdk 31`），`:app:assembleDebug` 通过。
- D1 数据层：**已完成**（解析 + seed 校验 + `CataloguePort` + 26 条单测）。
- D3 规则引擎：**已完成**（15 条规则 + PSU 公式；60/60 fixture）。
- D5 替换建议：**已完成**（5 条测试；337→300 锚点）。
- D6 比较：**已完成**（4 条测试；真机 A/B 对比）。
- Git：本地已初始化，基线 `main` + `chore/s1-scaffold`；GitHub 共享账号待建，push/CI 未接。
- 网络：代理 `127.0.0.1:1080` 可用；Gradle 走阿里云镜像（`settings.gradle.kts`），
  不提交私人代理地址。API 31+ 模拟器镜像仍需下载。
- L1 的「可构建」从 S1 合入 `main` 起生效；合并前以本地门禁为准。

---

## 1. 事实源与裁决顺序

同一件事只允许一个权威来源。**下层不能覆盖上层**。

| 层 | 位置 | 是什么 | 谁能改 | 冲突时 |
|---|---|---|---|---|
| S1 语义 | `docs/01-glossary.md` | 术语、三态/三结果 | 组长 + ADR | 以本文为准 |
| S2 契约 | `docs/02-contracts.md`、`contracts/src/**` | 数据类型、不变量、端口 | 组长 + ADR | 以契约为准 |
| S3 规则 | `docs/03-rules.md`、`assets/rules/`、`assets/fixtures/` | 15 条规则、60 条 fixture | 组长 + 规则 owner | 以 fixture 期望值为准 |
| S4 状态机 | `docs/04-state-machines.md` | 76 格转移表 | 组长 + 工作区 owner | 以转移表为准 |
| S5 模块卡 | `docs/06-modules.md` | 每模块职责/禁止/验收 | 模块 owner + 组长 | 以上层为准 |
| S6 工程约束 | `docs/05`、`docs/07`、`docs/09` | 依赖矩阵、验证、范围 | 任何 PR | 以上层为准 |
| S7 代码 | `app/`、`core/`、`domain/`、`data/`、`feature/`、`ui/` | 实现 | 模块 owner | 代码与 S1–S6 不一致 = 代码 bug |
| S8 原型 | `site/` | 可点击的 UI 逻辑参考 | UI owner | 以 S2–S4 为准 |
| S9 历史材料 | `materials/source/` | 提案、讲稿、Q&A 的 md 转写 | 只读 | **不作规格**；只用于答辩口径 |

**裁决规则**

1. 实现与规格冲突 → 改实现，不改规格。
2. 规格之间冲突 → 查 `docs/01`；仍不能解 → 提 ADR，组长裁决。
3. 提案/讲稿/PPT 与本仓库冲突 → 以本仓库为准；答辩材料必须改到一致（见 `docs/13 §13.3`）。
4. 任何人（包括 AI）不得在 PR 里「顺手」修改 S1–S4 来让自己编译通过。

### 1.1 哪些文件是「冻结区」

以下路径的变更**必须**单独开 `contract:` / `spec:` 分支，附 ADR 或影响说明，由组长批准：

```
contracts/**
contracts-test/**
assets/rules/**
assets/fixtures/**
docs/01-glossary.md
docs/02-contracts.md
docs/03-rules.md
docs/04-state-machines.md
docs/VERSION
```

以下路径**禁止手改**，只能由脚本生成：

```
site/data.generated.js      # node tools/gen-site-data.mjs
dist/**                     # node tools/build-single-md.mjs
build/** .gradle/**         # 构建产物，已在 .gitignore
```

> **对 `docs/02 §2.9` 的收紧**：该节允许「追加枚举值 / 带默认值的字段 / 端口方法 / TextKey」
> 无需人批、只留痕。本协议统一收紧为：**上述变更也要 Captain 批准**（一个 `contract:` PR 即可）。
> 原因：课程工期只有四周，AI 顺手扩契约的代价远高于多一次点击。语义本身仍按 §2.9 判定。

---

> 当前任务清单：[docs/tasks/BOARD.md](docs/tasks/BOARD.md)。**自由认领**：想做哪个先把表里的“认领”改成自己。

## 2. 角色与所有权

### 2.1 人类角色

| 角色 | 谁 | 拥有 | 可以合并 | 不能单独决定 |
|---|---|---|---|---|
| **Captain / 架构** | 待填 | `contracts/`、`ci/`、`main`、发布 | 所有 PR | 规则语义（需规则 owner） |
| **Data owner** | 待填 | `data/`、`assets/catalogue/` | `data/**` | 契约字段路径 |
| **Rules owner** | 待填 | `domain/rules/`、fixture 期望值 | `domain/rules/**` | 新增 ruleId |
| **Strategy owner** | 待填 | `domain/replacement/`、`domain/recommend/` | 对应目录 | 新端口 |
| **Workspace owner** | 待填 | `feature/`、状态机实现 | `feature/**` | 状态表 |
| **UI owner** | 待填 | `ui/`、`domain/compare/`、`site/` | `ui/**`、`site/**` | 状态与文案 key |

> 现实约束：**允许 owner 暂时不活跃**。Captain 是所有人的备份 owner；
> 但「备份」不等于「改语义」——语义变更仍走冻结区流程。

### 2.2 AI 代理

AI 是**执行者**，不是所有者：

- 只能在一个已分配的任务包（`docs/tasks/<id>.md`）范围内工作；
- 不能合并 PR、不能打 tag、不能改 `main`；
- 不能在 PR 里改契约、fixture 期望值、CI 白名单；
- 遇到规格空白必须停下，给 2–3 个选项，等人选；
- 产出必须附：改了哪些文件、跑了什么命令、输出是什么、还有什么没做。

### 2.3 所有权矩阵（按路径）

| 路径 | Owner | 备份 |
|---|---|---|
| `contracts/**`、`ci/**`、`.github/**` | Captain | 无（只能 Captain） |
| `data/**`、`assets/catalogue/**` | Data | Captain |
| `domain/rules/**` | Rules | Captain |
| `domain/replacement/**`、`domain/recommend/**` | Strategy | Captain |
| `feature/**` | Workspace | Captain |
| `ui/**`、`site/**`、`domain/compare/**` | UI | Captain |
| `docs/**`、`materials/**` | Captain | 各模块 owner 可提 PR |
| `app/**`（组装、导航、DI） | Captain | — |

**同一路径同一周只能有一个 owner。** 换人要交接四样东西（见 `docs/14 §14.4`）。

---

## 3. GitHub 一次性设置

### 3.1 仓库

- 名称：`pc-buildmate`（私有）。
- 默认分支：`main`。
- 不建 `develop` 分支；**trunk-based**，短分支直接进 `main`。
- 开启：Issues、Projects、Actions；关闭：Wiki、Discussions（用 Issue 就够）。
- 邀请 6 名成员为 `Write`；只有 Captain 为 `Admin`。

### 3.2 分支保护（Settings → Branches → `main`）

| 选项 | 值 |
|---|---|
| Require a pull request before merging | ✅ |
| Require approvals | 1（Captain 可用 admin 权限在 24h 无 review 后自审，须在 PR 留言理由） |
| Dismiss stale approvals | ✅ |
| Require status checks | `docs`、`jvm`（S1 后加 `apk`） |
| Require conversation resolution | ✅ |
| Require linear history | ✅ |
| Allow force pushes / deletions | ❌ |
| Include administrators | ❌（保留 Captain 应急合并能力） |

### 3.3 合并方式

只允许 **Squash and merge**；合并后自动删分支。
一个 PR 一个 commit 进 `main`，历史干净，回滚容易。

### 3.4 标签（Labels）

```
module:d1  module:d2  module:d3  module:d4  module:d5  module:d6  module:ui  module:ci
type:feat  type:fix  type:test  type:docs  type:chore  type:contract  type:scope-change
status:ready  status:doing  status:review  status:blocked  status:done
ai:ready  needs-human  solo-merge  good-first-task
```

### 3.5 看板（Projects）

列：`Backlog → Ready → Doing → Review → Done`。
Issue 的 `status:*` 标签与列一一对应；**任务包文件是权威，Issue 只是索引**。

### 3.6 本地与远程（已接通，2026-11-04）

```bash
cd PC_BUILDMATE
git remote add origin git@github.com:pc-buildmate/pcbuildmate.git
git push -u origin main
git push -u origin chore/s1-scaffold
```

- 仓库：<https://github.com/pc-buildmate/pcbuildmate>（public，默认分支 `main`）
- 认证：仓库级 **Deploy Key**（`dsh-agent-1`，已勾选 write access）；私钥在本地 `.ssh/`，已 gitignore
- `main` 目前只有文档基线；S1 在 `chore/s1-scaffold`，走 PR 合并
- 不要把 `local.properties`、`.ssh/`、`.DS_Store`、`build/`、`.gradle-home/` 提交上去

### 3.7 提交身份（2026-11-04 起）

| 用途 | 身份 |
|---|---|
| 仓库 owner / 共享账号 | `pc-buildmate`（`pcbuildmate@this.org.cn`） |
| 个人提交身份 | **Ruan L.K** `<72515069@cityu-dg.edu.cn>`，GitHub [@MoeField](https://github.com/MoeField) |
| 本机 git 配置 | `user.name=Ruan L.K`、`user.email=72515069@cityu-dg.edu.cn` |

- 个人邮箱必须先在 GitHub **Settings → Emails** 添加并验证，commit 才会关联到 @MoeField；
- 共享账号的 `pcbuildmate@this.org.cn` 只用于 owner 身份，不再用于日常 commit；
- 其他成员加入后，各自在本机设置自己的 `user.name` / `user.email`，不要共用；
- 历史 commit（S1/D1/D3）作者是共享身份，PR 用 **Squash and merge** 后，`main` 上的合并 commit 会记在点击合并的人名下。
- **deploy key 只负责 push 传输，不改变 commit 作者**；作者由上面的 `user.name` / `user.email` 决定。
- 仓库 owner 目前仍是共享账号 `pc-buildmate`；若要把仓库转到 @MoeField，在答辩后做（Settings → General → Transfer ownership），转移会改 URL，deploy key 随仓库走。

---

## 4. 分支、提交、PR

### 4.1 分支命名

```
feat/d3-rule-engine
fix/d2-stale-diagnosis
test/d5-ranking-order
docs/collab-v1
contract/field-source-conflict
chore/gradle-scaffold
```

- 生命周期 ≤ **3 天**；超过就拆成两个 PR。
- 每天 `git fetch && git rebase origin/main`；不要 `merge main` 进功能分支。

### 4.2 提交信息

```
<type>(<scope>): <一句话>

<body：为什么，不写「改了啥」——diff 已经说明>
< footer：Refs #12 / BREAKING CHANGE / AI: model, task-id >
```

- `type`：`feat|fix|test|docs|chore|refactor|contract`
- `scope`：`d1|d2|d3|d4|d5|d6|ui|ci|docs`
- 示例：`feat(d3): evaluate 15 rules from assets/rules-v1.json`

### 4.3 PR 大小与内容

- 目标 ≤ **400 行有效 diff**（不含生成文件、fixture 数据）。超了拆。
- 必须包含：
  1. 对应任务包 `docs/tasks/<id>.md` 的链接；
  2. 门禁命令的**实际输出**（贴最后 10 行）；
  3. UI 变更的**截图或 10 秒录屏**；
  4. 契约变更的 ADR 链接；
  5. AI 使用声明：模型名 + 任务 id（不需要贴完整 prompt）。
- 禁止：`TODO` / `FIXME` / `println` 进 `src/main`；顺手格式化全文件；升级依赖；
  改测试期望值来「变绿」；新增 `INTERNET`/相机/推送权限。

### 4.4 评审 SLA

- 24 小时内必须有人看：approve / request changes / 明确说「这周没空」。
- 24 小时无人 review 且 CI 全绿 → Captain 可加 `solo-merge` 标签后合并，PR 里写理由。
- 有 `request changes` 未解决 → **不得合并**。

---

## 5. 任务包：AI 的唯一输入

### 5.1 为什么是文件而不是聊天记录

聊天记录会丢、会变、会被误解。任务包是**可 diff、可引用、可验收**的规格。
每个任务一个文件：`docs/tasks/<id>.md`，模板见 [docs/tasks/TEMPLATE.md](docs/tasks/TEMPLATE.md)。

### 5.2 一个任务包必须包含的九块

| # | 块 | 说明 |
|---|---|---|
| 1 | 目标 | 一句话；不做什么 |
| 2 | 允许修改的路径 | 精确到目录/文件；AI 越界即失败 |
| 3 | 只读输入 | 模块卡 + 契约 + 规则/fixture 的具体小节 |
| 4 | 输出契约 | 类型、不变量、顺序、失败形态 |
| 5 | 验收命令 | 一条可复制执行的命令 |
| 6 | 反例 | 至少 1 个「实现写错会红」的测试 |
| 7 | 范围外 | 明确禁止的相邻功能 |
| 8 | 规格空白 | 已知未决点，要求 AI 停下提问 |
| 9 | 交付格式 | 分支名、commit 前缀、PR 模板 |

### 5.3 状态机

```
Backlog → Ready → Doing → Review → Done
              ↑            │
              └── blocked ─┘
```

- `Ready` 的条件：九块填满 + 依赖已合并 + owner 已认领。
- `Doing` 超过 3 天无 commit → 退回 `Ready` 并拆小。
- `Review` 超过 24h → 提醒 reviewer；超过 48h → Captain 介入。

### 5.4 给 AI 的提示词（固定四句）

任何任务包末尾都追加：

1. 「只实现本任务包范围，不要顺手改其它模块。」
2. 「不得修改 `contracts/`、fixture 期望值、`docs/01`–`04`；需要就停下说明。」
3. 「先写测试再写实现，测试必须能被错误实现弄红。」
4. 「遇到规格空白，列 2–3 个选项等我选，不要自行决定。」

---

## 6. CI 与质量门

### 6.1 工作流（S1 建工程后落地）

| 工作流 | 触发 | 内容 | 必过 |
|---|---|---|---|
| `ci-docs` | 所有 PR | 四个 node 脚本 + 文档链接检查 | ✅ |
| `ci-jvm` | 改动 `**/*.kt`、`**/*.kts` | `./gradlew :domain:test :data:test --no-daemon` | ✅ |
| `ci-apk` | 改动 `app/**` 或 `main` | `./gradlew :app:assembleDebug`，上传 APK artifact | PR 建议、main 必过 |
| `release` | 手动 / tag `v*` | 签名 APK → GitHub Release | 人工触发 |

- Ubuntu runner，JDK 21，缓存 `~/.gradle/caches` 与 `~/.gradle/wrapper`。
- `concurrency: cancel-in-progress`；单 job 超时 20 分钟。
- 仪器测试（`connectedAndroidTest`）**不进 CI**（模拟器太慢），由本地/人工门禁跑。
- 私有仓库 Actions 每月 2000 分钟：日常只跑 docs+jvm；`assembleDebug` 只在必要路径触发。

### 6.2 本地等价命令（提交前自跑）

```bash
node tools/check-docs.mjs
node ci/check-boundaries.mjs
node ci/check-keywords.mjs
node tools/check-matrix.mjs
./gradlew :domain:test :data:test :app:assembleDebug
```

### 6.3 完成定义（DoD）

一个任务只有同时满足以下 6 条才算 Done：

1. 验收命令通过，输出贴在 PR；
2. 新增行为有测试，且「把实现改错」能让测试红；
3. `check-keywords` 无新增命中，`check-boundaries` 无越界；
4. 输出确定性（排序、集合转 List、无时钟/随机）；
5. 文档/任务包状态已更新（若实现暴露了文档缺口，先改文档再改代码）；
6. UI 变更有人看过截图，APK 变更有人装过真机。

---

## 7. 防冲突规则（AI 协作的关键）

AI 最容易制造的不是 bug，是**大而全的 diff**。以下规则强制把 diff 关小：

1. **一个任务一个模块**。跨模块任务拆成多个 PR，按依赖顺序合。
2. **不格式化全文件**。只改必要的行；不调整 import 顺序、不换行、不重排。
3. **不改公共符号**。不改类名/函数名/字段名；要改先提 `refactor:` PR。
4. **不升级依赖**。Gradle/AGP/Compose 版本由 Captain 统一升级。
5. **不手改生成文件**（见 §1.1）。
6. **不并发改同一文件**。两个任务都要改 `MainViewModel.kt` → 串行，后一个 rebase。
7. **先 rebase 再 PR**。冲突自己解，解完重跑门禁；不要让 reviewer 替你解冲突。
8. **锁文件/大文件不进 PR**。APK 走 Release，截图放 PR 描述，不提交二进制。

---

## 8. 人工复核点（AI 不可替代）

| # | 复核点 | 谁 | 看什么 | 不通过就 |
|---|---|---|---|---|
| H1 | 契约/规则/夹具 diff | Captain | 是否只改必要处；ADR 是否存在；影响模块 | 打回 |
| H2 | UI 变更 | UI owner 或 Captain | 截图/录屏；文案；空态/错误态；深色模式 | 打回 |
| H3 | APK | 任一成员 | Android 12+ arm64 真机安装；跑一遍演示脚本 | 打回 |
| H4 | 发布/答辩 | Captain + 1 人 | tag、版本号、离线、演示步骤 1–9 | 不发 |

> 用户明确要求：**CLI 构建可以自动，但产物必须人工复核**。
> 因此 `ci-apk` 绿 ≠ 可发布；H3 必须有人真的装一次。

---

## 9. 发布与答辩

### 9.1 版本节奏

- 每周一个可运行 tag：`v0.1-m1` … `v0.4-m4`。
- tag 必须附：APK、`git log --oneline` 摘要、已知问题清单。
- 答辩版：`v1.0-defense`，只修 bug，不加功能。

### 9.2 设备基线

- **Android 12+（API 31+）**；release 只打 `arm64-v8a`，debug 可加 `x86_64` 供模拟器。
- 离线可用；安装包不得申请 `INTERNET`。
- 演示机：至少 1 台真机 + 1 个 API 31+ 模拟器（当前只有 API 25 的 AVD，需联网新建镜像）。

### 9.3 答辩前检查（与 `docs/13 §13.5` 对齐）

- [ ] 演示脚本 9 步一次跑通（7800 → 7900，BIOS 仍未验证）
- [ ] 屏幕上可见「演示数据」标识
- [ ] 价格旁有日期
- [ ] 编辑后旧诊断不显示（stale banner 正常）
- [ ] 设置里确认无网络权限
- [ ] 讲稿/PPT/Q&A 与本仓库口径一致（`docs/13 §13.3` 五处）

---

## 10. 节奏与沟通

- **不设每日站会**。用一个 pinned Issue「Daily log」异步留言：昨天/今天/阻塞。
- 每周一次 15 分钟同步（可选），只看四个数字：rules __/60、fsm __/76、inv __/18、mutation __/12。
- 不报「进度百分比」；只报「下一步做什么、谁做、什么时候」。
- 阻塞超过 30 分钟 → Issue 加 `status:blocked` + `needs-human`，Captain 当天裁决。
- 所有决定写进 Issue/PR，不写进私聊；私聊结论必须回帖。

---

## 11. 四周排期（按 12 月初答辩倒排）

| 周 | 日期 | 目标 | 出口 |
|---|---|---|---|
| W1 | 11/04–11/10 | Git + Gradle 骨架 + `:contracts` + D1 数据层 | `assembleDebug` 绿；目录可查；契约测试过 |
| W2 | 11/11–11/17 | D3 规则引擎 + D5 替换 | 60/60 fixture；替换闭环 |
| W3 | 11/18–11/24 | D2 工作区 + D4 推荐 + D6 比较 + UI 四屏 | 端到端可点；76 格状态机 |
| W4 | 11/25–12/01 | 集成、真机、APK、演示排练 | 演示脚本一次过；tag `v1.0-defense` |
| 缓冲 | 12/02–答辩 | 只修 bug、背稿、Q&A | 不新增功能 |

**功能冻结线：11/28。** 之后只允许 `fix:` 与 `docs:`。

---

## 12. 贡献台阶（给低动力队友的最小路径）

不要求每个人写一个大模块。按投入选一档，**每人至少留下一个 merged PR**（也是课程贡献证据）：

| 档 | 投入 | 做什么 | 例子 |
|---|---|---|---|
| L0 | 30 分钟 | Review 一个 PR | 点开截图，对照 `docs/06` 验收表 |
| L1 | 1–2 小时 | 数据/文案/测试 | 补 2 条 catalogue 记录；补 1 条 fixture |
| L2 | 1–2 天 | 一个模块（可全程用 AI） | 按任务包实现 D5，跑门禁 |
| L3 | 3–5 天 | 模块 + 测试 + review | D3 规则引擎 + 变异测试 |

> 到 W2 结束还没认领任何任务的人，由 Captain 把其模块转为备份并记录，**不静默等待**。

---

## 13. 待确认（组长拍板后改 v1.0）

1. GitHub 仓库归属：个人账号还是组织？仓库名 `pc-buildmate` 是否 OK？
2. 私有 / 公开？（建议私有；答辩后可转公开）
3. 是否接受「24h 无 review + CI 绿 → Captain 自审合并」？
4. 看板用 GitHub Projects，还是只用一个 `TASKS.md` 表格？（建议 Projects）
5. 六名成员的 GitHub 用户名（填进 §2.1）。
6. 答辩准确日期（用于校准 §11）。
7. 是否把 `bench/` 移出本仓库（当前在上级目录，与本项目无关）。
8. 代理什么时候可用（决定 Room / Navigation / 模拟器镜像 / GitHub 的时间点）。

---

## 14. 变更记录

| 版本 | 日期 | 变更 | 批准 |
|---|---|---|---|
| v0.1-draft | 2026-11-04 | 初稿：trunk-based、任务包、四类人工复核、四周排期 | 待批 |
