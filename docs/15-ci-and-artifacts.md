# 15 · CI 与构建产物

> 读者：组员 / AI agent。当前配置见 [`.github/workflows/ci.yml`](../.github/workflows/ci.yml)。

## 15.1 触发条件

| 事件 | 跑什么 | 产物 |
|---|---|---|
| Pull request | docs + JVM + Android **debug** | `pcbuildmate-debug-apk` |
| push `main` | 同上 | 同上 |
| Actions → Run workflow | 手动跑全部 | 同上 |
| push tag `v*` | **暂不触发** release；等 RELEASE-01 | — |

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

## 15.4 Release 计划（RELEASE-01）

需要四个 GitHub Secrets：

```text
KEYSTORE_BASE64
KEYSTORE_PASSWORD
KEY_ALIAS
KEY_PASSWORD
```

然后加一个 tag-triggered job：

```yaml
on:
  push:
    tags: ["v*"]
```

- 只有 tag 才 `assembleRelease` + 签名；
- 上传 `pcbuildmate-release-apk`；
- 没有 secrets 时只构建 debug，不假装“已发布”；
- tag 由 Captain 打，不跟普通 PR 混。

## 15.5 为什么不用 tag-only

| 方案 | 问题 |
|---|---|
| 只在 tag 构建 | 平时 PR 编译失败，到发布才发现 |
| 每个 PR 都签 release | 每个 PR 都接触 keystore，风险高 |
| **PR/main debug + tag release（推荐）** | 早发现问题，签名只在发布时碰 |

## 15.6 常见失败

| 现象 | 原因 | 处理 |
|---|---|---|
| `sdkmanager tools` 失败 | 旧 `setup-android` action 与新版 SDK 不兼容 | 已改为预装 SDK + 显式装 platform |
| `platforms;android-37.0` 找不到 | SDK 包名不是 `android-37` | 用 `android-37.0` |
| APK artifact 为空 | Gradle 失败但 job 继续 | `if-no-files-found: error` 已设 |
| 只改文档也跑 Android | 当前无条件 | 可接受；要省分钟数再加 `paths-ignore` |
