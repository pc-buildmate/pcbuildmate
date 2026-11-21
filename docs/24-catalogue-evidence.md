# 24 · 目录价格与证据审计

> Owner：@陈思言（INFO-01 / INFO-02 / INFO-03）
> 本文是价格核对与证据追溯的交付物样板；先核演示配置 8 件，再扩到 6 类各 3–5 条。
> 采集口径：同一日期、同一类目、页面显示价；不把促销券后价当稳定价；`confidence=low` 必须写原因。

---

## 0. 结论先说

- 演示配置 8 件**已核对完成**（11/17）：6 件真实型号补齐官网来源、`confidence` 升到 `high`；
- 2 件答辩占位符已替换为**真实型号**（保持 337mm / 320mm 锚点数值不变）：
  - `gpu-demo-337` → **MSI GeForce RTX 4070 Ti GAMING X TRIO 12G**（官方规格 337×140×62mm，精确 337mm）；
  - `case-demo-320` → **COUGAR Airface Pure Pro**（官方 320mm GPU clearance，精确 320mm）；
- 目录价合计 ¥7,800 是**答辩锚点价**，保持不变（牵连 `GPU_CASE_LENGTH` 规则、fixture、DEMO 脚本与数字附录）；
- 目录价与市场价的量级差异（如 CPU 1500 vs 京东约 999、内存 500 vs 2026 涨价后约 5000）记录在案，
  但不改动锚点价——价格是「带日期的参考价」，答辩口径以 `SOURCES.md` 为准；
- **6 类各 3–5 条已覆盖**（CPU 5 / 主板 6 / 内存 3 / 显卡 6 / 电源 3 / 存储 3），除 `gpu-demo-300`（边界测试占位符）外均已补官网来源，`low` 只剩这一条且有原因。

---

## 1. 演示配置 8 件核对结果（11/17 交付）

> 目录价来源 `assets/catalogue/catalogue-app.json`；`source_url` 已写入 `pricedata/seeds/2026-11.csv`。
> `observed_at=2026-11-04`（目录快照日期，保持 header 一致）。

| # | 记录 ID | 型号 | 目录价 | 来源（source_url） | source_type | confidence |
|---|---|---:|---|---|---|---|
| 1 | `cpu-demo-7600x` | AMD Ryzen 5 7600X | 1500 | amd.com（7000 系列产品页） | official | high |
| 2 | `mb-demo-b650` | ASUS TUF GAMING B650-PLUS WIFI | 900 | asus.com（TUF B650-PLUS WIFI） | official | high |
| 3 | `mem-demo-ddr5-32` | Kingston FURY Beast 32GB DDR5 | 500 | kingston.com（FURY Beast DDR5） | official | high |
| 4 | `storage-demo-nvme` | Samsung 990 EVO 1TB | 200 | samsung.com（990 EVO 1TB） | official | high |
| 5 | `gpu-demo-337` | MSI RTX 4070 Ti GAMING X TRIO 12G（337mm） | 3400 | msi.com（规格页 337×140×62） | official | high |
| 6 | `psu-demo-750` | Seasonic FOCUS GX-750 | 600 | seasonic.com（FOCUS GX） | official | high |
| 7 | `case-demo-320` | COUGAR Airface Pure Pro（320mm） | 400 | cougargaming.com（Airface Pure Pro） | official | high |
| 8 | `cooler-demo-160` | Thermalright PA120 | 300 | thermalright.com（PA120） | official | high |

**合计**：¥7,800（答辩锚点，不变）。

### 1.1 6 类覆盖情况

| 类目 | 记录数 | confidence 分布 | 说明 |
|---|---|---|---|
| CPU | 5 | 3 high + 2 medium | `13600k`/`7500f` 已补官网；`9600x`/`245k` 为零售 medium |
| 主板 | 6 | 6 high | `lga1700` 已补 MSI；其余 5 high |
| 内存 | 3 | 2 high + 1 medium | `ddr4-32` 已补金士顿；`ddr5-16` 为零售 medium |
| 显卡 | 6 | 5 high + 1 low | `gpu-300` 保留 low（边界占位符，见下） |
| 电源 | 3 | 2 high + 1 medium | `psu-550` 已补酷冷；`rm750e` 为零售 medium |
| 存储 | 3 | 2 high + 1 medium | `sata` 已补 Crucial；`sn580` 为零售 medium |

> 唯一保留 `confidence=low` 的是 `gpu-demo-300`（`DEMO-300`，非在售型号）：它是 `GPU_CASE_LENGTH` 的 `<=` 边界锚点（300<=320 PASSED / 320==320 等号），原因已写入 `seeds/2026-11.csv` 的 notes 与 `SOURCES.md`。

