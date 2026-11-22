# 19 · 测试证据链：每道门证明了什么、证明不了什么

> 读者：答辩主讲、接手测试的人。
> 相关：[07 单元验证](07-unit-verification.md)、[08 测试与归因](08-testing-and-triage.md)、[HANDOFF](../HANDOFF.md)。

## 19.1 为什么要写这一页

"我们有 60 条 fixture、160+ 个测试用例、矩阵全绿"不是结论，是**主张**。
主张要能回答三个追问：

1. 这些测试凭什么判对错？（规格 → fixture）
2. 实现改错了，测试会不会红？（变异）
3. 测试本身写错了，谁来发现？（参考模型）

这三问对应三道门，外加一道防漂移的检查。缺任何一道，"全绿"就只是"本轮没人报错"。

## 19.2 四道门

| 门 | 回答的问题 | 命令 | 当前数字 |
|---|---|---|---|
| **矩阵门** | 每条规则的 4 类结果、每格状态机转移、18 条不变量，是否都有**真实执行过**的用例 | `node tools/gen-matrix.mjs && node tools/check-matrix.mjs` | 60/60、76/76、18/18 |
| **变异门** | 把实现改坏一处，测试会不会红 | `node tools/run-mutations.mjs && node tools/check-matrix.mjs --mutations` | 12/12 KILLED |
| **参考模型门** | 测试与实现是不是同一套理解 | `:app` 的 `OracleCrossCheckTest` / `DiagnosisOracleCrossCheckTest` / `ReplacementOracleCrossCheckTest`；参考模型自身另有 `test-oracle` 单测（规则/两部状态机/预算算术/候选过滤） | 60+7 条 fixture、4×4 + 6×10 全格一致；`test-oracle` 自身 38 条单测（MiniJson 6、规则 10、状态机 13、预算 5、候选过滤 4） |
| **防漂移门** | 有测试的模块会不会悄悄不在 CI 里跑 | `node ci/check-test-tasks.mjs` | 覆盖全部含测试模块；有 `src/androidTest` 时还要求 CI 存在 instrumented 任务与 `room` job |

支撑它们的静态检查：`tools/check-docs.mjs`（文档/规则/fixture 自洽）、`ci/check-boundaries.mjs`
（模块依赖与 test-oracle 自证禁令）、`ci/check-keywords.mjs`（范围外关键词与权限）、
`ci/check-test-tasks.mjs`（测试任务覆盖）、`ci/check-mutations.mjs`（变异表还能不能打进去）、
`qa/check-audit-report.mjs`（`docs/20` 的目录审计数字与实测一致）。

CI 里五个常规 job：`docs`（上述静态检查 + 原型回归 + 矩阵工具自测）、`jvm`（单测 + 矩阵门）、
`android`（debug APK）、`mutations`（变异门，`needs: jvm`）、`room`（Room instrumented，main / 手动）。
另有手动触发的 `release-verify`（先查证据文档完整，再校验最新 release 产物）。

## 19.3 参考模型为什么算"独立"

`test-oracle/` 只依赖 `contracts`，被 `ci/check-boundaries.mjs` 强制禁止 import
`pcbuildmate.domain.*`。它：

- 自带 JSON 读取器（`MiniJson`），不复用 `:data` 的 `RuleParser`；
- 按 `docs/03 §3.4.2` 重写三态传播，不调用 `DefaultRuleEngine`；
- 按 `docs/04` 的转移表重写两张状态机，不调用 `DiagnosisMachine` / `ReplacementMachine`；
- 预算算术（`OracleBudget`）与候选硬约束过滤（`OracleAdvisor`）同样独立实现——前者不读 `BuildRevision` 的构造校验，后者不读 `DefaultReplacementAdvisor`。

交叉校验放在 `:app`——唯一能同时看到两边的模块，且只在 test classpath 上依赖 `:test-oracle`。

## 19.4 独立性怎么"反证"

断言"两套实现一致"很容易变成同义反复，所以做过两次反向验证：

