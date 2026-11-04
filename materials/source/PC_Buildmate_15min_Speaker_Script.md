# PC_Buildmate_15min_Speaker_Script


<!-- page 1 -->

PC Buildmate / EE5415 / Presentation Script
1
PC Buildmate Presentation Script
EE5415 英文分人讲稿 约 15 分钟
本稿与优化后的 25 页 PPT 一一对应。英文正文可以直接口述；中文提示、页码和用时用于排练，不需要念出。每位
成员独立成页，PPT 演讲者备注中也有同样的正文。
讲者
讲解模块
PPT 页码
时间段
陈思言
Problem and Motivation
1–6
00:00–02:40
王嘉瑞
App Vision
7–9
02:40–04:45
黄奕维
Key Features
10–13
04:45–07:55
阮立坤
Architecture and UI
14–16
07:55–10:40
刘喆
Market Position
17–20
10:40–13:05
杨壹硕
Conclusion
21–25
13:05–15:00
排练说明
英文正文共约 1728 词。时间表已包含章节封面、换页和交接，按每分钟约 125–135 词练习，最终以全组计时为准。
15 分钟按正式讲解安排，未另计问答时间。
第 3、7、10、14、17、21 页是章节封面，约 5 秒带过即可。第 16 页的零件和价格为概念数据：337 mm 显卡超过
 320 mm 机箱限制；换成 300 mm 显卡后，总价由 CNY 7800 增至 CNY 7900，BIOS 支持仍为未验证。
开发分工和上台分工不同：黄奕维负责架构与 UI 设计统筹，刘喆负责数据层，王嘉瑞负责领域层，阮立坤负责 UI 实
现，陈思言负责信息收集，杨壹硕负责测试整合和汇报材料。台上按上表讲解。


<!-- page 2 -->

PC Buildmate / EE5415 / Presentation Script
2
01 Problem and Motivation
陈思言 Chen Siyan 00:00–02:40
开场后自然进入问题。第 3 页章节封面快速带过。重点讲清楚部件之间的约束。
Slide 01 PC Buildmate (20 s)
Good morning, everyone. We are presenting PC Buildmate, our EE5415 Android app proposal. It helps
people plan a custom PC, check supported compatibility rules, and compare alternatives before buying. I
am Chen Siyan, and I will introduce the problem.
Slide 02 Contents (10 s)
We will cover the problem, our app vision, six key features, architecture and interface, market position, and
finally our development plan and conclusion.
Slide 03 Problem and Motivation (5 s)
Let us begin with the decisions a builder faces.
Slide 04 One Wrong Choice Is Expensive (50 s)
Choosing good parts individually does not ensure that they work together. A processor needs a compatible
motherboard. Memory must match the supported generation. A graphics card must fit inside the case, and
the power supply must meet the build's requirements. These relationships cover eight component
categories, including storage and cooling. Meanwhile, specifications, prices and community reports come
from different places and may disagree. A single mismatch can mean buying a replacement or delaying
assembly. Our motivation is to make these constraints visible before purchase, with an explanation of the
evidence behind each check.
Slide 05 Who Has This Problem (35 s)
Our main users are first-time builders and people upgrading within a fixed budget. A first-time builder needs
a clear starting point and understandable explanations. An upgrader needs to check a new part against the
components they already own. Enthusiasts and small PC shops may also benefit from saved
configurations and structured comparisons. For the course project, however, we will focus our evaluation
on the first two groups, so the prototype has a clear target.
Slide 06 Why an Android App (40 s)
Android suits this use because users may review a build at home, discuss it with friends, or adjust it in a
shop. Kotlin and Jetpack Compose support our proposed interface, while Room can keep a selected
catalogue and saved builds available locally. Local access has limits: a saved price may be old, so we will
display its source and date. Our first version concentrates on this checking workflow. Wang Jiarui will now
explain how the features connect from the user's perspective.


<!-- page 3 -->

