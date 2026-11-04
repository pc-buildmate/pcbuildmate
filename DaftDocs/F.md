# F. 范围外 CI 检查清单、文档消费方式与收口

## F.1 范围外清单（硬阻塞）

首版明确不做，出现即 CI fail，除非走 F.4 解除流程：

| 类别 | 禁止项 |
|---|---|
| 性能预测 | FPS、跑分、benchmark prediction、CPU/GPU 瓶颈评分、performance band |
| 3D/可视化 | 3D 装配、AR 预览、模型渲染 |
| 实时数据 | 实时库存、实时价格、电商 API、爬虫、后台自动刷新、价格轮询 |
| 二手/账号 | 二手价、账号系统、云同步、社交功能 |
| 设备能力 | 相机、条码扫描、推送通知、定位 |
| 范围外比较 | 目录版本对比、历史价格曲线 |
| 架构越界 | UI 直接读 DAO、Domain 依赖 Android/Room/Compose、模块间共享可变全局状态 |

## F.2 CI 三层检查

### 第一层：依赖检查（硬 fail）

- `domain/` 不得依赖 `android.*`、`androidx.room.*`、`androidx.compose.*`、网络库。
- `test-oracle/` 不得依赖生产 `domain/rules`、`domain/replacement`、`domain/recommend`。
- `ui/` 不得直接依赖 DAO；只能依赖 ViewModel/Repository 接口。
- `contracts/` 不得依赖任何实现模块。

实现：Gradle dependency constraint + 简单脚本扫 import。命中即 fail，白名单需在 `ci/allowlist.txt` 显式维护并附理由。

### 第二层：关键词/符号检查（硬 fail）

扫描范围：生产代码 `src/main/` 的标识符、字符串资源、权限声明、依赖坐标。  
**不扫描：** 注释、文档、测试 fixture、`debug/` 下显式 `OUT_OF_SCOPE_DEMO` 文件。

| 检查项 | 命中即 fail |
|---|---|
| 标识符 | `FPS`、`fps`、`BenchmarkPrediction`、`PerformanceBand`、`LiveInventory`、`SecondHand`、`RealtimePrice`、`PricePolling`、`AutoRefresh`、`Camera`、`ARPreview`、`PushNotification` |
| 权限 | `INTERNET`、`CAMERA`、`POST_NOTIFICATIONS`、`ACCESS_FINE_LOCATION`、`FOREGROUND_SERVICE` |
| 依赖坐标 | 网络库、推送 SDK、AR/相机库、爬虫库 |
| 字符串资源 | 出现“实时价格”“库存同步”“帧率预测”等面向用户文案 |

**例外：** 文档、注释、fixture 中讨论这些词允许；生产标识符不允许。plan card 分享用应用内 JSON + 系统分享 Intent，不需要 `INTERNET`。

### 第三层：功能开关与主流程隔离

- 主流程（Entry → Workspace → Diagnostics → Compare）不得出现范围外入口。
- debug/答辩入口必须：显式开关、文件位于 `debug/`、标识 `OUT_OF_SCOPE_DEMO`、默认关闭、不进 release。
- 现有 debug 入口仅允许“重新载入内置参考价”；不得扩展成网络刷新。

## F.3 白名单规则

- 白名单文件 `ci/allowlist.txt`，每条含：文件路径、关键词、理由、批准人、日期。
- 白名单只允许注释/测试/文档，不允许生产标识符或权限。
- 白名单变更走 git，默认批准，但 CI 记录并在批次报告中列出；频繁新增白名单视为范围漂移信号。

## F.4 解除范围阻塞流程

1. 提交 `scope-change` 文档：功能描述、为什么首版需要、影响模块、测试计划、答辩口径、风险。
2. 人工批准（你已定：必须提文档，由人批准）。
3. 批准后：更新 F.1/F.2、加白名单或移除检查、补验收用例和 fixture、更新范围外清单。
4. 未批准前：只能在独立分支实验，不得进主分支、不得进 release。

## F.5 文档消费方式（给 Android Studio LLM）

### 每模块 prompt 六块（缺一不可）

