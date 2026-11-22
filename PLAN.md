# PLAN · 11/15–11/28

> 更新：2026-11-15｜答辩：12 月初｜维护人：@MoeField
>
> 现实检查：远端自 11/09 后没有新提交；只有 yys 的 #33（CI 真实矩阵）合入。
> 不再等“自然发生”的贡献，按两人可完成的最小集推进。

## 1. 现在到哪了

| 能力 | 状态 |
|---|---|
| Entry 预算/用途 → 8 类推荐 | ✅ 8000/GAMING → 7900，权重可见 |
| Workspace / revision / 保存 A/B | ✅ 7800→7900，Room 跨进程恢复 |
| 15 条规则 + 三态证据 | ✅ 60 fixture + 7 anchor；BIOS 未验证 |
| 替换建议 | ✅ 337→300 +100，全量重检 |
| 诊断/替换状态机 | ✅ 76 格 + 覆盖 gate |
| A/B 比较 | ✅ 总价/状态/分类 |
| 无 GPU/无核显 Guard | ✅ 无核显拒绝 |
| QA | ✅ 60/76/18 + MUT 12/12 + Room 8/8 |
| CI | ✅ docs/JVM/Android debug APK；#33 接真实矩阵 |
| 签名 APK | ✅ 本地 `1.0.0-defense`，v2 签名，真机安装 |
| 答辩材料 | ❌ 只有任务卡，没有逐页讲稿/PPT |
| UI polish | 🟡 撤销按钮已加；文案/空态/可访问性未完 |
| 3D 间隙预览 | ✅ 盒体 + 程序化纹理；337/320 红框；可拖动旋转 |

## 2. 剩余 P0

1. **答辩脚本/Q&A/PPT**：15 分钟逐页、9 步真机、兜底录屏。
2. **APK 发布**：GitHub Release + checksum；CI tag release 需要 secrets。
3. **真机 3 次完整演练**：7800→检查→337→300→7900→BIOS→A/B→比较。
4. **UI 最后收口**：文案/空态/截图；不改业务语义。

## 3. 两周排期

| 日期 | 目标 | 出口 |
|---|---|---|
| 11/15–11/17 | 签名 APK + Release 文档 + 演示脚本初稿 | APK 可装；9 步脚本 |
| 11/18–11/22 | Q&A/PPT + UI 文案/截图 | 三份材料口径一致 |
| 11/23–11/27 | 真机连续 3 次 + 录屏兜底 | 计时 15 分钟不崩 |
| **11/28** | **功能冻结** | 只允许 `fix:` / `docs:` |
| 11/29–12/02 | 最终 APK / Release / 排练 | `v1.0-defense` |

## 4. 现在不做什么

- 不加新功能（Preferences、Compare 余量、3 份比较、pricedata adapter 都暂停）；
- 不改 `contracts/` / rules / fixture；
- 不再等没人认领的建议。

## 5. 每次演示前检查

```bash
node tools/check-docs.mjs
node ci/check-boundaries.mjs && node ci/check-keywords.mjs
node tools/gen-matrix.mjs && node tools/check-matrix.mjs
node tools/check-matrix.mjs --mutations
./gradlew :app:assembleDebug
```

APK：`artifacts/PC-Buildmate-1.0.0-defense.apk`
校验：`shasum -a 256 artifacts/PC-Buildmate-1.0.0-defense.apk`
