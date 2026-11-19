# ClearGrade — Project Status

Last updated: 2026-11-19 · **Current phase: pre-Phase 2B — configuration security correction. Application implementation has not started.**

## 1. Completed

- Reviewed full assignment PDF (5 pages, three embedded wireframes), README and both supplied CSVs; reconciled benchmark counts (25/16/9 and 5,000/4,685/315) with a fixed clock `2026-11-19` and identified 348 error occurrences.
- Produced the design documentation set and completed Phase 1.5 audit with all C-1…C-5 and T-1…T-6 corrections applied in Phase 1.6.
- **Phase 2A completed:** Git initialized on branch `main`; remote `origin` set to the private repository `https://github.com/soubhagya-behera/cleargrade-student-results.git`; initial documentation commit `706dfd541c1a152030b662f8fc822159923c21c2` (`docs: establish ClearGrade engineering blueprint`) pushed to `origin/main`. No `reference/` files, `.env` or credentials were committed.
- Configuration strategy recorded as D-19 (profile-based local / env-based deployment) and corrected by **D-20** (private local config moved to `backend/config/`, outside Maven resources; `.dockerignore` added).

## 2. Owner-ratified decisions (recorded in DECISIONS.md §1)

`D-07` grading (A≥80/B≥70/C≥60/D≥50/F<50; PASS ≥50; no subject threshold), `U-01` email (pragmatic validation, plus-addressing/subdomains; no RFC claim), `U-02` cross-file upsert (accepted rows only; preserve first-seen; new/changed/unchanged), `U-03` no authentication (local/trusted only), `U-04` support all README variations, `U-06` configurable 10 MiB/413, `I-01` header-width field count, `I-02` first occurrence reserves ID, `I-03` inclusive age 5–25, `I-04` injected clock with 2026-11-19 benchmark, `D-17` bonus deferred, `D-19` profile-based configuration (location revised by D-20), `D-20` external local configuration directory.

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
| Private config leaking into builds | Mitigated | D-20: outside Maven resources, Git-ignored, excluded by `.dockerignore` |
| No authentication | Ratified | Local/trusted evaluation only; must not be publicly exposed (U-03) |
| Domain boundary erosion | Open | Architecture test at G2→G3 |

## 5. Next actions

1. Owner review of the D-20 configuration-security correction (`.gitignore`, `.dockerignore`, docs).
2. Commit these configuration corrections on the `chore/configuration-hardening` feature branch and raise PR `chore: harden local and Docker configuration` for independent review; do not merge until approved.
3. Resolve the **C-4 spike** as the first technical task of phases 2–3; it is a hard blocker.
4. Begin Phase 1 implementation per DEVELOPMENT.md.

## 6. Scope notes

Current-result snapshot only (no term/year invented, D-11). Benchmark numbers are regression fixtures, never application logic (D-12). `NOTES.md` is reserved for final assignment submission. No sample CSV data or PDF content is reproduced in documentation. No application source, dependencies, database migrations or real credentials exist; Git initialization, the initial commit and the initial push did occur in Phase 2A.
