# ADR-0006 · 三态证据模型，不新增第四种结果

- 状态：已采纳（2026-10）
- 影响：contracts、domain:rules、ui 文案
- 相关：docs/01-glossary.md §1.1、docs/02-contracts.md §2.3

## 背景

真实数据的质量是分级的：厂商规格页、零售商页面、社区帖子，可信度不同。
最初的模型只有"通过/冲突"，于是所有"数据不够"的情况都被压成了"通过"——
这正好是提案要解决的问题（"the app cannot claim compatibility on the strength of a field nobody has verified"）。

## 决策

1. 字段级证据三态：`VERIFIED / REPORTED / EMPTY`（强弱顺序即此）；
2. 规则级结果三态：`Passed / Conflict / Unverified`；
3. **不新增**第四种结果：来源冲突（同字段多来源数值不一致）表达为
   `Unverified(reason = SOURCE_CONFLICT)`，而不是新的 `Conflict` 或 `Unknown`；
4. 最弱证据向上传播：`weakest` 必须是参与比较的字段中最弱的那一个；
5. `REPORTED` 可以产生 `Passed`，但 UI 必须显示"基于报告数据"。

## 后果

正面：

- "缺证据"与"冲突"在类型上不可混淆；
- 测试矩阵维度固定（3 状态 × 3 结果），不会随需求膨胀；
- 用户看到的是"未验证：缺少 BIOS 信息"，而不是一个红叉。

负面：

- 来源冲突丢失了"左右值"的展示能力（因为不是 Conflict）→ 需在 `missingField` 上指路；
- UI 需要额外解释三态，文案成本上升；
- 数据录入必须标注来源，录入工作量增加。

## 替代方案

| 方案 | 为什么不选 |
|---|---|
| 加第四种结果 `Unknown` | 与 Unverified 语义重叠，矩阵维度翻倍 |
| 把来源冲突做成 Conflict | 会诱导用户"选一个数值"，而正确做法是去确认 |
| 用布尔 `verified` | 丢失 REPORTED 与 EMPTY 的区别 |

## 复核触发条件

- 出现"合法但互相矛盾"的规格（例如厂商自己两版文档冲突）→ 仍归 SOURCE_CONFLICT，并附来源列表；
- 若未来需要"部分可信"的连续置信度 → 那是另一个模型，必须新 ADR。

## 答辩口径

"三态不是三个枚举值，是三种**责任**：
厂商说的、别人说的、没人说的。规则继承最弱的那一个——
所以只要有一项没人确认，我们就不会说'兼容'。"
