# PC_Buildmate_QA_Preparation


<!-- page 1 -->

PC Buildmate / EE5415 / Q&A Preparation
1
PC Buildmate Q&A Preparation
EE5415 汇报后问答备忘录 18 题
使用方法
本备忘录对应 PC Buildmate 的 25 页汇报 PPT。英文回答可作为口述起点，中文要点用于理解和防止误答。每题约
20–35 秒；先回答结论，再补一句理由或例子，老师追问后再展开。
优先练熟
Q6 能否保证整机兼容；Q7 预算推荐是否最优；Q8 智能是否等于机器学习；Q10 为什么选择 MVI；Q13 与现有工
具的差别；Q15 如何验证成功；Q16 原型与实际进度；Q17 如何控制范围。
王嘉瑞重点准备
你负责 App Vision，建议先练 Q1 和 Q13，讲清用户价值。你的开发分工是 Domain Layer，因此也准备 Q5–Q9 的
追问，尤其是“先过滤兼容约束，再排序”和“没有候选怎么办”。
上台前核对
本材料没有假定你们已经完成代码、用户访谈或测试。Q16 的实际进度需要用团队真实情况补充。如果没有验证结果
，就说明实现或测试计划，不报完成比例、准确率、用户数量或具体上线日期。
全组统一口径
名称统一为 PC Buildmate。价格是带日期的参考数据。字段证据状态与规则检查结果要区分。解决一个冲突不代表
整机全部验证通过。第 16 页是概念原型，337 mm 显卡超过 320 mm 限制，替换为 300 mm 后加价 CNY 100，
BIOS 支持仍未验证。
自然交接
如问题进入另一位同学的负责范围，可说：My teammate can explain that part in more detail. 随后由相关同学直
接接着回答。建议主答者是准备分工，并不限制现场由谁回答。


<!-- page 2 -->

PC Buildmate / EE5415 / Q&A Preparation
2
01 App vision and users
愿景和用户
Q1 What exact problem does PC Buildmate solve?
你们到底解决什么问题？ 建议：王嘉瑞主答，陈思言补充 对应 PPT：4–9
PC Buildmate helps users understand whether selected PC parts satisfy supported constraints and
what to change when they do not. The proposed workflow connects budget planning, editing,
checking, replacement previews and comparison. Our goal is to help a first-time builder make an
informed choice while keeping missing information visible.
中文要点：围绕用户决策讲：选完零件后，知道哪里冲突、怎么换、差多少钱。不要把定位缩成商品目录或购物平台。
Q2 Who is your primary user, and why would they need this app?
主要用户是谁，为什么需要你们？ 建议：陈思言主答，王嘉瑞补充 对应 PPT：5、18
Our primary users are first-time builders and people upgrading within a fixed budget. Beginners need
understandable checks and a starting configuration. Upgraders need to check new parts against the
components they keep. We plan to evaluate these two groups first, rather than designing the initial
prototype for every type of PC user.
中文要点：先讲新手和预算有限的升级用户。发烧友、小店属于次要用户。用户需求目前是待验证的假设，不要说已
获大量用户认可。
Q3 Why are you building an Android app instead of a website?
为什么做 Android，不能用网页吗？ 建议：陈思言主答，阮立坤补充 对应 PPT：6
Android fits our intended use of reviewing and adjusting a saved build on a phone. Kotlin, Compose
and Room also fit the course implementation. A website could support a similar workflow. Our choice
gives us a focused platform for local data access and a consistent mobile interface, rather than
proving that the idea requires native Android.
中文要点：说明场景和课程实现选择即可。不要说网页无法离线、无法推送，也不要说现有工具只有电脑端。


<!-- page 3 -->

