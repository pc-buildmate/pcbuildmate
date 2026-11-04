# 09 · 范围外控制与 CI

> 读者：全员。
> 本章把"不做"变成**可执行的失败**。三项治理规则中的 G3（范围外硬阻塞）在这里落地。

---

## 9.1 范围外清单（出现即 fail）

| 类别 | 禁止项 |
|---|---|
| 性能预测 | FPS、跑分、benchmark prediction、CPU/GPU 瓶颈评分、performance band |
| 3D / 可视化 | 3D 装配、AR 预览、模型渲染 |
| 实时数据 | 实时库存、实时价格、电商 API、爬虫、后台自动刷新、价格轮询 |
| 二手 / 账号 | 二手价、账号系统、云同步、社交 |
| 设备能力 | 相机、条码扫描、推送、定位 |
| 越界比较 | 目录版本对比、历史价格曲线 |
| 架构越界 | UI 直读 DAO、domain 依赖 Android/Room/Compose、模块间共享可变全局状态 |

**为什么"性能分档"必须裁掉**：它不是"暂时没数据"，而是**结构上无法满足公理 A1**
（没有证据就不能断言）。一半成品会逼着前端显示一个没有来源的数字。

## 9.2 CI 三层检查

### 第 1 层：依赖边界（硬 fail，INV-16）

- `:domain:*` 不得依赖 `android.*` / `androidx.room.*` / `androidx.compose.*` / 网络库；
- `:ui` 不得依赖 `:feature` / `:data` / `:domain:*`；
- `:feature` 不得依赖 `:domain:rules|replacement|recommend|compare`；
- `test-oracle` 不得依赖生产 `domain.*`；
- `:contracts` 不得依赖任何实现模块。

执行：`node ci/check-boundaries.mjs`（读 `ci/modules.json`）。

### 第 2 层：关键词 / 权限 / 依赖坐标（硬 fail）

扫描范围：`src/main/**` 的标识符、字符串资源、`AndroidManifest.xml` 权限、`build.gradle.kts` 依赖坐标。
**不扫描**：注释、`docs/`、测试 fixture、`debug/` 下显式 `OUT_OF_SCOPE_DEMO` 文件。

| 检查项 | 命中即 fail |
|---|---|
| 标识符 | `FPS`、`fps`、`BenchmarkPrediction`、`PerformanceBand`、`LiveInventory`、`SecondHand`、`RealtimePrice`、`PricePolling`、`AutoRefresh`、`Camera`、`ARPreview`、`PushNotification` |
| 权限 | `INTERNET`、`CAMERA`、`POST_NOTIFICATIONS`、`ACCESS_FINE_LOCATION`、`FOREGROUND_SERVICE` |
| 依赖坐标 | 网络库（okhttp/retrofit/ktor）、推送 SDK、AR/相机库、爬虫库 |
| 文案 | 「实时价格」「库存同步」「帧率预测」「跑分」 |
| 卫生 | `TODO` / `FIXME` / `println` 在 `src/main` |

执行：`node ci/check-keywords.mjs`。

**例外面**：文档、注释、fixture 里讨论这些词是允许的；生产标识符不允许。
plan card 分享用应用内 JSON + 系统分享 Intent，**不需要** `INTERNET` 权限。

### 第 3 层：主流程隔离

- 主流程（Entry → Workspace → Diagnostics → Compare）不得出现范围外入口；
- debug / 答辩入口必须：显式开关、位于 `debug/`、标识 `OUT_OF_SCOPE_DEMO`、默认关闭、不进 release；
- 现有 debug 入口**只允许**"重新载入内置参考价"（纯本地重读 assets），不得扩展成网络刷新。

## 9.3 白名单

`ci/allowlist.txt`，每条一行：

```
# 文件路径 | 关键词 | 理由 | 批准人 | 日期
app/src/test/.../FpsRegressionTest.kt | FPS | 反例测试：断言范围外字段不会出现在 UI | 组长 | 2026-10-20
```

规则：

- 白名单只允许出现在**测试 / 文档 / 注释**，不允许生产标识符或权限；
- 白名单变更走 git（默认批准），但 CI 在批次报告中列出；
- **频繁新增白名单 = 范围漂移信号**，评审时点名。

## 9.4 解除范围阻塞（G3）

```
1. 写 scope-change 文档：功能描述 / 为什么首版需要 / 影响模块 / 测试计划 / 答辩口径 / 风险
2. 人批准（默认不批；批准需说明理由）
3. 批准后：更新 09 章 §9.1、§9.2 → 移除/加白名单 → 补 fixture 与验收用例 → 更新 00 章 §0.5
4. 未批准前：只能独立分支实验，不得进 main、不得进 release
```

**scope-change 模板**（六节，缺一不批）：

| 节 | 内容 |
|---|---|
| 1 功能 | 一句话 + 用户场景 |
| 2 必要性 | 不做会怎样；能否用现有功能替代 |
| 3 影响模块 | 依赖矩阵哪几格会变 |
| 4 测试计划 | 新增哪些 fixture / 矩阵哪些格子 |
| 5 答辩口径 | 一句话怎么讲，不能说什么 |
| 6 风险 | 工期、数据来源、合规 |

## 9.5 关键词表是怎么维护的

关键词表在 `ci/keywords.json`，与本文同步。加词的判断标准：

- 该词**只可能**出现在范围外功能里（如 `PricePolling`）；
- 或该词是范围外功能的**唯一标识**（如 `PerformanceBand`）。

**不要**加泛词（`AI`、`Score`、`Market`）——误报会把白名单撑爆，白名单一多就没人看了。

## 9.6 检查脚本的失败输出长什么样

```
$ node ci/check-boundaries.mjs
✗ feature/src/main/kotlin/.../WorkspaceViewModel.kt:42
    import pcbuildmate.domain.rules.DefaultRuleEngine
    违规：:feature 不得依赖 :domain:rules 的实现类
    依据：docs/05-module-independence.md §5.3
    修复：改为注入 CompatibilityEvaluator 接口

$ node ci/check-keywords.mjs
✗ app/src/main/AndroidManifest.xml
    权限 INTERNET 属于范围外（docs/09-ci-scope.md §9.2）
    若确需，请提 scope-change 并由人批准
```

**报错必须带文档链接**，否则 LLM 会自己猜怎么修，然后猜错。

## 9.7 与其它章节的对应

| 本文条目 | 对应 |
|---|---|
| §9.1 范围外 | 00 章 §0.5；13 章 §13.4 答辩口径 |
| §9.2 三层检查 | 05 章 §5.5（编译期闸）；07 章 DoD 第 4 条 |
| §9.3 白名单 | 10 章 §10.6 评审清单第 6 条 |
| §9.4 解除流程 | README §5 治理规则 G3 |
