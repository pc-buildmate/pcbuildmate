# ADR-0004 · 纯 JVM domain + 端口适配器

- 状态：已采纳（2026-10）
- 影响：全部模块划分
- 相关：docs/05-module-independence.md

## 背景

六个人并行开发，其中大量代码由 LLM 生成。最大的风险不是写不出来，而是
**模块互相渗透**：ViewModel 里算规则、UI 里读数据库、domain 里 import Android。
一旦发生，任何一处的改动都会引发连锁修改，且无法单独测试。

## 决策

1. 所有 `domain:*` 与 `contracts`、`testing` 使用 `kotlin("jvm")`，**无 Android 插件**；
2. 模块之间只能通过 `contracts` 中的数据类型与端口接口通信；
3. 实现类一律 `internal`，跨模块不可见；
4. 依赖矩阵（05 章 §5.3）由 `ci/check-boundaries.mjs` 强制执行；
5. 第三方库（Room、Compose）一律 `implementation`，不随依赖链泄露。

## 后果

正面：

- "UI 读 DAO" 在编译期就不可能（DAO 类型不在 ui 的 classpath）；
- domain 测试 5 秒跑完，不需要模拟器；
- 替换实现（Room ↔ 内存）不影响任何调用方；
- LLM 看不见的东西，它写不出来——这是最有效的约束。

负面：

- 模块数 13 个，构建配置成本上升；
- 需要写端口与 Fake，前期代码量 +20%；
- 对初学者而言"为什么不能直接调用"需要解释。

## 替代方案

| 方案 | 为什么不选 |
|---|---|
| 单模块 + 包结构约定 | 靠自觉，LLM 一定违反；且无法单独测试 |
| 只分 app/data/domain | UI 与 feature 仍可互相 import 实现 |
| 使用 DI 框架约束依赖 | 运行期才报错，太晚 |

## 复核触发条件

- 模块构建时间 > 3 分钟 → 合并过细模块（优先合并 recommend/compare）；
- 端口方法数 > 8 → 拆端口（05 章 §5.6 反例 7）。

## 答辩口径

"我们把 domain 做成**纯 Kotlin**，所以它连 Android 的类都 import 不到。
这不是纪律问题，是编译问题——写错就编译不过。"
