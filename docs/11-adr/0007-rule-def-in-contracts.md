# ADR-0007 · `RuleDef` 放在 `:contracts`

- 状态：**提议**（等 Captain 批准；未批准前只在分支上，不进 `main`）
- 影响：`:contracts`、`:data`、`:domain:rules`
- 相关：`docs/03 §3.2`、`docs/05 §5.4`、`docs/06 D3`

## 背景

`docs/05 §5.4` 与 `docs/06 D3` 都写了：

```kotlin
class DefaultRuleEngine(private val rules: List<RuleDef>, ...)
```

并规定「JSON 解析在 `:data`，domain 只接收已解析的 `List<RuleDef>`」。
但 `:contracts` 里没有 `RuleDef`，`docs/03 §3.2` 只给了 JSON schema。

这造成一个循环：

- `RuleDef` 放 `:domain:rules` → `:data` 必须依赖 `:domain:rules`，违反 `ci/modules.json` 的 `data.allowed = [":contracts", ":contracts-test"]`；
- `RuleDef` 放 `:data` → `:domain:rules` 必须依赖 `:data`，违反「domain 只依赖 contracts」；
- 让 domain 自己解析 JSON → 违反 ADR-0005 与 `domain:rules` 的 `kotlinx.serialization` 禁令。

## 决策

在 `:contracts` 新增一个纯数据文件 `Rules.kt`，只包含类型，不含解析与判定：

1. `RuleKind`：7 种，冻结，与 `docs/03 §3.2` 一一对应；
2. `Aggregation`：`SINGLE / PER_ITEM / COUNT / SUM`；
3. `RuleOperand(category, path)`；
4. `RuleDef(ruleId, kind, left, right, severity, aggregation, formula)`。

`fixtures` 字段不进 `RuleDef`：它只服务 CI，引擎不需要；`:data` 的 DTO 可以忽略它。

## 后果

正面：

- 依赖矩阵不变：`data → contracts`、`domain:rules → contracts`；
- `RuleDef` 可被 `contracts-test` 复用，规则表 schema 有单一类型来源；
- 引擎构造函数与文档签名一致。

负面：

- `:contracts` 多了一个「规则 DSL」概念，不再是纯结果类型；
- 今后新增 `kind` 需要改契约（但这本来就是冻结要求）。

## 替代方案

| 方案 | 为什么不选 |
|---|---|
| `RuleDef` 放 `:domain:rules` | `:data` 不能依赖 domain，矩阵破 |
| `RuleDef` 放 `:data` | domain 不能依赖 data，矩阵破 |
| domain 自己解析 JSON | 违反 ADR-0005；domain 会引入序列化库 |
| 每个模块各自定义一份 | 两份类型会漂移，fixture 与实际引擎对不上 |

## 复核触发条件

- 规则 DSL 需要支持第 8 种 `kind`；
- `formula` 需要从字符串升级为表达式树；
- 规则需要跨进程/网络传输（届时可能需要 `@Serializable`，但不应由 contracts 引入序列化依赖）。

## 答辩口径

「规则表是数据，不是代码；`RuleDef` 是数据在类型系统里的形状，放在契约层，
所以数据层能解析、领域层能判定，两边都不需要认识对方的实现。」
