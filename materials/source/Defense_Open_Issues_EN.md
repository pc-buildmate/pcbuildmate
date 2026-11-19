# Defense · Open issues and how to answer if asked

> Snapshot 2026-11-19, main `7b94db8`. These are the six open issues at that moment. For each:
> does it affect what the demo shows, and what to say if an examiner asks. Answers stay inside the
> evidence rules — no claim beyond what the repo can show.

| Issue | What it is | Does the demo show it? | If asked, say |
|---|---|---|---|
| **#104** lint: missing launcher icon, `allowBackup` without `dataExtractionRules` | Polishing: the app has no custom icon; backup policy uses a deprecated attribute | Not during the demo; visible only on the home screen | "Launcher icon and backup rules are release polish, tracked as an issue. Nothing in the compatibility flow depends on them." |
| **#119** two stale rows in `SOURCES.md` | Two models listed in the source table no longer exist in the shipped catalogue | No | "Two rows in the source table are stale and queued for cleanup; the catalogue itself is unaffected — record-level sources remain 30/30." |
| **#122** cooler mount coverage | Both coolers carry `socketMounts`, but no cooler supports LGA1851, and only one supports AM5 | Only if you build with the 245K or pair AM5 with the NH-D15: the cooler rule returns **unverified** instead of pass/conflict | "Cooler mount data is incomplete for the newest socket, so that rule reports unverified rather than guessing. That is the designed behaviour for missing data, and the gap is logged." |
| **#123** 3D view when a part has no dimensions | The clearance view can look empty instead of explaining what is missing | Only if you open 3D with an incomplete build | "The 3D view is a size aid; when a part has no dimensions it should say so rather than look broken. That wording is queued." |
| **#149** one motherboard lacks the two PCIe fields | `mb-demo-lga1700` has no `pcieX16SlotMm` / `pcieSlotCount` (five of six boards have them) | Not in the current demo build (b650 board) | "Five of six boards record the slot position; one does not yet, so the view falls back to the documented constant for it." |
| **#152** 3D view does not read the new PCIe fields | The fields exist in the catalogue and registry, but `Scene3d` still uses the 7.52 mm constant and form-factor slot count | Not visible: for the demo board the constant and the field agree closely | "The catalogue now records the slot position per board; the 3D view still uses the documented constant. Wiring the field in is open work, not a claim we make today." |

## Rules for answering

1. **Say "open issue" rather than "almost done"** — the issue number makes it checkable.
2. **Say what the demo does, not what the release will do.** The device evidence was recorded on rc2.
3. If an answer would require a number not in `Defense_Numbers_To_Confirm.md`, do not invent it.
4. The safest framing for missing data is the product's own rule: *missing evidence means unverified,
   not a pass.*

## What this list is not

It is not a promise of dates. Owners are named in the issues; nothing here commits anyone to a fix
before the 11/28 freeze.
