# 03 · 规则表：DSL、字段路径、15 条规则与 fixture

> 读者：LLM（实现 D3 / D5 前必读）、测试负责人。
> 数据源：[../assets/rules/rules-v1.json](../assets/rules/rules-v1.json)（生成物，勿手改）。
> 生成器：[../tools/gen-fixtures.mjs](../tools/gen-fixtures.mjs)。
> 规则是**数据**，不是代码分支：新增硬件代际 = 改数据 + 加 fixture，不改 UI（提案的核心承诺）。

---

## 3.1 为什么规则必须是数据

如果规则写成代码：

```kotlin
if (cpu.socket != mb.socket) return Conflict(...)
if (mb.biosGeneration < cpu.minBiosGeneration) return Conflict(...)
```

那么每一次新增检查都要改 domain、改测试、改 UI 提示，而且 LLM 每次都会写出略微不同的文案与判定顺序。
写成表格后：

| 变化 | 需要改什么 | 不需要改什么 |
|---|---|---|
| 新增一条检查 | rules JSON + 4 条 fixture | 引擎、状态机、UI |
| 新增一代硬件（DDR6） | 目录数据 + 规则参数 | 引擎 |
| 调整严重度 | rules JSON 一行 | 其它全部 |

**代价**：规则表达力受限于 DSL 的 7 种 kind。这是刻意的——表达力越强，LLM 越容易写出「只有它自己懂」的规则。

## 3.2 DSL schema

```json
{
  "ruleId": "GPU_CASE_LENGTH",
  "kind": "NUMERIC_LE",
  "left":  { "category": "GPU",  "path": "lengthMm" },
  "right": { "category": "CASE", "path": "maxGpuLengthMm" },
  "severity": "BLOCKER",
  "aggregation": "SINGLE",
  "formula": null,
  "fixtures": ["fx_gpu_case_length_pass", "..."]
}
```

| 字段 | 取值 | 语义 |
|---|---|---|
| `ruleId` | 大写蛇形，全局唯一 | 结果的稳定标识；测试 tag、文案 key、批次报告都用它 |
| `kind` | 见下表 7 种 | 比较方式 |
| `left` / `right` | `category + path` | 操作数；`path` 必须在 §3.3 表内 |
| `severity` | `BLOCKER / MAJOR / MINOR` | 只影响 overall 与风险，不改变是否 CONFLICT |
| `aggregation` | `SINGLE / PER_ITEM / COUNT / SUM` | 多件同类部件时如何聚合 |
| `formula` | 字符串或 null | 只用于 PSU 负载这类「先算中间量再比较」的规则 |
| `fixtures` | fixtureId 列表 | CI 用来验证四类齐全 |

### 7 种 kind（冻结，不增不减）

| kind | 语义 | 左/右约定 | 例 |
|---|---|---|---|
| `EQUALS` | `left == right`（字符串精确，先 trim） | 两个单值 | CPU socket / MB socket |
| `CSV_CONTAINS` | `right ∈ split(left)` | **左是集合**，右是单值 | `mb.chipset ∈ cpu.supportedChipsets` |
| `CSV_SUBSET` | `set(right) ⊆ set(left)` | **左是集合**，右是集合 | `gpu.requiredPsuConnectors ⊆ psu.connectors` |
| `NUMERIC_LE` | `left <= right` | 左是被约束量 | `gpu.lengthMm <= case.maxGpuLengthMm` |
| `NUMERIC_GE` | `left >= right` | 左是被约束量 | `psu.continuousW >= requiredW` |
| `COUNT_LE` | `Σ(qty × left) <= right` | 左是每件计数 | 内存条数 / 插槽 |
| `SUM_LE` | `Σ(qty × left) <= right` | 左是每件容量 | 内存总容量 |

`COUNT_LE` 与 `SUM_LE` 的公式相同；分开是为了让测试与文案能区分「数量超了」和「容量超了」。

## 3.3 字段路径冻结表

**唯一权威**。规则、fixture、seed 校验都引用这张表。新增路径必须走契约变更（02 章 §2.9）。

