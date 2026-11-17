# Defense · Speaker script — sections 1–4 (draft)

> **Draft, written by 杨壹硕 for the owners to edit.** Each section names its owner; they can keep,
> rewrite or cut freely. Numbers follow `Defense_Numbers_To_Confirm.md` (confirmed ones only).
> Timings follow `Defense_PPT_Outline.md`. Bracketed Chinese lines are rehearsal notes, not spoken.

---

## Section 1 · Problem and motivation — 陈思言 (0:00–2:40)

**S1 · Title (20 s)**
> "Good morning. We are PC Buildmate. We help someone planning a custom PC check whether the parts
> actually fit together, understand what to change when they do not, and compare two builds before
> spending money."

**S2 · Agenda (10 s)**
> "Five parts: the problem, our three principles, a live demo, the architecture behind it, and the
> evidence that it works — including what it does not prove."

**S3 · Cover (5 s)** — "Let us start with the decision a builder faces."

**S4 · Choosing good parts is not enough (50 s)**
> "Every part here can be individually good and still not work together. The processor needs a
> matching socket. Memory must match the board's generation. A graphics card has to physically fit
> the case, and the power supply has to cover the load. That is six relationships across eight
> component categories. One mismatch means a replacement part, a delayed build, or a build that does
> not start."

**S5 · Evidence is scattered (35 s)**
> "The harder problem is that the information itself is unreliable. Specs, prices and community
> reports come from different places and disagree — and often a field is simply missing. A user
> cannot tell 'it fits' from 'nobody checked'. So the app does not just answer yes or no; it shows
> the evidence behind each answer."

**S6 · Who this is for (35 s)**
> "Our primary users are first-time builders and people upgrading on a fixed budget. Enthusiasts and
> small shops may benefit later, but the first version is not designed for overclocking or for shop
> inventory. That focus is deliberate."

（中文提示：语速放慢，最后一句"deliberate"停一下，把重点交给下一位。）

---

## Section 2 · Vision and principles — 王嘉瑞 (2:40–4:45)

**S7 · Cover (5 s)** — "So what does the app promise?"

**S8 · Three principles (70 s)**
> "Three principles, and each one shows up in the demo you are about to see."
> "First, evidence before conclusion. Every field carries a state: verified, reported, or empty. If a
> field is missing, the rule involving it cannot pass — it becomes 'unverified'. Unverified is not a
> pass."
> "Second, a diagnosis belongs to a revision. If you edit the build, the old verdict is no longer
> about what is on screen, so the app stops showing it and asks you to recheck."
> "Third, replace and recheck. A suggested part is not applied directly: you preview it, then the
> app re-runs every rule, and only then is it applied."

**S9 · What we refuse to do (35 s)**
> "We also decided what not to build: no FPS or benchmark prediction, no claim of an optimal build,
> no live prices, no account or cloud. Choosing a narrow scope is what makes the remaining claims
> trustworthy."

（中文提示：三条原则要各给一句"为什么"，不要只念名字。）

---

## Section 3 · Key features and live demo — 黄奕维 (4:45–11:20)

**S10 · Cover (5 s)** — "Let me show you what that looks like on a device."

**S11 · Steps 1–2 (60 s)**
> "Budget 8,000, use case gaming. The recommendation gives six categories with the weights shown —
> about 7,869 in total. I will use our fixed demo build instead, so the numbers are the same every
> time: 7,800. Notice the graphics card is 337 millimetres and the case allows 320. Both are on
> screen, and nothing is red yet — because nobody has checked."

**S12 · Steps 3–4 (75 s)**
> "Save this as A, our baseline. Now check. Score 52, one blocker, one major, and one unverified.
> The blocker is the length: 337 against 320. The unverified one is BIOS support — the catalogue has
> no BIOS generation for this board, so the app refuses to guess. That line is the most important on
> the screen: a missing field is not a pass."

**S13 · Steps 5–7 (90 s)**
> "I click the blocker. Candidates are filtered first: anything that leaves this conflict alive, or
> introduces a new blocker, is removed. What remains is ranked by price difference, then by how many
> other rules it affects. Rank one: a 300-millimetre card, plus 100."
> "Preview shows 7,900 — and the current build has not changed. Apply runs every rule again. That is
> the point of the third principle: fixing one conflict does not mean the build is compatible."

**S14 · Step 8 (50 s)**
> "Save this as B and compare. A is 7,800 with a conflict; B is 7,900 with one unverified item. Only
> fields the rules can decide are compared — we do not show a performance prediction next to them."

**S15 · Step 8.5 — 3D clearance (optional, 40 s)**
> "If time allows: the same 337-millimetre card inside the 320-millimetre space, drawn as boxes. This
> is a size visualisation, not a product model — it answers 'does it fit', not 'how do I build it'.
> Where a dimension is missing we estimate it and say so."

**S16 · Step 9 (30 s)**
> "Close the app, reopen it: 'continue last build' brings the build back. The diagnosis is not
> restored, because it belonged to an earlier revision — the build has to be checked again."

（中文提示：演示时把"未验证"那一行指出来，它是全场最关键的证据点。）

---

## Section 4 · Architecture and UI — 阮立坤 (11:20–12:50)

**S17 · Cover (5 s)** — "Which raises the question: how is this built?"

**S18 · 14 modules (60 s)**
> "Fourteen Gradle modules with a frozen contract layer. The domain modules are pure JVM — no
> Android, no database, no clock. The UI module knows only the contracts, not the feature layer. The
> app module is the only one allowed to assemble everything. The dependency rules are enforced by a
> script in CI, so a wrong import fails the build rather than being caught in review."

**S19 · Why a clearance view (50 s)**
> "The 3D view is deliberately limited. It is read-only: you cannot drag parts or route cables. The
> camera is a fixed orbit. It runs on Compose Canvas with no GL dependency and no external model, so
> it works offline and stays small. Its job is to make one rule — the one that blocks this build —
> obvious at a glance."

（中文提示：如果被问"为什么不做完整 3D"，直接引用 §9.1 的范围外声明。）

---

## Handover lines

- 陈思言 → 王嘉瑞: "…and that is why the app promises something narrower than it first seems."
- 王嘉瑞 → 黄奕维: "Let me show you what that looks like on a device."
- 黄奕维 → 阮立坤: "…which raises the question of how it is built."
- 阮立坤 → 杨壹硕: "All of that is only useful if you can trust it."