1. **目标**：本模块职责一句话。
2. **输入契约**：接口签名 + 依赖模块的只读接口。
3. **输出契约**：数据类型 + 不变量 + 失败返回。
4. **禁止事项**：本卡“禁止事项”+ F.1 相关项。
5. **验收用例**：本卡验收 + D.7 对应契约测试 + B.4 相关 fixture。
6. **失败模式**：`Rejected`/错误结构/降级行为。

### 上下文打包

每个 prompt 附带：本模块卡 + A 章相关 schema + C 章相关状态机 + B 章相关规则/fixture + D.7 对应测试。不附全文档，避免上下文超限和注意力稀释。

### 生成顺序与闸门

```
D1 数据 → D3 规则 → D5 替换 → D2 工作区 → D4 推荐 → D6 比较 → D7 UI
```

每步：生成 → 编译 → 跑契约测试 + 不变量 + mutation 关键项 → 通过才进入下一步。不过就回退，不让 LLM 自行改契约。

### 契约变更

走 git，默认批准自动合并；CI 记录 diff、影响模块、ADR。LLM 不得在实现 PR 中顺手改 `contracts/`；契约 diff 单独提交，便于追踪。

## F.6 文档目录与版本

建议交付结构：

```
docs/
  00-overview.md          # 定位、范围、用户旅程
  01-glossary.md          # 术语与三态语义
  02-contracts.md         # A 章 schema + B.0 补丁
  03-rules.md             # B 章 DSL + 15 条规则 + fixture 规范
  04-state-machines.md    # C 章两张转移表 + 导航 guard
  05-modules.md           # D 章六模块卡 + D.7 矩阵
  06-testing.md           # E 章 oracle、归因、mutation、批次报告
  07-ci-scope.md          # F 章范围外检查与解除流程
  08-adr/                 # 架构决策记录
  09-prompt-templates/    # 每模块 prompt 模板
contracts/                # Kotlin 契约源码（只读）
test-oracle/              # 独立参考模型
assets/
  catalogue/static-v1.json
  rules/rules-v1.json
  fixtures/
```

版本：文档 `v0.1-draft`；`catalogueVersion=static-v1`；`rulesVersion=rules-v1`。失败报告必须记录三者 + commit + promptHash/modelVersion + seed。

## F.7 剩余 TBD 与人工闸门汇总

**人工闸门（你已定）：**
1. 契约变更：git 默认批准，CI 记录，不设双签。
2. 测试批次最终通过：人批，只看汇总/未分类/未杀死 mutation/未覆盖项。
3. 解除功能阻塞：提 `scope-change` 文档，人批。

**仍建议保留的最小 TBD（不影响 LLM 开工）：**
- 最低 Android API（建议 26+）。
- 随机测试每批 seed 数 N（建议 50，团队定）。
- 静态目录具体条目数（建议 8 类 × 3–8 件）。
- 预算推荐具体权重数值（只要求可见可调，测试只测不变量）。
- 答辩前填“当前真实进度”，不报完成比例/准确率/用户数。

**已知风险（文档写明，不假装解决）：**
- 需求仍是合理假设，未做用户验证；成功指标分技术正确性与用户理解。
- 参考模型只覆盖可判定部分，推荐/替换“最优”不做断言。
- LLM 断言有假阳性，必须单独统计，不直接判 bug。
- 静态目录不能证明真实市场兼容性；UI 必须保留“未知/未验证”。

## F.8 收口

到 F 为止，粗略文档已覆盖：范围与用户 → 契约 schema → 规则表与 fixture → 两张状态机 → 六模块卡与配合矩阵 → 测试 oracle 与归因 → 范围外 CI 与文档消费方式。  
LLM 可以按 D0 顺序开工；每个模块有输入/输出/禁止/验收/失败模式；跨模块靠契约测试和不变量卡住；范围靠 CI 硬阻塞；批次靠人批。

如果你确认，我下一步可以把 A–F 合并成一份可交付的 `docs/` 目录草稿（分文件、带标题层级和交叉引用），或者先展开 `08-adr/` 里最关键的 3 条 ADR：**为什么 MVI+revision**、**为什么静态目录+无实时价格**、**为什么 LLM 不做最终 oracle**。你选哪个？