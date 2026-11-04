# 12 · Prompt 模板（可直接复制）

> 用法：复制对应模板 → 把 `{{...}}` 替换为实际内容 → 连同 §0 的"通用头"一起发给 LLM。
> 每个模板都遵守 06 章的六块结构；**不要删减块**，删了就会漂移。

---

## 12.0 通用头（每个任务都要带）

```
你是 PC Buildmate 项目的实现者。项目规格见 docs/（我已给你相关章节）。

硬性约束（违反即作废）：
1. 只实现本任务描述的范围；不要修改其它模块，不要"顺手优化"。
2. 不得修改 contracts/ 下的任何文件；不得修改 assets/fixtures/ 的期望值。
   如果确实需要，停止生成，列出原因与建议的契约 diff。
3. 先写测试，再写实现。每个测试都必须在"实现写错"时失败。
4. 输出必须确定性：所有集合在输出前显式排序，不使用随机、时钟、Locale。
5. 遇到规格未覆盖的情况，列出 2–3 个选项并等待我选择，不要自行决定。
6. 不引入新依赖，除非我明确批准。不使用 Android API（除本模块声明允许的）。
7. 不要生成 TODO / FIXME / println。
8. 完成后输出：改了哪些文件、跑了哪些命令、矩阵覆盖情况（ruleId/case 或 fsm 格子）。
```

---

## 12.1 D1 硬件数据库

```
【目标】
实现 :data 的目录加载与查询端口，使 D3 能在无 Android 依赖的情况下读到只读目录。
不做兼容性判断，不做价格计算。

【输入契约】
- contracts/Model.kt 的 CatalogueRecord / FieldValue / FieldSource
- contracts/Ports.kt 的 CataloguePort
- assets/catalogue/demo-v1.json（格式样例；生产 seed 另给）
- contracts-test/.../CataloguePortContract.kt（必须继承并通过）

【输出契约】
- AssetCatalogueAdapter : CataloguePort
- SeedValidator：启动时校验（路径在 docs/03-rules.md §3.3 表内、id 唯一、isDemo 与目录分离、数值可解析）
- 查询结果按 componentId 字典序

【禁止事项】
- 禁止网络、实时价、库存（docs/09-ci-scope.md §9.2）
- 禁止在数据层计算兼容性
- 禁止把 isDemo=true 的记录放进生产 seed
- 禁止新增字段路径

【验收用例】
1. getById 命中返回完全相等的记录
2. getById 未命中返回 null 不抛异常
3. search 大小写不敏感、结果确定
4. 重复 id -> 启动 fail-fast
5. 非法路径 -> 启动 fail-fast
6. demo id 出现在生产 seed -> fail-fast

【失败模式】
NotFound -> null；SchemaViolation -> 启动即抛（数据层允许 fail-fast）；EmptyCatalogue -> 空列表
```

---

## 12.2 D3 兼容性检查（规则引擎）

```
【目标】
实现 :domain:rules 的 CompatibilityEvaluator：输入 BuildRevision，输出按 ruleId 字典序的 List<RuleOutcome>。
纯 Kotlin，无 Android、无 I/O、无时钟。

【输入契约】
- contracts/Outcomes.kt（RuleOutcome / UnverifiedReason / Severity）
- contracts/Ports.kt（CataloguePort / CompatibilityEvaluator）
- docs/03-rules.md §3.2–§3.4（DSL 与五步判定）、§3.7（PSU 公式）
- assets/rules/rules-v1.json（15 条规则）
- assets/fixtures/fixtures-rules-v1.json（60 条，作为验收数据）

【输出契约】
- evaluate 结果按 ruleId 字典序
- Conflict 带 conflictId / 左右 FieldRef / 左右值 / severity
- Unverified 带 reason，能定位时带 missingField
- 同输入两次调用结果 == 相等

【禁止事项】
- 禁止缺字段 PASS（INV-04）
- 禁止把 REPORTED 当 VERIFIED
- 禁止读价格/预算/时间/UI 文案
- 禁止抛异常（未知规则降级为 UNSUPPORTED_RULE 并记日志）
- 禁止改 §3.7 公式

【验收用例】
1. 60 条 fixture 全绿（参数化，@Tag("rule:*") @Tag("case:*")）
2. 337/320 -> CONFLICT，conflictId 固定
3. BIOS EMPTY -> UNVERIFIED，missingField 指向 mb.biosGeneration
4. 两来源 337/335 -> UNVERIFIED(SOURCE_CONFLICT)
5. 空 revision -> 空列表
6. PSU 等号/差 1/取整三个边界

【失败模式】
未知 ruleId/路径 -> Unverified(UNSUPPORTED_RULE)；缺部件 -> 该规则 Unverified(MISSING_FIELD)
```

