# PC Buildmate · v1.0.0-defense

> 课程答辩包：离线可用、静态参考价、无网络权限。
> 功能冻结包，2026-11-27 发布。

## 包含内容

- Entry：预算/用途 → 8 类推荐（权重可见，含机箱+散热）
- Workspace：编辑、撤销、保存 A/B、检查 15 条兼容规则
- Diagnostics：冲突/未验证、替换候选、预览、全量重检、应用
- Compare：两份配置的总价/状态/分类差异
- 3D 间隙预览：盒体尺寸示意（板面/俯视/背视），337mm 显卡 vs 320mm 机箱红框
- 离线静态目录（30 条，2026-11-04 参考价）+ Room 本地持久化

## 构建与验证

| 项 | 值 |
|---|---|
| 构建 | `./gradlew :app:assembleRelease` |
| 签名 | v2，CN=PC Buildmate / EE5415 / CityU |
| ABI | **arm64-v8a**（release 只打 arm64） |
| 权限 | 无 `INTERNET` / 相机 / 定位 / 推送 |
| minSdk | 31（Android 12+） |
| 版本 | `versionCode=1`、`versionName=1.0.0-defense` |

测试闸门：rules 60/60、fsm 76/76、inv 18/18、mutation 12/12、Room 8/8、`:ui` 语义 21/21；CI 五项全绿。

## 安装

```bash
adb install -r PC-Buildmate-1.0.0-defense.apk
```

## 校验和

```text
SHA-256: 见 release 附件 .sha256
```

## 范围与边界

- 价格是 2026-11-04 的静态快照，**不承诺实时/库存**；
- 不提供 FPS/跑分预测、3D 装配/拖拽安装、账号、推送、AR；
- 3D 只是尺寸示意，部分尺寸为估算（见 `docs/18 §18.8`）；
- BIOS 兼容性以 `REPORTED/EMPTY` 呈现，缺字段不会 PASS。

## 已知限制

- 目录 30 条为演示规模，不覆盖全市场；
- 机箱/散热/无线网卡只有 demo 数据，不做长期价格；
- 替换排序只做兼容过滤 + 价格，不做性能评分。