PC Buildmate / EE5415 / Q&A Preparation
3
02 Data quality and uncertainty
数据和不确定性
Q4 Where will the hardware specifications and prices come from?
硬件参数和价格从哪里来？ 建议：陈思言主答，刘喆补充 对应 PPT：11、22
For the prototype, we plan to curate a limited catalogue using manufacturer specifications, retailer
listings and community reports. We will retain the source and date, and mark compatibility fields by
verification state. Manufacturer specifications are our preferred basis for hard constraints, while
community reports remain clearly identified as reported evidence.
中文要点：回答来源、日期、证据状态。不要临时承诺已经接入电商 API、全自动爬取或覆盖全部硬件。
Q5 What happens if a specification is missing or the sources disagree?
缺参数或不同来源冲突怎么办？ 建议：陈思言主答，王嘉瑞补充 对应 PPT：9、11–12
We will keep the relevant check unverified when the available evidence is insufficient or
contradictory. The interface should show which field needs confirmation. We distinguish the evidence
state of a field from the result of a compatibility rule. A missing field must never silently turn into a
passing check.
中文要点：区分两套状态：字段证据为 verified / reported / unverified；检查结果为 pass / conflict / unverified。
reported 不自动等于已证实兼容。
Q6 Can your app guarantee that a recommended PC will work?
你们能保证推荐的电脑一定能用吗？ 建议：黄奕维主答，王嘉瑞补充 对应 PPT：12、16
We cannot guarantee complete real-world compatibility from a limited catalogue. We can explain the
supported checks and their evidence. For example, a shorter GPU can resolve the stated clearance
conflict, while BIOS support may remain unverified. Users should see both the resolved issue and the
remaining checks before purchasing.
中文要点：第 16 页可直接举例：337 mm 超过 320 mm；换成 300 mm 只证明该尺寸检查通过。不能把一个绿色
结果说成整机完全安全。


<!-- page 4 -->

PC Buildmate / EE5415 / Q&A Preparation
4
03 Recommendation and ranking
预算和替换算法
Q7 How does budget recommendation work, and is the result optimal?
预算推荐怎么做，能保证最优吗？ 建议：黄奕维主答，王嘉瑞补充 对应 PPT：12
We propose a constrained heuristic approach. The intended use guides category budgets,
compatibility rules remove unsuitable combinations, and the remaining candidates are ranked within
the available reference-price budget. This produces an editable starting point. We do not claim a
globally optimal build or exact performance prediction across every possible component combination.
中文要点：关键词：预算分配、约束过滤、候选排序。不要说背包算法已经保证最强配置，也不要捏造已确定的权重
或性能分数。
Q8 What makes the replacement suggestions intelligent? Do you use machine
learning?
智能替换的智能在哪，需要机器学习吗？ 建议：王嘉瑞主答，黄奕维补充 对应 PPT：13
In our first version, intelligence means using the current build and the detected conflict to select
relevant alternatives. We plan to filter candidates by supported constraints, then rank them by factors
such as price change and suitability for the intended use. The proposal does not require a trained
machine-learning model.
中文要点：先过滤硬约束，再做偏好排序，不能靠高分抵消不兼容。具体权重仍需实现和测试，别临时编模型、训练
数据或准确率。
Q9 What if no compatible replacement fits the budget?
预算内找不到兼容替代品怎么办？ 建议：王嘉瑞主答，黄奕维补充 对应 PPT：12–13
We should report that no suitable candidate was found in the current catalogue under the selected
constraints. We can then offer explicit choices, such as increasing the budget or reconsidering
another selected component. We must not silently exceed the budget or relax a hard constraint. Any
revised configuration needs another full check.
中文要点：这是建议采用的边界处理，不代表已完成实现。限定“当前目录和约束下无候选”，不要扩大为市场上绝
对不存在。


<!-- page 5 -->

PC Buildmate / EE5415 / Q&A Preparation
5
04 Architecture and interface
架构和界面
Q10 Why did you choose MVI rather than MVVM?
为什么选 MVI 而不是 MVVM？ 建议：阮立坤主答，黄奕维补充 对应 PPT：15
Our main reason is state consistency. One component edit affects the selected parts, total and
diagnostics, so we want one explicit flow from user action to updated state. MVI is our chosen
organization for that flow. MVVM can also support unidirectional data flow, so we are not claiming
that MVVM is inherently inconsistent.
中文要点：讲清设计动机和取舍即可。MVI 并非天然优于 MVVM，状态管理正确与否取决于具体实现。
Q11 How will you stop an old check from overwriting a newer build?
用户连续换零件，旧检查结果怎么处理？ 建议：阮立坤主答，王嘉瑞补充 对应 PPT：15
We plan to associate diagnostic results with a build revision. When a result returns, the ViewModel
should apply it only if its revision still matches the current build. Cancelling obsolete work can reduce
unnecessary processing, but the revision check protects correctness. We will test rapid edits and
delayed results together.
中文要点：用版本号解释竞态最清楚：结果属于旧配置就不应用。取消旧任务是辅助措施，不要把取消等同于一定消
除了所有竞态。
Q12 What works offline, and are the prices live?
离线能做什么，价格实时吗？ 建议：刘喆主答，阮立坤补充 对应 PPT：6、11、23
With the required records stored locally, the prototype is intended to support saved builds, local
browsing and checks based on those records. Its prices are dated references, not live retailer
quotations or stock guarantees. A price update needs a separate data refresh, and users should
confirm the current purchase price with the seller.
中文要点：本地数据可用与价格实时是两回事。不要承诺自动刷新周期、库存同步或后台更新已实现。


