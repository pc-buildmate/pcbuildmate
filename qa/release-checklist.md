# 发布日检查单（12/01 最终发布）

> 角色：发布由阮立坤执行；**校验**由测试线执行，结果写回
> [docs/21 §21.3](../docs/21-device-evidence.md)。流程细节见 `docs/15 §15.4`。

## 0. 前置条件（缺一项就不要打 tag）

- [ ] 功能冻结已过（11/28），`main` 上 CI 全绿（含 `room` job）
- [ ] `app/build.gradle.kts` 的 `versionName` 已定稿（当前 `1.0.0-defense`）
- [ ] 四个 Secrets 仍在：`KEYSTORE_BASE64` / `KEYSTORE_PASSWORD` / `KEY_ALIAS` / `KEY_PASSWORD`
- [ ] `docs/defense/RELEASE_NOTES.md` 已更新到本次版本
- [ ] `node qa/check-evidence.mjs` 通过（`docs/21` 无空格子、截图齐全）

## 1. 打 tag（**管理者执行**，会触发签名与 draft release）

```bash
git checkout main && git pull
git tag -a v1.0.0-defense -m "PC Buildmate 1.0.0-defense"
git push origin v1.0.0-defense
```

tag 规则：正式 tag 去掉 `v` 必须**完全等于** `versionName`；`-rcN` 预发布只比较前缀。

## 2. 等 release workflow

- Actions → `release`，约 5–10 分钟；
- 失败常见原因：secrets 缺失、版本号与 tag 不一致（脚本会点名）、ABI 含非 arm64。

## 3. 校验产物（测试线执行）

```bash
# 从 draft release 下载两个附件到本地后
bash qa/verify-release.sh PC-Buildmate-v1.0.0-defense.apk PC-Buildmate-v1.0.0-defense.apk.sha256 1.0.0-defense
```

期望六项全过：sha256 / 签名（拒绝 debug 证书）/ 权限（无 INTERNET、CAMERA、LOCATION、推送）/ ABI（仅 arm64-V8a）/
包名 `io.github.pcbuildmate` / versionName。

或手动触发 CI 的 `release-verify` job（勾选 `verify_release`），它会自动取最新 release 跑同一脚本。

## 4. publish 前再确认

- [ ] draft 附件名与版本一致；
- [ ] `.sha256` 内容与 APK 实算一致（第 3 步已验）；
- [ ] Release notes 没有范围外承诺（无实时价、FPS、最优解）。

publish 由管理者点击；这一步不做自动化。

## 5. 发布后（当天完成）

- [ ] 在真机装发布包：`adb install -r <apk>`，飞行模式启动，Entry 正常；
- [ ] **在发布包上重跑九步**（`bash qa/drive-demo.sh final qa/screenshots`），与 `docs/21 §21.2` 的锚点逐项对：
  `7869`、`7800 → 7900`、冲突分 `52`、`+100`、revision `#2`、`BIOS 未验证`、A/B 比较、杀进程后恢复；
  有任何一项不同，先当作回归查清，再决定是否发布后修；
- [ ] 若这一版的 UI/3D 与 rc2 截图有可见差异（例如 3D 显卡槽位按 `pcieX16SlotMm` 调整），
  同步重截答辩 deck 里对应的那几页，不要让 PPT 与发布包对不上；
- [ ] 把第 3 步结果与设备记录写进 `docs/21 §21.3` / `§21.2`；
- [ ] 更新 `docs/21` 顶部“校验对象”为本次 tag；
- [ ] 若产物与此前记录的哈希不同，在 `docs/21 §21.4` 记一行原因。

## 回滚

若 draft 阶段发现问题：删除 tag 与 draft release，修 `versionName`/数据后重新打 tag。
**publish 之后不回滚 tag**——改为在 Release notes 标注问题版本并打补丁版本。
