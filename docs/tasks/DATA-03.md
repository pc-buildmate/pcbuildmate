# DATA-03 · 目录一致性审计

## 1. 元信息

| 项 | 值 |
|---|---|
| 任务 ID | `DATA-03` |
| 模块 | 数据层（目录 / 证据） |
| Owner | 刘喆（@LIU132578） |
| Reviewer | 黄奕维（@MoeField） |
| 关联 | 与 `INFO-01`（陈思言，价格与来源表）接口；与 `ARCH-01`（字段 ADR）无依赖 |
| 依赖 | 无。`INT-01` 目录快照 30 条与 `D1-01` 已合并 |
| 预计 | 1 天 |
| 截止 | 2026-11-17 出第一版，2026-11-20 定稿 |
| 分支 | `feat/data-03-catalogue-audit` |

## 2. 目标（一句话）

用脚本把 `assets/catalogue/catalogue-app.json` 的 30 条记录做成**逐条可核对**的证据表：每条记录、每个字段的合格或异常都有稳定结论，异常要么修复、要么标注已解释。

**不做**：不修数据、不改来源表、不改冻结字段表、不新增字段。

## 3. 允许修改的路径

```
tools/audit-catalogue.mjs
docs/20-catalogue-audit.md
docs/tasks/DATA-03.md
```

禁止：`contracts/**`、`assets/**`（含 `catalogue-app.json` 与 `SOURCES.md`）、`docs/01`–`04`、`data/**`（属 DATA-04）。

## 4. 只读输入

- 判定依据：`docs/03-rules.md` §3.3 冻结字段表、§3.4 CSV / 数值解析规范
- 审计对象：`assets/catalogue/catalogue-app.json`（30 条 / `2026.11` / `2026-11-04`）
- 交叉核对：`assets/catalogue/SOURCES.md`（**只读**，owner 陈思言）
- 生成链路：`pricedata/src/pricepipe/export.py`（确认 `sources` 与 `state` 的来源）
- 任务卡：本文

## 5. 输出契约

- `tools/audit-catalogue.mjs` 零依赖、仅读文件；默认打印摘要并重写 `docs/20-catalogue-audit.md`，`--no-write` 只检查。
- 报告分四组检查：A 记录级、B 字段级、C 交叉级、D 来源表；E 为证据强度统计（**不**计为异常）。
- `EMPTY` 是合法三态（规则读到即 `UNVERIFIED`），只统计，不判异常；`state=EMPTY` 必须无值，非 `EMPTY` 必须非空。
- 每条异常有稳定代码（`MISSING_FIELD` / `BAD_ENUM` / `SOURCE_STALE` …），已解释的异常必须写明去向。
- 退出码：无待修异常 → 0；有待修 → 1。`--strict` 把已解释的来源表异常也计为失败（供 INFO-01 补采后当闸门）。
- 报告内容全部由数据推导，不读时钟、不读写随机，重复运行结果逐字节一致。

## 6. 验收命令

```bash
node tools/audit-catalogue.mjs
node tools/check-docs.mjs
node ci/check-boundaries.mjs
node ci/check-keywords.mjs
```

期望：审计退出码 0，报告 20.3 节无「待修」项，文档门禁全绿。

## 7. 反例（脚本必须能抓住）

- 把 `mb-demo-b650` 的 `biosGeneration` 从 `EMPTY` 改成有值但 `state` 仍是 `EMPTY` → `EMPTY_WITH_VALUE` 必须报。
- 把 `tdpW` 写成 `"0"` 或 `"-1"` → `NUMBER_BELOW_MIN` 必须报。
- 把 `socket` 写成 `am5`（小写）或 `LGA1700 `（带空格） → `BAD_ENUM` 必须报。
- 给主板加一个 `pcieX16SlotMm` 但没走 ADR → `UNKNOWN_FIELD` 必须报（说明冻结表才是权威）。
- 删除 `SOURCES.md` 里某条表格行 → `SOURCE_MISSING` 必须报。

## 8. 范围外

- 不修改 `catalogue-app.json` 与 `SOURCES.md`（前者由 `pricedata` 导出，后者 owner 陈思言）。
- 不新增字段（`pcieX16SlotMm` 等属 `INFO-02` + ADR）。
- 不判规则语义（15 条规则归 `DOMAIN-01`），只判字段是否满足冻结表的类型与取值域。
- 不把「字段级缺 URL」判为异常：当前 `sources` 全空是导出管线的既有行为，记为已解释项（见报告 20.5）。

## 9. 规格空白与已定口径

已澄清（2026-11-16 与 Owner 确认）：

| # | 问题 | 结论 |
|---|---|---|
| Q1 | `brief-liu` 要求校验「formFactor 与板尺寸映射（ATX 305×244…）」，但冻结表与数据都没有板尺寸字段 | 改为**值域校验 + 与机箱 `supportedFormFactors` 交叉**；板尺寸如需落地，走 ADR 归 `INFO-02`，不由本卡编造 |
| Q2 | `brief-liu` 的「容量 ≤0」检查 | 只有 MEMORY 有 `moduleCapacityGb` / `kitCapacityGb`；STORAGE 无容量字段，属冻结表的既定范围，不报异常 |
| Q3 | 必需字段缺失 vs `state=EMPTY` | 分开：键缺失才是 `MISSING_KEY`；`EMPTY` 合法，单列统计 |
| Q4 | `SOURCES.md` 是否纳入审计 | 纳入，但**只读**、只报不改；陈旧行标注去向 `INFO-01` |

仍待确认：

- `Q5`：「价格表无空来源」若被当作硬闸门，需要 `INFO-01` 给 16 条早期 demo 补逐条 URL；本卡不代为断言，只在报告 20.6 写清现状。

## 10. 交付

- commit 前缀：`docs(data):`（脚本 + 报告一起提）
- PR 描述附：`node tools/audit-catalogue.mjs` 完整输出、30 行结论摘要、异常 2 条的已解释理由
- PR 内不自带 `docs/tasks/BOARD.md` 改动；认领写在 PR 描述并群内 @ 黄奕维回写
