# 任务看板

> **自由认领**：不锁分工。想做哪个，先把表里的“认领”改成自己，开分支
> `feat/<id>-<slug>`，按 [COLLABORATION.md](../../COLLABORATION.md) 走 PR。
> 可以跨优先级、可以一人做多块；撞车就拆任务，不要抢同一个文件。

## 已完成

| ID | 模块 | 状态 | Owner | 文档 |
|---|---|---|---|---|
| S1-00 | Gradle 骨架 / 14 模块 / CI | done | @MoeField | [D1-01](D1-01.md) |
| D1-01 | 目录解析 + `CataloguePort` | done | @MoeField | [D1-01](D1-01.md) |
| D2-01 | 诊断/替换状态机 76 格 | done | @MoeField | [D2-01](D2-01.md) |
| D2-02 | ViewModel + Entry/Workspace/Diagnostics | done | @MoeField | [D2-01](D2-01.md) |
| D3-01 | 15 条规则 + PSU 公式 | done | @MoeField | [D3-01](D3-01.md) |
| D5-01 | 替换建议 | done | @MoeField | [D5-01](D5-01.md) |
| D6-01 | 比较 + 快照 | done | @MoeField | [D6-01](D6-01.md) |
| DEMO-01 | 目录扩到 32 条（8 类 ≥3）+ 来源表 | done | @MoeField | [DEMO-01](DEMO-01.md) |
| D4-01 | 预算推荐（6 类，权重可见，iGPU 过滤） | done | @MoeField | [D4-01](D4-01.md) |

## 待做（按优先级）

| ID | 模块 | 状态 | 认领 | 依赖 | 文档 |
|---|---|---|---|---|---|
| PRICEDATA-01 | 2020+ 价格数据管道（6 类，半自动） | **design** | @MoeField | — | [pricedata/README.md](../../pricedata/README.md) |
| QA-01 | 矩阵 / 变异 / 批次报告 | ready | 开放 | D1–D6 | [QA-01](QA-01.md) |
| DATA-02 | Room 持久化 | backlog | 开放 | D2 | [DATA-02](DATA-02.md) |
| POLISH-01 | 文案资源 / 可访问性 / 截图 | doing | @MoeField | D2/D6 | [POLISH-01](POLISH-01.md) |
| DEFENSE-01 | 讲稿 / 演示脚本 / Q&A | backlog | 开放 | D4/D6 | [DEFENSE-01](DEFENSE-01.md) |
| RELEASE-01 | 签名 APK / Release | backlog | @MoeField | QA | [RELEASE-01](RELEASE-01.md) |

## 认领规则

1. 先改本表“认领”，再开分支；没改就开 PR 也算认领，但要在 PR 里写。
2. 只改自己任务包里的路径；跨模块先拆任务。
3. 一个 PR ≤ 400 行有效 diff；超过就拆。
4. AI 生成的代码也要人来跑一次验收命令、看一眼截图。
5. 不强制按顺序，也不强制每人只做一个；没人做的由 @MoeField 兜底，但不要沉默地等。

## 本周目标（2026-11-04 起）

1. **D4 推荐**：Entry 能输入预算/用途，生成 8 类起始配置；
2. **DEMO 目录**：已到 32 条；继续补到 8 类 × 4（另开 DEMO-02）；
3. **QA 矩阵**：`check-matrix` 读真实测试结果，不再只打印规格；
4. **DATA 持久化**：杀进程后 A/B 还在；
5. 上述完成后，更新演示脚本与截图。
