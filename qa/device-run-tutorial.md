# 真机 9 步 ×3 · 操作教程（Aura A1）

> 对象：演示人（杨壹硕）。产出填进 [docs/21](../docs/21-device-evidence.md) §21.2。
> 演示脚本：[docs/defense/DEMO.md](../docs/defense/DEMO.md)。APK：Release `v1.0.0-defense-rc2`
> （arm64、无 INTERNET 权限、sha256 已校验，见 docs/21 §21.3）。
>
> **不要开本机模拟器**：这一步用的是真机；模拟器占性能，需要时另约时间。

## 0. 一次性准备（约 3 分钟）

1. 手机：设置 → 关于手机 → 连点「版本号」7 次，开启开发者选项；
2. 设置 → 系统 → 开发者选项 → 打开 **USB 调试**（Aura A1 可能叫「USB 调试（安全设置）」也一并打开）；
3. 数据线连电脑，手机弹窗选 **传输文件 / 允许 USB 调试**（勾选「始终允许」）；
4. 电脑上确认：

```bash
adb devices
# 期望：<序列号>	device        （不是 unauthorized / offline）
```

> 如果显示 `unauthorized`：解锁手机，重新点「允许」；如果空的：换线/换 USB 口，确认驱动。
> adb 路径：`C:\androidsdk\platform-tools\adb.exe`（没进 PATH 就用全路径）。

## 1. 安装与演示前设置（每轮都做）

```bash
APK="C:/Users/yys/AppData/Local/Temp/rc2/apk.zip"     # 本地已下载并校验过 sha256
adb install -r "$APK"
adb shell pm clear io.github.pcbuildmate                # 清数据：Entry 从空开始
adb shell cmd connectivity airplane-mode enable          # 飞行模式（证明离线）
adb shell settings put system accelerometer_rotation 0    # 锁定竖屏
adb shell settings put system user_rotation 0
adb shell settings put system screen_brightness 128       # 亮度固定
adb shell svc power stayon true                           # 演示中不锁屏
```

可选：先开始录屏兜底（结束后 `adb pull` 出来）：

```bash
adb shell screenrecord --size 720x1280 --bit-rate 4000000 /sdcard/defense-run1.mp4
# 本轮结束后 Ctrl+C，再： adb pull /sdcard/defense-run1.mp4 qa/
```

## 2. 九步（每步做完截一张图）

截图命令（PC 侧，存到仓库 `qa/`）：

```bash
adb exec-out screencap -p > qa/run1-01-entry.png
```

| # | 你要做的 | 屏幕上应该看到 | 截图 |
|---|---|---|---|
| 1 | Entry：预算填 `8000`，用途选「游戏」，点 **生成配置**；返回后再点 **载入演示配置 · ¥7,800** | 推荐 6 类、约 ¥7869、权重卡；随后进入工作区 | `run1-01` |
| 2 | 看总价与零件 | **¥7,800**，剩余 ¥200；337mm 显卡与 320mm 机箱同屏 | `run1-02` |
| 3 | 点 **保存当前配置**（记为 A） | 出现已保存提示 | `run1-03` |
| 4 | 点 **检查兼容性** | 顶部「存在冲突」、分数 52；第一条「显卡长度 BLOCKER 337 vs 320」；BIOS 行「未验证——缺少 biosGeneration」 | `run1-04` |
| 5 | 点第一条冲突的 **看看能换成什么** | rank 1 候选：300mm、`+¥100` | `run1-05` |
| 6 | 点 **预览** | 新总价 **¥7,900**，仍在预算内 | `run1-06` |
| 7 | 点 **应用并全量重检** | 「已应用并重检」，¥7,900；BIOS 仍「未验证」 | `run1-07` |
| 8 | 点 **保存当前配置**（B）；点 **比较已保存的 2 份配置** | 两列 A 7800 / B 7900，差异高亮 | `run1-08` |
| 8.5 | （可选）工作区点 **3D 间隙预览**，拖动旋转 | 337 盒体穿出 320 空间，红框 | `run1-08b` |
| 9 | 回 Entry，点 **继续上次配置 · 已保存 2 份** | 进入工作区，证明 Room 持久化 | `run1-09` |

**时间**：第 1 步约 60s，其余每步 15–45s；整轮目标 4–5 分钟（不含讲解）。

## 2.5 可选：脚本化跑完九步

`qa/drive-demo.sh` 会用 `uiautomator` 按文本找控件，自动走完九步并逐步截图（找不到控件会直接失败，不会静默跳过）：

```bash
export ADB="$HOME/AppData/Local/android-sdk/platform-tools/adb"   # 或 adb 已在 PATH
bash qa/drive-demo.sh run4 qa/screenshots
```

脚本会先 `pm clear` 应用数据，所以每一轮都是干净状态；截图存成 `<run>-01..08-*.png`。
第一次跑前请确认设备已授权（`adb devices` 显示 `device`）。

## 3. 每一轮之间

```bash
adb shell am force-stop io.github.pcbuildmate
# 等 5 秒，再用桌面图标重新打开，确认第 9 步的持久化仍然成立
```

**Run 2、Run 3 重复第 1 节的安装/清理（APK 不用重装）+ 第 2 节的九步**，
截图文件名换成 `run2-*`、`run3-*`。

## 4. 收尾

```bash
adb shell cmd connectivity airplane-mode disable
adb shell svc power stayon false
adb shell screenrecord --help >/dev/null 2>&1 || true   # 若在录屏，先 Ctrl+C
```

把每轮结果填进 `docs/21` §21.2：日期时间、系统版本（`adb shell getprop ro.build.display.id`）、
每步是否命中锚点、截图文件名。**任何一步没命中锚点，先开 issue（标题 `[QA-03] ...`），不要只在群里说。**

## 5. 常见问题

| 现象 | 处理 |
|---|---|
| `adb devices` 空 | 换数据线/口；手机重新确认「允许 USB 调试」 |
| `install` 报 `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | 旧签名不同：`adb uninstall io.github.pcbuildmate` 后重装 |
| 生成配置后数字不是 ≈7869 | 记录实际值并开 issue；锚点以 App 实测为准，不要改口 |
| 检查后 BIOS 显示「通过」 | 立刻停，开 issue（这是最强的正确性信号，不能放过） |
| 应用杀进程后「继续上次配置」消失 | 开 issue，附 `adb logcat -d -t 300` 输出 |
