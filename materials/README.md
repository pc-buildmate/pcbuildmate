# 原始材料（只读）

这里保存答辩相关的历史材料，**不作规格使用**。规格以仓库根目录的 `docs/` 与 `contracts/` 为准。

## 来源与转换

| 文件 | 原始格式 | 来源 | 转换方式 | 备注 |
|---|---|---|---|---|
| `BuildMate_EE5415_Original_Format.md` | `.docx` | `BuildMate_EE5415_Original_Format.docx`（微信接收，2026-10-12） | `pandoc -t gfm --extract-media` | 提案原文；图片在 `source/media/` |
| `PC_Buildmate_15min_Speaker_Script.md` | `.pdf` | `PC_Buildmate_15min_Speaker_Script.pdf`（桌面，2026-10-13） | `pypdf` 逐页提取 | 7 页；英文讲稿 + 中文排练提示 |
| `PC_Buildmate_QA_Preparation.md` | `.pdf` | `PC_Buildmate_QA_Preparation.pdf`（微信接收，2026-10-13） | `pypdf` 逐页提取 | 7 页；18 题 Q&A |

> PDF 为文本层提取，版式/分栏可能丢失；以原始 PDF 为准。
> Docx 转换保留了表格与图片，但标题层级被压平成段落；阅读时按加粗行分段。

## 与本仓库的口径差异（答辩前必须处理）

| # | 原始材料 | 本仓库 | 处理 |
|---|---|---|---|
| C1 | 提案 D6 含 “indicative performance band” | 已裁掉（`docs/11-adr/0002`，范围外） | 讲稿/PPT 删掉该词，或改成「可比较的字段」 |
| C2 | 提案 “Why Android” 列举相机/AR/推送 | 范围外（`docs/09 §9.1`） | 讲成「平台可能性」，明确首版不做 |
| C3 | 提案 “duplicate a build” | 首版无独立 Intent（TBD-3） | 统一说「另存为一份配置」 |
| C4 | 讲稿 Slide 16：7800 → 7900、BIOS 未验证 | 与 fixture `fx_suggestion_300_plus_100` 一致 | 保持，这是最佳演示锚点 |
| C5 | Q&A Q16：「界面是概念原型」 | 与 `isDemo` 隔离一致 | 保持；演示前确认屏幕有「演示数据」标识 |

## 其他差异

- 提案写「compatibility score, risk level」（Diagnostics）；`site/` 原型有分数/风险面板，但 `docs/06` 的 D3 只输出逐条 `RuleOutcome`。**分数只能是派生展示，不得成为新的判定语义**；若答辩要讲，说明它是严重度汇总，不是性能预测。
- 提案写 Room；当前设计只要求 `BuildStorePort` / `DraftStorePort`，实现可用 Room，也可先用内存/文件。**端口不变**。
- 提案写「one-tap apply」；设计要求「预览 → 全量重检 → 应用」（`docs/04`）。答辩按设计讲。
