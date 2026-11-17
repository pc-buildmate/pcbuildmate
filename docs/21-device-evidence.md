# 21 · 设备侧证据（Room / 真机 9 步 / Release 校验）

> 任务：QA-03、TEST-01、RELEASE 校验（见 `docs/tasks/QA-03.md`）
> 演示脚本：[defense/DEMO.md](defense/DEMO.md)　CI：[`.github/workflows/ci.yml`](../.github/workflows/ci.yml)
> APK：`io.github.pcbuildmate`，`versionName = 1.0.0-defense`，`versionCode = 1`，`minSdk = 31`

这份文档只记录**可复现的证据**：谁在什么设备上、跑了什么命令、结果如何。
没有跑过的格子留空，不预填"应该通过"。

---

## 21.1 Room 持久化契约（8 个用例）

测试类：`data/src/androidTest/kotlin/pcbuildmate/data/store/RoomStoreContractTest.kt`
（`BuildStorePortContract` + `DraftStorePortContract` 的 Room 实现）

| 时间 | 环境 | 命令 | 结果 | 记录人 |
|---|---|---|---|---|
| 2026-11-16 | 本机 AVD `Pixel_9`（API 37，google_apis_playstore，x86_64） | `./gradlew :data:connectedDebugAndroidTest` | **8 / 8 通过，0 失败 0 跳过**（2m38s） | @yys1yys2yys |
| 2026-11-16 17:57Z | GitHub Actions `room` job（run `43923693331`，API 33，x86_64） | 同上 | **通过**：模拟器 30 s 启动，`connectedDebugAndroidTest` + `assembleDebug` 三次 `BUILD SUCCESSFUL`，启动冒烟拿到 pid 5253 | @yys1yys2yys |

结论：Room 的 `BuildStorePort` / `DraftStorePort` 在两个独立实现环境（本地模拟器 + CI 模拟器）上都通过同一份契约。

跑法（本地）：

```bash
export JAVA_HOME="C:/androidd/jbr"        # 本机 Gradle 启动器需要 JDK 25（AS 自带 JBR）
emulator -avd Pixel_9 -no-window -no-audio -gpu swiftshader_indirect &
adb wait-for-device
./gradlew :data:connectedDebugAndroidTest
```

> 模拟器占内存/CPU，**开之前先跟机主确认时间窗**；跑完 `adb emu kill`。

---

## 21.2 真机 9 步演示（Aura A1）

设备：**Aura A1**（真机，型号/系统版本待首次记录时填写）
脚本：[defense/DEMO.md](defense/DEMO.md) §2；操作步骤与命令：[qa/device-run-tutorial.md](../qa/device-run-tutorial.md)；
每步记录"开始/结束时间、结果、偏差、截图"。

### Run 1

| 项 | 记录 |
|---|---|
| 日期/时间 | ⬜ |
| 系统版本 / 构建号 | ⬜ |
| 安装方式 | `adb install -r artifacts/PC-Buildmate-1.0.0-defense.apk`（⬜ 待阮立坤产出） |
| 演示前 | 清数据、飞行模式、亮度固定（⬜ 执行人签字：杨壹硕） |
| 结果 | ⬜ 9/9 通过 / 失败在第 N 步 |

逐步记录：

| # | 步骤 | 期望锚点 | 实际 | 截图 |
|---|---|---|---|---|
| 1 | Entry：8000 / 游戏 → 生成配置 | 6 类、≈7869、权重可见 | ⬜ | ⬜ |
| 2 | Workspace | ¥7,800，剩 ¥200，337 vs 320 同屏 | ⬜ | ⬜ |
| 3 | 保存 A | 保存成功 | ⬜ | ⬜ |
| 4 | 检查 | 存在冲突、52 分、GPU 长度 BLOCKER、BIOS 未验证 | ⬜ | ⬜ |
| 5 | 候选 | rank1 = 300mm、+¥100 | ⬜ | ⬜ |
| 6 | 预览 | ¥7,900，不改 revision | ⬜ | ⬜ |
| 7 | 应用 + 重检 | 已应用并重检、¥7,900、BIOS 仍未验证 | ⬜ | ⬜ |
| 8 | 保存 B + 比较 | A 7800 / B 7900、差异高亮 | ⬜ | ⬜ |
| 8.5 | 3D 间隙预览（可选） | 337 盒体穿出 320 空间、红框 | ⬜ | ⬜ |
| 9 | 回 Entry | "继续上次配置 · 已保存 2 份" | ⬜ | ⬜ |

### Run 2 / Run 3

同上表格，各复制一份。三次之间：杀进程重开一次，确认持久化与"重新检查"语义一致。

---

## 21.3 Release 校验（与阮立坤的发布包对账）

校验对象：[Release `v1.0.0-defense-rc2`](https://github.com/pc-buildmate/pcbuildmate/releases/tag/v1.0.0-defense-rc2)
`PC-Buildmate-v1.0.0-defense-rc2.apk`（23,545,828 B，`versionName = 1.0.0-defense`，`versionCode = 1`，
`minSdk = 31`，`targetSdk = 37`，`package = io.github.pcbuildmate`）。

| 检查 | 命令 | 期望 | 结果（2026-11-17） |
|---|---|---|---|
| APK 存在且签名 | `apksigner verify --print-certs` | 签名有效、证书非 debug | ✅ `CN=PC Buildmate, OU=EE5415, O=CityU`，证书 SHA-256 `d6d6b224…82c10` |
| sha256 | `sha256sum` | 与 Release 的 `.sha256` 一致 | ✅ `750b2d76…ecd44e`，与附件逐字节一致 |
| 权限 | `aapt2 dump permissions` | **无 INTERNET / CAMERA / LOCATION / POST_NOTIFICATIONS** | ✅ 仅 `io.github.pcbuildmate.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`（androidx 自用） |
| ABI | `aapt dump badging` / `unzip -l` | 含 `arm64-v8a` | ✅ **仅** `arm64-v8a`（无 x86_64） |
| 安装 | `adb install -r` | Success | ⬜ 待 Aura A1 |
| 离线启动 | 飞行模式启动 | Entry 正常、无网络请求 | ⬜ 待 Aura A1 |

---

## 21.4 失败与 issue

| 时间 | 现象 | issue | 状态 |
|---|---|---|---|
| 2026-11-16 15:37Z | `room` job 失败：`Unknown AVD name [ci]`（avdmanager 未写出 `ci.ini`），模拟器起不来 | 未开 issue：与 main 上同一修复合并前的一次实验运行（PR #70） | **已解决**：main 的创建步骤加了 `--abi x86_64` 与一次重试，之后 main 上连续三次 `room` 全绿 |
| 2026-11-16 14:08Z | `room` job 撞 35 分钟 job 超时被标记 cancelled | 同上 | **已解决**：等待改为 `timeout 180 adb wait-for-device` + 进程存活检查 + 8 分钟启动上限，失败会打印模拟器日志 |

规则：失败必须开 issue，标题 `[QA-03] ...`，不要只在群里说。