1. 把 oracle 的 `weakest` 传播写反（`>=` 改 `<=`）→ 规则交叉校验变红；
2. 替换状态机的 guard 顺序若按文档表格的字面顺序实现 → 9 个组合与生产实现不一致。

反向验证不是一次性表演：任何人改了 oracle 或生产实现，交叉校验都会立刻对不上。

## 19.5 这套东西**证明不了**什么

| 证明不了 | 为什么 | 靠什么补 |
|---|---|---|
| 规则参数是否符合真实硬件 | fixture 是冻结的规格，规格错了两边一起错 | 人工核对数据（PRICEDATA-01 / 兼容性数据） |
| 价格是否准确 | 目录是静态快照，没有来源对账 | 同上 |
| 字段级来源是否可追溯 | 168 个字段的 `sources` 全为空（0/168），只有**记录级**来源（`SOURCES.md`，30/30，其中 27 条带 URL，见 `docs/20 §20.5`） | 数据补齐（INFO-01）；答辩口径：只说记录级，不声称字段级可溯源 |
| 3D 预览的槽位坐标是否正确 | 5/6 块主板按 `pcieX16SlotMm` 放置，但值是**标准布局参考值**（非厂商图实测）；`mb-demo-lga1700` 缺字段走常量回退（`docs/18 §18.8`） | 拿到厂商布局图后逐板核实（INFO-02） |
| UI 交互与可访问性 | Entry / Workspace / 诊断 / 比较四屏已有 Compose semantics 测试（Robolectric，JVM 跑）；可访问性（TalkBack 顺序、触控目标尺寸）未测 | POLISH-01 + 后续补测 |
| Room 持久化在 PR 上是否可靠 | instrumented 测试需要设备；`room` job 只在 main / 手动触发（main 上已连续绿三次） | 改 `:data` 的 PR 手动 dispatch 一次 `room` job |
| 性能 | 范围外 | 不承诺 |

## 19.6 被问"测试是不是照抄实现"时的答法（30 秒）

> "规则和状态机各有一份独立参考模型，只能依赖 contracts，import 生产的 domain 层会被 CI 直接拒。
> 同一份 fixture 上两边逐条比对：规则 60 条 + anchor 7 条，状态机 76 格全覆盖。
> 我把参考模型的 weakest 传播故意写反过，交叉测试立刻变红——它能抓住分歧，不是摆设。
> 另外 12 条关键变异每次 PR 都在 CI 里跑，全杀死才算过。"

## 19.7 本地复现全部证据

```bash
node tools/check-docs.mjs
node ci/check-boundaries.mjs
node ci/check-keywords.mjs
node ci/check-test-tasks.mjs
node tools/test-matrix-tools.mjs
node ci/check-mutations.mjs
node ci/check-field-paths.mjs

./gradlew :app:testDebugUnitTest :ui:testDebugUnitTest :feature:testDebugUnitTest \
  :data:testDebugUnitTest :test-oracle:test \
  :domain:rules:test :domain:revision:test :domain:recommend:test \
  :domain:replacement:test :domain:state:test :domain:compare:test

node tools/gen-matrix.mjs
node tools/check-matrix.mjs

node tools/run-mutations.mjs
node tools/check-matrix.mjs --mutations
```

设备与发布（需要真机 / 已发布的 APK，见 [21 章](21-device-evidence.md)）：

```bash
# 真机九步（会 pm clear 应用数据，逐步截图到 qa/screenshots/）
bash qa/drive-demo.sh run1 qa/screenshots

# 发布产物：sha256 / 签名 / 权限 / ABI / 版本号
bash qa/verify-release.sh PC-Buildmate-<ver>.apk PC-Buildmate-<ver>.apk.sha256 <ver>

# 发布前：证据文档里不能有空格子，引用的截图必须存在
node qa/check-evidence.mjs
node qa/check-audit-report.mjs
```

## 19.8 设备与发布证据

上面四道门守的是"代码里的行为"；还有两类证据只能来自真实设备，记在
[21 · 设备侧证据](21-device-evidence.md)：

