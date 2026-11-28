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

## 21.2 真机 9 步演示（×3）

设备：**HUAWEI DBR-W10**（平板，Android 12 / SDK 31，arm64-v8a，构建 `DBR-W10 4.2.0.247(C00E100R4P5)`）
安装包：`PC-Buildmate-v1.0.0-defense-rc2.apk`（sha256 见 §21.3）
脚本：[defense/DEMO.md](defense/DEMO.md) §2；操作步骤：[qa/device-run-tutorial.md](../qa/device-run-tutorial.md)
截图：[qa/screenshots/](../qa/screenshots/)（每轮 4–6 张，缩放到 800 px 宽的 JPG）

> 说明：分工文档写的是 Aura A1，实际执行用的是这台平板；型号按实测记录，不改口径。
> 另一个时间点说明：三轮记录基于 **rc2**（2026-11-17）。此后 ADR-0011 给目录加了 `pcieX16SlotMm` / `pcieSlotCount`，
> 但演示流程与锚点不涉及这两个字段，因此记录仍然有效；**最终发布包需要按 §21.3 重新校验**，并在发包后补一次真机确认（见 `qa/release-checklist.md` 第 5 步）。
>
> **2026-11-23 起第 1 步的期望值变了**：#185 让推荐器输出 8 类（含机箱/散热），8000/游戏在 main 上
> 算出 **¥7,900**（目录里没有 ≥337 mm 的机箱，显卡自动降到 300 mm）。本节三张截图与「三次一致性」
> 表仍是 **rc2 的 6 类 / ¥7,869**，属于历史记录，不改写；新值要在最终发布包上重跑九步后再回填。

### Run 1（2026-11-17 09:41–09:44）

| # | 步骤 | 期望锚点 | 实际 | 截图 |
|---|---|---|---|---|
| 1 | Entry：8000 / 游戏 → 生成配置 → 返回 → 载入演示配置 | 本版 8 类、≈7900；记录时 rc2 为 6 类、¥7,869。随后 ¥7,800 | **¥7,869（6 类，rc2）**、权重卡、价格快照 `2026-11-04` → ¥7,800 | `run1-01-entry-recommend.png` |
| 2 | Workspace | ¥7,800，剩 ¥200 | ✅ ¥7,800 / 剩 ¥200 | — |
| 3 | 保存 A | 保存成功 | ✅ 按钮变「已保存（再次点击会更新诊断）」 | — |
| 4 | 检查 | 存在冲突、52 分、显卡长度 BLOCKER、BIOS 未验证 | ✅ 风险高 · 分数 **52** · revision #1；`337 vs 320` BLOCKER；`未验证 —— 缺少 biosGeneration` | `run1-02-diagnostics.png` |
| 5 | 候选 | rank1 300mm、+¥100 | ✅ `#1 RTX 4070 (300mm) +¥100`，影响 `GPU_CASE_THICKNESS` | — |
| 6 | 预览 | ¥7,900 | ✅ 新总价 ¥7,900 | `run1-03-preview.png` |
| 7 | 应用 + 重检 | 已应用、¥7,900、BIOS 仍未验证 | ✅ revision #2、风险低、BIOS 仍「未验证」 | `run1-04-applied.png` |
| 8 | 保存 B + 比较 | A 7800 / B 7900 | ✅ 配置 A ¥7,800（存在冲突）/ 配置 B ¥7,900（有未验证项） | `run1-05-compare.png` |
| 9 | 回 Entry → 继续上次配置 | 进入工作区 | ✅ ¥7,800 | `run1-06-continue.png` |

结果：**9/9 通过**，锚点全部命中。

### Run 2（09:47–09:49）

同一步骤序列，逐项核对：¥7,869 → ¥7,800 → 52 分 → `+¥100` → ¥7,900 → revision #2（BIOS 仍未验证）→ A/B 比较 → 继续上次配置 ¥7,800。
结果：**9/9 通过**。
截图：`run2-01-diagnostics.png`、`run2-02-applied.png`、`run2-03-compare.png`。

