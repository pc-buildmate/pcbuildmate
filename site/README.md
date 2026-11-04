# PC Buildmate · 交互原型（site/）

> 这是一个**用来看 UI 逻辑跑不跑得通**的静态站点：假数据、真规则、真状态机。
> 不是真机 App，也不是最终交付物；它的作用是让「设计文档里的状态机」变成可以点的东西。

打开方式（二选一）：

1. **直接双击** `site/index.html`（纯静态，无构建、无网络请求）；
2. 或起一个本地服务：`node tools/serve-site.mjs` → http://127.0.0.1:4173/

---

## 1. 三分钟演示脚本（按这个顺序点）

| # | 操作 | 应该看到 |
|---|---|---|
| 1 | 打开页面 → 「载入答辩演示配置 · ¥7,800」 | 8 类零件，总价 7,800，预算 8,000，状态「未检查」 |
| 2 | 点「检查兼容性」 | 诊断页：**有冲突 / 2 冲突 / 1 未验证**，分数 52，风险 HIGH |
| 3 | 看冲突卡片 | `GPU_CASE_LENGTH` 337 vs 320（BLOCKER）、`GPU_CASE_THICKNESS` 61 vs 60（MAJOR）；BIOS 一条显示「未验证 · 缺少 biosGeneration」 |
| 4 | 点「看看能换成什么」 | 候选列表：300mm（+¥100，rank 1）；会引入新 BLOCKER 的候选已被过滤 |
| 5 | 点「预览」→「应用（全量重检）」 | 预览价 7,900 → 重检 → 已应用；GPU 转「通过」，**BIOS 仍是未验证** |
| 6 | 回到配置页，改内存数量 | 诊断立刻清空，出现「配置已修改…请重新检查」横幅；**不会自动重跑** |
| 7 | 打开右侧「慢检查」→ 检查 → 中途改数量 | 检查中的结果迟到返回时被丢弃，事件日志出现 `DROPPED` |

第 5 步是重点：**修一个冲突 ≠ 全部兼容**。这也是答辩讲稿 Slide 16 的那句话。

## 2. 右侧开发者面板（这是核对逻辑的地方）

| 标签 | 看什么 |
|---|---|
| 状态 | `revision.id`、`diagnosisState`、`diagnosis.revisionId`、`replacement` 状态机当前态 |
| 不变量 | 10 条实时检查（INV-01/02/03/04/06/07/11/12/15 + 类别唯一），红一条就说明逻辑漏了 |
| 事件日志 | 每次转移的 guard 结果：`"迟到的诊断属于 revision #3…已丢弃"`、`"APPLY_REVISION_STALE：基线已变"` |
| 证据模拟 | 把任意字段改成 EMPTY / REPORTED，看结论怎么从「通过」变成「未验证」 |
| 说明 | 与设计文档的对应关系、简化点 |

**建议先用「证据模拟」把 `mb-demo-b650.biosGeneration` 从 EMPTY 改回 VERIFIED**，
再点检查：BIOS 那条会变成「通过」——这正好演示了三态模型不是装饰。

## 3. 文件

```
site/
├─ index.html          外壳（手机 + 检查器）
├─ styles.css          样式（深色，390×812 手机画布）
├─ data.generated.js   由 assets/ 生成的目录与规则（勿手改）
├─ engine.js           规则引擎：15 条规则 / 7 种 kind / weakest 传播 / 五步判定
├─ state.js            工作区 reducer：revision、诊断状态机、替换状态机、建议、不变量
├─ ui.js               Compose 的替身：只读 state、只发 intent
└─ README.md
```

## 4. 它到底验证了什么

| 命令 | 验证内容 |
|---|---|
| `node tools/test-demo-engine.mjs` | 用仓库里的 **60 条 fixture + 8 条锚点** 跑这个引擎，逐条比对 outcome/severity/weakest/reason/conflictId/左右值 |
| `node tools/test-demo-flow.mjs` | 无头跑完整旅程：7800→冲突→候选+100→预览7900→重检→应用；编辑清诊断；迟到丢弃；无候选；撤销；不变量；推荐可行/不可行 |
| `node tools/test-demo-ui.mjs` | 用极简 DOM 替身渲染全部 5 个界面 + 5 个面板，确认没有运行时错误（含「诊断过期」分支） |

三条都过，说明**页面里的判定不是另写的一套**，而是与设计文档同一套语义。
反过来说：如果哪天有人在 `engine.js` 里「顺手改一下」，fixture 会立刻红。

## 5. 与设计文档的对应

| 文档 | 在站点里的体现 |
|---|---|
| [docs/02-contracts.md](../docs/02-contracts.md) | 五步判定、overall 四步、分数字段、conflictId |
| [docs/03-rules.md](../docs/03-rules.md) | 15 条规则、7 种 kind、PSU 公式、weakest |
| [docs/04-state-machines.md](../docs/04-state-machines.md) | 诊断 4 态、替换 6 态、Apply 的 3 个 guard |
| [docs/06-modules.md](../docs/06-modules.md) | D2 的「编辑不自动重检」、D5 的「先过滤再排序」 |
| [docs/07-unit-verification.md](../docs/07-unit-verification.md) | 不变量面板、矩阵与锚点 |
| [docs/13-risks.md](../docs/13-risks.md) §13.5 | 演示脚本与 ¥7,800 → ¥7,900 |

## 6. 明确简化了什么（别当成 bug）

| 简化 | 原因 | 正式实现 |
|---|---|---|
| `missingField` 是路径字符串 | 原型省掉 `FieldRef` 类型 | `FieldRef(componentId, fieldPath)` |
| 没有持久化 | 刷新即重置 | Room + `BuildStorePort` |
| 推荐是贪心启发式 | 只验证「预算内 + 硬约束通过 + 权重可见」 | `BudgetRecommenderPort` |
| 目录只有 16 个零件 | 演示用 | 静态 seed（仍在范围内） |
| 没有 i18n | 中文单语 | `TextKey` + 资源表 |
| 证据改动用「模拟器」 | 方便看三态效果 | 数据录入时标注 `FieldSource` |

**范围外的东西一个都没有**：没有实时价格、没有网络请求、没有 FPS/跑分、没有 3D、没有相机权限（
`site/` 里连一个 `fetch` 都没有）。

## 7. 已知限制

- 只在 Chromium / Safari 的现代版本上测过（用了 `closest`、CSS Grid、`backdrop-filter`）；
- 手机画布固定 390×812，窄屏时会溢出（这是有意为之：模拟真机而不是响应式网页）；
- 检查用 `setTimeout` 模拟异步，刷新页面会中断正在进行的检查；
- 没有键盘可达性优化（真实 App 需要）。

## 8. 如果要把它变成真服务

当前是纯静态：`site/` 下没有后端。若要接 Node/Flask：

```
静态资源 → 原样伺服 site/
POST /api/evaluate  → 复用 site/engine.js 的 evaluate()（同一份规则数据）
POST /api/suggest   → 复用 site/state.js 的 suggest()
```

但注意：**服务端计算会让「离线可用」这条卖点失效**，而且答辩演示会多一个故障点。
建议保持静态，把服务端留到真的需要多设备同步时再说。
