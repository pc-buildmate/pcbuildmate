# 20 · 目录一致性审计（DATA-03）

> 生成方式：`node tools/audit-catalogue.mjs`。本文件的统计与表格由脚本产出，数据变更请改 `pricedata/` 后重新导出，不要手改本文件。
> 审计对象：`assets/catalogue/catalogue-app.json` — 30 条 / 版本 `2026.11` / 价格日 `2026-11-04` / `isDemo=true`
> 判定依据：[`docs/03-rules.md`](03-rules.md) §3.3 冻结字段表与 §3.4 CSV、数值解析规范；任务包 [`docs/tasks/DATA-03.md`](tasks/DATA-03.md)
> 运行结果：**30 条记录、168 个字段逐项检查完毕；异常 4 条（待修 2 条）；退出码 1**

## 20.1 结论

- 记录级（必需键、ID 唯一、类别、价格与日期、版本一致）：30 条全部合格；
- 字段级（冻结表字段齐全、枚举/正则/版本格式/数值下限、证据三态）：168 个字段全部合格；
- 交叉级（板型↔机箱、socket↔主板、内存代数↔主板、芯片组↔主板）：无断链；
- 来源表：本轮采集 28/30 条与目录对上，陈旧 2 条、目录缺来源 0 条，见 20.6；
- 异常清单：4 条，其中 **2 条待修**，见 20.3；
- 待答辩关注项：字段级 `sources` 落地 0/168，见 20.5。

## 20.2 逐条结果

| # | componentId | 类别 | 型号 | 字段 | 记录级 | 字段级 | 异常代码 |
|---:|---|---|---|---:|---|---|---|
| 1 | `cpu-demo-13600k` | CPU | Core i5-13600K | 5 | PASS | PASS | — |
| 2 | `cpu-demo-245k` | CPU | Core Ultra 5 245K | 5 | PASS | PASS | — |
| 3 | `cpu-demo-7500f` | CPU | Ryzen 5 7500F | 5 | PASS | PASS | — |
| 4 | `cpu-demo-7600x` | CPU | Ryzen 5 7600X | 5 | PASS | PASS | — |
| 5 | `cpu-demo-9600x` | CPU | Ryzen 5 9600X | 5 | PASS | PASS | — |
| 6 | `mb-demo-b550m` | MOTHERBOARD | BATTLE-AX B550M GAMING WIFI6E V14 | 12 | PASS | PASS | — |
| 7 | `mb-demo-b650` | MOTHERBOARD | TUF B650-PLUS | 12 | PASS | PASS | — |
| 8 | `mb-demo-b850-cvn` | MOTHERBOARD | CVN B850 GAMING PRO WIFI7 V14 | 12 | PASS | PASS | — |
| 9 | `mb-demo-b850m` | MOTHERBOARD | iGame B850M ULTRA-S V14 | 12 | PASS | PASS | — |
| 10 | `mb-demo-lga1700` | MOTHERBOARD | PRO B760M-A | 10 | PASS | MISSING_FIELD | MISSING_FIELD |
| 11 | `mb-demo-z890m` | MOTHERBOARD | iGame Z890M ULTRA W V20 | 12 | PASS | PASS | — |
| 12 | `mem-demo-ddr4-32` | MEMORY | FURY Beast 32GB DDR4 | 4 | PASS | PASS | — |
| 13 | `mem-demo-ddr5-16` | MEMORY | FURY Beast DDR5 6000 16GB | 4 | PASS | PASS | — |
| 14 | `mem-demo-ddr5-32` | MEMORY | FURY Beast 32GB | 4 | PASS | PASS | — |
| 15 | `gpu-demo-300` | GPU | RTX 4070 (300mm) | 6 | PASS | PASS | — |
| 16 | `gpu-demo-337` | GPU | GeForce RTX 4070 Ti GAMING X TRIO 12G | 6 | PASS | PASS | — |
| 17 | `gpu-demo-5060` | GPU | iGame RTX 5060 Ultra W DUO OC 8GB | 6 | PASS | PASS | — |
| 18 | `gpu-demo-5070` | GPU | iGame RTX 5070 Vulcan X OC 12GB | 6 | PASS | PASS | — |
| 19 | `gpu-demo-5070ti` | GPU | 战斧 RTX 5070 Ti 豪华版 SFF 16GB | 6 | PASS | PASS | — |
| 20 | `gpu-demo-5080` | GPU | iGame RTX 5080 Ultra W OC 16GB | 6 | PASS | PASS | — |
| 21 | `psu-demo-550` | PSU | MWE 550 | 2 | PASS | PASS | — |
| 22 | `psu-demo-750` | PSU | FOCUS GX-750 | 2 | PASS | PASS | — |
| 23 | `psu-demo-rm750e` | PSU | RM750e 750W | 2 | PASS | PASS | — |
| 24 | `case-demo-320` | CASE | Airface Pure Pro | 6 | PASS | PASS | — |
| 25 | `case-demo-itx` | CASE | NR200 | 6 | PASS | PASS | — |
| 26 | `cooler-demo-160` | COOLER | PA120 | 2 | PASS | PASS | — |
| 27 | `cooler-demo-175` | COOLER | NH-D15 | 2 | PASS | PASS | — |
| 28 | `storage-demo-nvme` | STORAGE | 990 EVO 1TB | 1 | PASS | PASS | — |
| 29 | `storage-demo-sata` | STORAGE | MX500 1TB | 1 | PASS | PASS | — |
| 30 | `storage-demo-sn580` | STORAGE | WD Blue SN580 1TB | 1 | PASS | PASS | — |

