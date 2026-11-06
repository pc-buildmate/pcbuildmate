# AGENTS.md

> 给所有 AI 编码代理的入口文件。开工前读完本页，再读你被分配的任务包。
> 人类流程、GitHub 设置、排期见 [COLLABORATION.md](COLLABORATION.md)。

---

## 0. 你面对的是一个「契约优先」的仓库

- 这个仓库首先是**可执行的规格**，其次才是代码。
- 你最容易犯的错不是写不出 Kotlin，而是**自行补充语义**、**扩大 diff**、**改测试让它变绿**。
- 规格与你的直觉冲突时：**停下来问**，不要选一个「更合理」的实现。

## 1. 开工前必须读（且只读这些）

1. 你的任务包：`docs/tasks/<task-id>.md`（没有任务包 → 拒绝开工）
2. 任务包指定的模块卡（`docs/06-modules.md` 对应一节）
3. 任务包列出的相关契约（`contracts/src/main/kotlin/pcbuildmate/contracts/*.kt`）
4. 任务包列出的规则/状态机小节（`docs/03` / `docs/04`）
5. [`COLLABORATION.md`](COLLABORATION.md) 的 §1、§4、§5、§7、§8

**不要**通读全仓库，不要读 `docs/05`、`08`、`09`、`13`、`DaftDocs/`、`dist/`、`materials/`，除非任务包明确要求。
上下文越杂，语义漂移越严重。

## 2. 硬规则（违反即 PR 打回）

| # | 规则 |
|---|---|
| A1 | 只修改任务包 `allowed paths` 内的文件。越界即失败。 |
| A2 | 不得修改 `contracts/**`、`contracts-test/**`、`assets/rules/**`、`assets/fixtures/**`、`docs/01`–`04`。 |
| A3 | 不得修改测试的期望值来让测试通过；不得删除/跳过失败测试。 |
| A4 | 不得新增网络、相机、定位、推送、存储权限；不得引入 okhttp/retrofit/ktor 等网络库。 |
| A5 | 不得出现 `FPS`、`PerformanceBand`、`fps`、`RealtimePrice`、`LiveInventory` 等范围外标识符（见 `docs/09 §9.1`）。 |
| A6 | `src/main` 不得出现 `TODO`、`FIXME`、`println`。 |
| A7 | 不得顺手格式化全文件、重排 import、改公共符号名、升级依赖。 |
| A8 | 不得提交 `build/`、`.gradle/`、`local.properties`、`*.apk`、`.DS_Store`。 |
| A9 | 一个任务只做一个模块；跨模块需求拆成多个任务。 |
| A10 | 不得自行创建新的 Gradle 模块、端口、枚举值、规则；需要就停下提方案。 |

## 3. 遇到这些情况必须停

- 任务包没写清楚一个语义（例如「reported 字段的 numeric 怎么解析」）。
- 需要改契约、fixture、状态表才能继续。
- 需要新增依赖或权限。
- 验收命令失败但你认为是测试/文档的错。
- 同一文件已被另一个未合并的分支修改。

**停下来的格式**：

```
BLOCKED: <一句话>
需要决定：<问题>
选项：
A) ... 影响 ...
B) ... 影响 ...
建议：A，因为 ...
```

不要在没有回答的情况下「先按 A 写着」。

## 3.1 同步规则

- 开工前、开 PR 前、合并前各同步一次；**不要在一个已经落后的分支上连续加功能**。
- 开工前先看 open PR（`gh pr list --state open` 或 GitHub 页面）；同路径已有 PR 时先停下来 review/协调。
- 不要只看 BOARD 空位：认领可能只写在未合并分支里；main 的 BOARD 空着 ≠ 没人做。
- 同一文件已有未合并 PR 时，先停下来协调；不要「我先把我的版本写完」。
- 同步后必须重跑对应门禁；rebase 引起的冲突不要用 `-X ours/theirs` 整片吞掉。

## 4. 工作步骤

1. `git fetch origin && git switch -c <type>/<module>-<slug> origin/main`；**开工前先 rebase 一次**。
2. 读任务包与只读输入；用 5 行说明「打算改哪些文件、为什么」。
3. 先写测试（能红），再写实现（转绿）。
4. 跑门禁：

```bash
node tools/check-docs.mjs
node ci/check-boundaries.mjs
node ci/check-keywords.mjs
node tools/check-matrix.mjs
./gradlew :domain:test :data:test          # 工程建立后
./gradlew :app:assembleDebug               # 改到 app/ui 时
```

5. 自检 diff：`git diff --stat` 是否只碰了允许路径；是否 < 400 行。
6. 提交（Conventional Commits，scope 用模块 id）：

```
feat(d3): evaluate 15 rules from rules-v1.json

Refs #12
AI: <model>, task D3-02
```

7. 开 PR，填模板，贴命令输出与截图；**开 PR 前再 `git fetch origin && git rebase origin/main`**。
8. 等 review；不要自己合并。

## 5. 代码约定（摘要）

- Kotlin，包名 `pcbuildmate.*`；`domain` 是纯 JVM，不 import `android.*` / `androidx.*`。
- 依赖方向：`ui → feature → domain ← data`；`contracts` 被所有人依赖，自己不依赖别人。
- 所有输出确定性：排序用 `sortedBy`，集合对外返回 `List`，不读时钟/随机/系统语言。
- 失败返回结构化结果（`sealed interface`），不把异常抛给 UI。
- 文案走 `TextKey` / `strings.xml`，不在 Composable 里硬编码中文句子。
- 时间、ID 由端口注入（`ClockPort` / `IdPort`），domain 不自己生成。
- 状态机是纯函数：`transition(state, event) -> TransitionResult`，不调用端口。

## 6. 交付时必须报告

```
改了：<文件列表>
命令：<逐条命令 + 结果>
没做：<明确列出未完成项>
风险：<已知偏差/待确认点>
```

不要写「已完成」而不贴输出；不要用「应该可以」描述测试结果。

## 7. 参考

- 规格入口：[`docs/00-overview.md`](docs/00-overview.md)
- 协作流程：[`COLLABORATION.md`](COLLABORATION.md)
- 任务包模板：[`docs/tasks/TEMPLATE.md`](docs/tasks/TEMPLATE.md)
- 现有原型（只读参考）：[`site/`](site/)
