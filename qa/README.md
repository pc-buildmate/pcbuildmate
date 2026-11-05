# QA 矩阵现状

> 生成器：`node tools/gen-matrix.mjs`；检查：`node tools/check-matrix.mjs`
> 数据来自真实 JUnit XML + `fixtures-rules-v1.json` + docs/07 的冻结列表，不手填数字。

## 当前结果（2026-11-05）

| 矩阵 | 已覆盖 | 证据 |
|---|---:|---|
| 规则 15×4 | **60/60** | `FixtureTest`；`MISSING` 来自 fixture id 的 `_missing` 后缀（RuleOutcome 仍是 `UNVERIFIED`） |
| 状态机 4×4 + 6×10 | **76/76** | `DiagnosisMachineTest` / `ReplacementMachineTest` 的 `full matrix` |
| 不变量 INV-01..18 | **18/18** | 见下表；测试 + CI 检查 |
| 关键变异 MUT-01..12 | **12/12** | `tools/run-mutations.mjs`；每条改坏源码后目标测试必须失败 |

```bash
node tools/gen-matrix.mjs
node tools/check-matrix.mjs
# [matrix] rules 60/60, fsm 76/76, inv 18/18

GRADLE_BIN=./gradlew node tools/run-mutations.mjs
node tools/check-matrix.mjs --mutations
# [matrix] OK — 12/12 关键变异被杀死
```

## 不变量挂载

| INV | 证据 |
|---|---|
| 01 总价 | `RevisionInvariantsTest.inconsistent total` |
| 02 revision id | `WorkspaceRevisionTest.revision ids increase on edit and undo` |
| 03 诊断当前性 | `DefaultBuildComparatorTest.stale diagnosis` |
| 04 EMPTY | `FixtureTest.all 60 rule fixtures` |
| 05 来源冲突 | `FixtureTest.all 7 rule anchor fixtures`（`fx_source_conflict_gpu_len`） |
| 06 weakest | 同上 |
| 07 Apply 重检 | `ReplacementMachineTest.replacement full matrix` |
| 08 无候选 | `DefaultReplacementAdvisorTest.structured no-candidate` |
| 09 非法事件 | `DiagnosisMachineTest.diagnosis full matrix` |
| 10 Undo | `WorkspaceRevisionTest.revision ids increase on edit and undo` |
| 11 demo 隔离 | `CatalogueParserTest.parses the demo catalogue` |
| 12 过期比较 | `DefaultBuildComparatorTest.stale diagnosis` |
| 13 预算 | `DefaultBudgetRecommenderTest.8000 gaming` |
| 14 替换过滤 | `DefaultReplacementAdvisorTest.300mm gpu` |
| 15 确定性 | `FixtureTest.evaluation is deterministic` |
| 16 依赖矩阵 | `node ci/check-boundaries.mjs` |
| 17 端口契约 | `InMemoryBuildStoreTest`（Room 另由真机验证） |
| 18 无 I/O | `node ci/check-boundaries.mjs` |

## 仍要补的

- **Room 契约测试**：目前只在真机验证；JVM 契约测试只覆盖内存实现，不能算 18/18 的 Room 证据。
- **`:test-oracle` 参考模型**：还没单独建模块；目前靠 JS 原型差分 + fixture。
- **UI semantics**：Node 原型 + 真机 smoke，没有 Compose 测试。

D4 全规则集成已补：`:app:testDebugUnitTest` 的 `RecommendationIntegrationTest`
用完整 15 条规则 + 30 条合并目录，断言预算/无 BLOCKER/无 GPU 核显，不断言具体型号。

## 原则

- 每条 INV 要么有独立断言，要么指向真实 CI 检查；
- 不为了数字写“只调用一次实现”的空测试；
- 变异测试优先于覆盖率数字；
- 矩阵从测试结果生成，不手填。
