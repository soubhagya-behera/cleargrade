# ClearGrade — Project Status

Last updated: 2026-11-19 · **Current phase: 1.6 — Owner decisions ratified; documentation corrections applied. Implementation not started.**

## 1. Completed

- Reviewed full assignment PDF (5 pages, three embedded wireframes), README and both supplied CSVs; reconciled benchmark counts (25/16/9 and 5,000/4,685/315) with a fixed clock `2026-11-19` and identified 348 error occurrences.
- Produced the design documentation set (architecture, data model, API, UX, security, test strategy, decisions, development plan, status, README).
- Completed Phase 1.5 audit and **applied all corrections in Phase 1.6**: owner-approved decisions recorded; C-1…C-5 and T-1…T-6 resolved in the documents; duplicate decision identifiers consolidated with history preserved.

## 2. Owner-ratified decisions (recorded in DECISIONS.md §1)

`D-07` grading (A≥80/B≥70/C≥60/D≥50/F<50; PASS ≥50; no subject threshold), `U-01` email (pragmatic validation, plus-addressing/subdomains; no RFC claim), `U-02` cross-file upsert (accepted rows only; preserve first-seen; new/changed/unchanged), `U-03` no authentication (local/trusted only), `U-04` support all README variations, `U-06` configurable 10 MiB/413, `I-01` header-width field count, `I-02` first occurrence reserves ID, `I-03` inclusive age 5–25, `I-04` injected clock with 2026-11-19 benchmark, `D-17` bonus deferred.

## 3. Open technical spike (blocker)

| Spike | Status | Gate |
|---|---|---|
| **C-4 physical line numbers** — prove chosen Commons CSV version reports the physical starting line with header = 1 across embedded newlines, blank lines, a malformed row after the newline and a subsequent valid row; counting `Reader` not accepted untested | **UNRESOLVED** | G2→G3 (phase 3 blocked until passed) |

## 4. Residual risks

| Risk | Status | Mitigation / note |
|---|---|---|
| Benchmark counts drift with real date | Mitigated | Fixed clock `2026-11-19` in tests (I-04); document in `NOTES.md` |
| Unseen CSV contents unknown | Open | U-04 design + generated fixtures (TEST_STRATEGY §8) |
| Unusual valid email forms | Open | U-01 rule documented; plus-address/subdomain supported |
| Concurrent identical uploads | Designed | Partial unique index + digest reservation (C-1/C-2); test in phase 3 |
| Decimal precision | Designed | `BigDecimal` + unconstrained `NUMERIC`, no rounding (C-3) |
| No authentication | Ratified | Local/trusted evaluation only; must not be publicly exposed (U-03) |
| Domain boundary erosion | Open | Architecture test at G2→G3 |

## 5. Next actions

1. Owner review/approval of this corrected documentation set (Phase 1.6 gate).
2. Resolve the **C-4 spike** as the first technical task of phases 2–3; it is a hard blocker.
3. On approval, begin phase 1 of DEVELOPMENT.md.

## 6. Scope notes

Current-result snapshot only (no term/year invented, D-11). Benchmark numbers are regression fixtures, never application logic (D-12). `NOTES.md` is reserved for final assignment submission. No sample CSV data or PDF content is reproduced in these documents. No application source, migrations, dependencies, credentials or Git operations exist.
