# POLISH-01 · UI 打磨与可访问性

> 状态：**doing**｜建议 owner：@MoeField（可换）｜预估：2 天｜依赖：D2/D6

## 目标

让演示看起来像一个完整 App，而不是“功能验证页”。

## 现状

- Entry / Workspace / Diagnostics / Compare 四屏可跑；
- 文案大量硬编码在 Compose 里；
- 空态、错误态、加载态有雏形；
- 状态栏已修成深色图标；
- 深色模式、TalkBack、字号适配未验证。

## 需要写

- `:ui` 文案迁到 `strings.xml` 或 `TextKey`；
- 统一 `Scaffold` / 顶栏 / 卡片间距；
- TalkBack contentDescription、可点击区域 ≥48dp；
- 深色模式配色（可后置）；
- 截图回归：四屏各一张，放 `docs/screenshots/`。

## 验收

- 四屏在 720×1600 与 1080×2400 都不截断；
- 关键按钮有语义标签；
- `node ci/check-keywords.mjs` 不新增命中；
- 真机截图更新到 README。

## 不要做

- 不做 3D/动画炫技；
- 不改业务语义；
- 不加范围外入口。

## 第一步

先把 `Screens.kt` 里用户可见的中文抽到 `ui/src/main/res/values/strings.xml`。
