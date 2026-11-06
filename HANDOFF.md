# HANDOFF · 2026-11-05

> 当前 `main`：`5a52d3e`（之后可能多一个 Undo 收尾提交）
> 状态：**核心 + QA 已收口，暂停等组员一周**。@MoeField 不再推新功能。

## 现在能跑什么

真机（Android 16 / arm64）已验证：

1. Entry：输入预算/用途，生成 6 类配置（8000/GAMING → 7869，权重可见）
2. 载入演示配置：8 件、¥7,800
3. 检查：337mm 显卡 vs 320mm 机箱 → BLOCKER；BIOS 未验证
4. 替换：300mm 显卡 +¥100 → 预览 ¥7,900 → 全量重检 → 应用
5. 保存 A/B：杀进程后重开，Entry 出现“继续上次配置”
6. 比较：总价/状态/分类差异
7. 无 GPU + 无核显：`DisplayOutputGuard` 拒绝
8. Undo：VM 支持；Workspace 有“撤销上一步”（收尾提交）

## 测试入口

```bash
node tools/check-docs.mjs
node ci/check-boundaries.mjs
node ci/check-keywords.mjs

node tools/gen-matrix.mjs
node tools/check-matrix.mjs                 # 60/60, 76/76, 18/18
node tools/check-matrix.mjs --mutations     # 12/12

PYTHONPATH=pricedata/src python3 -m unittest discover -s pricedata/tests

./gradlew :data:testDebugUnitTest :feature:testDebugUnitTest :app:testDebugUnitTest \
  :domain:rules:test :domain:revision:test :domain:recommend:test \
  :domain:replacement:test :domain:state:test :domain:compare:test \
  :app:assembleDebug

# 真机 Room 契约（CI 无设备，不跑）
./gradlew :data:connectedDebugAndroidTest

# CI 的 Android job 会构建 debug APK；Actions artifact: pcbuildmate-debug-apk
```

## 组员从哪开始

只看 [docs/tasks/BOARD.md](docs/tasks/BOARD.md)。当前空位：

| 优先级 | 任务 | 适合谁 |
|---|---|---|
| P1 | [DEFENSE-01](docs/tasks/DEFENSE-01.md) 讲稿/Q&A/PPT | 不写代码也行 |
| P1 | [RELEASE-01](docs/tasks/RELEASE-01.md) 签名 APK | 需要 keystore |
| P2 | POLISH-01 文案/可访问性/截图 | UI |
| P2 | PRICEDATA-01 华硕/七彩虹 adapter、Wayback | 数据/脚本 |
| P3 | D4 Preferences、Compare 功耗/余量、3 份比较 | 领域/UI |

规则：先改 BOARD owner，再开分支；**开工前先看 open PR，开工/开 PR 前各 `git fetch origin && git rebase origin/main`**；
同一文件已有未合并 PR 先协调；一个 PR 一个模块；CI 绿了再 squash merge。

## 不要顺手改

- `contracts/` 的冻结类型/端口；
- `assets/rules/rules-v1.json` 和 60 条 fixture 期望值；
- `docs/03` 的字段路径表；
- `assets/catalogue/catalogue-app.json` 的手工内容（应由 `pricedata` merge 生成）。

如果确实要改契约/规则/字段，先写 ADR + scope-change，再在 PR 里说明影响 D3/D5/D6。

## 已知缺口

- UI 只有真机 smoke + Node 原型，没有 Compose semantics；
- Room 契约是真机 instrumented，CI 不跑；
- `:test-oracle` 独立参考模型未单独建模块，目前靠 JS 差分 + fixture；
- D4 Preferences、Compare 功耗/余量、3 份比较未做；
- 价格数据 adapter/Wayback 未做，当前快照是静态 26 active + 4 demo；
- `Add/Remove/Import/Reload` intent 还没有 UI 入口。

## 这一周（11/06–11/12）

我不推功能，只做两件事：

1. 看组员 PR，按 BOARD 合并；
2. 有人接答辩/APK 就配合，没人接我兜底。

11/13 起进入 W3：UI 收口 + 答辩材料；11/28 功能冻结。
