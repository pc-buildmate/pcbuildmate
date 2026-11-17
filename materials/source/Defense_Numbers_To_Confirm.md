# Defense · Numbers to confirm (owner sheet)

> Every number that appears in the slides or the Q&A, who owns it, where the truth lives, and its status.
> Rule: no number goes on a slide until its owner confirms it here. `[x]` = confirmed, `[ ]` = pending.
> Checkpoint: 11/22 slides + script + Q&A numbers consistent.

## Product and data

| # | Number / fact | Used in | Owner | Where the truth lives | Status |
|---|---|---|---|---|---|
| N1 | Rule count: **15** | S21, Q15 | 王嘉瑞 | `assets/rules/rules-v1.json` | [x] verified by matrix 60/60 |
| N2 | Rule matrix: **60 / 60** | S21, Q15 | 杨壹硕 | `node tools/check-matrix.mjs` | [x] |
| N3 | State machine: **76 / 76** | S21, Q15 | 杨壹硕 | same command | [x] |
| N4 | Invariants: **18 / 18** | S21, Q15 | 杨壹硕 | same command | [x] |
| N5 | Mutations: **12 / 12 killed** | S21, Q15 | 杨壹硕 | `run-mutations.mjs` + CI `mutations` job | [x] |
| N6 | Catalogue size: **30 records** | S18/S23 maybe | 刘喆 / 陈思言 | `assets/catalogue/catalogue-app.json`（30 条，见 #81 审计报告） | [x] 2026-11-17 核对 |
| N7 | Price snapshot: **2026-11-04**（catalogueVersion 2026.11） | S12, Q4 | 陈思言 | `assets/catalogue/catalogue-app.json` + `SOURCES.md` | [x] 文件值；App 内显示待截图确认 |
| N8 | Demo anchors **¥7,800 → ¥7,900**, +¥100 | S11–S16, Q&As | 王嘉瑞 | fixture `fx_suggestion_300_plus_100` | [x] fixture-frozen |
| N9 | Recommendation example **¥7,869** | S11 | 黄奕维 / 陈思言 | rc2 APK 实测（三次一致） | [x] 2026-11-17 真机 |
| N10 | Conflict score **52** | S12, step 4 | 王嘉瑞 | rc2 APK 实测（三次一致） | [x] 2026-11-17 真机 |
| N11 | Module count **14** | S18 | 黄奕维 | `settings.gradle.kts` | [x] |
| N12 | 3D constants: GPU 宽 110 mm、第一槽 I/O 下缘 −7.52 mm、槽距 20.32 mm | S15, S19, Q&A 3D | 阮立坤 | `docs/18 §18.2`（§18.8 仍列估算项） | [ ] 数值已从文档核对；**x16 槽位置仍是数据缺口**（INFO-02） |
| N13 | APK identity: `1.0.0-defense`, arm64-only, 23,545,828 B, sha256 `750b2d76…` | S24, Q&A release | 杨壹硕 | `docs/21 §21.3` | [x] verified |
| N14 | Device demo runs: **3 / 3** recorded（DBR-W10 / Android 12） | S24, Q15 | 杨壹硕 | `docs/21 §21.2` + `qa/screenshots/` | [x] 2026-11-17 |

## How to close a row

1. Owner writes the confirmed value and the source in the PR/issue comment (or here, if they own the file).
2. 杨壹硕 updates the slide/script/Q&A to that exact wording.
3. If a number changes after 11/22, everything that cites it changes in the same PR — no partial updates.

## Numbers we must NOT use

- accuracy percentages, user counts, completion percentages;
- FPS, benchmark scores, performance bands;
- "latest" or "real-time" price claims;
- any claim that the recommendation is optimal.
