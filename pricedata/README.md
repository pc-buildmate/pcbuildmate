# pricedata · 价格数据管道

> 状态：**design / MVP 待开工**｜决定日期：2026-11-04
> 这不是 App 运行时模块，而是 App 的**构建期依赖**：产出静态快照，App 只读本地 JSON。
> 目标不是“抓全网”，而是得到**可追溯、可版本化、可推荐**的 2020 以后家用硬件价格。

---

## 1. 已定的事

| 决定 | 值 |
|---|---|
| 数据起始 | **2020-01-01** |
| 2016–2019 | **默认不做**；如课程需要趋势，只作为 `historical`，`recommendable=false` |
| 推荐资格 | `release_year >= 2020` 且 `status = active` 且 `condition = new` |
| 价格类型 | `list_price_cny` / `promo_price_cny` 分开，另存 `price_cny`（实际采用） |
| 币种 | CNY |
| 渠道 | 官方商城优先；Wayback 补历史；京东/淘宝只做人工 CSV |
| App 集成 | 只读 `assets/catalogue/catalogue-<version>.json`；不联网、不加 `INTERNET` |
| 自动程度 | **半自动**：适配器采集 + 人工复核异常；不做全站全自动爬虫 |

理由：2020 前的平台大量 EOL（Windows 11、驱动、保修、库存），给新手推荐老硬件是不负责任的。
2020 以后的 AM4/B550、LGA1200、RTX 30、DDR4/DDR5 仍有可买新件，才适合做“起始配置”。

### 1.1 类目范围（2026-11-04 确认）

| 类目 | 长期价格数据 | App 推荐 | 说明 |
|---|---|---|---|
| CPU | ✅ 必收 | ✅ | |
| 主板 | ✅ 必收 | ✅ | |
| 内存 | ✅ 必收 | ✅ | 联想官方配件页可作参考 |
| 硬盘 | ✅ 必收 | ✅ | 联想官方配件页可作参考 |
| 电源 | ✅ 必收 | ✅ | |
| 显卡 | ✅ 收 | ✅ 可选 | 核显/办公配置可以没有独显；**无 GPU 时 CPU 必须有核显** |
| 机箱 | ❌ 不做 | ⚠️ 仅 demo | 不采历史价；App 保留 `case-demo-320` 做 337/320 锚点 |
| 散热 | ❌ 不做 | ⚠️ 仅 demo | 不采历史价；App 保留 1 条 demo 散热做扣具/高度演示 |
| 无线网卡 / 显示器 / 外设 | ❌ 不做 | ❌ | 课程作业范围外 |

> **为什么 App 还留机箱/散热**：答辩的核心锚点是“337mm 显卡 vs 320mm 机箱”。
> 数据管道不采它们的长期价格，但 App 的 demo 目录保留 1–2 条记录；
> 如果以后要彻底去掉机箱，必须同时删 `GPU_CASE_*` / `MB_CASE_FORM_FACTOR` / `COOLER_CASE_HEIGHT`
> 规则与 fixture，属于契约变更，不在本次范围。

---

## 2. 两层数据

| 层 | 内容 | 是否进 App 推荐 |
|---|---|---|
| **active** | 2020+ 发布、当前仍可买新件、仍受支持 | ✅ D4/D5/D6 只读这一层 |
| **historical** | 2020–2025 已停产/退市型号 | ❌ 只做价格趋势/答辩背景 |

`recommendable` 不是手填，由规则生成：

```text
recommendable = status == "active"
             && release_year >= 2020
             && condition == "new"
```

---

## 3. 目录结构

```text
pricedata/
├─ pyproject.toml                  # uv；Python 3.12+
├─ config/
│  └─ sources.example.yml          # 来源、频率、解析器、限速
├─ src/pricepipe/
│  ├─ adapters/
│  │  ├─ manual_csv.py             # 团队人工填的 CSV
│  │  ├─ asus_store.py             # 华硕商城（优先 JSON 接口，Playwright 兜底）
│  │  ├─ colorful_store.py         # 七彩虹商城
│  │  └─ wayback.py                # CDX + 存档页
│  ├─ normalize.py                 # 型号对齐、单位、渠道、去重
│  ├─ validate.py                  # schema、异常价、缺失字段
│  └─ export.py                    # CSV / JSON / manifest
├─ data/
│  ├─ raw/                         # 原始 HTML/JSON，缓存，不进 git
│  └─ derived/
│     ├─ products.csv
│     ├─ prices.csv
│     ├─ catalogue-<yyyy.mm>.json  # App 快照
│     └─ manifest.json
├─ schema.sql                      # SQLite schema（见同目录）
└─ tests/
```

---

## 4. 数据模型

### products