| 类别 | 路径 | 类型 | 单位/取值 |
|---|---|---|---|
| CPU | `socket` | string | `AM5 / AM4 / LGA1700 / LGA1851` |
| CPU | `supportedChipsets` | csv | 大写 token，如 `B650,X670` |
| CPU | `tdpW` | number | W |
| CPU | `minBiosGeneration` | version | `主.次`，如 `1.0` |
| MOTHERBOARD | `socket` | string | 同上 |
| MOTHERBOARD | `chipset` | string | 大写 token |
| MOTHERBOARD | `biosGeneration` | version | `主.次` |
| MOTHERBOARD | `memoryGeneration` | string | `DDR4 / DDR5` |
| MOTHERBOARD | `memorySlots` | number | 个 |
| MOTHERBOARD | `maxMemoryGb` | number | GB |
| MOTHERBOARD | `formFactor` | string | `ATX / MICRO_ATX / MINI_ITX` |
| MOTHERBOARD | `m2Slots` | number | 个 |
| MOTHERBOARD | `sataPorts` | number | 个 |
| MOTHERBOARD | `storageInterfaces` | csv | `M2_NVME,SATA` |
| MEMORY | `generation` | string | `DDR4 / DDR5` |
| MEMORY | `moduleCapacityGb` | number | GB |
| MEMORY | `kitCapacityGb` | number | GB（整套） |
| MEMORY | `moduleCount` | number | 每套条数 |
| GPU | `lengthMm` | number | mm |
| GPU | `thicknessMm` | number | mm |
| GPU | `slotWidth` | number | 槽 |
| GPU | `tdpW` | number | W |
| GPU | `recommendedPsuW` | number | W（仅展示，不参与判定） |
| GPU | `requiredPsuConnectors` | csv | `12VHPWR,8PIN_X2` |
| PSU | `continuousW` | number | W（**连续**功率，非峰值） |
| PSU | `connectors` | csv | 同上 |
| CASE | `maxGpuLengthMm` | number | mm |
| CASE | `maxGpuThicknessMm` | number | mm |
| CASE | `maxGpuSlots` | number | 槽 |
| CASE | `maxCoolerHeightMm` | number | mm |
| CASE | `supportedFormFactors` | csv | 同上 |
| CASE | `driveBays` | number | 个 |
| COOLER | `heightMm` | number | mm |
| COOLER | `socketMounts` | csv | `AM5,AM4,LGA1700` |
| STORAGE | `interface` | string | `M2_NVME / SATA` |

### CSV 解析规范（冻结）

1. 按 `,` 分割 → 去首尾空白 → 丢弃空 token；
2. token 必须匹配 `^[A-Z0-9_]+$`，否则 seed 校验失败（fail-fast）；
3. 比较**区分大小写**（因为 token 已规范化）；
4. 空集合是合法的（表示「没有」），但此时 `CSV_CONTAINS` 必为 CONFLICT：
   「CPU 支持列表为空」不是「不限制」，而是「不支持任何主板」——若想表达未知，用 `EMPTY`。

### 数值解析规范（冻结）

1. 优先用 `FieldValue.numeric`；
2. `numeric == null` 且 `value` 非空 → 用 `value.toDoubleOrNull()`；
3. 解析失败 → `Unverified(INSUFFICIENT_DATA, missingField=该字段)`，**不得**当成 0；
4. 比较使用 `Double` 但所有参数字段约定为整数或一位小数，禁止浮点累加误差敏感的比较。

### 版本号规范（已知限制，必须记住）

`biosGeneration` 采用 `主.次` 两段、比较时转数值（`1.2 -> 1.2`）。
**限制**：`1.10` 会被当成 1.1，小于 1.9。
**缓解**：seed 校验强制 `^\d+\.\d{1,2}$` 且次版本号 ≤ 9；超过就改用新的编码方案（走契约变更）。
**不要**在实现里偷偷改成字符串比较——那会让 `1.10 < 1.9` 变成「字典序正确」，但与数值语义不一致，fixture 会跨掉。

## 3.4 执行语义（冻结）

### 3.4.1 五步判定

见 02 章 §2.3.1。这里只补三个细节：

- **类别不匹配**：`items` 里声明 `category=GPU` 但目录记录是 `MEMORY` → 视为部件缺失 → `Unverified(MISSING_FIELD)`。
  数据层应当在 seed 校验就拦住这种不一致，运行期只是防御。