PC Buildmate / EE5415 / Presentation Script
3
02 App Vision
王嘉瑞 Wang Jiarui 02:40–04:45
第 8 页指着五个步骤讲流程；第 9 页强调用户能理解依据。不要逐条机械读屏幕。
Slide 07 App Vision (5 s)
Thank you, Siyan. I will explain our app vision.
Slide 08 One Repeated Journey (65 s)
Our vision is to turn a budget and a use case into an editable PC configuration with clear checks. Imagine a
student with an eight-thousand-yuan budget who mainly wants to play games. First, the planning step
proposes a starting build. The student can then customize it, perhaps replacing the graphics card or
choosing a smaller case. The checking step evaluates the supported rules using actual specifications. If a
conflict appears, the resolution step offers ranked alternatives with their price changes. The student
previews a replacement and checks the updated build before applying it. Finally, two or three saved
options can be compared using the same fields. All these steps work on one configuration. Whenever the
user changes a part, the relevant totals and checks must change with it.
Slide 09 What Changes (55 s)
The intended improvement is that the user can understand the decision. They should see where a
specification came from and whether it is verified, reported or still unverified. A missing BIOS detail, for
example, should remain visible as an unknown instead of becoming a pass. Replacement suggestions
should show what changes and how much it costs, while keeping the user's choice reversible through
saved alternatives. We are proposing a workflow that helps people reason about a build. Its usefulness still
needs to be tested with users. This gives us a clear design principle: every recommendation should
connect to understandable data and checks. Huang Yiwei will now explain the six features that support this
vision.


<!-- page 4 -->

PC Buildmate / EE5415 / Presentation Script
4
03 Key Features
黄奕维 Huang Yiwei 04:45–07:55
三页各讲两个功能。兼容性先过滤，之后再排序。预算推荐不承诺数学上的全局最优。
Slide 10 Key Features (5 s)
Thanks, Jiarui. Our proposal contains six connected features.
Slide 11 Hardware Database and DIY Configuration (55 s)
The first feature is the hardware database. It stores component specifications, dated reference prices, and
verification states for compatibility fields. A relational model gives each component a consistent record and
links it to its sources. This is the foundation for reliable checks. The second feature is DIY configuration.
Users can select parts, replace them, duplicate a build and save alternatives. Each edit creates an updated
build revision, so the total and diagnostic results refer to the same selection. Together, these features let
users keep control of their configuration while the app manages the relationships between records.
Slide 12 Compatibility Checking and Budget Planning (70 s)
The third feature is compatibility checking. We will express supported constraints as explicit rules, such as
matching CPU sockets, checking memory type and comparing GPU length with case clearance. Each
result should explain whether the check passes, finds a conflict, or lacks enough evidence. This is
especially important when data is missing. The fourth feature is budget recommendation. We will allocate
category budgets according to the intended use, filter incompatible choices, and rank feasible
configurations from the catalogue. For gaming, for example, the graphics card may receive more budget
than it would for basic office work. This is a constrained heuristic approach. We do not claim that it always
finds the globally best configuration. The user receives a practical starting point and can edit the result.
Slide 13 Replacement Suggestions and Build Comparison (60 s)
The fifth feature provides replacement suggestions after a conflict. The system first filters candidates
against the supported constraints, then ranks suitable options using factors such as price and intended
use. A preview shows the cost difference, and the full build is checked again before the change is
accepted. The sixth feature compares saved builds. We will use matching units and fields for total price,
check results, key specifications and upgrade options. Estimates and unknowns remain clearly labelled.
Exact game frame-rate prediction is outside the first release. These two features help users move from
identifying a problem to choosing an alternative. Ruan Likun will now show how the architecture and
interface support that process.


<!-- page 5 -->

PC Buildmate / EE5415 / Presentation Script
5
04 Architecture and UI
阮立坤 Ruan Likun 07:55–10:40
MVI 字母分开读。第 15 页按数据流解释，第 16 页按四屏顺序指图。说明数字是演示数据。
Slide 14 Architecture and UI Approach (5 s)
Thank you, Yiwei. I will explain the implementation approach.
Slide 15 MVI Unidirectional Data Flow (85 s)
We propose MVI for the main configuration and diagnostic flow because one edit affects several outputs. If
a user replaces the GPU, the selected part, total price and compatibility results must all refer to the same
build. We will hold these values in one immutable BuildUiState. The Compose interface sends an intent,
such as ReplaceComponent, to the ViewModel. The ViewModel calls the domain logic and repository, then
publishes an updated state through StateFlow. Results carry the build revision, so an older check cannot
overwrite a newer edit. The three layers have clear responsibilities. The UI layer handles screens and user
actions. The domain layer contains the compatibility rules and replacement ranking in Kotlin. The data layer
provides Room records and repository access. MVI is our chosen organization for this state-sensitive flow.
Unidirectional data flow is also possible with MVVM, and we can keep secondary screens simpler. We will
test rapid edits as well as ordinary checks to verify state consistency.
Slide 16 One Path Four Screens (75 s)
The prototype has four screens. Plan Your Build collects the budget and intended use. The workspace
shows the selected parts and total. Diagnostics explains conflicts and possible replacements. Comparison
shows saved alternatives side by side. Here is an illustrative example. Build A costs seven thousand eight
hundred yuan. Its graphics card is three hundred and thirty-seven millimetres long, but the case supports
only three hundred and twenty. The app suggests a three-hundred-millimetre alternative for one hundred
yuan more. Build B therefore costs seven thousand nine hundred yuan, still within the eight-thousand-yuan
budget. The clearance check now passes, but BIOS support remains unverified in both builds. Resolving
one conflict does not prove every aspect of compatibility. These are concept data, not live product prices.
Liu Zhe will now discuss the market position.