## 20.3 异常与解释

| # | 记录 | 检查项 | 现象 | 判定 | 去向 |
|---:|---|---|---|---|---|
| 1 | `mb-demo-lga1700` | 冻结表要求的字段不存在（`MISSING_FIELD` / `pcieX16SlotMm`） | 冻结表要求但记录中没有该字段 | **待修** | 需在本卡或后续卡修复 |
| 2 | `mb-demo-lga1700` | 冻结表要求的字段不存在（`MISSING_FIELD` / `pcieSlotCount`） | 冻结表要求但记录中没有该字段 | **待修** | 需在本卡或后续卡修复 |
| 3 | `case-demo-l216` | 来源表有该 ID 但目录无记录（`SOURCE_STALE` / `SOURCES.md`） | 表格行仍写「LIAN LI Lancool 216」¥579，目录中已无此 ID | 已解释 | 型号已下线或替换；SOURCES.md 归陈思言（INFO-01），本审计只报不改，留痕待其更新来源表 |
| 4 | `cooler-demo-pa120max` | 来源表有该 ID 但目录无记录（`SOURCE_STALE` / `SOURCES.md`） | 表格行仍写「Thermalright PA120 VISION MAX ARGB」¥439，目录中已无此 ID | 已解释 | 型号已下线或替换；SOURCES.md 归陈思言（INFO-01），本审计只报不改，留痕待其更新来源表 |

## 20.4 交叉一致性

主板板型 ↔ 机箱支持列表（`✓` = 该机箱支持该板型）：

| 主板 | formFactor | `case-demo-320` | `case-demo-itx` |
|---|---|:-:|:-:|
| `mb-demo-b550m` | MICRO_ATX | ✓ | ✓ |
| `mb-demo-b650` | ATX | ✓ | — |
| `mb-demo-b850-cvn` | ATX | ✓ | — |
| `mb-demo-b850m` | MICRO_ATX | ✓ | ✓ |
| `mb-demo-lga1700` | MICRO_ATX | ✓ | ✓ |
| `mb-demo-z890m` | MICRO_ATX | ✓ | ✓ |

- socket `AM5`：3 颗 CPU ↔ 3 块主板；
- socket `AM4`：0 颗 CPU ↔ 1 块主板；
- socket `LGA1700`：1 颗 CPU ↔ 1 块主板；
- socket `LGA1851`：1 颗 CPU ↔ 1 块主板；
- 内存 `DDR4`：1 套内存 ↔ 1 块主板；
- 内存 `DDR5`：2 套内存 ↔ 5 块主板；

## 20.5 证据链现状（统计，不计为异常）

| 指标 | 值 |
|---|---:|
| 字段总数 | 168 |
| `VERIFIED` | 144 |
| `REPORTED` | 23 |
| `EMPTY` | 1 |
| 字段级 `sources` 非空 | 0 |
| 字段级来源覆盖率 | 0% |
| 记录级在 `SOURCES.md` 有出处 | 30 / 30 |
| 　其中本轮采集带 URL | 27 / 30 |
| 　其中仅早期 demo 列举（无逐条 URL） | 3 / 30 |

`EMPTY` 是合法三态：规则读到即返回 `UNVERIFIED`，不视为缺数据 bug。本次共 1 个：`mb-demo-b650.biosGeneration`。

**要点**：168 个字段的 `sources` 全部为空数组——字段级证据目前没有落地 URL；`VERIFIED / REPORTED` 的差别来自 `pricedata` 导出时整条记录的 `confidence`（见 [`pricedata/src/pricepipe/export.py`](../pricedata/src/pricepipe/export.py) 的 `_catalogue`），记录级来源在 [`assets/catalogue/SOURCES.md`](../assets/catalogue/SOURCES.md)。答辩口径只能表述为「记录级有来源、字段级暂无 URL」，不要声称字段级可溯源。