- **多实例（PER_ITEM）**：对每个 `BuildItem` 各生成一条结果；`ruleId` 相同，`conflictId` 不同（带 `#index`）。
- **聚合（COUNT / SUM）**：先求和再比较，只产生一条结果；`leftValue` 写求和后的值（如 `"6"`）。

### 3.4.2 weakest 传播

`weakest = min(所有参与比较的字段的 state)`，强弱顺序 `VERIFIED > REPORTED > EMPTY`。

| 场景 | weakest | 结果 |
|---|---|---|
| 全 VERIFIED，比较通过 | VERIFIED | PASSED |
| 含 REPORTED，比较通过 | REPORTED | PASSED（UI 加「基于报告数据」） |
| 含 EMPTY | EMPTY | UNVERIFIED |
| 含 conflict=true | 最弱来源 | UNVERIFIED(SOURCE_CONFLICT) |

**注意**：`weakest` 只看**参与该规则比较的字段**，不是整机最弱。这是为了让「某个零件数据差」不污染无关规则。

### 3.4.3 输出顺序

结果按 `ruleId` 字典序（`String.compareTo`，区分大小写）排序。**禁止**按 severity 排序后再交给 UI——
UI 可以自己分组展示，但引擎输出顺序必须稳定（INV-15）。

## 3.5 conflictId 生成（稳定标识）

```
conflictId = ruleId ":" left.componentId "." left.path ":" right.componentId "." right.path
多实例追加 "#" itemIndex
```

示例：

```
GPU_CASE_LENGTH:gpu-demo-337.lengthMm:case-demo-320.maxGpuLengthMm
MEMORY_DDR_GENERATION:mem-demo-ddr4-32.generation:mb-demo-b650.memoryGeneration#0
```

规则：

1. **同一冲突重复计算必须得到同一个 id**（用户点「重新检查」，候选列表不能因为 id 变了而清空）。
2. id 只由引用构成，**不含数值**——数值变了但仍是同一条冲突时，id 不变，UI 才能「原地更新」。
3. `SuggestionResult.conflictId` 用完整 id；`conflictRuleId` 用 `ruleId`。
4. 替换建议的过滤以 `ruleId` 为单位（换掉零件就能消除这类冲突），以 `conflictId` 定位是哪一个实例。

## 3.6 规则明细（15 条）

> 左/右列遵循 §3.2 的 kind 约定。severity 已冻结。
> 完整数据见 [../assets/rules/rules-v1.json](../assets/rules/rules-v1.json)。

| # | ruleId | kind | 左 | 右 | severity | 一句话 | 边界/坑 |
|---|---|---|---|---|---|---|---|
| 1 | `CPU_SOCKET_MATCH` | EQUALS | CPU.socket | MB.socket | BLOCKER | 插槽必须完全相等 | 大小写、空白差异不得影响 |
| 2 | `CPU_MB_CHIPSET_SUPPORT` | CSV_CONTAINS | CPU.supportedChipsets | MB.chipset | BLOCKER | 主板芯片组需被 CPU 支持 | 空集合 = 不支持任何主板（不是「不限」） |
| 3 | `CPU_MB_BIOS_SUPPORT` | NUMERIC_GE | MB.biosGeneration | CPU.minBiosGeneration | BLOCKER | 主板 BIOS 需够新 | 缺失即 UNVERIFIED（答辩锚点） |
| 4 | `MEMORY_DDR_GENERATION` | EQUALS | MEMORY.generation | MB.memoryGeneration | BLOCKER | 内存代际匹配 | PER_ITEM；两条内存各自一条结果 |
| 5 | `MEMORY_SLOT_COUNT` | COUNT_LE | MEMORY.moduleCount × qty | MB.memorySlots | MAJOR | 条数不超插槽 | 多套累加；qty=3 的套装 = 6 条 |
| 6 | `MEMORY_CAPACITY_LIMIT` | SUM_LE | MEMORY.kitCapacityGb × qty | MB.maxMemoryGb | MAJOR | 总容量不超上限 | 与 `moduleCapacityGb` 混用是常见错误 |
| 7 | `GPU_CASE_LENGTH` | NUMERIC_LE | GPU.lengthMm | CASE.maxGpuLengthMm | BLOCKER | 显卡长度 ≤ 机箱限长 | 答辩锚点 337 vs 320 |
| 8 | `GPU_CASE_THICKNESS` | NUMERIC_LE | GPU.thicknessMm | CASE.maxGpuThicknessMm | MAJOR | 厚度 | 61 vs 60 是「看起来没问题」的典型 |
| 9 | `GPU_CASE_SLOT_WIDTH` | NUMERIC_LE | GPU.slotWidth | CASE.maxGpuSlots | MAJOR | 槽宽 | 等号边界必须 PASSED |
| 10 | `PSU_CONTINUOUS_POWER` | NUMERIC_GE | PSU.continuousW | formula | BLOCKER | 连续功率 ≥ 估算负载 | 公式见 §3.7；向上取整；缺 CPU/GPU 即 UNVERIFIED |
| 11 | `PSU_CONNECTORS` | CSV_SUBSET | PSU.connectors | GPU.requiredPsuConnectors | BLOCKER | 接口集合包含 | GPU 无此字段 → UNVERIFIED，不得默认满足 |
| 12 | `COOLER_CASE_HEIGHT` | NUMERIC_LE | COOLER.heightMm | CASE.maxCoolerHeightMm | BLOCKER | 散热器高度 | 160 vs 160 必须 PASSED |
| 13 | `COOLER_CPU_SOCKET` | CSV_CONTAINS | COOLER.socketMounts | CPU.socket | BLOCKER | 扣具支持 | 与 #1 方向相反，别搞混 |
| 14 | `MB_CASE_FORM_FACTOR` | CSV_CONTAINS | CASE.supportedFormFactors | MB.formFactor | BLOCKER | 板型可装入 | ATX 板进 ITX 机箱 |
| 15 | `STORAGE_MB_INTERFACE` | CSV_CONTAINS | MB.storageInterfaces | STORAGE.interface | MAJOR | 主板支持该接口 | SATA 盘插纯 NVMe 板 → CONFLICT |