<!-- page 6 -->

PC Buildmate / EE5415 / Q&A Preparation
6
05 Market position and evaluation
竞品和验证
Q13 Why would users choose you over existing PC-building tools?
已有装机工具，为什么还要用你们？ 建议：刘喆主答，王嘉瑞补充 对应 PPT：18–20
Existing tools already address component selection and compatibility, so our case cannot rely on
mobile access alone. Our proposed focus is an understandable workflow with replacement previews,
field-level evidence and dated CNY reference prices. We need user testing to show whether this
combination helps our target users, rather than claiming proven superiority.
中文要点：承认已有工具的能力，再讲你们选择做深的流程。不要说独家、唯一手机端、竞品都不会检查兼容性。
Q14 How do you know users want this workflow?
你们如何证明用户真的需要这些功能？ 建议：陈思言主答，刘喆补充 对应 PPT：5、18、20
The proposal identifies a plausible problem, but it does not yet demonstrate demand. We plan to ask
target users to complete representative build tasks and explain their decisions. Their difficulties and
feedback can tell us whether the workflow addresses a real need and which steps deserve further
development.
中文要点：如已有真实访谈或测试，只补充能说明来源的结果。没有就说计划验证，不编问卷数量、满意度、下载量。
Q15 How will you measure whether the project succeeds?
怎样衡量项目是否成功？ 建议：杨壹硕主答，王嘉瑞或阮立坤补充 对应 PPT：20、23–24
We propose evaluating technical correctness and user understanding separately. A regression set
should include compatible cases, known conflicts, missing fields and rapid edits. User tasks can
measure completion and whether participants explain a conflict and replacement correctly. We would
record these results before setting claims about accuracy or time saved.
中文要点：规则测试与用户测试分开。尺寸规则可测小于、等于、大于边界；缺字段应保持未知。不要报未经测试的
百分比或节省时间。


<!-- page 7 -->

PC Buildmate / EE5415 / Q&A Preparation
7
06 Scope and delivery
范围和交付
Q16 Is the interface a working app, and what have you implemented so far?
这是运行中的 App 吗，已经做完多少？ 建议：杨壹硕主答，阮立坤说明实际代码 对应 PPT：16、23
The interface shown here is a concept prototype that illustrates the intended workflow. Its component
values and prices are demonstration data. We should distinguish that design from verified
implementation progress. For a working demonstration, we would show the actual screens and
checks that have been implemented and tested.
中文要点：这份 PPT 只能证明概念设计，无法证明代码进度。上台前按真实情况准备一句“已完成 X，正在做 Y”；
没有证据不要报完成比例。
Q17 Can you realistically finish all six features within the course?
六个功能会不会做不完？ 建议：杨壹硕主答，黄奕维补充 对应 PPT：22–23
We will control scope through a limited catalogue and a defined set of compatibility rules. The priority
is one complete path from planning to comparison. Exact FPS prediction, three-dimensional
assembly and live inventory are later work. We can expand coverage after the core modules
integrate and pass their regression checks.
中文要点：强调有限目录、有限规则、完整流程。若追问时间表，只报团队确认的里程碑；不要即兴承诺具体日期或
硬件条目数。
Q18 Who owns the implementation, and how will you integrate the modules?
谁负责哪些实现，模块如何整合？ 建议：杨壹硕主答，黄奕维补充 对应 PPT：22
Yiwei leads architecture and UI design. Zhe owns data, Jiarui owns domain rules, Likun implements
the UI, and Siyan gathers source information. I coordinate integration testing and presentation
materials. We plan to agree on shared build records and repository interfaces early, then test the
same example configuration across the modules.
中文要点：开发分工与演讲分工不同。由总结负责人杨壹硕使用这段中的 I；其他人回答时改成 Yishuo coordinates。