---

## 12.3 D5 替换建议

```
【目标】
实现 :domain:replacement 的 ReplacementAdvisorPort：先过硬约束过滤，再按固定键排序，无候选给结构化原因。
不修改 build，不调用 UI。

【输入契约】
- contracts/Suggestions.kt（Candidate / SuggestionResult / NoCandidateReason）
- contracts/Outcomes.kt（RuleOutcome.Conflict）
- contracts/Ports.kt（CataloguePort / CompatibilityEvaluator）
- docs/06-modules.md#d5（算法契约）
- assets/fixtures/fixtures-anchor-v1.json 的 fx_suggestion_300_plus_100

【输出契约】
- 每个候选替换后该 conflictId 消失
- 候选不得引入新的 BLOCKER Conflict（引入 MAJOR/MINOR 的保留并写入 affectsOtherRules）
- 排序：priceDeltaCny 升序 -> 对剩余余量影响小 -> componentId 字典序
- 无候选：candidates=[] 且 noCandidate 非空

【禁止事项】
- 禁止先排序再过滤
- 禁止用综合分抵消不兼容
- 禁止隐藏会影响其它规则的候选而不标注
- 禁止声称"最优替换"

【验收用例】
1. 337/320 -> 含 300mm，priceDelta=100，rank=1
2. 会引入 PSU 不足的候选被过滤或标注
3. 无候选 -> blockingConstraint 指向 ruleId+fieldPath
4. 同输入两次顺序一致
5. base 过期 -> noCandidate(blockingConstraint="STALE_REVISION")

【失败模式】
无候选/目录空/陈旧 base -> 结构化返回，不抛异常
```

---

## 12.4 D2 工作区（ViewModel 编排）

```
【目标】
实现 :feature 的 WorkspaceViewModel：Intent -> 新 revision -> 驱动两张状态机 -> 执行 Effect -> 单一 UiState。
不计算规则、不直接访问 Room、不自动重检。

【输入契约】
- contracts/Workspace.kt（WorkspaceIntent / WorkspaceUiState / Effect / 两张状态机类型）
- docs/04-state-machines.md（全部转移表，这是唯一权威）
- docs/06-modules.md#d2（不变量与失败模式）
- :domain:state 的 transition 函数（纯函数，直接调用）

【输出契约】
- StateFlow<WorkspaceUiState>
- 每次编辑产生新 revision，totalCny 同步
- 编辑后 diagnosisState == Idle 且 currentDiagnosis == null
- 只有经 Rechecking 才能 Applied
- 被拒绝的意图不改 state，只写 rejection

【禁止事项】
- 禁止写兼容性 if / 直接调 DefaultRuleEngine
- 禁止编辑后自动 StartCheck
- 禁止把草稿混入 savedBuilds
- 禁止 GlobalScope；协程必须在 viewModelScope 且可取消

【验收用例】
1. 空 build + Add CPU -> 新 revision、total 更新、Idle
2. 已诊断 + SetQuantity -> currentDiagnosis=null、banner 出现、无 Running
3. 慢检查迟到 -> 丢弃（交错用例，用可控 Deferred，不用 sleep）
4. Undo -> 新 id 更大、内容等于上一快照
5. preview 中 base 过期 -> Rejected(APPLY_REVISION_STALE)，build 不变
6. 非法 JSON plan card -> 拒绝，savedBuilds 不变

【失败模式】
全部通过 Rejection 表达（CATALOGUE_MISS / QUANTITY_INVALID / APPLY_REVISION_STALE / IMPORT_INVALID_SCHEMA）
```

---

## 12.5 D4 预算推荐