### 明确不做（写进范围外，见 09 章）

CPU/GPU 瓶颈评分、FPS 估算、跑分排序、机箱硬盘位数量校验、显卡功耗墙、内存时序兼容、
BIOS 具体版本号与 CPU 型号的对应表（需要厂商数据，首版不做——所以 BIOS 只有「够新 / 不够 / 未知」三态）。

## 3.7 PSU 负载公式（冻结）

```
requiredW = ceil( (cpu.tdpW + gpu.tdpW + 100) * 1.2 )
```

| 项 | 含义 | 缺失时 |
|---|---|---|
| `cpu.tdpW` | CPU 标称 TDP（W） | 该规则 `Unverified(MISSING_FIELD)` |
| `gpu.tdpW` | 显卡 TDP（W） | 同上（无独显配置首版不评估此规则，返回 UNVERIFIED 而不是 PASSED） |
| `+100` | 主板/存储/风扇等的固定余量 | 常量，改它走契约 |
| `*1.2` | 20% 余量 | 常量，改它走契约 |

**取整**：先求和、乘系数、再 `ceil`，**最后**与 `continuousW` 比较。
顺序错会差 1 W，而「差 1 W」正是 mutation MUT-03 要杀死的边界。

示例（答辩用）：`(105 + 285 + 100) × 1.2 = 588 → 750W 电源通过，550W 电源冲突`。

**禁止**：用 `gpu.recommendedPsuW` 直接比较（那是厂商建议值，不是规则）；
禁止把公式实现成「分段查表」（不可测且不可解释）。

## 3.8 fixture 规范

### 3.8.1 四类强制用例

每条 ruleId **必须**有且命名一致的四类：

| case | 构造 | 期望 outcome | 期望 weakest |
|---|---|---|---|
| `PASSED` | 两个值都 VERIFIED 且满足比较 | PASSED | VERIFIED |
| `CONFLICT` | 两个值都 VERIFIED 且不满足 | CONFLICT | VERIFIED |
| `UNVERIFIED` | 任一输入 EMPTY | UNVERIFIED(MISSING_FIELD) | EMPTY |
| `MISSING` | 缺一个部件（items 里没有） | UNVERIFIED(MISSING_FIELD) | 视剩余输入 |

`UNVERIFIED` 与 `MISSING` 的区别是**被测代码路径不同**：
前者走「字段为空」，后者走「部件不存在」。两条路径都必须覆盖，因为它们的 `missingField` 不同
（一个是具体 `FieldRef`，一个是 `null`）。

