# Data Appendix · 数字溯源

> INFO-03 交付物。每个出现在答辩材料里的数字，在这里指回它的**单一真相源**。
> 规则：数字以「文件里的值」为准，不以「口头约定」为准；改动数字必须同时改这里和引用它的材料。
> 与 `materials/source/Defense_Numbers_To_Confirm.md` 互补：那张表是「答辩数字 owner + 确认状态」，本附录是「数字 → 真相源」映射。

---

## 1. 演示配置总价：¥7,800 → ¥7,900

| 数字 | 含义 | 真相源 |
|---|---|---|
| **7800** | 演示配置 8 件合计 | `assets/fixtures/fixtures-anchor-v1.json` → `fx_suggestion_300_plus_100.expectedBefore.totalCny`；`assets/catalogue/catalogue-app.json` 8 件求和 |
| **7900** | 换 300mm 显卡后合计（+100） | 同上 → `expectedAfter.totalCny` |

8 件构成（`catalogue-app.json`）：

| componentId | 价格 | 说明 |
|---|---:|---|
| `cpu-demo-7600x` | 1500 | AMD Ryzen 5 7600X |
| `mb-demo-b650` | 900 | ASUS TUF GAMING B650-PLUS WIFI |
| `mem-demo-ddr5-32` | 500 | Kingston FURY Beast 32GB DDR5 |
| `storage-demo-nvme` | 200 | Samsung 990 EVO 1TB |
| `gpu-demo-337` | 3400 | MSI RTX 4070 Ti GAMING X TRIO 12G（337mm） |
| `psu-demo-750` | 600 | Seasonic FOCUS GX-750 |
| `case-demo-320` | 400 | COUGAR Airface Pure Pro（320mm） |
| `cooler-demo-160` | 300 | Thermalright PA120 |
| **合计** | **7800** | |

---

## 2. 推荐示例：¥7,900（8 类）/ 真机记录 ¥7,869（6 类，rc2）

| 数字 | 含义 | 真相源 |
|---|---|---|
| **7900** | 预算 8000 / 游戏 → **8 类**（含机箱+散热）推荐总价；目录里没有 ≥337 mm 的机箱，显卡自动降到 300 mm | `#185` 后 main 的算法实测（`DefaultBudgetRecommender` + 真实 30 条目录 / 15 条规则）；`DemoJourneyEndToEndTest` 已钉住此值 |
| 7869 | 同上，但 rc2 上的 **6 类**旧值 | `docs/21-device-evidence.md §21.2` 三轮真机记录（2026-11-17） |

> **注意**：7900 是推荐算法输出，**不是固定锚点**；目录价格或权重变动会改变它。答辩时以 App 实测为准，不要把它当常量。
> 真机截图目前仍是 rc2 的 7869/6 类；最终发布包出来后重跑九步，再把 7900 换成真机实测值。

---

## 3. 尺寸锚点：337 mm / 320 mm

| 数字 | 含义 | 真相源 |
|---|---|---|
| **337** | MSI RTX 4070 Ti GAMING X TRIO 12G 长度（mm） | `catalogue-app.json` → `gpu-demo-337.lengthMm`；MSI 官方规格 337×140×62mm |
| **320** | COUGAR Airface Pure Pro 显卡限长（mm） | `catalogue-app.json` → `case-demo-320.maxGpuLengthMm`；COUGAR 官方 320mm GPU clearance |
| **BLOCKER** | `GPU_CASE_LENGTH`：337 > 320 | `assets/rules/rules-v1.json` → `GPU_CASE_LENGTH`；fixture `fx_gpu_len_conflict_337_320` |

---

## 4. PSU 负载公式

```
requiredW = ceil( (cpu.tdpW + gpu.tdpW + 100) × 1.2 )
```

| 项 | 含义 | 真相源 |
|---|---|---|
| 公式 | 见左 | `docs/03-rules.md §3.7`（冻结） |
| 示例 | (105 + 285 + 100) × 1.2 = 588 → 750W 通过，550W 冲突 | `docs/03 §3.7`；fixture `fx_psu_power_conflict` |
| `+100` / `×1.2` | 固定余量 / 20% 余量 | 常量，改它走契约变更 |

---

## 5. 目录日期与规模

| 数字 | 含义 | 真相源 |
|---|---|---|
| **2026-11-04** | 价格快照日期（`priceDate`） | `catalogue-app.json` header；`assets/catalogue/SOURCES.md` |
| **2026.11** | 目录版本（`catalogueVersion`） | 同上 |
| **30** | 目录记录数（26 active + 4 demo anchors） | `catalogue-app.json`；`docs/20-catalogue-audit.md` |

---

## 6. 数字 × 材料交叉引用

| 数字 | 出现在 | owner | 真相源类型 |
|---|---|---|---|
| 15 / 60 / 76 / 18 / 12 | 规则 / 矩阵 / 状态机 / 不变量 / 变异 | 王嘉瑞 / 杨壹硕 | `assets/rules`、`tools/check-matrix.mjs` |
| 30 | 目录记录数 | 刘喆 / 陈思言 | `catalogue-app.json` |
| 7800 / 7900 | 演示锚点 | 王嘉瑞 | fixture `fx_suggestion_300_plus_100` |
| 7900 | 推荐示例（8 类） | 黄奕维 / 陈思言 | main 算法实测（#185），真机待最终包重跑 |
| 337 / 320 | 3D + 冲突锚点 | 阮立坤 / 陈思言 | `catalogue-app.json` + fixture |
| 588 / 750 / 550 | 电源规则 | 王嘉瑞 | `docs/03 §3.7` + fixture |
| 2026-11-04 / 2026.11 | 价格快照 | 陈思言 | `catalogue-app.json` + `SOURCES.md` |
