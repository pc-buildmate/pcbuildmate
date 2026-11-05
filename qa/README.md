# QA 矩阵现状

> 生成器：`node tools/gen-matrix.mjs`；检查：`node tools/check-matrix.mjs`
> 数据来自真实 JUnit XML + `fixtures-rules-v1.json` + docs/07 的冻结列表，不手填数字。

## 当前结果（2026-11-05）

| 矩阵 | 已覆盖 | 说明 |
|---|---:|---|
| 规则 15×4 | **60/60** | `FixtureTest` 通过；`MISSING` 来自 fixture id 的 `_missing` 后缀（RuleOutcome 仍是 `UNVERIFIED`） |
| 状态机 4×4 + 6×10 | **76/76** | 两个 `full matrix` 测试通过 |
| 不变量 INV-01..18 | **0/18** | **未挂载；这是缺口，不是通过** |

`check-matrix` 现在会准确报出 18 个不变量空格，而不是打印“规格模式”。

## 为什么 INV 还是 0

不变量不是“再跑一遍规则 fixture”就能证明的；需要逐条挂到真实测试或 CI 检查上：

| INV | 需要的证据 | 现状 |
|---|---|---|
| 01 总价 | 手工构造不一致 total 必须抛异常 | 待补 |
| 02 revision id | 编辑序列 id 严格递增 | 待补 ViewModel/revision 测试 |
| 03 诊断当前性 | stale diagnosis 不展示 | compare 测试覆盖一部分 |
| 04 EMPTY | 15 条 `missing` fixture | ✅ fixture |
| 05 来源冲突 | source conflict fixture | ✅ fixture |
| 06 weakest | fixture weakest 断言 | ✅ fixture |
| 07 Apply 重检 | 替换机 Apply/Recheck 转移 | ✅ state matrix |
| 08 无候选 | D5 no-candidate | ✅ |
| 09 非法事件 | state full matrix | ✅ |
| 10 Undo | 新 id + 内容等于旧快照 | 待补 |
| 11 demo 隔离 | `isDemo` 记录不进生产 seed | 待补 |
| 12 过期比较 | compare stale | ✅ |
| 13 预算 | D4 `total <= budget` | ✅ |
| 14 替换过滤 | D5 先过滤再排序 | ✅ |
| 15 确定性 | 双跑相等 | ✅ |
| 16 依赖矩阵 | `ci/check-boundaries.mjs` | CI 检查，未挂 JUnit |
| 17 端口契约 | `contracts-test` | ✅ 内存；Room 仅真机 |
| 18 无 I/O | boundaries/keywords | CI 检查，未挂 JUnit |

## 原则

- 不为了 18/18 写“只调用一次实现”的空测试；
- 每条 INV 要么有独立断言，要么指向真实 CI 检查；
- 变异测试（MUT-01..12）优先于覆盖率数字；
- 先让矩阵报洞，再补洞。
