# Defense · Open issues and how to answer if asked

> Snapshot 2026-11-20, main `47b4ff8`. Three issues are open; three were closed since the 11-19 snapshot.
> For each: does it affect what the demo shows, and what to say if an examiner asks. Answers stay inside the
> evidence rules — no claim beyond what the repo can show.

| Issue | What it is | Does the demo show it? | If asked, say |
|---|---|---|---|
| **#119** two stale rows in `SOURCES.md` | Two models listed in the source table no longer exist in the shipped catalogue | No | "Two rows in the source table are stale and queued for cleanup; the catalogue itself is unaffected — record-level sources remain 30/30." |
| **#122** cooler mount coverage | Both coolers carry `socketMounts`, but no cooler supports LGA1851, and only one supports AM5 | Only if you build with the 245K or pair AM5 with the NH-D15: the cooler rule returns **unverified** instead of pass/conflict | "Cooler mount data is incomplete for the newest socket, so that rule reports unverified rather than guessing. That is the designed behaviour for missing data, and the gap is logged." |
| **#149** one motherboard lacks the two PCIe fields | `mb-demo-lga1700` has no `pcieX16SlotMm` / `pcieSlotCount` (five of six boards have them) | Not in the current demo build (b650 board); if you pick that board in 3D, the view labels the fallback | "Five of six boards record the slot position; one does not, so the view falls back to the documented constant and says so on screen." |

## Closed since 11-19 (answer as fixed, with the PR number)

| Issue | Fixed by | One line if asked |
|---|---|---|
| **#104** lint: missing launcher icon / backup rules | #177 | "Adaptive launcher icon and backup rules shipped; the remaining two lint items are disabled with a recorded reason." |
| **#123** 3D view looked empty without a case or cooler | #176 | "The view now says which part is missing instead of drawing nothing, and draws no placeholder box." |
| **#152** 3D view did not read the new PCIe fields | #173 | "The view reads the slot position from the catalogue and puts the card in the first x16 slot; for the one board without the field it falls back to the documented constant and labels it." |

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