| 字段 | 说明 |
|---|---|
| `product_id` | 稳定 slug，例如 `cpu-amd-ryzen5-9600x` |
| `category` | CPU / MOTHERBOARD / MEMORY / GPU / PSU / CASE / COOLER / STORAGE |
| `brand` / `model` / `part_number` | 目录展示与去重 |
| `release_year` | 用于 2020 门槛 |
| `platform` | AM4 / AM5 / LGA1700 / LGA1851 … |
| `integrated_graphics` | `YES` / `NO`；CPU 专用。无独显配置必须选 `YES` |
| `status` | `active` / `legacy` / `eol` |
| `recommendable` | 规则生成，不手填 |
| `specs_json` | 只放 `docs/03 §3.3` 冻结字段；导出时映射到 `attributes` |

### prices（长表，核心）

| 字段 | 说明 |
|---|---|
| `observation_id` | 稳定 ID |
| `product_id` | 外键 |
| `observed_at` | ISO 日期（采集日，不是发布日期） |
| `price_cny` | 实际采用价 |
| `list_price_cny` / `promo_price_cny` | 列表价 / 促销价，可空 |
| `channel` | `asus_store` / `colorful_store` / `lenovo_store` / `jd` / `taobao` / `wayback` |
| `condition` | `new` / `used` |
| `source_url` | 可复核链接 |
| `source_type` | `official` / `wayback` / `manual` / `marketplace` |
| `confidence` | `high` / `medium` / `low` |
| `captured_by` | 可填 GitHub handle |
| `notes` | 券后、缺货、疑似错价等 |

---

## 5. 来源与边界

| 来源 | 用途 | 方式 |
|---|---|---|
| 华硕商城 `store.asus.com.cn` | 华硕主板/显卡/机箱当前价 | 低频率；优先找页面自身 JSON；Playwright 兜底 |
| 七彩虹商城 `skycolorful.com` | 七彩虹显卡/主板当前价 | 低频率；页面价 |
| 其他厂商官方商城（华硕、七彩虹、微星、技嘉、华擎、三星、WD、Crucial、金士顿、海韵、海盗船等） | 当前价 | 有官方商城且能稳定取价就加 adapter；没有就人工 CSV |
| 联想官方配件页 | SSD / 内存参考价 | 只收标准消费级型号；OEM/专有件不进兼容规则 |
| Wayback Machine | 2020–2025 历史 | CDX + 存档页；限速 1 req/s；缓存 |
| ZOL / 太平洋 | 历史参数/价格 | 低频；只做交叉核对 |
| 京东 / 淘宝 | 当前价人工抽样 | **只做 manual CSV**，不自动抓 |
| 联想商城 | 整机/笔记本 | **默认不进口配件目录**；如做整机，单列类目，不参与兼容性 |

**不做**：绕过登录/验证码、批量抓 JD/Taobao、把历史价当实时价、抓图片/评论/个人信息。

---

## 6. 管道

```text
collect  →  raw/<source>/<date>/<id>.json + sha256
normalize→  型号匹配、单位、渠道、去重
validate →  schema / 异常价 / 缺失字段 / 2020 门槛
export   →  products.csv + prices.csv + catalogue-<version>.json + manifest.json
publish  →  GitHub Pages（人看）+ GitHub Release（App 取）
```

命令（MVP 目标）：

当前 MVP 用标准库实现，不依赖 uv 安装：

```bash
PYTHONPATH=pricedata/src python3 -m pricepipe.cli validate \
  --seed pricedata/seeds/2026-11.csv

PYTHONPATH=pricedata/src python3 -m pricepipe.cli export \
  --seed pricedata/seeds/2026-11.csv \
  --out pricedata/data/derived \
  --version 2026.11 \
  --generated-at 2026-11-04T00:00:00+00:00
```

---

## 7. 输出与 App 集成

`catalogue-<version>.json` 是 App 唯一依赖，字段与 `contracts/CatalogueRecord` 对齐：

```json
{
  "catalogueVersion": "2026.11",
  "priceDate": "2026-11-04",
  "isDemo": true,
  "components": [
    {
      "componentId": "gpu-demo-5060",
      "category": "GPU",
      "manufacturer": "COLORFUL",
      "model": "iGame RTX 5060 Ultra W DUO OC 8GB",
      "partNumber": "IGAME-5060-UW",
      "priceCny": 4099,
      "attributes": { "lengthMm": { "value": "231", "state": "VERIFIED", "numeric": 231 } },
      "catalogueVersion": "2026.11",
      "priceDate": "2026-11-04",
      "isDemo": true
    }
  ]
}
```

- `isDemo=true` 直到每条记录都有来源、日期、复核人；不要为了去掉“演示数据”标签而提前改 false。
- 显卡是可选的：`UseCase.GAMING` 默认需要独显；办公/学习配置可以没有 GPU。
  没有 GPU 时，PSU 公式规则按 `docs/03 §3.7` 返回 `UNVERIFIED`，不要假装 PASSED。
- **无 GPU ≠ 一定能亮机**：无独显配置必须选 `integrated_graphics=YES` 的 CPU。
  D4 的过滤条件：`gpu == null -> cpu.integrated_graphics == YES`；
  否则不生成该配置，而不是生成一台点不亮的机器。
  当前 demo 四颗 CPU 都有核显；下一批会加 `NO` 的型号（如 7500F / KF）来测这条分支。
