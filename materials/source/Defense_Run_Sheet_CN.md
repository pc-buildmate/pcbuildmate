# 答辩现场卡（中文操作 + 英文口播）

> 打印一页随身带。操作提示是中文，**要念的句子是英文**（方框内）。
> 完整版：[Defense_Run_Sheet.md](Defense_Run_Sheet.md)；演示脚本 `docs/defense/DEMO.md`。

## 上台前 10 分钟

- [ ] 平板充电、**飞行模式**、锁竖屏、亮度固定
- [ ] 清数据后载入一次演示配置（或确认"继续上次配置"为空——现在决定，不要台上想）
- [ ] 投屏连好，点一下确认投影有反应
- [ ] 录屏打开放在第二个窗口（**兜底**）
- [ ] PPT 归到第 1 页、计时器可见

## 时间轴（谁 → 说什么）

| 时间 | 谁 | 中文提示 | 交接句（英文） |
|---|---|---|---|
| 0:00–2:40 | 陈思言 | 讲"每件都好也可能装不上"，别念规则清单 | "…so what should the app actually promise?" |
| 2:40–4:45 | 王嘉瑞 | 三条原则各给一句"为什么" | "Let me show you what that looks like on a device." |
| 4:45–11:20 | 黄奕维 | 九步演示（下方卡片） | "…which raises the question of how it is built." |
| 11:20–12:50 | 阮立坤 | 14 模块 + 为什么是尺寸可视化 | "Can you trust any of this?" |
| 12:50–14:10 | 杨壹硕 | 四道门 + **这套东西证明不了什么** | "So what do we still not have?" |
| 14:10–15:00 | 杨壹硕 | 边界、下一步、收尾句 | "Thank you — questions welcome." |

## 九步口播卡（你点什么 → 念什么）

> 排练口径：在 **rc2** 包上排练时屏幕是 **6 类 / ¥7,869**；最终发布包才是 **8 类 / ¥7,900**。
> 若在小包上看到 7869，不是 App 坏了，是包旧了。

1. Entry：8000 / 游戏 → **生成配置** → **载入演示配置**
   > "Eight categories, case and cooler included, about 7,900. Our anchor build is 7,800."
2. 工作区 → **"337 millimetres card, 320 millimetre case, on one screen."**
3. 保存 A → **"A is the baseline."**
4. 检查 → 指 BIOS 那一行，停一秒
   > "Score 52; the length is a blocker; and the BIOS row is unverified — unverified is not a pass."
5. 看看能换成什么 → **"Rank one: 300 millimetres, plus 100."**
6. 预览 → **"7,900 — and the current revision has not changed."**
7. 应用并全量重检 → **"One fixed conflict does not mean the build is compatible."**
8. 保存 B → 返回 → 继续上次配置 → 比较
   > "A is 7,800, B is 7,900; only decidable fields are compared."
9. 回 Entry → 继续上次配置
   > "Persistence — and the diagnosis must be re-run, because it belongs to a revision."

**出问题**：停嘴 → "Let me use the recording." → 切录屏 → 照读同一段。

## 数字（必须准）

能说：15 条规则 · 60/76/18 格 · 12/12 变异 · Room 8/8 · 真机 3 次 · **7800 → 7900** · **52** · arm64-only、无网络权限。

不能说：保证兼容 · 实时/最低价 · 最优配置 · AI/机器学习 · 准确率/用户数 · FPS/跑分/性能分档。

## 最先会被问的四题（英文答，中文记要点）

1. **BIOS 为什么未验证？** → 目录缺 `biosGeneration`，我们不猜。
2. **测试是不是照抄实现？** → 独立 oracle（禁 import 生产层），写反它会立刻变红；12 条变异在 CI 跑。
3. **为什么不做 3D 装配？** → 只读间隙视图，固定相机，不做装配。
4. **每个字段能溯源吗？** → 记录级可（30/30，27 条带 URL）；**字段级 0/168**，不声称逐字段可溯源。
