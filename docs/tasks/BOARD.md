# 任务看板

> 状态以任务包为准；本表只做索引。认领时把自己的 GitHub handle 填进“owner”，
> 开分支 `feat/<id>-<slug>`，按 [COLLABORATION.md](../../COLLABORATION.md) 走 PR。

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

## 待做（按优先级）

| ID | 模块 | 状态 | 建议 owner | 依赖 | 文档 |
|---|---|---|---|---|---|
| D4-01 | 预算推荐：预算+用途 → 完整配置 | **next** | @MoeField | D1/D3 | [D4-01](D4-01.md) |
| DEMO-01 | 目录 16 → 24+ 条 | ready | @LIU132578 | D1 | [DEMO-01](DEMO-01.md) |
| QA-01 | 矩阵 / 变异 / 批次报告 | ready | @yys1yys2yys | D1–D6 | [QA-01](QA-01.md) |
| DATA-02 | Room 持久化 | backlog | @LIU132578 | D2 | [DATA-02](DATA-02.md) |
| POLISH-01 | 文案资源 / 可访问性 / 截图 | doing | @MoeField | D2/D6 | [POLISH-01](POLISH-01.md) |
| DEFENSE-01 | 讲稿 / 演示脚本 / Q&A | backlog | @yys1yys2yys | D4/D6 | [DEFENSE-01](DEFENSE-01.md) |
| RELEASE-01 | 签名 APK / Release | backlog | @MoeField | QA | [RELEASE-01](RELEASE-01.md) |

## 认领规则

1. 只改自己模块的路径；跨模块先拆任务。
2. 任务包里的“允许修改的路径”就是边界，越界 PR 打回。
3. 一个 PR ≤ 400 行有效 diff；超过就拆。
4. AI 生成的代码也要人来跑一次验收命令、看一眼截图。

## 本周目标（2026-11-04 起）

1. **D4 推荐**：Entry 能输入预算/用途，生成 8 类起始配置；
2. **DEMO 目录**：GPU/机箱/电源先补到每类 ≥3；
3. **QA 矩阵**：`check-matrix` 读真实测试结果，不再只打印规格；
4. **DATA 持久化**：杀进程后 A/B 还在；
5. 上述完成后，更新演示脚本与截图。