---

## 2. 占位符替换记录

- `gpu-demo-337`：`NVIDIA / RTX 4070 Ti (337mm) / DEMO-337` → `MSI / GeForce RTX 4070 Ti GAMING X TRIO 12G / RTX-4070TI-GAMING-X-TRIO-12G`。
  官方规格 337×140×62mm、285W、16-pin（12VHPWR）——与 demo 锚点 `lengthMm=337 / tdpW=285 / 12VHPWR` 一致，`thicknessMm` 保留 demo 的 61（官方 62，差异 1mm，不影响 `GPU_CASE_THICKNESS` 判定）。
- `case-demo-320`：`Fractal / North (320mm) / FD-NORTH` → `COUGAR / Airface Pure Pro / CGR-2GA5B-P`。
  官方 320mm GPU clearance、支持 ATX/Micro-ATX——与 demo 锚点 `maxGpuLengthMm=320 / supportedFormFactors=ATX,MICRO_ATX` 一致。
- 同步改动：`seeds/2026-11.csv`、`overlay/demo-anchors.json`、`catalogue-app.json`、`demo-v1.json`、`SOURCES.md`。
- **未改 `assets/fixtures/`（冻结区）**：fixture 按 `componentId + attributes` 求值，`conflictId` 依赖 `gpu-demo-337.lengthMm` / `case-demo-320.maxGpuLengthMm`，
  型号名不影响判定；`fx_gpu_len_conflict_337_320` 的 337 vs 320 仍 BLOCKER。

---

## 3. 市场参考价（记录，不改锚点价）

| 记录 ID | 目录价 | 市场参考价（2026-11 初查） | 说明 |
|---|---:|---:|---|
| `cpu-demo-7600x` | 1500 | ≈ 999（京东 AMD 自营） | 锚点价偏高 |
| `mb-demo-b650` | 900 | ≈ 1799（京东） | 锚点价偏低 |
| `mem-demo-ddr5-32` | 500 | ≈ 5269（京东自营，异常高） | 2026 DDR5 涨价；锚点价是涨价前量级 |
| `storage-demo-nvme` | 200 | ≈ $149.99（三星美站） | 锚点价偏低 |
| `psu-demo-750` | 600 | ≈ 799（全新）/ 449（拆机） | 锚点价居中 |
| `cooler-demo-160` | 300 | ≈ 175–220（京东/淘宝） | 锚点价偏高 |

---

## 4. 字段核对（INFO-02 收尾）

- `docs/03 §3.3` 新增 MOTHERBOARD `pcieX16SlotMm`（number，mm）与 `pcieSlotCount`（number，个）；
- `FieldPathRegistry.kt` 同步（代码冻结表副本）；
- 已填 5 块主板（`mb-demo-b650`、`mb-demo-b850-cvn`、`mb-demo-b850m`、`mb-demo-z890m`、`mb-demo-b550m`），
  值为标准布局参考值（ATX 137.5mm / M-ATX 76.5mm，基于现有 3D 常量公式）；
- `Scene3d` 已按字段放置显卡（`pcieX16SlotMm` 即首个 x16 槽，`pcieSlotCount` 覆盖槽数），
  缺字段的 `mb-demo-lga1700` 走 I/O 下缘 − 7.52 的常量回退，界面注明「缺字段回退」；
- 导出时字段 `state` 默认随记录级 `confidence=high` 标为 `VERIFIED`，但数值精度是「标准布局参考级」，
  精确逐板值需厂商布局图人工核实（TODO：升为逐板实测值）；
- 自 2026-11-21 起，`specs_json` 里的单个字段可显式写 `"state": "REPORTED"`（或 `VERIFIED`/`EMPTY`）
  覆盖记录级推导，取值与「EMPTY 不许带 `value`/`numeric`」在 `export.validate()` 里校验——
  ADR-0011 决策 5 要求证据等级是**每字段**的，不是每记录；
  这个开关同时能提级：把低 `confidence` 记录的字段覆盖成 `VERIFIED` 必须在 PR 描述里给出处，
  否则审查无法判断证据是否够；
- `mb-demo-lga1700` 未加字段，保留「唯一 EMPTY = `biosGeneration`」锚点。

---

## 5. 后续

- ~~6 类各 3–5 条价格核对~~ ✅ 已完成（见 §1.1）；
- ~~数字附录（INFO-03）~~ ✅ 已产出 `materials/source/Data_Appendix.md`；
- 5 块主板的 `pcieX16SlotMm` 用厂商布局图核实后升 `VERIFIED`（当前为标准布局参考值）；
- P4 爬虫：补 1 个官方 adapter（华硕/七彩虹）或人工 CSV（可后置）。