| 证据 | 现状（2026-11-17） |
|---|---|
| Room 契约（8 个 instrumented 用例） | ✅ 本地 AVD 8/8；✅ CI `room` job 在 main 上连续通过（模拟器 30 s 启动，含启动冒烟） |
| Release 产物（`v1.0.0-defense-rc2`） | ✅ sha256 与附件一致；✅ 发布证书（非 debug）；✅ 无 INTERNET/CAMERA/LOCATION/推送权限；✅ 仅 `arm64-v8a`。复现：`bash qa/verify-release.sh <apk> <sha>`，或手动 dispatch 勾选 `verify_release`（job `release-verify` 先跑 `qa/check-evidence.mjs` 与 `qa/check-audit-report.mjs`，再下载最新 release 校验） |
| 真机 9 步 ×3 | ✅ 2026-11-17 在 HUAWEI DBR-W10（Android 12）上连跑三次，锚点全中；步骤见 [qa/device-run-tutorial.md](../qa/device-run-tutorial.md)，截图在 [qa/screenshots](../qa/screenshots/)，驱动脚本 `qa/drive-demo.sh` |

答辩口径：三行都可以当场给数字和命令。

## 19.9 本地静态分析（Android Lint）

CI 里没有跑 Lint；本地执行 `./gradlew :app:lintDebug` 的结果：

| 时间 | 结果 | 说明 |
|---|---|---|
| 2026-11-17 | **0 error / 3 warning** | 报告：`app/build/reports/lint-results-debug.txt` |
| 2026-11-20 | **0 error / 0 warning** | #177 修复三条后复测 |

三条告警的去向（2026-11-20 已全部收口）：

| 检查项 | 处理 |
|---|---|
| `MissingApplicationIcon`（manifest 无 `android:icon`） | 已修：自适应 icon + monochrome（#177） |
| `DataExtractionRules`（`allowBackup` 已废弃） | 已修：`data_extraction_rules.xml` 关闭云备份/迁移（#177） |
| `ChromeOsAbiSupport`（缺 x86_64） | 按原因 `disable`：release 明确只出 arm64（#177） |

**关于接进 CI**（2026-11-18 更正）：Android Lint 默认**只在 error 时**让任务失败——实测 0 error / 3 warning 时 `:app:lintDebug` 是成功的，warning 不会让 CI 红。要防回归必须显式配置 `lint { warningsAsErrors = true }`（或接 baseline）；只加一个 job 拦不住任何东西。当前已 0 warning，尚未接进 CI（本地命令 + 证据表为准）。

## 19.10 答辩旅程端到端（数字钉）

四道门回答的是「判对错 / 会不会红 / 谁来发现 / 会不会漂移」。除此之外还有一类风险：**答辩念的数字和生产栈实测不一致**——数据或讲稿改了，单测却全绿，因为没有人把「7900 / 7800 / 52 / 7900」这些具体数字钉在生产路径上。

`app/src/test/.../DemoJourneyEndToEndTest.kt`（#182）就是这根钉子：它用真实 30 条目录 + 真实规则 + 真实引擎 + 真实替换建议器 + 真实 ViewModel，走完答辩脚本的完整旅程并断言**具体数字**：

- 推荐 8000/游戏 = **7900**、8 类（含机箱+散热）、含独显、无 BLOCKER；
- 演示配置 **7800**（8 件）；
- 检查 = **CONFLICT / HIGH / 52**（337>320 BLOCKER + 61>60 MAJOR + BIOS 未验证）；
- 替换 = 300mm 卡 **+¥100**、预览 **7900**、应用重检后无 BLOCKER 但 **BIOS 仍未验证**；
- 保存 A/B → 比较 `7800 → 7900`；编辑内存 → 诊断过期不自动重跑；单件类目改数量 → 拒绝；Undo 恢复。

运行位置：`jvm` job 的 `:app:testDebugUnitTest`（与其余 JVM 单测同一处，PR / main 都跑）。任何一处数字漂移都会让这条测试红，提醒先改 `Defense_Numbers_To_Confirm.md` 再改代码，而不是悄悄改断言。