### 20.5.1 分门类覆盖率

| 类别 | 记录 | 字段 | 字段级有来源 | 覆盖率 |
|---|---:|---:|---:|---:|
| CPU | 5 | 25 | 0 | 0% |
| MOTHERBOARD | 6 | 70 | 0 | 0% |
| MEMORY | 3 | 12 | 0 | 0% |
| GPU | 6 | 36 | 0 | 0% |
| PSU | 3 | 6 | 0 | 0% |
| CASE | 2 | 12 | 0 | 0% |
| COOLER | 2 | 4 | 0 | 0% |
| STORAGE | 3 | 3 | 0 | 0% |
| **合计** | **30** | **168** | **0** | **0%** |

### 20.5.2 冻结表反向覆盖（声明了、但没有任何记录使用的字段）

`docs/03` §3.3 声明的每一条字段路径都至少被一条记录使用。

## 20.6 来源表交叉（只读，不改）

`assets/catalogue/SOURCES.md` 里有两段口径不同的内容：一段是 2026-11-04 的本轮采集表格（逐条带来源 URL），一段是用散文列出的早期 demo seed（文件自己声明「价格不是本轮采集结果」）。两段分开统计，后一段不计为缺来源：

| 指标 | 值 |
|---|---:|
| 本轮采集表格行 | 30 |
| 　其中与目录匹配 | 28 |
| 　其中目录已无此 ID（陈旧） | 2 |
| 早期 demo seed（散文列举） | 16 |
| 　其中与目录匹配 | 16 |
| 目录记录 | 30 |
| 目录有记录、两段都未提及 | 0 |

陈旧的来源表行（型号已下线或替换，`SOURCES.md` 归陈思言，本卡只报不改）：

- `case-demo-l216` → 来源表写「LIAN LI Lancool 216」¥579，目录中已无该 ID；
- `cooler-demo-pa120max` → 来源表写「Thermalright PA120 VISION MAX ARGB」¥439，目录中已无该 ID；

**口径**：「价格表无空来源」在本仓库只能是「本轮采集的 28 条有 URL」；另 16 条早期 demo 没有逐条 URL，来源表已声明，属已解释项。若要把「每条都有 URL」当闸门，需陈思言在 INFO-01 补采后另开任务，本审计不代为断言。

## 20.7 检查项清单

脚本实现的每条规则都有稳定代码，`grep` 代码即可定位：

- `MISSING_KEY`：记录必需键缺失或为空；
- `DUPLICATE_ID`：componentId 重复；
- `BAD_CATEGORY`：类别不在 8 类之内；
- `BAD_PRICE`：priceCny 非正数；
- `BAD_DATE`：priceDate 不是 YYYY-MM-DD；
- `VERSION_DRIFT`：记录目录版本与顶层不一致；
- `DEMO_DRIFT`：记录 isDemo 与顶层不一致；
- `MISSING_FIELD`：冻结表要求的字段不存在；
- `UNKNOWN_FIELD`：字段不在冻结表内；
- `BAD_STATE`：证据状态不在三态之内；
- `BAD_SOURCES`：sources 不是数组；
- `CONFLICT_FLAG`：conflict=true 需人工裁定；
- `EMPTY_WITH_VALUE`：EMPTY 却带值；
- `VALUE_MISSING`：非 EMPTY 却无值；
- `BAD_ENUM`：枚举值不在取值域；
- `BAD_TOKEN`：CSV token 不是大写 token 或不在取值域；
- `BAD_VERSION`：版本号不是「主.次」；
- `BAD_NUMBER`：数值字段不可解析为数字；
- `NUMERIC_MISMATCH`：numeric 与 value 不一致；
- `NUMBER_BELOW_MIN`：数值低于冻结表下限；
- `CASE_COVERAGE`：板型没有任何机箱支持；
- `BOARD_COVERAGE`：没有主板满足该 CPU socket / 内存代数；
- `CHIPSET_COVERAGE`：CPU 支持芯片组与主板芯片组无交集；
- `SOURCE_STALE`：来源表有该 ID 但目录无记录；
- `SOURCE_MISSING`：目录有记录但来源表两段都未提及；

## 20.8 复现与验收

```bash
node tools/audit-catalogue.mjs            # 打印摘要并重写本文件
node tools/audit-catalogue.mjs --no-write # 只检查不写文件（CI 用）
```

验收标准：退出码 0，且 20.3 节没有「待修」项。
