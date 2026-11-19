# Defense · PPT Outline (English, 25 slides / 15 min)

> Owner: @yys1yys2yys (DEFENSE-01). Base: `docs/defense/DEMO.md` (9-step device demo) and
> `docs/19-test-evidence.md` (evidence chain). Numbers marked `[confirm: owner]` must be
> reconciled with that owner before the 11/22 checkpoint.
>
> The old 10-13 script (`PC_Buildmate_15min_Speaker_Script.md`) still frames the app as a
> concept prototype and has no 3D preview, no quantity policy and no test-evidence story.
> This outline replaces those parts; the rest of its wording can be reused.

**Deck skeleton**: [Defense_Deck.pptx](Defense_Deck.pptx) — 25 slides, 16:9, English titles and bullets,
speaker notes carrying the English paragraph to say, plus a one-line Chinese rehearsal hint (中文提示) on each content slide. It is a skeleton to fill in, not a finished
design: images, screenshots (from `qa/screenshots/`) and the owners' own wording still have to go in.

## Timing and speakers

| # | Section | Slides | Time | Speaker |
|---|---|---|---|---|
| 1 | Problem and motivation | 1–6 | 0:00–2:40 | 陈思言 |
| 2 | App vision and principles | 7–9 | 2:40–4:45 | 王嘉瑞 |
| 3 | Key features (live demo) | 10–16 | 4:45–11:20 | 黄奕维 |
| 4 | Architecture, UI and the 3D view | 17–19 | 11:20–12:50 | 阮立坤 |
| 5 | Test evidence and boundaries | 20–22 | 12:50–14:10 | 杨壹硕 |
| 6 | Limits, next steps, conclusion | 23–25 | 14:10–15:00 | 杨壹硕 |

Demo occupies slides 11–16 as a single uninterrupted live run of `docs/defense/DEMO.md`
(9 steps, 7800 → 7900, BIOS stays unverified). Slides behind the demo are the step labels,
so a failed run can fall back to a recording without losing the structure.

---

## 1 · Problem and motivation (陈思言)

**Slide 1 — Title.** PC Buildmate · EE5415 · team names.

**Slide 2 — Agenda.** Five parts: problem, vision, features, architecture, evidence.

**Slide 3 — Section cover.** Problem.

**Slide 4 — Choosing good parts is not enough.** Every part can be individually good and still
not fit: socket, memory generation, GPU length, PSU headroom, cooler height, form factor.
Six relationships, eight component categories.

**Slide 5 — Evidence is scattered.** Specs, prices and community reports disagree; a field may be
missing entirely. The user cannot tell "it fits" from "nobody checked".

**Slide 6 — Who this is for.** First-time builders and budget upgraders. Explicitly not
enthusiasts' overclocking or shop inventory management.

## 2 · Vision and principles (王嘉瑞)

**Slide 7 — Section cover.** What the app promises.

**Slide 8 — Three principles.**
1. **Evidence before conclusion** — every field carries a state: verified / reported / empty.
2. **A diagnosis belongs to a revision** — editing the build invalidates the old verdict.
3. **Replace, then recheck** — a fix is applied only after a full re-evaluation.

**Slide 9 — What we refuse to do.** No FPS or benchmark prediction, no "best build" claim,
no live prices, no account or cloud. Scope is a design decision, not an excuse.

## 3 · Key features and live demo (黄奕维)

**Slide 10 — Section cover.** Six features, demonstrated live.

**Slide 11 — Step 1–2: budget entry and workspace.** 8000 CNY, gaming; demo build loaded at
¥7,800; 337 mm GPU and 320 mm case on one screen.

**Slide 12 — Step 3–4: save A, then check.** Conflict summary, GPU length BLOCKER (337 > 320),
and BIOS **unverified** — the line that matters most: unverified is not a pass.

**Slide 13 — Step 5–7: candidate, preview, apply.** The 300 mm card at +¥100; preview shows
¥7,900 without touching the current revision; apply triggers a full recheck.

**Slide 14 — Step 8: save B and compare.** Two columns, differences highlighted, only decidable
fields compared.

**Slide 15 — Step 8.5: 3D clearance view (optional, cut first if time runs short).**
Boxes express size and clearance, not product appearance; missing height is estimated and
labelled. [confirm: 阮立坤 for the current estimated constants]

**Slide 16 — Step 9: persistence.** "Continue last build" after a process kill; diagnosis must be
re-run because it belongs to a revision.

## 4 · Architecture and UI (阮立坤)

**Slide 17 — Section cover.** How it is built.

**Slide 18 — 14 modules, contract-first.** Contracts are frozen; domain is pure JVM; UI knows only
contracts; app is the only module that assembles everything. CI enforces the dependency matrix.

**Slide 19 — The 3D view in context.** Why a clearance view and not assembly: read-only, fixed
orbit camera, no GL dependency, offline. [confirm: 阮立坤 for screenshot and §18.8 items]

## 5 · Test evidence and boundaries (杨壹硕)

**Slide 20 — Section cover.** Can you trust the green?

**Slide 21 — Three questions, four gates.**
- Are all cells really executed? Matrix 60/76/18 from real JUnit output.
- Does breaking the code turn tests red? 12/12 mutations killed, in CI.
- Are the tests just restating the implementation? An independent oracle (contracts-only,
  forbidden from importing domain code) agrees on 60+7 fixtures and all 76 state-machine cells.
- Will it drift? A check fails if any module with tests is missing from the CI task list.

**Slide 22 — What this does not prove.** Rule parameters, price accuracy, **field-level source URLs
(0 of 168 fields; only record-level sources exist)**, 3D slot coordinates (the field now exists, the view does not read it yet), UI accessibility, and Room on pull requests. Say it before the examiner asks.

## 6 · Limits and conclusion (杨壹硕)

**Slide 23 — Known limits.** Static catalogue with dated reference prices; motherboard x16 slot
position not yet in the catalogue (3D slot uses a constant); no real-device automation of gestures.

**Slide 24 — Next steps.** Signed release with checksum, device evidence ×3, official-store price
adapters, then feature freeze on 11/28.

**Slide 25 — Closing.** "The weakest evidence decides the conclusion." Thank you; invite questions.

---

## Appendix (not counted in the 15 minutes)

| Slide | Content | When to jump |
|---|---|---|
| A1/A2 | The 15 rules with their kinds | "How do you organise the rules?" |
| B | Evidence at a glance (60/76/18, 12/12, oracle, Room, three runs) | "Why should we trust the checks?" |
| C | Scope boundaries (no FPS/prices/accounts/3D assembly) | Any request for something out of scope |
| D | Data provenance: 30 records, 27 URLs, 0/168 field-level sources | "Where does the data come from?" |

Every appendix answer is one line; the detail lives in `docs/03`, `docs/19` and `docs/21`.

## Delivery notes

- English only on slides and speech; Chinese only in rehearsal notes.
- Never claim: guaranteed compatibility, real-time or lowest price, optimal build, AI, accuracy
  percentages, FPS or performance bands.
- If the device demo fails: switch to the recording, keep the same narration, announce it plainly.
- Questions to expect first: BIOS unverified, why not 3D assembly, where the prices come from,
  how the tests avoid restating the implementation.
