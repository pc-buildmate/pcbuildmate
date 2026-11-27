# 任务看板

> **自由认领**：不锁分工。想做哪个，先把表里的“认领”改成自己，开分支
> `feat/<id>-<slug>`，按 [COLLABORATION.md](../../COLLABORATION.md) 走 PR。
> 可以跨优先级、可以一人做多块；撞车就拆任务，不要抢同一个文件。
> **开工前先 `gh pr list --state open`**：认领可能只写在未合并分支里，BOARD 空着不等于没人做。

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
| D2-03 | 无显示输出 Guard | done | @MoeField | [D2-03](D2-03.md) |
| INT-01 | App 目录快照合并（30 条） | done | @MoeField | [INT-01](INT-01.md) |
| DATA-02 | Room 持久化 A/B | done | @MoeField | [DATA-02](DATA-02.md) |
| DATA-03 | 目录一致性审计（脚本 + 30 条报告） | done | @LIU132578 | [DATA-03](DATA-03.md) · [docs/20](../20-catalogue-audit.md) |
| DATA-04 | 目录/Revision 契约测试 + 拒绝分支 | done | @LIU132578 | [DATA-04](DATA-04.md) |
| D4-01 | 预算推荐（8 类，权重可见，iGPU 过滤） | done | @MoeField | [D4-01](D4-01.md) |
| VIZ-01 | 3D 间隙预览（盒体 + 程序化纹理） | done | @MoeField | [docs/17](../17-3d-preview.md) |

## 待做（按优先级）

| ID | 模块 | 状态 | 认领 | 依赖 | 文档 |
|---|---|---|---|---|---|
| QA-04 | demo-v1 生成链解耦 + 守护检查 | **doing** | @ray045678-cyber | — | [#197](https://github.com/pc-buildmate/pcbuildmate/issues/197) |
| PRICEDATA-01 | 2020+ 价格数据管道（6 类，半自动） | paused | @MoeField | — | [pricedata/README.md](../../pricedata/README.md) |
| QA-01 | 矩阵 + 12 条关键变异 | done | @MoeField | [QA-01](QA-01.md) |
| QA-02 | `:test-oracle` 参考模型 + 交叉校验（规则 / 两张状态机） | done | @yys1yys2yys | issue #55 |
| POLISH-01 | 文案资源 / 可访问性 / 截图 | **doing** | @MoeField | D2/D6 | [POLISH-01](POLISH-01.md) |
| DEFENSE-01 | 讲稿 / 演示脚本 / Q&A / PPT | **doing** | @MoeField | D4/D6 | [DEFENSE-01](DEFENSE-01.md) |
| RELEASE-01 | 签名 APK / GitHub Release | **doing** | @MoeField | QA | [RELEASE-01](RELEASE-01.md) |

## 认领规则

1. 先改本表“认领”，再开分支；没改就开 PR 也算认领，但要在 PR 里写。
2. 只改自己任务包里的路径；跨模块先拆任务。
3. 一个 PR ≤ 400 行有效 diff；超过就拆。
4. AI 生成的代码也要人来跑一次验收命令、看一眼截图。
5. 不强制按顺序，也不强制每人只做一个；没人做的由 @MoeField 兜底，但不要沉默地等。

## 本周目标（2026-11-15 起）

1. **RELEASE-01**：本地 `1.0.0-defense` signed APK 已出，补 GitHub Release + checksum；
2. **DEFENSE-01**：9 步脚本、15 分钟讲稿、Q&A、PPT；
3. **POLISH-01**：文案/空态/截图，不改业务语义；
4. **11/28 功能冻结**，之后只修 bug / 文档。
