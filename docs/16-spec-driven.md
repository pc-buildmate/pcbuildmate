# 16 · 规约驱动开发与 spec-kit

> 结论：**方法论值得借鉴，但本项目不引入 spec-kit。**
> 本地有 `uv` 也不代表要装 `specify-cli`；装了反而多一个规格源。

## 16.1 我们其实已经在做

| spec-kit 阶段 | 本项目对应物 |
|---|---|
| Constitution | `docs/01`–`04`、`contracts/`、`AGENTS.md`、`COLLABORATION.md` |
| Specify | `docs/00`、`docs/tasks/<id>.md` 的“目标 / 输出契约 / 反例” |
| Clarify | 任务包 §9“规格空白（必须提问）” |
| Plan | `docs/05`、`docs/06`、`PLAN.md`、ADR |
| Tasks | `docs/tasks/BOARD.md` + 每个任务包 |
| Implement | 分支 + PR，按 allowed paths |
| Converge | 测试、矩阵、变异、review、diff 自查 |
| Checklist | `check-docs` / `check-boundaries` / `check-keywords` |
| Analyze | `check-matrix` + `MUT-01..12` |

我们比通用 spec-kit 多了三样东西：

1. **冻结契约**：`contracts/`、`assets/rules/`、`assets/fixtures/`、`docs/01`–`04`；
2. **可执行验收**：60 fixture、76 格状态机、18 不变量、12 变异、Room 契约；
3. **领域约束**：三态证据、revision 保护、PSU 公式、无 GPU/核显 guard。

这些不是 `spec.md` 能自动生成的。

## 16.2 为什么不装 spec-kit

| 问题 | 后果 |
|---|---|
| 会多出 `.specify/`、`specs/` 一套平行规格 | 和 `docs/`、`contracts/` 争“唯一事实源” |
| 通用模板不懂本项目的冻结区 | 生成的 task 很可能让 agent 改 fixture/契约 |
| 迁移成本 | 距 11/28 冻结只剩三周，收益低于冲突成本 |
| 命令生成新文件 | 又一次 #19 式“同路径两份实现” |
| CodeBuddy 集成 | 当前 agent 已按 `AGENTS.md` 工作，不需要再插一层 |

**现在引入 = 给已经稳定的流程加一层中间层。** 不做。

## 16.3 值得借的三点

1. **先写“能看见的行为”，再写代码**
   任务包 §2 目标 + §7 反例，比“帮我做个功能”清楚得多。
2. **clarify 要前置**
   有规格空白就停在任务包 §9，不要边写边猜。
3. **implement 之后要有 converge**
   实现完对照 §5 输出契约、§6 验收、§7 反例，逐条勾；缺什么补什么，再开 PR。
   本项目已把 converge 做成测试/矩阵/变异；不要只靠“我觉得写完了”。

## 16.4 什么时候可以试 spec-kit

- 新开一个**没有冻结契约**的绿地项目；
- 或单独 `spike/` 分支做实验，不碰 `main`；
- 课程项目本身不建议。

## 16.5 给组员的一句话

> 我们已经有 spec / plan / tasks / converge，只是名字不叫 speckit。
> 你按 `docs/tasks/TEMPLATE.md` 填满九块，比跑 `/speckit.*` 更稳。
