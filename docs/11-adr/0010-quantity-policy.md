# ADR-0010 · 数量策略：单件类目固定 1 件

- 状态：**已采纳**（2026-11-16）
- 影响：`:contracts`（`QuantityRules`、`TextKey`）、`:feature`（SetQuantity 守卫）、`:ui`（行内控件）
- 相关：`docs/02 §2.8`、`docs/06 §D2`、`docs/defense/DEMO.md`

## 背景

工作区每一行都有 `− / +`，包括 CPU、主板、显卡、电源、机箱、散热。
但 15 条规则按**单件**评估：

- `PSU_CONTINUOUS_POWER` 用 `cpu.tdpW + gpu.tdpW + 100`，**不乘数量**；
- `CPU_SOCKET_MATCH` 等成对规则只比较一条记录；
- 数量只影响 `totalCny`（`BuildItem.quantity × unitPriceCny`）。

因此把 CPU 改成 2 件会：总价 +1500，功耗判定不变，诊断仍可能 `PASSED`。
这是"看起来通过、实际没验证"的错误结论，违背三态证据的初衷。

### 显卡的 0 / 1 / 2

显卡是唯一"可以没有"的类目，但要分清两件事：

| 情况 | 表示方式 | 现在是否允许 |
|---|---|---|
| 核显主机，无独显 | **没有 GPU 条目**（不是 `quantity=0`） | ✅ 允许；`DisplayOutputGuard` 要求 CPU `integratedGraphics=YES` |
| 单卡 | GPU 条目 ×1 | ✅ 默认 |
| 双卡/多卡 | GPU 条目 ×2..4 | ❌ 拒绝：规则按单卡评估功耗与插槽，无法给出可信结论 |

物理上双卡可行，但课程范围与新手场景都不推荐；更重要的是，规则没覆盖就不能假装通过。
所以"支持 0 件"靠的是**不添加条目**，`quantity` 永远 ≥ 1（`BuildItem.init` 已保证）。

## 决策

1. `contracts.QuantityRules` 定义 `multiInstance = {MEMORY, STORAGE}`；
   其余类目 `isValid(category, quantity)` 只接受 `1`。
2. `:feature.setQuantity` 在生成新 revision **之前**校验；不合法时：
   不产生新 revision、写 `Rejection(QUANTITY_INVALID, QUANTITY_NOT_APPLICABLE)` 与 banner。
3. `:ui` 对单件类目只显示 `×1`，不渲染 `− / +`；内存/硬盘保留按钮。
4. 新增 `TextKey.QUANTITY_NOT_APPLICABLE`（只增不改）。
5. 内存/硬盘**不设硬上限**：超槽位应报 `MEMORY_SLOT_COUNT` 冲突，而不是静默拒绝。

## 后果

**正面**

- 用户无法构造"多 CPU / 多主板"这种规则覆盖不到、却显示通过的配置；
- UI 与规则能力一致：能改的才给按钮；
- 拒绝发生在编辑入口，revision 不前进，旧诊断自然过期。

**负面**

- 消费级双路 CPU / 双电源等场景不支持（课程范围外）；
- `AddComponent` 将来实现时也必须调用同一策略，否则会出现第二条绕过路径。

## 替代方案

| 方案 | 为什么不选 |
|---|---|
| 允许 + 页面警告 | 警告不阻止用户保存/比较；诊断仍会显示 `PASSED`，是错误保证 |
| 允许，规则里乘数量 | 要改 15 条规则的语义与 60 条 fixture；PSU 公式也要改，超出范围 |
| 不限制，靠文档说明 | 演示时最容易踩的坑就是点 `+`；文档拦不住 |
| 单件类目也允许但标记 `UNVERIFIED` | 会把"配置无效"伪装成"证据不足"，混淆两种责任 |

## 复核触发条件

- 支持双路 CPU / 多卡并行（需要新规则与新 fixture）；
- `AddComponent` / 导入 plan card 上线（必须复用 `QuantityRules`）；
- 规则开始按数量聚合（例如 `SUM` 覆盖 GPU 功耗）。

## 答辩口径

> 消费级配置里 CPU、主板、显卡、电源、机箱、散热固定 1 件，只有内存和硬盘能改数量。
> 因为规则是按单件评估功耗和插槽的；允许 2 颗 CPU 会让总价翻倍但兼容结论不变，
> 那就是假通过。所以我们直接拒绝，而不是给一个警告。
