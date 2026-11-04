# B. 规则表 DSL 与规则明细

## B.0 先打三个契约补丁（走 git 默认批准）

B 章一展开，A 的 schema 有三处不够支撑“来源冲突”和“冲突可引用”，先补：

**补丁 1：`FieldValue` 支持多来源。** 原 `source: String?` 无法表达“两个 reported 来源数值不同”。

```kotlin
data class FieldSource(
    val source: String,          // 厂商页/零售商/社区
    val rawValue: String,
    val state: VerificationState
)

data class FieldValue(
    val value: String?,          // 合并后的展示值；冲突时可保留其中一个，但 conflict=true
    val state: VerificationState,// 合并后最弱/冲突标记
    val sources: List<FieldSource>,
    val conflict: Boolean,       // 多来源数值不一致
    val numeric: Double? = null
)
```

**补丁 2：`Conflict` 增加 `conflictId`。** 同一规则可能对多条内存产生多个冲突，替换建议需要稳定引用。

```kotlin
data class Conflict(
    override val ruleId: String,
    override val weakest: VerificationState,
    val conflictId: String,      // 生成规则见 B.2
    val left: FieldRef,
    val leftValue: String,
    val right: FieldRef,
    val rightValue: String,
    val severity: Severity
) : RuleOutcome
```

**补丁 3：新增字段路径（只追加，不改旧路径）。**

| 路径 | 类型 | 用途 |
|---|---|---|
| `cpu.minBiosGeneration` | string | BIOS 规则左值 |
| `memory.moduleCount` | number | 每套内存条数，算插槽占用 |
| `case.maxGpuSlots` | number | GPU 槽宽上限 |
| `gpu.requiredPsuConnectors` | csv | PSU 接口子集检查 |
| `motherboard.storageInterfaces` | csv | 存储接口支持 |

## B.1 规则 DSL schema

规则存 `assets/rules/rules-v1.json`，运行时解析为：

```kotlin
data class RuleDef(
    val ruleId: String,
    val kind: RuleKind,
    val left: FieldSpec,
    val right: FieldSpec?,
    val severity: Severity,
    val aggregation: Aggregation,
    val formula: String? = null,   // 仅 PSU 负载等需要公式的规则
    val fixtures: List<String>
)

data class FieldSpec(
    val category: Category,
    val path: String
)

enum class RuleKind {
    EQUALS, CSV_CONTAINS, CSV_SUBSET,
    NUMERIC_LE, NUMERIC_GE,
    COUNT_LE, SUM_LE
}

enum class Aggregation { SINGLE, PER_ITEM, SUM, COUNT }
```

`rules-v1.json` 校验（CI）：`ruleId` 唯一、`path` 在 A.5 + B.0 路径表内、`severity` 合法、fixtures 覆盖四类、`kind` 与字段类型匹配。

## B.2 规则执行语义（冻结）

**输入输出契约：**

```kotlin
interface CompatibilityRuleEngine {
    fun evaluate(revision: BuildRevision, catalogue: Catalogue): List<RuleOutcome>
}
```

- 不读价格、不依赖 Android、不调用 UI。
- 输出按 `ruleId` 字典序稳定排序；UI 可再按 severity 展示，但引擎输出顺序固定。

**单条规则三态判定顺序：**

1. 部件缺失或类别不匹配 → `Unverified(MISSING_FIELD)`。
2. 任一输入字段 `conflict == true` → `Unverified(SOURCE_CONFLICT)`。
3. 任一输入字段 `value == null || state == EMPTY` → `Unverified(MISSING_FIELD, missingField=第一个缺失字段)`。
4. 比较通过 → `Passed(weakest)`。
5. 比较失败 → `Conflict(..., weakest, severity)`。

**weakest 传播：** 取该规则所有输入字段 `state` 中最弱，`EMPTY > REPORTED > VERIFIED`。`REPORTED` 可以产生 `Passed`，但 `weakest=REPORTED`，UI 必须显示“基于报告数据”。只有 `EMPTY`/缺失/来源冲突才强制 `Unverified`。

**conflictId 生成（稳定）：**
`"${ruleId}:${left.componentId}.${left.path}:${right?.componentId}.${right?.path}"`。多实例规则（如每条内存）在末尾追加 `#${itemIndex}`。替换建议的 `conflictRuleId` 用 `ruleId`，具体实例用 `conflictId`。

**失败模式：** 未知 `ruleId`/未知路径 → 契约错误，CI 应提前拦截；运行时降级为 `Unverified(UNSUPPORTED_RULE)` 并记日志，**不得 crash**。缺少部件 → 该规则 `Unverified(MISSING_FIELD)`，不影响其他规则。

## B.3 规则表（首版 14 条）

