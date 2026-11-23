# Defense · Run sheet (one page, print this)

> Day plan for the 15-minute defence. Numbers are the confirmed ones in
> [Defense_Numbers_To_Confirm.md](Defense_Numbers_To_Confirm.md).
> Demo steps: `docs/defense/DEMO.md`. Device run has been rehearsed three times
> (`docs/21 §21.2`).

## Before going on stage (T-10 min)

- [ ] Tablet charged, **airplane mode ON**, rotation locked, brightness fixed
- [ ] App data cleared, then load the demo build once so "继续上次配置" is not empty
  (or deliberately clear it — decide now, not on stage)
- [ ] Screen mirroring connected; test one tap and check the projector
- [ ] Recording of the full nine steps open in a second window (**fallback**)
- [ ] Slides at page 1; clicker works; timer visible

## Timeline

| Clock | Who | What | Handoff line into next |
|---|---|---|---|
| 0:00–2:40 | 陈思言 | Problem: parts are individually fine, together they are not; evidence is scattered | "…so what should the app actually promise?" |
| 2:40–4:45 | 王嘉瑞 | Three principles: evidence before conclusion, diagnosis belongs to a revision, replace then recheck | "Let me show you what that looks like on a device." |
| 4:45–11:20 | 黄奕维 | **Live demo, nine steps** (below) | "…and that is why the next slide is about how it is built." |
| 11:20–12:50 | 阮立坤 | 14 modules, frozen contracts, why a clearance view and not 3D assembly | "Which raises the question: can you trust any of this?" |
| 12:50–14:10 | 杨壹硕 | Four gates, then what none of it proves | "So what do we still not have?" |
| 14:10–15:00 | 杨壹硕 | Limits, next steps, closing line | "Thank you — questions welcome." |

## Demo cue card (nine steps, say the bold part)

1. Entry · 8000 / 游戏 → **"recommendation: eight categories, case and cooler included, about 7,900"** → load demo **"anchor build: 7,800"**
2. Workspace · **"337 mm card, 320 mm case, on one screen"**
3. Save A · **"A is the baseline"**
4. Check · **"score 52; the length is a blocker; the BIOS row is unverified — unverified is not a pass"**
5. Candidates · **"rank one, 300 mm, plus 100"**
6. Preview · **"7,900, and the current revision has not changed"**
7. Apply · **"full recheck — one fixed conflict does not mean the build is compatible"**
8. Save B + compare · **"A 7,800 and B 7,900, only decidable fields compared"**
9. Continue last build · **"persistence; and the diagnosis must be re-run because it belongs to a revision"**

If anything freezes: stop talking, say "let me use the recording", switch, keep the same narration.

## Numbers you may say / must not say

Say: 15 rules · 60/76/18 cells · 12/12 mutations · 8 Room contracts · 7,800 → 7,900 · 52 · arm64-only, no network permission.

Never say: guaranteed compatible · real-time or lowest price · optimal build · AI/ML · accuracy %, user counts · FPS/benchmarks.

## Q&A first three (most likely)

- **"Why is BIOS unverified?"** The catalogue has no `biosGeneration`; we do not guess.
- **"Are the tests just restating the implementation?"** Independent oracle, contracts-only, forbidden to import domain code; inverting it turns the cross-check red; 12 mutations killed in CI.
- **"3D assembly?"** Deliberately out: read-only clearance view, fixed camera, no GL, no external model.
- **"Can I trace each spec to a source?"** Record level: all 30 records are in `SOURCES.md` (27 with a URL).
  Field level is 0 of 168 today — say that plainly rather than implying per-field citations.

Fallbacks: "We have not measured that — here is what we can show instead." / "That is deliberately outside this version."
