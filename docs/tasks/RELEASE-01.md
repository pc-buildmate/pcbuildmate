# RELEASE-01 · 签名 APK 与发布

> 状态：**doing**（本地 signed APK 已完成，GitHub Release 待发）｜认领：@MoeField｜预估：半天｜依赖：D4/D6/QA

## 目标

产出一个能在 Android 12+ arm64 真机安装、离线运行、可复现的答辩 APK。

## 需要做

- 生成 release keystore（不进仓库），`keystore.properties` 走本地/Secrets；
- `versionCode` / `versionName` 规则；
- `./gradlew :app:assembleRelease`；
- 在 Aura A1 / 另一台 Android 12+ 真机安装；
- 确认 Manifest 无 `INTERNET` / 相机 / 定位 / 推送权限；
- 把 APK 放到 GitHub Release，附 commit 与校验和。

## 验收

- 真机安装、冷启动、完整演示 9 步通过；
- `aapt dump permissions` 无网络权限；
- APK 是 arm64-v8a（可含 x86_64 debug 包，但不进 release）；
- 卸载重装后数据行为符合预期（当前无持久化则明确说明）。

## 不要做

- 不把 keystore / 密码提交；
- 不用 debug 签名当答辩包（除非明确接受）；
- 不发布范围外功能。

## 已完成（2026-11-15）

- `versionName=1.0.0-defense`，`versionCode=1`；
- 本地 keystore `.signing/pcbuildmate-defense.jks`（不进仓库）；
- `:app:assembleRelease` 通过，v2 签名验证通过；
- 真机安装成功，冷启动正常；
- 产物：`artifacts/PC-Buildmate-1.0.0-defense.apk`
- SHA-256：`15ef72e62cea6e0b389d7241eeb86a3d199f627073ae2d3154a6e83f46b218b1`

## 2026-11-16 进展

- release ABI 收紧为 **arm64-v8a**（`app/build.gradle.kts` 的 `release.ndk.abiFilters`）；
- 重新构建并验证：v2 签名、无 `INTERNET`、arm64 only；
- 校验和：`artifacts/PC-Buildmate-1.0.0-defense.apk.sha256`；
- GitHub Release 以 **draft** 形式创建，等 11/28 冻结后替换最终包再 publish；
- release notes：`docs/defense/RELEASE_NOTES.md`。

## 2026-11-17 自动化

- 新增 `.github/workflows/release.yml`：`v*` tag → 解码 Secrets → `assembleRelease` → 校验签名/权限/ABI → 生成 checksum → **draft** Release；
- Secrets 已设置：`KEYSTORE_BASE64`、`KEYSTORE_PASSWORD`、`KEY_ALIAS`、`KEY_PASSWORD`；
- publish 仍由人点；流程见 `docs/15 §15.4`。

## 还差

- 上传 GitHub Release（等 11/28 冻结后再打 tag）；
- CI tag release 需要四个 secrets；
- 另一台 Android 12+ 真机复验。