- App 只读 assets 里的快照；**不**在运行时请求 GitHub Pages。
- `manifest.json` 记录 `datasetVersion / generatedAt / datasetStart / minRecommendYear / productCount / priceCount / commit`。
- 版本 pin：App PR 更新 `assets/catalogue/catalogue-2026.11.json` 和 `SOURCES.md` 一起提交。

---

## 8. 质量门

1. 每条价格必须有 `source_url` + `observed_at` + `channel`；
2. 同一 `product_id + observed_at + channel + condition + source_type` 唯一；
3. 价格必须是正整数；`promo < list` 或相等；
4. `status` / `recommendable` 与 `release_year` 一致；
5. 异常检测：同类目同月价格 > 3× 中位数或 < 0.3× 中位数 → 进人工复核队列；
6. 导出前跑 `validate`；任何 error 阻断发布；
7. 历史数据只进 `prices.csv`，不进 `catalogue-active`。

---

## 9. 变更手册（给组员的 agent 看）

### 9.1 想把起点改成 2016

1. 改 `config/sources.yml` 的 `dataset_start: 2016-01-01`；
2. 改 `min_recommend_year` 仍保持 `2020`（**推荐门槛不跟着降**）；
3. 2016–2019 全部标 `status=legacy`、`recommendable=false`；
4. 只补 `prices.csv`，不要进 `catalogue-active`；
5. 更新 `README`/答辩口径：“历史参考，不推荐新手”。

### 9.2 想加整机/笔记本（联想）

- 不要往 `Category` 里加 `PREBUILT`：契约枚举冻结，新增类目要走 ADR + `docs/02` 变更；
- 更稳的做法：单独 `prebuilts.csv` / `prebuilts.json`，不参与兼容性规则；
- 如果一定要进 App，先写 ADR，说明为什么需要新类目、对 D3/D5/D6 的影响。

### 9.3 想加自动适配器（JD/Taobao）

- 先读目标站 robots/ToS；不能绕过登录/验证码；
- 只允许低频、缓存、可关闭的 adapter；失败不能阻断其他来源；
- 价格必须带 `source_url`；不能把券后价当列表价；
- 未经批准不要把电商 API key 放进 GitHub Secrets。

### 9.4 想换 App 快照

1. `pricepipe export --version <v>` 生成新 JSON；
2. 跑 `node ci/check-keywords.mjs`（确保没有范围外字段）；
3. 更新 `assets/catalogue/SOURCES.md`；
4. 在 PR 里贴 `manifest.json` 和差异行数；
5. 只改数据，不改 `contracts/`。

### 9.5 想删掉 2016–2019 历史

- 直接改 `dataset_start`，重跑 export；历史表可保留在 `prices.csv`，不影响 App；
- 如果答辩不需要趋势，删掉也无妨，但别声称“覆盖 10 年”。

### 9.6 想彻底不做机箱/散热

不要只删数据。机箱/散热牵连 5 条规则：

`GPU_CASE_LENGTH`、`GPU_CASE_THICKNESS`、`GPU_CASE_SLOT_WIDTH`、`COOLER_CASE_HEIGHT`、`MB_CASE_FORM_FACTOR`。

要删就按契约变更走：

1. 改 `docs/03 §3.3` 字段表、§3.6 规则明细；
2. 改 `assets/rules/rules-v1.json` 与 60 条 fixture（删对应 20 条）；
3. 改 `assets/fixtures/fixtures-anchor-v1.json` 的 337/320 锚点；
4. 改 `docs/06` D2/D5/D6 与 UI 文案；
5. 写 ADR + scope-change，说明答辩演示改用什么冲突。

**课程作业不建议**：337/320 是最直观的演示，删了要重新设计整条故事线。

### 9.7 想加无线网卡/显示器

- 不要往 `Category` 里加枚举；契约冻结；
- 单独 `peripherals.csv`，不参与兼容规则；
- 或者等课程结束再提 ADR。

---

## 10. MVP 进度

- [x] `schema.sql` + `manual_csv` seed（26 条，6 类，2020+）
- [x] `export`：`products.csv` / `prices.csv` / `catalogue-2026.11.json` / `manifest.json`
- [x] `integrated_graphics` 字段；`cpu-demo-7500f` 为 `NO`，用于无 GPU 冲突测试
- [x] 标准库测试 3 条 + CI 步骤
- [ ] `asus_store` / `colorful_store` adapter（当前价）
- [ ] `wayback` 历史回填
- [ ] GitHub Action 每周采集
- [ ] App pin 快照（等 D4 需要时）

> 生成物在 `pricedata/data/derived/`；App 现在仍用 `assets/catalogue/demo-v1.json`，
> 不要在 D4 完成前切换，否则 demo 的 337/320 锚点会失去 case/cooler。

## 11. 明确不做

- 不承诺“2010 年以来所有家用硬件价格”；
- 不做实时价格/库存/电商 API；
- 不把历史价显示成当前可买价；
- 不把 2016–2019 的 EOL 平台推荐给新手；
- 不在 App 运行时联网。
