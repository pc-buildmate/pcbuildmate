# TEST-01 · 测试证据链与防漂移门禁

> 状态：**done**（2026-11-20 收尾；后续维护见 `docs/19`）｜认领：@yys1yys2yys｜依赖：QA-01 / QA-02 / DATA-02 已合并
> 本文件此前只出现在 `docs/21` 的引用里，2026-11-20 按 reviewer 意见补齐为最小任务包。

## 1. 元信息

| 项 | 值 |
|---|---|
| 任务 ID | `TEST-01` |
| 模块 | 测试与 CI（跨模块，只读被测代码） |
| Owner | @yys1yys2yys |
| Reviewer | @MoeField |
| 依赖 | `QA-01`、`QA-02`、`DATA-02` 已合并 |
| 分支 | `test/*`、`qa/*`（每个检查一个 PR，不合并成大 PR） |

## 2. 目标（一句话）

让「测试全绿」这句话可核查：每条主张都能指到一个命令、一份产物或一次真机记录，且这些证据自己不会悄悄过期。

**不做**：不新增业务规则、不调 fixture 期望值、不改 `contracts/**`。

## 3. 允许修改的路径

```text
qa/**
ci/**
tools/**
.github/workflows/ci.yml
docs/19-test-evidence.md
docs/21-device-evidence.md
docs/20-catalogue-audit.md（只由 tools/audit-catalogue.mjs 生成）
materials/tools/**
```

禁止：`contracts/**`、`domain/**`、`data/**`、`feature/**`、`app/**`、`ui/**`（发现缺陷开 issue 交 owner，不自行改）。

## 4. 只读输入

- 规格：[docs/03](../03-rules.md)、[docs/04](../04-state-machines.md)、[docs/07](../07-unit-verification.md)；
- 冻结数据：`assets/rules/rules-v1.json`、`assets/fixtures/fixtures-rules-v1.json`、`assets/catalogue/catalogue-app.json`、`assets/catalogue/SOURCES.md`；
- 已有证据：[docs/19](../19-test-evidence.md)、[docs/21](../21-device-evidence.md)、[docs/20](../20-catalogue-audit.md)。

## 5. 输出契约

- 四道门各有一个**可单独执行**的命令，失败返回非零：矩阵门、变异门、参考模型门、防漂移门；
- 每个新增检查自带 `--self-test`，且 CI 里两种模式都跑（防止检查本身坏掉还报绿）；
- 证据文档里的数字与实测不一致时，检查必须红，并指出**差在哪一项**（不是只报「不一致」）；
- 真机证据必须能对到截图文件；发布证据必须能对到 tag 与 sha256。

## 6. 验收命令

```bash
node tools/check-docs.mjs
node ci/check-boundaries.mjs && node ci/check-keywords.mjs
node ci/check-test-tasks.mjs && node ci/check-mutations.mjs && node ci/check-field-paths.mjs
node qa/check-audit-report.mjs --self-test && node qa/check-audit-report.mjs
node materials/tools/check-deck.mjs
node tools/gen-matrix.mjs && node tools/check-matrix.mjs
node tools/run-mutations.mjs && node tools/check-matrix.mjs --mutations
node qa/check-evidence.mjs
```

期望：全部返回 0；`check-matrix` 打印 60/60、76/76、18/18、12/12。

## 7. 反例（必须能红）

- 把 `docs/20` 的字段数手改成 158 → `check-audit-report` 必须点名「字段数」；
- 把 `docs/20` 里某条异常换成另一块主板、条数不变 → 必须点名「报告缺少异常 / 多出异常」；
- 在 `FieldPathRegistry` 加字段而 `docs/03 §3.3` 没加 → `check-field-paths` 必须红；
- 把 deck 某页页脚改成 `22 / 30`、或让两条 ⏱ 时间轴重叠、或把附录 D 的 168 改成 169 → `check-deck` 必须点名页码/时段/数字；
- 把参考模型的最弱传播写反 → `OracleCrossCheckTest` 必须红（见 `docs/19 §19.4`）。

## 8. 范围外

- 不做性能/可访问性评测；
- 不测 UI 视觉像素，只测语义与几何数值；
- 不自动发布、不自动 approve PR；
- 不改别人的业务代码来「顺手修好」。

## 9. 规格空白（必须提问，不自行决定）

- `Q1`：`docs/20` 的异常条目身份算口径变更还是措辞变更？（2026-11-20 按「身份」实现，比对 `KIND id.detail 状态`）
- `Q2`：发布包与 rc2 的九步证据能否共用？（检查单口径：最终包必须重跑，记入 `docs/21`）

## 10. 交付

- commit 前缀：`test(qa):` / `ci:` / `docs(qa):`；
- PR 描述附：允许路径、跑过的命令与输出、反证方式；
- 每加一个检查，同步更新 `qa/README.md` 的脚本文档与 `docs/19` 的复现命令。