| # | ruleId | kind | 左 → 右 | severity | 说明 |
|---|---|---|---|---|---|
| 1 | `CPU_SOCKET_MATCH` | EQUALS | cpu.socket → mb.socket | BLOCKER | 插槽必须完全相等 |
| 2 | `CPU_MB_CHIPSET_SUPPORT` | CSV_CONTAINS | mb.chipset ∈ cpu.supportedChipsets | BLOCKER | 主板芯片组需被 CPU 支持 |
| 3 | `CPU_MB_BIOS_SUPPORT` | NUMERIC_GE | mb.biosGeneration ≥ cpu.minBiosGeneration | BLOCKER | 任一缺失 → Unverified；答辩 BIOS 空即走此条 |
| 4 | `MEMORY_DDR_GENERATION` | EQUALS | memory.generation → mb.memoryGeneration | BLOCKER | PER_ITEM，每条内存都查 |
| 5 | `MEMORY_SLOT_COUNT` | COUNT_LE | sum(memory.quantity × moduleCount) ≤ mb.memorySlots | MAJOR | 插槽占用 |
| 6 | `MEMORY_CAPACITY_LIMIT` | SUM_LE | sum(memory.quantity × kitCapacityGb) ≤ mb.maxMemoryGb | MAJOR | 容量上限 |
| 7 | `GPU_CASE_LENGTH` | NUMERIC_LE | gpu.lengthMm ≤ case.maxGpuLengthMm | BLOCKER | 337 vs 320 锚点 |
| 8 | `GPU_CASE_THICKNESS` | NUMERIC_LE | gpu.thicknessMm ≤ case.maxGpuThicknessMm | MAJOR | 厚度 |
| 9 | `GPU_CASE_SLOT_WIDTH` | NUMERIC_LE | gpu.slotWidth ≤ case.maxGpuSlots | MAJOR | 槽宽 |
| 10 | `PSU_CONTINUOUS_POWER` | NUMERIC_GE | psu.continuousW ≥ requiredW | BLOCKER | `requiredW = (cpu.tdpW + gpu.tdpW + 100) × 1.2`，向上取整；公式冻结，改公式走契约 |
| 11 | `PSU_CONNECTORS` | CSV_SUBSET | gpu.requiredPsuConnectors ⊆ psu.connectors | BLOCKER | 接口集合 |
| 12 | `COOLER_CASE_HEIGHT` | NUMERIC_LE | cooler.heightMm ≤ case.maxCoolerHeightMm | BLOCKER | 散热器高度 |
| 13 | `COOLER_CPU_SOCKET` | CSV_CONTAINS | cpu.socket ∈ cooler.socketMounts | BLOCKER | 扣具支持 |
| 14 | `MB_CASE_FORM_FACTOR` | CSV_CONTAINS | mb.formFactor ∈ case.supportedFormFactors | BLOCKER | 板型 |
| 15 | `STORAGE_MB_INTERFACE` | CSV_CONTAINS | storage.interface ∈ mb.storageInterfaces | MAJOR | 存储接口 |

**明确不做：** CPU/GPU 瓶颈、FPS、跑分、3D、机箱存储位数量、实时价格。瓶颈属于性能预测，进范围外清单。

## B.4 Fixture 规范与答辩锚点

每个 `ruleId` 至少四个 fixture：`pass / conflict / unverified / missing`。Fixture 格式：

```json
{
  "fixtureId": "fx_gpu_len_conflict_337_320",
  "isDemo": true,
  "components": {
    "gpu-demo-337": {
      "category": "GPU",
      "attributes": {
        "lengthMm": {"value": "337", "state": "VERIFIED", "sources": [], "conflict": false, "numeric": 337.0}
      }
    },
    "case-demo-320": {
      "category": "CASE",
      "attributes": {
        "maxGpuLengthMm": {"value": "320", "state": "VERIFIED", "sources": [], "conflict": false, "numeric": 320.0}
      }
    }
  },
  "expected": {
    "GPU_CASE_LENGTH": {
      "outcome": "CONFLICT",
      "conflictId": "GPU_CASE_LENGTH:gpu-demo-337.lengthMm:case-demo-320.maxGpuLengthMm",
      "leftValue": "337",
      "rightValue": "320",
      "weakest": "VERIFIED",
      "severity": "BLOCKER"
    }
  }
}
```

**答辩锚点 fixture（必须存在，`isDemo=true`）：**

| fixtureId | 断言 |
|---|---|
| `fx_gpu_len_conflict_337_320` | 337 > 320 → CONFLICT，BLOCKER |
| `fx_gpu_len_pass_300_320` | 300 ≤ 320 → PASSED |
| `fx_bios_missing` | mb.biosGeneration 为 EMPTY → UNVERIFIED，missingField 指向它 |
| `fx_source_conflict_gpu_len` | 两个 reported 来源 337/335 → UNVERIFIED，reason=SOURCE_CONFLICT |
| `fx_psu_power_conflict` | requiredW > continuousW → CONFLICT |
| `fx_memory_gen_conflict` | DDR4 vs DDR5 → CONFLICT |
| `fx_suggestion_300_plus_100` | 替换后总价 7800→7900，GPU_CASE_LENGTH PASSED，BIOS 仍 UNVERIFIED |

最后一条跨规则引擎和替换模块，作为端到端 fixture。

## B.5 规则新增与 CI 校验

新增规则或硬件代际：扩 catalogue → 加 `rules-v1.json` 条目 → 加四类 fixture → 跑回归。**不改 UI、不改状态机**。CI 校验：

- 路径存在、类型匹配、`ruleId` 唯一。
- 每个 ruleId 至少 4 fixture，且覆盖 `PASSED/CONFLICT/UNVERIFIED/MISSING_FIELD`。
- `isDemo=true` 的 fixture 不得进入生产 catalogue seed。
- mutation testing：`NUMERIC_LE` 的 `<=` 改 `<`、`EMPTY` 改 `PASSED`、`CSV_CONTAINS` 反向，必须被测试杀死，否则不合并。

## B.6 规则引擎测试清单

- 每条规则四类 fixture。
- weakest 传播：全 VERIFIED → VERIFIED；含 REPORTED → REPORTED；含 EMPTY → 必 UNVERIFIED。
- 来源冲突优先于数值比较。
- 多内存 PER_ITEM 冲突各自生成 conflictId。
- 输出顺序稳定；同输入两次结果完全相等。
- 缺部件不 crash，只该规则 Unverified。
- 公式规则（PSU）边界：等于、差 1、向上取整。

---

B 到此。下一步按顺序是 **D：六模块契约卡逐张展开**（输入/输出/失败模式/禁止事项/验收用例），还是先 **E：测试 oracle 与失败归因**？我建议先 D，因为模块卡直接决定 LLM 生成顺序；E 可以最后收口。