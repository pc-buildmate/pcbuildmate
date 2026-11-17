# Defense · Repository walkthrough (if asked to show the code)

> One page for the moment an examiner says "show me". Claim → where it lives → what to point at.
> Open the repo on the projector beforehand; do not type paths live.

## 1. "Rules are data, not code"

- **Files**: `assets/rules/rules-v1.json` (15 rules), `assets/fixtures/fixtures-rules-v1.json` (60 cases).
- **Point at**: one rule object — `ruleId`, `kind`, `left/right` paths, `severity`, four fixture ids.
- **Say**: adding a rule is a JSON line plus four fixtures; the engine never learns a new rule id.

## 2. "Missing evidence cannot pass"

- **Files**: `contracts/.../Outcomes.kt` (three states), `domain/rules/.../DefaultRuleEngine.kt` (propagation).
- **Point at**: `weakest` computation and the missing-field branch; then the demo's BIOS row, which is
  empty in the catalogue (`docs/20` records that single `EMPTY`).

## 3. "A diagnosis belongs to a revision"

- **Files**: `contracts/.../Workspace.kt` (`currentDiagnosis` returns null when ids differ),
  `ui/src/test/.../DiagnosticsScreenSemanticsTest.kt` (stale case renders nothing).
- **Say**: the accessor is the guard; the Compose test proves what the screen does when it returns null.

## 4. "Replace, then recheck"

- **Files**: `domain/state/.../ReplacementMachine.kt` (preview → rechecking → applied only),
  `HANDOFF`/`docs/04 §4.4` (the five forbidden transitions).
- **Point at**: the transition that reacts to a new blocker by going back to candidates instead of
  applying.

## 5. "The tests are not just restating the implementation"

- **Files**: `test-oracle/src/main/kotlin/pcbuildmate/oracle/` (independent model),
  `app/src/test/.../OracleCrossCheckTest.kt` (production vs oracle),
  `ci/check-boundaries.mjs` (forbids the oracle from importing domain code).
- **Say**: inverting one line of the oracle turns the cross-check red — we did it to prove the test
  can fail.

## 6. "Every cell is really executed"

- **Files**: `tools/gen-matrix.mjs` + `tools/check-matrix.mjs` (`build/reports/matrix.json` from JUnit
  XML), CI job `jvm`.
- **Point at**: the run summary line `rules 60/60, fsm 76/76, inv 18/18` printed from the artifact,
  not from a table.

## 7. "Breaking the code turns tests red"

- **Files**: `tools/run-mutations.mjs` (12 patches), CI job `mutations`.
- **Point at**: one mutation definition — the `find`/`replace` pair and the test that must fail.

## 8. "It is offline"

- **Files**: `app/src/main/AndroidManifest.xml` (no INTERNET permission), `docs/21 §21.3` (verified on
  the release APK).
- **Say**: not "we do not call the network" but "the artifact cannot".

## 9. "Data is dated and audited"

- **Files**: `assets/catalogue/catalogue-app.json`, `assets/catalogue/SOURCES.md`,
  `tools/audit-catalogue.mjs` → `docs/20`.
- **Say**: 30 records, snapshot 2026-11-04, record-level sources; field-level sources are the known gap.

## 10. If asked "where would you start reading?"

`README.md` → `docs/00` → `docs/03` → `contracts/` → `tools/matrix-spec.mjs` → `test-oracle/`.
That order goes from what the product promises to how it is checked.
