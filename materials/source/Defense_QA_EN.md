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

**Q2. Who is the primary user?**
First-time builders and people upgrading on a fixed budget. Enthusiasts and small shops are secondary;
we deliberately did not design for them first.

**Q3. Why Android rather than a web app?**
The task is reviewing and adjusting a saved build on a phone, offline. Kotlin, Compose and Room also
fit the course constraints. A web app could do something similar; we are not claiming Android is
required.

**Q4. Where do specifications and prices come from?**
A static, versioned catalogue shipped with the app; every price carries a snapshot date, and sources
are listed in `assets/catalogue/SOURCES.md`. Prices are being reconciled against official store pages
[confirm: 陈思言]; we do not scrape live prices.

## Evidence and correctness

**Q5. What if a field is missing or sources disagree?**
A field carries one of three states: verified, reported, or empty. Any rule touching an empty field
returns "unverified", and any rule touching conflicting sources returns "source conflict" — neither is
treated as a pass. This is why the BIOS check in the demo stays unverified.

**Q6. Can you guarantee a build will work?**
No, and we do not claim it. We guarantee only what the rules can decide, with the weakest evidence
propagating to the conclusion.

**Q7. Is the budget recommendation optimal?**
No. It is a heuristic with visible weights: within budget, no blockers, and the allocation is shown so a
user can argue with it. "Optimal" would require a definition of best we deliberately refuse.

**Q8. What is "intelligent" about the replacement suggestions? Do you use machine learning?**
No machine learning. Candidates are filtered by hard constraints — the target conflict must disappear
and no new blocker may appear — then ranked by price delta, number of affected rules, and id.

**Q9. What if no replacement fits the budget?**
The result is a structured reason: which constraint blocks it and a suggested direction, not an empty
list. MUT-08 exists precisely to keep that behaviour alive.

## Engineering

**Q10. Why MVI and explicit state machines?**
Because the product promise is about stale results. Each screen transition is a pure function returning
the next state, the effects, and an optional rejection; 76 state-machine cells are tested from a table,
so "an old diagnosis overwrote a new build" is a test failure, not a race.

**Q11. How do you stop an old check from overwriting a newer build?**
A diagnosis carries the revision id it belongs to. The UI exposes a diagnosis only when the ids match;
otherwise the screen shows "please recheck". That accessor is covered by unit tests and by Compose
semantics tests on the workspace and diagnostics screens.

**Q12. What works offline, and are prices live?**
Everything works offline. Prices are static reference values with dates; there is no network permission
in the manifest, so a live price feed is not merely unimplemented, it is impossible in this build.

**Q13. Why choose this over existing PC-building tools?**
Existing tools mostly answer "does it fit". We answer "how do you know": three evidence states per field,
diagnoses bound to revisions, replacement only after a full recheck, and a test evidence chain — an
independent oracle plus mutation testing — that a reviewer can re-run.

**Q14. Do you know users want this workflow?**
No user study yet; that is an honest gap. The workflow comes from the proposal and from analysing what
first-time builders get wrong, and it is the part we would validate first with real users.

**Q15. How do you measure success?**
By things that can be checked: the 60 rule cells, 76 state-machine cells and 18 invariants generated from
real test output; 12 mutations killed in CI; the oracle agreeing with the production engine; and the
nine-step demo running three times on a device with identical numbers. Not by accuracy percentages or
user counts.

**Q16. Is the interface a working app, and what is implemented?**
It is a working app: budget entry and recommendation, workspace editing, compatibility check with three
evidence states, replacement preview and recheck, saved builds in Room, comparison, and a 3D clearance
view. Interface copy and accessibility are still being polished [confirm: 阮立坤].

**Q17. Can you finish all six features in the course?**
The six features are implemented and frozen on 11/28; what remains is release signing, defence material
and data accuracy. If something slips, the 3D view is the first thing we cut — it is a visual aid, not a
rule.

**Q18. Who owns what, and how do you integrate?**
Fourteen Gradle modules with frozen contracts: domain is pure JVM, UI knows only contracts, and the app
module is the only one that assembles everything. Ownership follows modules, and CI enforces the
dependency matrix — a module importing something it should not fails the build.

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
