# 15 · CI 与构建产物

> 读者：组员 / AI agent。当前配置见 [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)。

## 15.1 触发条件

| 事件 | 跑什么 | 产物 |
|---|---|---|
| Pull request | docs + JVM + **mutations** + Android **debug** | `pcbuildmate-debug-apk`、`key-mutations` |
| push `main` | 同上 + **Room instrumented（模拟器）** | 同上、`room-instrumented-results` |
| Actions → Run workflow | 全部，含 Room instrumented | 同上 |
| push tag `v*` | **release.yml**：签名 arm64 APK + 校验 + **draft** GitHub Release | `PC-Buildmate-<tag>.apk` + `.sha256` |

**结论：debug 构建是无条件的**（PR/main 都跑），因为：

- 只想尽早发现“合并后编译不过”，不是发布；
- debug 用 debug key，**不需要 secrets**；
- 单次约 2–4 分钟，课程项目可接受。

**release 不应该无条件跑**：签名需要 keystore secrets，只在 tag（如 `v1.0-defense`）
或手动触发时跑，避免每个 PR 都碰签名密钥。

## 15.2 下载 APK

1. 打开 PR / commit 的 Actions run；
2. 页面底部 **Artifacts**；
3. 下载 `pcbuildmate-debug-apk`；
4. 解压后 `adb install -r app-debug.apk`。

Artifact 保留 14 天；过期就重跑 workflow。

## 15.3 本地复现

```bash
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

CI 用 GitHub runner 预装的 `/usr/local/lib/android/sdk`，并安装：

```text
platforms;android-37.0
build-tools;37.0.0
platform-tools
```

## 15.4 自动 release（2026-11-17 起）

`.github/workflows/release.yml`，只对 `v*` tag 触发：

1. 从 GitHub Secrets 解码 `KEYSTORE_BASE64` 到 `$RUNNER_TEMP/signing/defense.jks`，写临时 `keystore.properties`；
2. `./gradlew :app:assembleRelease`；
3. `apksigner verify`、检查无 `INTERNET`、`aapt dump badging` 必须只有 `arm64-v8a`；
4. 生成 `PC-Buildmate-<tag>.apk` + `.sha256`；
5. `gh release create <tag> --draft`，上传两个附件。

**publish 仍由人点**：自动化只到 draft；答辩/冻结前由 Captain 检查后 publish。

Secrets（已设置，加密、不进仓库）：

```text
KEYSTORE_BASE64
KEYSTORE_PASSWORD
KEY_ALIAS
KEY_PASSWORD
```

## 15.5 本地签名 APK（已完成）

- `versionName=1.0.0-defense`
- 本地 `.signing/pcbuildmate-defense.jks`，`keystore.properties` 已在 `.gitignore`
- `./gradlew :app:assembleRelease` → `app/build/outputs/apk/release/app-release.apk`
- `apksigner verify`：v2 通过
- 答辩副本：`artifacts/PC-Buildmate-1.0.0-defense.apk`

## 15.5 Release 流程（RELEASE-01）

1. 冻结后本地确认 `versionCode`/`versionName`（决定是否 +1）；
2. `git tag -a v1.0.0-defense -m "..." && git push origin v1.0.0-defense`；
3. `release` workflow 自动出签名 APK + draft Release；
4. 人检查 draft 的 APK/checksum/notes，点 **Publish**；
5. 真机装 release 包跑 9 步。

tag 由 Captain 打，不跟普通 PR 混；PR 上不碰 keystore。

## 15.6 为什么不用 tag-only

| 方案 | 问题 |
|---|---|
| 只在 tag 构建 | 平时 PR 编译失败，到发布才发现 |
| 每个 PR 都签 release | 每个 PR 都接触 keystore，风险高 |
| **PR/main debug + tag release（推荐）** | 早发现问题，签名只在发布时碰 |

## 15.7 常见失败

| 现象 | 原因 | 处理 |
|---|---|---|
| `sdkmanager tools` 失败 | 旧 `setup-android` action 与新版 SDK 不兼容 | 已改为预装 SDK + 显式装 platform |
| `platforms;android-37.0` 找不到 | SDK 包名不是 `android-37` | 用 `android-37.0` |
| APK artifact 为空 | Gradle 失败但 job 继续 | `if-no-files-found: error` 已设 |
| 只改文档也跑 Android | 当前无条件 | 可接受；要省分钟数再加 `paths-ignore` |
