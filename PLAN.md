# PLAN · 接下来四周

> 制定日期：2026-11-05｜答辩：12 月初（按 4 周倒排）｜维护人：@MoeField
> **11/05 更新**：W1 + W2 的 P0/QA 已完成；暂停一周等组员，11/13 起进 W3。
>
> 原则：**先保证“能跑 + 说真话”，再谈完整**。六个功能已有五个能演示；剩余工作按 P0 → P1 → P2 排。

## 1. 现在到哪了

| 能力 | 状态 | 证据 |
|---|---|---|
| Entry 预算/用途 → 6 类推荐 | ✅ | `:domain:recommend:test` 5/5；真机 8000/GAMING → 7869 |
| Workspace / revision / 保存 A/B | ✅ 核心 | 真机 7800→7900；保存后比较 |
| 15 条兼容规则 | ✅ | `:domain:rules:test` 60/60 |
| 三态证据 / BIOS 未验证 | ✅ | 真机诊断页 |
| 替换建议（先过滤再排序） | ✅ | 337→300 +100；5/5 |
| 诊断/替换状态机 | ✅ | 22+75 case |
| A/B 比较 | ✅ | 总价/状态/分类差异 |
| 价格数据管道 | 🟡 MVP | 26 条、6 类、2020+；无 App 快照 pin |
| 机箱/散热 | 🟡 仅 demo | 337/320 锚点；不采历史价 |
| 持久化 | ❌ | 杀进程 A/B 丢失 |
| 无 GPU/无核显 guard | ❌ | D4 已过滤；手动删件/导入未守 |
| QA 矩阵/变异 | ❌ | `check-matrix` 仍是规格模式 |
| 答辩材料/APK | ❌ | 讲稿/PPT 还是概念稿 |

## 2. 差距分级

### P0（不做会影响“能跑/说真话”）

1. **DisplayOutputGuard**：无 GPU 且 CPU 无核显 → 拒绝/提示；D4 之外的手动编辑、导入也必须守。
2. **Room 持久化**：保存的 A/B、草稿跨进程存活；`BuildStorePort` / `DraftStorePort` 契约测试。
3. **App 快照 overlay**：`pricedata` 的 26 条 active + demo 锚点（case/cooler/337/320）合成一份 App 目录；不能直接把 demo 换掉，否则答辩锚点丢。
4. **QA 真实矩阵**：`check-matrix` 读测试结果，不再只打印规格；至少 rules 60/60、fsm 76/76、inv 18/18 可复现。
5. **答辩 APK**：签名、真机安装、9 步演示一次过。

### P1（影响完整度，时间够就做）

- Compare 补功耗余量/升级余量/物理尺寸（需要 CataloguePort，仍不调用规则引擎）。
- Preferences（caseSize/noiseLevel/upgradeRoom）参与 D4；当前只占位。
- UI 文案资源化、TalkBack、深色模式、截图更新。
- 3 份配置比较（横向滚动）。

### P2（课程范围外/后续）

- 2016–2019 历史价（只做 historical，`recommendable=false`）。
- Wayback/华硕/七彩虹自动 adapter；更多厂商官方商城。
- 无线网卡/显示器/外设；整机/笔记本类目。
- 实时价格/库存/爬虫（明确不做）。

## 3. 四周排期

| 周 | 日期 | 目标 | 出口（可判定） |
|---|---|---|---|
| **W1** | 11/05–11/11 | Guard + overlay + Room 起步 | 无 GPU+7500F 被拒；App 用合并目录仍能 7800→7900；Room 能存/读 1 份 |
| **W2** | 11/12–11/18 | Room 完整 + QA 矩阵 | 杀进程 A/B 还在；`check-matrix` 读真实结果；rules 60/60 + fsm 76/76 |
| **W3** | 11/19–11/25 | 端到端 + 答辩材料 | 真机 9 步连续 3 次；讲稿/Q&A/PPT 口径一致；截图更新 |
| **W4** | 11/26–12/02 | 冻结 + APK + 排练 | `v1.0-defense` APK；11/28 功能冻结；全组计时 15 分钟 |
| 缓冲 | 12/03–答辩 | 只修 bug | 不新增功能 |

**功能冻结线：11/28。** 之后只允许 `fix:` / `docs:`。

## 4. 本周（W1）具体任务

| ID | 任务 | 验收 |
|---|---|---|
| D2-03 | `DisplayOutputGuard`：无 GPU + CPU `integratedGraphics != YES` → rejected；GPU 存在则放行 | `:domain:revision:test` 3 条；StartCheck/Import 前调用 |
| INT-01 | 合并 `pricedata` active 26 条 + demo overlay（case/cooler/337/320）→ `assets/catalogue/catalogue-app.json` | 真机 demo 仍 7800→7900；D4 仍 8000 可行 |
| DATA-02 | Room `BuildStore`/`DraftStore` + 契约测试 | 杀进程后 A/B 还在；`:data:testDebugUnitTest` 绿 |
| QA-01 | `gen-matrix` 读 JUnit XML → `matrix.json`；`check-matrix` 真实模式 | rules 60/60、fsm 76/76、inv 18/18 |
| POLISH-01 | 文案/错误态/权重说明 | 截图；无硬编码关键文案（允许逐步） |

## 5. 每天/每周检查

```bash
node tools/check-docs.mjs
node ci/check-boundaries.mjs
node ci/check-keywords.mjs
node tools/check-matrix.mjs
PYTHONPATH=pricedata/src python3 -m unittest discover -s pricedata/tests
./gradlew :data:testDebugUnitTest :domain:rules:test :domain:recommend:test \
  :domain:replacement:test :domain:state:test :domain:compare:test :app:assembleDebug
```

四个数字：rules __/60、fsm __/76、inv __/18、mutation __/12。

## 6. 砍功能顺序（时间不够时）

1. 先砍 Compare 的功耗/余量字段，保留总价/状态/分类；
2. 再砍 Preferences 对 D4 的影响，保留默认权重；
3. 再砍 3 份配置比较，保留 2 份；
4. **绝不砍**：三态证据、revision 保护、替换前全量重检、无 GPU/无核显 guard。

## 7. 风险

| 风险 | 缓解 |
|---|---|
| Room/KSP 版本与 AGP 9.5 不兼容 | 先用内存实现 + 契约测试，Room 单独 PR；失败则用 JSON/DataStore，端口不变 |
| 价格数据 overlay 破坏 337/320 | 先写集成测试：demo 8 件仍 7800，D4 6 类仍可行 |
| 队友并行改同一文件 | 按 BOARD 认领；`WorkspaceViewModel`/`Screens.kt` 串行 |
| 答辩前赶功能 | 11/28 冻结；W4 只修 bug |
| 数据/价格被质疑 | SOURCES.md + 日期 + “参考价”口径；不承诺实时 |

## 8. 完成定义（答辩版）

- 真机安装（Android 12+ / arm64），离线，无网络权限；
- 9 步演示一次过：7800 → 检查 → 337 冲突 → 300mm +100 → 7900 → BIOS 仍未验证 → 保存 A/B → 比较；
- 预算输入能生成 6 类配置；无 GPU 时只选有核显 CPU；
- 讲稿/Q&A/PPT 与 App 数字一致；
- `main` CI 绿，APK 在 GitHub Release。