<!-- page 6 -->

PC Buildmate / EE5415 / Presentation Script
6
05 Market Position
刘喆 Liu Zhe 10:40–13:05
承认已有竞品能力，尤其 BuildCores 已有手机端。强调项目的设计重点和后续验证，不声称独家。
Slide 17 Market Position (5 s)
Thanks, Likun. I will explain where the project fits.
Slide 18 Positioning (35 s)
PC Buildmate targets first-time and budget-conscious builders using Android. Our project focuses on what
happens after parts are selected: understanding the checks, reviewing replacement costs and comparing
saved alternatives. We want users to see both the evidence and the remaining unknowns. This positioning
gives the course project a manageable scope. It is a proposed value proposition, and user testing will
determine whether the workflow is actually easier to understand.
Slide 19 Competitive Landscape (55 s)
We should recognize the capabilities of existing products. PCPartPicker is a reference for component
selection, compatibility checking and price comparison. BuildCores also offers compatibility features,
pricing and three-dimensional visualization, with access through the web, Android and iOS. So mobile
access alone is not a unique advantage. Our prototype will concentrate on explainable replacement
previews, evidence attached to individual fields, and dated reference prices in Chinese yuan. These are
design priorities, rather than claims that competitors lack every similar feature. We will evaluate whether
combining them in our chosen workflow helps a first-time builder understand a conflict and select a suitable
alternative.
Slide 20 Product Focus and Validation (50 s)
The six focus areas on this slide connect the product experience to that positioning. They include a
compact Android workflow, consistent comparisons, and transparent local reference data. The next step is
validation. For usability, we can ask a user to complete a build and explain why a suggested replacement is
suitable. For technical quality, we will test supported rules, missing fields and rapid changes to a
configuration. Commercial options, such as disclosed referrals or tools for small shops, remain future
possibilities. They depend on evidence of demand and are outside our first-release implementation. Yang
Yishuo will now summarize the development ownership and project scope.


<!-- page 7 -->

PC Buildmate / EE5415 / Presentation Script
7
06 Conclusion
杨壹硕 Yang Yishuo 13:05–15:00
开发分工与上台分工不同，不要逐字重复六个人名。第 24 页完整收束，第 25 页致谢并进入提问。
Slide 21 Conclusion (5 s)
Thank you, Zhe. I will conclude our proposal.
Slide 22 Work Allocation (25 s)
This slide shows our development ownership, which differs from today's speaking order. Yiwei leads the
architecture and UI design. The other modules cover data, domain rules, interface implementation and
information gathering. I will coordinate integration testing, the regression set and presentation materials, so
the separate modules can be evaluated as one complete workflow.
Slide 23 Release Scope (35 s)
For the first release, we plan to connect all six features in an Android prototype. A user should be able to
start with a budget or selected parts, inspect supported checks, preview a replacement and compare saved
options. Prices will be dated references. Exact benchmark prediction, three-dimensional assembly, live
inventory and second-hand pricing remain later work. Keeping these boundaries clear gives us more time
to implement and test the core process.
Slide 24 Conclusion (45 s)
To conclude, the problem is that PC parts depend on each other while the supporting information is
scattered. PC Buildmate brings the relevant decisions into an editable mobile workflow. Its six features
share a consistent build state, supported by three implementation layers and a four-screen interface. Our
next task is to prove that the rules behave correctly and that users understand their results. A successful
prototype should let a first-time builder identify a conflict, review a suitable replacement and recognize any
remaining unknowns. That is the outcome our team will work toward.
Slide 25 Thank You (5 s)
Thank you for listening. We welcome your questions.