> Run 2 曾有 09:45 的一次脚本化尝试因点击时序失败（UI 未就绪就点了"保存"），已作废；上表为交互式重跑的结果。

### Run 3（09:49–09:51）

先 `am force-stop` 杀进程再重开，验证持久化后重跑同一序列。
结果：**9/9 通过**，最终仍落在 ¥7,800 工作区。
截图：`run3-01-recommend.png`（¥7,869）、`run3-02-diagnostics.png`、`run3-03-applied.png`、`run3-04-compare.png`、`run3-05-continue.png`。

### 三次一致性

| 锚点 | Run 1 | Run 2 | Run 3 |
|---|---|---|---|
| 推荐总价 | ¥7,869 | ¥7,869 | ¥7,869 |
| 演示配置 | ¥7,800 / 剩 ¥200 | 同 | 同 |
| 冲突分数 | 52 | 52 | 52 |
| 候选 rank1 | 300mm +¥100 | 同 | 同 |
| 预览 | ¥7,900 | 同 | 同 |
| 应用后 revision | #2 | #2 | #2 |
| BIOS | 未验证 | 未验证 | 未验证 |
| 比较 | A 7800 / B 7900 | 同 | 同 |

### 答辩包预检（2026-11-28，**不是发布证据**）

答辩（11/30）用当天 main 的 debug 包在 HUAWEI DBR-W10 上重跑了一遍九步，确认「8 类 / ¥7,900」的界面与讲稿一致：

| 项 | 值 |
|---|---|
| 构建 | `./gradlew :app:assembleDebug`（main @ `72146ad` + 本地文案修复），`applicationId` 同发布包 |
| 安装 | 先 `adb uninstall`（debug 与 rc2 签名不同），再 `adb install -r app-debug.apk` |
| 驱动 | `ADB=<adb> bash qa/drive-demo.sh pre qa/screenshots`（脚本会把锚点从 7869 改为 7900） |
| 结果 | **9/9 通过**；推荐 ¥7,900（8 件，含机箱/散热）→ 演示配置 ¥7,800 → 冲突 52 → 候选 +¥100 → 预览 ¥7,900 → revision #2 → A/B 7800/7900 → 继续上次配置 |
| 截图 | `qa/screenshots/pre-*.png`（8 张，答辩 deck 第 11–16 页已换成这一批） |

> 这一轮只证明"答辩演示包的行为与讲稿一致"；发布证据仍以 §21.3 的正式 APK 校验为准，最终提交前会在发布包上再跑一次。

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
| 安装 | `adb install -r` | Success | ✅ 2026-11-17 09:40 在 DBR-W10 上 `Success`（`versionName 1.0.0-defense`） |
| 离线启动 | 飞行模式启动 | Entry 正常、无网络请求 | ✅ 三次演示全程 `airplane_mode_on=1`，推荐/检查/替换/比较均正常 |

> 一条命令复现上面六项的机器可判定部分：`bash qa/verify-release.sh <apk> <sha256 文件或值> [<期望 versionName>]`（sha256 / 签名 / 权限 / ABI / 包名 / 版本号，任一不符返回非零）；安装与离线启动需要设备。

---

## 21.4 失败与 issue

| 时间 | 现象 | issue | 状态 |
|---|---|---|---|
| 2026-11-16 15:37Z | `room` job 失败：`Unknown AVD name [ci]`（avdmanager 未写出 `ci.ini`），模拟器起不来 | 未开 issue：与 main 上同一修复合并前的一次实验运行（PR #70） | **已解决**：main 的创建步骤加了 `--abi x86_64` 与一次重试，之后 main 上连续三次 `room` 全绿 |
| 2026-11-16 14:08Z | `room` job 撞 35 分钟 job 超时被标记 cancelled | 同上 | **已解决**：等待改为 `timeout 180 adb wait-for-device` + 进程存活检查 + 8 分钟启动上限，失败会打印模拟器日志 |

规则：失败必须开 issue，标题 `[QA-03] ...`，不要只在群里说。
