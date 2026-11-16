# 19 · 测试证据链：每道门证明了什么、证明不了什么

> 读者：答辩主讲、接手测试的人。
> 相关：[07 单元验证](07-unit-verification.md)、[08 测试与归因](08-testing-and-triage.md)、[HANDOFF](../HANDOFF.md)。

## 19.1 为什么要写这一页

"我们有 60 条 fixture、88 个测试用例、矩阵全绿"不是结论，是**主张**。
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
| **参考模型门** | 测试与实现是不是同一套理解 | `:app` 的 `OracleCrossCheckTest` / `DiagnosisOracleCrossCheckTest` / `ReplacementOracleCrossCheckTest` | 60+7 条 fixture、4×4 + 6×10 全格一致 |
| **防漂移门** | 有测试的模块会不会悄悄不在 CI 里跑 | `node ci/check-test-tasks.mjs` | 覆盖全部含测试模块 |

支撑它们的三项检查：`tools/check-docs.mjs`（文档/规则/fixture 自洽）、`ci/check-boundaries.mjs`
（模块依赖与 test-oracle 自证禁令）、`ci/check-keywords.mjs`（范围外关键词与权限）。

CI 里四个 job 对应：`docs`（后三项 + 原型回归 + 矩阵工具自测）、`jvm`（单测 + 矩阵门）、
`android`（debug APK）、`mutations`（变异门，`needs: jvm`）。

## 19.3 参考模型为什么算"独立"

`test-oracle/` 只依赖 `contracts`，被 `ci/check-boundaries.mjs` 强制禁止 import
`pcbuildmate.domain.*`。它：

- 自带 JSON 读取器（`MiniJson`），不复用 `:data` 的 `RuleParser`；
- 按 `docs/03 §3.4.2` 重写三态传播，不调用 `DefaultRuleEngine`；
- 按 `docs/04` 的转移表重写两张状态机，不调用 `DiagnosisMachine` / `ReplacementMachine`。

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
| 3D 预览的槽位坐标是否正确 | 缺主板 x16 槽位置数据，当前是常量估算（`docs/18 §18.8`） | 数据补齐后重估 |
| UI 交互与可访问性 | Entry / Workspace / 诊断 / 比较四屏已有 Compose semantics 测试（Robolectric，JVM 跑）；可访问性（TalkBack 顺序、触控目标尺寸）未测 | POLISH-01 + 后续补测 |
| Room 持久化在 PR 上是否可靠 | instrumented 测试需要设备；`room` job 只在 main / 手动触发 | 改 `:data` 的 PR 手动 dispatch 一次 `room` job |
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

./gradlew :app:testDebugUnitTest :ui:testDebugUnitTest :feature:testDebugUnitTest \
  :data:testDebugUnitTest :test-oracle:test \
  :domain:rules:test :domain:revision:test :domain:recommend:test \
  :domain:replacement:test :domain:state:test :domain:compare:test

node tools/gen-matrix.mjs
node tools/check-matrix.mjs

node tools/run-mutations.mjs
node tools/check-matrix.mjs --mutations
```
