# DATA-04 · 数据层字段覆盖与持久化契约

## 1. 元信息

| 项 | 值 |
|---|---|
| 任务 ID | `DATA-04` |
| 模块 | 数据层（目录契约 / Room 载体） |
| Owner | 刘喆（@LIU132578） |
| Reviewer | 黄奕维（@MoeField） |
| 关联 | 输入来自 `DATA-03` 的审计结论；`pricedata` 导出侧归陈思言 |
| 依赖 | `DATA-03`（审计报告 `docs/20`）、`DATA-02`（Room 持久化 A/B） |
| 预计 | 0.5 天 |
| 截止 | 2026-11-20（首版交付） |
| 分支 | `feat/data-04-catalogue-field-tests` |

## 2. 目标（一句话）

给 `:data` 补 JVM 契约测试，让**真正上架的目录资产**、**Room 落盘的 JSON 载体**和**目录到配置单的字段保真**都有测试守着；**只加测试，不改生产代码**。

**不做**：不改 `FieldPathRegistry` 的校验语义、不改 Room 实体、不改 `assets/**`。

## 3. 允许修改的路径

```
data/src/test/kotlin/pcbuildmate/data/catalogue/ShippedCatalogueAssetTest.kt
data/src/test/kotlin/pcbuildmate/data/catalogue/ShippedCatalogueRejectionTest.kt
data/src/test/kotlin/pcbuildmate/data/store/StoredRevisionJsonTest.kt
data/src/test/kotlin/pcbuildmate/data/store/CatalogueBackedRevisionTest.kt
docs/tasks/DATA-04.md
```

禁止：`data/src/main/**`（不改生产代码）、`contracts/**`、`assets/**`、`docs/01`–`04`。

## 4. 只读输入

- 上架资产：`assets/catalogue/catalogue-app.json`（30 条 / `2026.11` / `2026-11-04`）
- 校验实现：`data/src/main/kotlin/pcbuildmate/data/catalogue/FieldPathRegistry.kt`
- 落盘实现：`data/src/main/kotlin/pcbuildmate/data/store/RoomBuildStore.kt`、`StoreEntities.kt`
- 判定依据：`docs/03-rules.md` §3.3 冻结字段表、§3.4 CSV / 数值解析规范
- 审计结论：`docs/20-catalogue-audit.md`

## 5. 输出契约

- 测试全部落在 `:data` 的 JVM 单测，`./gradlew :data:testDebugUnitTest` 可跑，不需要模拟器。
- 三条主线：**上架资产**（`catalogue-app.json` 此前零覆盖）、**落盘载体**（`StoredRevision` / `StoredItem` 的 JSON 形状）、**目录到 revision**（版本、价格日期、单价、类目保真）。
- 只断言既有行为：任何一条测试转红都说明生产行为变了，不允许改期望值让它变绿。
- 不引入新依赖（Robolectric / Room in-memory 都不行，见 §9 Q1）。
- 测试数据来自真实资产的最小字符串改动，不手搓 JSON 副本。

## 6. 验收命令

```bash
./gradlew :data:testDebugUnitTest
node tools/check-docs.mjs
node ci/check-boundaries.mjs
node ci/check-keywords.mjs
```

期望：`BUILD SUCCESSFUL`；用例数与 `data/build/test-results/testDebugUnitTest/*.xml` 一致（51 条，0 失败 0 跳过）；文档与边界门禁全绿。

## 7. 反例（测试必须能红）

- 把「30 条」的期望改成 31 → `ShippedCatalogueAssetTest` 必须红（已人工验证：`9 tests completed, 1 failed`）。
- 改 `StoredItem` 的字段名（如 `unitPriceCny` → `unitPrice`）→ `StoredRevisionJsonTest` 的键集合断言必须红。
- 把 `numeric` 改成与 `value` 不一致 → `ShippedCatalogueRejectionTest` 必须红。
- 给 `value: null` 的字段填上值但保留 `state=EMPTY` → 同上必须红。
- 把 `biosGeneration` 补全（不再有 `EMPTY`）→ 锚点断言必须红，属**有意变更**，需同步 `docs/03` 与答辩脚本。

## 8. 范围外

- **Room 实体 ⇄ 模型的真机映射**：只有 `:data:connectedDebugAndroidTest` 能覆盖，本机无模拟器，留给 `QA-01`/真机卡。
- **`FieldPathRegistry` 只校验「出现过的字段」**，不校验「冻结表要求的字段是否齐全」：这是行为缺口，改它需要 ADR，不在本卡。
- 不判规则语义（15 条规则归 `DOMAIN-01`）。
- 不替 `pricedata` 修「字段级无来源 URL」（报告 20.5 已写明，归陈思言 INFO-01）。

## 9. 规格空白与已定口径

已澄清（2026-11-16 与 Owner 确认）：

| # | 问题 | 结论 |
|---|---|---|
| Q1 | 能不能用 Robolectric / Room in-memory 测真实 Room 映射 | 不能：会引入新依赖，违 A10 + 本卡「只加测试」；改为冻结落盘 JSON 的键名与容错行为，JVM 侧能守的那一半先守住 |
| Q2 | 上架资产测试要不要硬编码 30 条与八类计数 | 要：这是上架快照的契约；数据变化应显式改测试，而不是让测试跟着数据漂 |
| Q3 | 唯一 `EMPTY` 字段（`mb-demo-b650.biosGeneration`）要不要断言 | 要：答辩 9 步脚本依赖它返回 `UNVERIFIED`；补数后测试转红属于有意变更，需同步文档与脚本 |
| Q4 | 负例用字符串改动还是手搓 JSON | 用真实资产的最小字符串改动，改坏一个字符即应被拒；手搓副本会与真实数据脱节 |

仍待确认：

- `Q5`：`FieldPathRegistry` 是否补「必填字段齐全」校验（会让缺字段的记录直接解析失败）。需要 ADR + 黄奕维裁决；若采纳，本卡需补一组红→绿的反例。

## 10. 交付

- commit 前缀：`test(data):`
- PR 描述附：`./gradlew :data:testDebugUnitTest` 输出、用例数对照、红探针结果
- 不写 `docs/tasks/BOARD.md`；认领写在 PR 描述并群内 @ 黄奕维回写
