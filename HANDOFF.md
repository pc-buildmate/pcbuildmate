# HANDOFF · 2026-11-17

> 状态：@MoeField 暂时代理 lead（合并 / 发布 / CI）；QA 线由 @yys1yys2yys 推进，其余任务挂在 BOARD。
> 以 `main` 最新提交为准，不看本文件里的哈希。

## 现在能跑什么

真机（Android 16 / arm64）已验证：

1. Entry：输入预算/用途，生成 6 类配置（8000/GAMING → 7869，权重可见）
2. 载入演示配置：8 件、¥7,800
3. 检查：337mm 显卡 vs 320mm 机箱 → BLOCKER；BIOS 未验证
4. 替换：300mm 显卡 +¥100 → 预览 ¥7,900 → 全量重检 → 应用
5. 保存 A/B：杀进程后重开，Entry 出现"继续上次配置"
6. 比较：总价/状态/分类差异
7. 无 GPU + 无核显：`DisplayOutputGuard` 拒绝
8. Undo：VM 支持；Workspace 有"撤销上一步"
9. 3D 间隙预览：337mm 显卡与 320mm 机箱的盒体叠放，可切换机箱外壳显隐（**尺寸示意，不是 3D 装配**，见 `docs/17`）

## 测试入口

```bash
node tools/check-docs.mjs
node ci/check-boundaries.mjs
node ci/check-keywords.mjs
node ci/check-test-tasks.mjs      # 有测试的模块必须在 CI 任务列表里

node tools/gen-matrix.mjs          # 要先跑测试，否则只有规格
node tools/check-matrix.mjs        # 60/60, 76/76, 18/18
node tools/run-mutations.mjs       # 12 条变异（CI 里也跑）
node tools/check-matrix.mjs --mutations   # 12/12 KILLED

PYTHONPATH=pricedata/src python3 -m unittest discover -s pricedata/tests

./gradlew :app:testDebugUnitTest :ui:testDebugUnitTest :feature:testDebugUnitTest \
  :data:testDebugUnitTest :test-oracle:test \
  :domain:rules:test :domain:revision:test :domain:recommend:test \
  :domain:replacement:test :domain:state:test :domain:compare:test \
  :app:assembleDebug

# 真机 Room 契约（CI 无设备，不跑）
./gradlew :data:connectedDebugAndroidTest
```

CI（`.github/workflows/ci.yml`）五个 job：`docs`（含边界/关键词/任务覆盖/原型回归）、`jvm`（单测 + 矩阵）、`android`（debug APK）、`mutations`（12 条变异，`needs: jvm`）、`room`（Room instrumented，真机/模拟器，只在 main 与手动触发）。

## 成员从哪开始

只看 [docs/tasks/BOARD.md](docs/tasks/BOARD.md)。当前状态：

| 任务 | 状态 | owner |
|---|---|---|
| PRICEDATA-01 | paused | @MoeField |
| POLISH-01 | doing | @MoeField |
| DEFENSE-01 | doing | @MoeField |
| RELEASE-01 | doing | @MoeField |

QA 侧（不在 BOARD 卡里，见 issue #55）：`:test-oracle` 参考模型已覆盖规则引擎与两张状态机，交叉校验在 `app/src/test`。

规则：先改 BOARD owner，再开分支；**开工前先看 open PR，开工/开 PR 前各 `git fetch origin` 并 rebase**；
同一文件已有未合并 PR 先协调；一个 PR 一个模块、≤400 行；CI 绿了再 squash merge。

## 不要顺手改

- `contracts/` 的冻结类型/端口；
- `assets/rules/rules-v1.json` 和 60 条 fixture 期望值；
- `docs/03` 的字段路径表；
- `assets/catalogue/catalogue-app.json` 的手工内容（应由 `pricedata` merge 生成）。

如果确实要改契约/规则/字段，先写 ADR + scope-change，再在 PR 里说明影响 D3/D5/D6。

## 已知缺口

- UI：Entry / Workspace / 诊断 / 比较四屏已有 Compose semantics 测试（`ui/src/test/.../*ScreenSemanticsTest.kt`，Robolectric，JVM 可跑）；可访问性未测；
- Room 契约：已在 `room` job 里用 CI 模拟器跑（仅 main / 手动）；PR 上仍不跑，改 `:data` 的 PR 需要手动 dispatch 一次；
- D4 Preferences、Compare 功耗/余量、3 份比较未做（P3）；
- 价格数据 adapter/Wayback 未做，当前快照是静态数据，价格准确度未核对；
- 兼容性数据不全：例如**主板第一个 x16 槽的位置信息没有记录**，3D 预览里的槽位是按常量估算的（`docs/18 §18.8`）；
- `Add/Remove/Import/Reload` intent 还没有 UI 入口。

## 时间线

- 11/13 起 W3：UI 收口 + 答辩材料；
- **11/28 功能冻结**，之后只修 bug / 文档。