```
【目标】
实现 :domain:recommend 的 BudgetRecommenderPort：预算+用途+偏好 -> 可编辑起始配置；权重可见；不可行必报。

【输入契约】
- contracts/Suggestions.kt（RecommendationRequest / RecommendationResult）
- contracts/Ports.kt（CataloguePort / CompatibilityEvaluator）
- docs/06-modules.md#d4
- docs/03-rules.md §3.6（硬约束清单）

【输出契约】
- feasible=true => total <= budget，且无 BLOCKER Conflict
- feasible=false => shortfall > 0，relaxedPreferences 非空（若放宽）
- weightsVisible 非空且与实际分配一致
- 同输入同目录版本结果确定

【禁止事项】
- 禁止声称最优
- 禁止性能分数/FPS/跑分排序
- 禁止超预算或放宽硬约束而不报告
- 禁止隐藏权重

【验收用例】
1. 8000/GAMING -> total<=8000，硬约束无 BLOCKER
2. 1000/GAMING -> feasible=false，shortfall>0
3. GAMING vs STUDY_OFFICE -> GPU 权重前者大，且均可展示
4. 推荐结果再跑引擎 -> 无新 BLOCKER

【失败模式】
空目录/预算过低/偏好不可满足 -> 结构化不可行结果
```

---

## 12.6 D6 比较

```
【目标】
实现 :domain:compare 的 BuildComparatorPort：2–3 个 saved build 并排；只读已有诊断；过期不显示旧结论。

【输入契约】
- contracts/Suggestions.kt（ComparisonResult / FieldDelta）
- contracts/Ports.kt（BuildComparatorPort / BuildStorePort）
- docs/06-modules.md#d6（字段冻结清单）

【输出契约】
- diagnoses[i] 非空 <=> 该 build 诊断 revision 匹配
- warnings 用 TextKey
- 字段：总价、兼容状态、分类花费、功耗余量、升级余量、物理尺寸
- 不做 performance band

【禁止事项】
- 禁止调用规则引擎
- 禁止把过期诊断当当前结论
- 禁止修改 build

【验收用例】
1. 2 个 build -> 字段对齐、delta 正确
2. 某 build 诊断过期 -> 该列为 null + warning
3. <2 个 build -> 拒绝
4. 目录版本不同 -> warning，仍比较

【失败模式】
InsufficientBuilds -> Rejected；DiagnosisStale -> null + warning；BuildNotFound -> 整体错误
```

---

## 12.7 D7 UI 绑定

```
【目标】
实现 :ui 的四屏 Compose 界面：只读 WorkspaceUiState，只发 WorkspaceIntent，不含业务计算。

【输入契约】
- contracts/Workspace.kt（WorkspaceUiState / WorkspaceIntent / TextKey）
- docs/06-modules.md#d7
- docs/13-risks.md §13.4（哪些话不能出现在 UI）

【输出契约】
- 每个可见元素有稳定 testTag（用 TextKey.key 或语义 id）
- 相同 state 渲染相同 UI（无内部可变状态）
- 360dp 小屏关键操作可达

【禁止事项】
- 禁止 import data / feature 实现 / domain
- 禁止在 Composable 里算兼容性、价格、delta
- 禁止自行拼接冲突文案（用 leftValue/rightValue + 资源模板）
- 禁止用 onNodeWithText 断言长句

【验收用例】
1. 有冲突 state -> 可见 conflict_<ruleId> tag，左右值均显示
2. stale state -> 可见 diagnostics_stale_banner，不可见 PASSED 徽标
3. 空 build 点检查 -> 提示且未导航
4. 360dp -> 关键按钮可达

【失败模式】
guard 失败只提示不导航；过期诊断只显示 banner
```

---

## 12.8 收尾模板（每次任务结束贴一次）

```
请按以下格式汇报，不要写散文：
1. 改动文件清单（按模块）
2. 新增/修改的测试（列出 tag：rule/case/inv/fsm）
3. 已运行的命令与结果
4. 矩阵覆盖：rules __/60，fsm __/76，inv __/18
5. 遗留问题（含规格空白，用 §10.5 格式）
6. 是否触碰 contracts/ 或 fixtures/（应为"否"）
```
