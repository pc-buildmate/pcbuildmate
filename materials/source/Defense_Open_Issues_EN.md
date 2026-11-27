# Defense · Open issues and how to answer if asked

> Snapshot 2026-11-27, main `72146ad`. **Two** issues are open: the scope-change proposal (#175) and a
> tech-debt item about a legacy prototype asset (#197). Six were closed since 11-19.
> For each: does it affect what the demo shows, and what to say if an examiner asks. Answers stay inside the
> evidence rules — no claim beyond what the repo can show.

| Issue | What it is | Does the demo show it? | If asked, say |
|---|---|---|---|
| **#175** proposal: optional catalogue updates via GitHub Pages | A proposal for an optional network update channel — `manifest.json` plus immutable versioned catalogue files. It is **blocked by design**: `AGENTS.md` A4 and `docs/09 §9.1` forbid network permission and any HTTP client, and `verify-release.sh` asserts the release manifest has no `INTERNET` | No. The demo and the release stay fully offline | "That is a proposal, not shipped behaviour. Our scope rules forbid network permission and `qa/verify-release.sh` asserts the release has none. Changing that boundary is a human decision, not something we slipped in." |
| **#197** legacy `demo-v1.json` out of sync with the generator | A prototype asset used by early tests still has 32 records and predates the two PCIe fields; `tools/gen-fixtures.mjs` would now produce 16. The **shipped** catalogue and the fixtures the app and tests load are generated and consistent | No. It is not loaded at runtime | "That file is a leftover from the first prototype and is not part of the shipped catalogue. The shipped data is generated and audited; cleaning the legacy asset is tracked as tech debt." |

## Closed since 11-19 (answer as fixed, with the PR number)

| Issue | Fixed by | One line if asked |
|---|---|---|
| **#149** one motherboard lacked the two PCIe fields | #196 | "All six boards now record the x16 slot position and slot count; the last board's slot position is a standard-layout reference value, and the slot count is from the vendor's spec page." |
| **#122** cooler mount coverage | #196 | "Both coolers' mount lists now include LGA1851 and AM5, sourced from the manufacturers, so the cooler rule decides on all four sockets instead of returning unverified." |
| **#119** two stale rows in `SOURCES.md` | #179 | "The two demo-only rows moved out of the source table into a footnote; the table is 28 rows, all matching the catalogue, and record-level sources remain 30/30, 27 with a URL." |
| **#104** lint: missing launcher icon / backup rules | #177 | "Adaptive launcher icon and backup rules shipped; the remaining two lint items are disabled with a recorded reason." |
| **#123** 3D view looked empty without a case or cooler | #176 | "The view now says which part is missing instead of drawing nothing, and draws no placeholder box." |
| **#152** 3D view did not read the new PCIe fields | #173 | "The view reads the slot position from the catalogue and puts the card in the first x16 slot. Missing values would fall back to the documented constant and be labelled, though no shipped board needs that now." |

## Rules for answering

1. **Say "open issue" rather than "almost done"** — the issue number makes it checkable. For closed
   issues, give the PR number the same way.
2. **Say what the demo does, not what the release will do.** The device evidence was recorded on rc2;
   the 3D screenshots will be re-taken on the final build.
3. If an answer would require a number not in `Defense_Numbers_To_Confirm.md`, do not invent it.
4. The safest framing for missing data is the product's own rule: *missing evidence means unverified,
   not a pass.*

## What this list is not

It is not a promise of dates. Owners are named in the issues; nothing here commits anyone to a fix
before the 11/28 freeze.