### 3.8.2 文件与命名

```
assets/fixtures/fixtures-rules-v1.json    # 60 条：fx_<ruleId 小写>_<case>
assets/fixtures/fixtures-anchor-v1.json   # 8 条答辩锚点（含跨规则、端到端）
```

fixture 只携带**本用例需要的目录记录**与**字段覆写**，不复制整个目录；
加载器 = 基础目录 → 覆写 → 构造 revision → 调引擎。这样 fixture 小、可读、diff 干净。

### 3.8.3 答辩锚点（必须存在、必须绿）

| fixtureId | 断言 | 为什么重要 |
|---|---|---|
| `fx_gpu_len_conflict_337_320` | 337 > 320 → CONFLICT/BLOCKER，conflictId 固定 | 提案里的标志性例子 |
| `fx_gpu_len_pass_300_320` | 300 ≤ 320 → PASSED | 杀死 `<=` 改 `<` 的变异 |
| `fx_gpu_len_equal_320_320` | 320 ≤ 320 → PASSED | 等号边界 |
| `fx_bios_missing` | BIOS EMPTY → UNVERIFIED，missingField 指向它 | 回答「数据不全怎么办」 |
| `fx_source_conflict_gpu_len` | 两来源 337/335 → UNVERIFIED(SOURCE_CONFLICT) | 回答「数据打架怎么办」 |
| `fx_psu_power_conflict` | requiredW 588 > 550 → CONFLICT | 公式与取整 |
| `fx_memory_gen_conflict` | DDR4 vs DDR5 → CONFLICT | 最常见装机错误 |
| `fx_suggestion_300_plus_100` | 7800 → 7900；GPU PASSED；BIOS 仍 UNVERIFIED | 回答「解决一个冲突就万事大吉了吗」 |

最后一条是**端到端**：它跨 D1→D3→D5→D2，任何一层出错都会让它失败。

### 3.8.4 fixture 也是代码

- 改 fixture 的期望值 = 改语义 → 必须与 ADR 一起提（评审清单第 2 条）。
- **禁止**为了让测试通过而放宽 fixture（例如把 CONFLICT 改成 UNVERIFIED）。见 07 章 §7.10 反模式。
- 生成器 `--check` 模式会比对「生成物 vs 源」，手改生成物会被 CI 抓到。

## 3.9 新增规则 / 新增硬件代际

### 新增一条规则（六步，顺序不可乱）

1. 确认路径已在 §3.3；不在就先走契约变更加路径。
2. 在 `tools/gen-fixtures.mjs` 的 `RULES` 与 `CASES` 里加条目（唯一真相源）。
3. 跑 `node tools/gen-fixtures.mjs`，生成 4 条 fixture，逐个确认期望值**人工正确**。
4. `:domain:rules` 不需要改代码——如果必须改代码，说明规则不是数据，回头改设计。
5. 补一条跨模块测试（若它影响替换建议的过滤）。
6. 更新本文 §3.6 表。

### 新增一代硬件（例：DDR6）

1. 目录加记录（生产 seed）；
2. `memoryGeneration` 的合法取值加 `DDR6`（枚举式字符串，不需要改 schema）；
3. 规则 4 自动生效（EQUALS 比较字符串）；
4. 新增 fixture：DDR6 内存 + DDR5 主板 → CONFLICT；
5. **不改** UI、不改状态机、不改引擎。

这就是提案里「rules live in a data table」的具体含义，答辩时用这条回答「以后怎么扩展」。

## 3.10 规则引擎自检清单

- [ ] `rules-v1.json` 中每个 `path` 都在 §3.3 表内
- [ ] 每个 `ruleId` 有 4 条 fixture 且 case 名正确
- [ ] 每条规则输出按 `ruleId` 字典序
- [ ] 两次同输入调用结果完全相等（`==` 比较）
- [ ] 空 revision 返回空列表且不抛异常
- [ ] 未知 ruleId / 未知路径降级为 `Unverified(UNSUPPORTED_RULE)` 且记日志，不 crash
- [ ] 缺部件只影响该规则，其它规则正常
- [ ] PSU 公式的等号 / 差 1 / 取整三个边界都有测试
- [ ] 多内存 PER_ITEM 的 conflictId 各自独立
