# RELEASE-01 · 签名 APK 与发布

> 状态：**backlog**｜认领：开放（先改 BOARD）｜预估：半天｜依赖：D4/D6/QA

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

## 第一步

先定 `versionName`（建议 `1.0.0-defense`）并生成 keystore，再跑一次 release 构建。
