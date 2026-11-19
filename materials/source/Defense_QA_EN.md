# Defense · Q&A (English, 18 questions)

> Supersedes the "concept prototype" answers in `PC_Buildmate_QA_Preparation.md` (10-13).
> Keep the old file for topic coverage; use these answers for facts about what is now built.
> Answer pattern: conclusion first, then one reason or example. 20–35 s each.
> Anything marked `[confirm: owner]` is pending that owner's number.

## Product and scope

**Q1. What problem does PC Buildmate solve?**
It tells a builder whether the chosen parts actually fit together, and what to change when they do not.
The output is not a score: every rule shows its own verdict and the evidence behind it, so a missing
specification stays visible instead of silently passing.
中文要点：不是打分，是把“哪条规则、依据什么”摊开；缺字段就说不确定。

**Q2. Who is the primary user?**
First-time builders and people upgrading on a fixed budget. Enthusiasts and small shops are secondary;
we deliberately did not design for them first.
中文要点：新手 + 固定预算升级者；明确不面向发烧友/小店。

**Q3. Why Android rather than a web app?**
The task is reviewing and adjusting a saved build on a phone, offline. Kotlin, Compose and Room also
fit the course constraints. A web app could do something similar; we are not claiming Android is
required.
中文要点：手机上看和改已保存配置、离线；不要说“网页做不到”。

**Q4. Where do specifications and prices come from?**
A static, versioned catalogue shipped with the app; every price carries a snapshot date. Source
traceability is at **record level** today: all 30 records are listed in `assets/catalogue/SOURCES.md`,
27 of them with a URL. Field-level source URLs are **not yet populated** (0 of 168 fields), so we do
not claim per-field provenance. We do not scrape live prices.
中文要点：静态目录 + 带日期的参考价；记录级有来源（30/30，27 带 URL），字段级 0/168，不声称逐字段可溯源。

## Evidence and correctness

**Q5. What if a field is missing or sources disagree?**
A field carries one of three states: verified, reported, or empty. Any rule touching an empty field
returns "unverified", and any rule touching conflicting sources returns "source conflict" — neither is
treated as a pass. This is why the BIOS check in the demo stays unverified.
中文要点：三态；缺字段=未验证、来源冲突=未验证，都不算通过。

**Q6. Can you guarantee a build will work?**
No, and we do not claim it. We guarantee only what the rules can decide, with the weakest evidence
propagating to the conclusion.
中文要点：不保证；只保证规则能判定的部分，最弱证据决定结论。

**Q7. Is the budget recommendation optimal?**
No. It is a heuristic with visible weights: within budget, no blockers, and the allocation is shown so a
user can argue with it. "Optimal" would require a definition of best we deliberately refuse.
中文要点：启发式、权重可见；不是最优，也不承诺最优。

**Q8. What is "intelligent" about the replacement suggestions? Do you use machine learning?**
No machine learning. Candidates are filtered by hard constraints — the target conflict must disappear
and no new blocker may appear — then ranked by price delta, number of affected rules, and id.
中文要点：没有 ML；先过硬约束过滤，再按价格差/影响规则数/ID 排序。

**Q9. What if no replacement fits the budget?**
The result is a structured reason: which constraint blocks it and a suggested direction, not an empty
list. MUT-08 exists precisely to keep that behaviour alive.
中文要点：给结构化原因（阻塞约束 + 方向），不是空列表；MUT-08 守着这个行为。

## Engineering

**Q10. Why MVI and explicit state machines?**
Because the product promise is about stale results. Each screen transition is a pure function returning
the next state, the effects, and an optional rejection; 76 state-machine cells are tested from a table,
so "an old diagnosis overwrote a new build" is a test failure, not a race.
中文要点：为了让“旧诊断覆盖新配置”变成测试失败而不是竞态；纯函数 + 76 格。

**Q11. How do you stop an old check from overwriting a newer build?**
A diagnosis carries the revision id it belongs to. The UI exposes a diagnosis only when the ids match;
otherwise the screen shows "please recheck". That accessor is covered by unit tests and by Compose
semantics tests on the workspace and diagnostics screens.
中文要点：诊断带 revisionId，不匹配就不返回；单元测试 + Compose 语义测试都在守。

**Q12. What works offline, and are prices live?**
Everything works offline. Prices are static reference values with dates; there is no network permission
in the manifest, so a live price feed is not merely unimplemented, it is impossible in this build.
中文要点：全部离线；manifest 没有 INTERNET 权限，所以“没有实时价”是结构性的。

**Q13. Why choose this over existing PC-building tools?**
Existing tools mostly answer "does it fit". We answer "how do you know": three evidence states per field,
diagnoses bound to revisions, replacement only after a full recheck, and a test evidence chain — an
independent oracle plus mutation testing — that a reviewer can re-run.
中文要点：别人答“能不能装”，我们答“凭什么这么判断”——三态、revision 绑定、重检、可复跑的测试证据。

**Q14. Do you know users want this workflow?**
No user study yet; that is an honest gap. The workflow comes from the proposal and from analysing what
first-time builders get wrong, and it is the part we would validate first with real users.
中文要点：还没有用户研究，如实说；这是最该先验证的部分。

**Q15. How do you measure success?**
By things that can be checked: the 60 rule cells, 76 state-machine cells and 18 invariants generated from
real test output; 12 mutations killed in CI; the oracle agreeing with the production engine; and the
nine-step demo running three times on a device with identical numbers. Not by accuracy percentages or
user counts.
中文要点：用可检查的证据衡量（矩阵/变异/oracle/真机三次），不用准确率或用户数。

**Q16. Is the interface a working app, and what is implemented?**
It is a working app: budget entry and recommendation, workspace editing, compatibility check with three
evidence states, replacement preview and recheck, saved builds in Room, comparison, and a 3D clearance
view. Interface copy and accessibility are still being polished [confirm: 阮立坤].
中文要点：是可运行的应用；列功能，别用“原型”这个词。

**Q17. Can you finish all six features in the course?**
The six features are implemented and frozen on 11/28; what remains is release signing, defence material
and data accuracy. If something slips, the 3D view is the first thing we cut — it is a visual aid, not a
rule.
中文要点：六项已实现，11/28 冻结；真要砍先砍 3D。

**Q18. Who owns what, and how do you integrate?**
Fourteen Gradle modules with frozen contracts: domain is pure JVM, UI knows only contracts, and the app
module is the only one that assembles everything. Ownership follows modules, and CI enforces the
dependency matrix — a module importing something it should not fails the build.
中文要点：14 模块 + 冻结契约；依赖矩阵由 CI 强制。

---

## Lines never to say

- guaranteed compatibility / "it will definitely work"
- real-time or lowest price
- "optimal build"
- AI / machine-learning recommendation
- accuracy percentages, user counts, completion percentages
- FPS, benchmark scores, performance bands

## Fallback phrasings

- Don't know: "We have not measured that; here is what we can show instead."
- Out of scope: "That is deliberately outside this version — the boundary is documented in `docs/09`."
- Handing over: "My teammate can explain that part in more detail."
