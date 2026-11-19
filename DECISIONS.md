# ClearGrade — Engineering Decisions

Status: **Design documentation only — no implementation.** Owner approvals recorded 2026-11-19. `[REQ]` = assignment requirement; `[REC]` = engineering choice; `[INF]` = inference confirmed by supplied data.

## 1. Owner-approved decisions (canonical, active)

### D-07 — Grading scheme and pass rule `[REQ delegated; APPROVED]`
**Context:** The report must show total, percentage, grade and pass/fail; the assignment requires the scheme to be chosen and stated.
**Chosen (owner-approved):** A ≥ 80, B ≥ 70, C ≥ 60, D ≥ 50, F < 50. **PASS when overall percentage ≥ 50.** There is **no individual-subject passing threshold**. This is a deliberately **simplified chosen scheme**, not a claim of any official board standard. Percentage for display is `round(total / 500 × 100, 1)` using HALF_UP; rounding applies only to the derived display value, never to stored marks.
**Alternatives considered:** pass at 35/40 with a separate F band; grade on raw total; relative grading; subject-wise minimums (explicitly excluded by owner).
**Consequences:** Deterministic and easy to explain. Freeze before phase 2; changes are test-visible (band boundaries 80/70/60/50).

### U-01 — Email validation `[APPROVED]`
**Context:** "Valid email address" has no formal grammar in the brief; the unseen file may contain unusual but valid forms.
**Chosen (owner-approved):** A **pragmatic conventional** rule: conventional local part and domain, supporting **plus-addressing, subdomains and ordinary valid domain labels**; reject obvious malformed addresses (missing `@`, missing domain dot, double `@`, leading `@`, whitespace, bare words). Store lowercase. Document the exact rule in code tests. **Do not claim complete RFC compliance.**
**Alternatives considered:** permissive RFC-like grammar; third-party validation library (new dependency, rejected); minimal "contains @" check (rejected).
**Consequences:** No new dependency; deterministic; plus-addresses and subdomains are accepted. Residual risk: an unusual valid address in the unseen file may still be rejected — covered by U-04 and tests.

### U-02 — Cross-file duplicates and upsert semantics `[APPROVED]`
**Context:** The assignment leaves repeated/different-file handling open.
**Chosen (owner-approved):** Upsert by **normalized uppercase `student_id`**. **Only accepted rows may update records** — a later row for an existing ID that is rejected for any reason must not modify the stored student. **Preserve `first_seen_batch_id`; update `last_updated_batch_id`.** Distinguish **new**, **actually changed** and **unchanged** records in batch counters.
**Alternatives considered:** reject all cross-file duplicates; insert a new version row (would require a history model, out of scope per D-11).
**Consequences:** Last accepted upload wins; previous values are overwritten; no history retained. Counters are `new_count` + `changed_count` + `unchanged_count` = `imported_count`. "Changed" is determined by comparing normalized field values, not merely by the row being accepted.

### U-03 — Authentication scope `[APPROVED]`
**Context:** The assignment is silent on login, but the portal handles personal data.
**Chosen (owner-approved):** **No authentication implementation in this assignment.** Document that the application is for **local/trusted evaluation only** and **must not be publicly exposed without authentication**.
**Alternatives considered:** Spring Security with a user store (out of scope); HTTP basic with static credentials (unsafe).
**Consequences:** Lower complexity and no secret management. Deployment guidance must state the exposure restriction prominently (SECURITY.md, README.md).

### U-04 — Unseen CSV file `[APPROVED]`
**Context:** The README states graders will use a different messy CSV in the same format.
**Chosen (owner-approved):** Support **every variation specified by the supplied README** — UTF-8 with/without BOM, Windows/Unix line endings, quoted commas and quoted values, reordered/mixed-case/spaced header names, extra columns, blank lines, all-empty rows, wrong field counts, and case-variant IDs. **Do not hardcode sample dataset assumptions.** (Physical line numbering is separately gated by the open C-4 spike, §4.)
**Alternatives considered:** assume the supplied-file shape only (rejected).
**Consequences:** CSV adapter and generated fixtures must explicitly exercise each listed variation; no logic keyed to filenames or expected totals.

### U-06 — Upload size limit `[APPROVED]`
**Context:** SECURITY.md proposed a 10 MiB cap as a default, not a requirement.
**Chosen (owner-approved):** Configurable **10 MiB** default; return **HTTP 413** when exceeded; the limit remains **configurable** (property-driven).
**Alternatives considered:** larger fixed cap; no cap (rejected — DoS risk).
**Consequences:** Testable 413 path; unseen files larger than the configured limit require an operator configuration change, not a code change.

### I-01 — Field-count basis `[APPROVED; INF]`
**Context:** The sample contains an extra `parent_phone` column.
**Chosen (owner-approved):** Compare record field count against the **actual CSV header column count**, not the 11 required columns. `[REQ/README: extra columns ignored]`
**Alternatives:** compare against 11 (would accept rows missing trailing extra columns).
**Consequences:** Rows must match the header width; test fixtures must include header-width and extra-column cases.

### I-02 — Duplicate reservation `[APPROVED; INF]`
**Context:** The small benchmark (16/9) requires this behaviour.
**Chosen (owner-approved):** The **first occurrence of a normalized student ID reserves that ID even when that row has other validation errors**. Later occurrences receive `DUPLICATE_ID`.
**Alternatives:** reserve only if the first row is otherwise valid (rejected — changes benchmark counts).
**Consequences:** Counterintuitive but deterministic: a rejected row can block a later valid row. Must be explained in row-problem messages and unit tests.

### I-03 — Age bounds `[APPROVED; INF]`
**Context:** "Between 5 and 25 years old today" is ambiguous at the boundaries.
**Chosen (owner-approved):** Age limits **5 and 25 are inclusive**.
**Alternatives:** exclusive lower bound (verified to change the benchmark from 4,685 to 4,662 imported).
**Consequences:** Domain age validator uses `5 <= age <= 25` against the injected clock.

### I-04 — Clock and benchmark date `[APPROVED]`
**Context:** Age-dependent counts drift with the real date.
**Chosen (owner-approved):** Use an **injected `Clock`**. Production uses the **configured actual date** (system clock, UTC). Regression benchmark tests use a fixed clock at **2026-11-19**.
**Alternatives:** `LocalDate.now()` inline (untestable); hardcoding expected counts (forbidden by D-12).
**Consequences:** Reproducible tests; NOTES.md must state the benchmark date and that counts are date-sensitive.

### D-17 — Bonus features `[APPROVED]`
**Context:** Error CSV download, class filter, mark bars and print are labelled BONUS in the wireframes.
**Chosen (owner-approved):** **Defer all optional bonus features until core requirements are completed.**
**Consequences:** Phase 6 starts only after phase 5 gates pass (DEVELOPMENT.md).

## 2. Active engineering decisions (no owner ratification required)

- **D-01 — Modular monolith.** Single Spring Boot deployable with enforced internal boundaries; no microservices. Alternative: microservices/serverless (unnecessary at ~5,000 rows).
- **D-02 — Java 21 + Spring Boot + Maven.** Single Maven module; strict packages plus architecture test rather than multi-module build.
- **D-03 — PostgreSQL + Spring Data JPA + Flyway.** Only application database; no Redis/Kafka. Alternatives rejected: H2 (dialect mismatch), MongoDB (no integrity need).
- **D-04 — Apache Commons CSV.** Explicit format and BOM-aware reader. **Caveat:** physical line numbering is **not** claimed solved by record numbers — see open spike C-4 in §4.
- **D-05 — Domain purity.** Framework-free domain; application ports; adapters inward. Fast pure unit tests at the cost of DTO mapping.
- **D-09 — SHA-256 identical-file detection (enhanced by C-1/C-2).** Concurrency-safe reservation via a PostgreSQL **partial unique index** on active/successful batches. Full design in DATA_MODEL.md §5–7 and ARCHITECTURE.md §5–6.
- **D-10 — Rejected rows vs error occurrences.** `rejected_count` (rows) and `error_count` (occurrences) stored separately; one `import_row_error` row per occurrence (matches 315 vs 348).
- **D-11 — Current-result snapshot only.** No term/year field exists; no history invented.
- **D-12 — Never hardcode dataset counts.** Benchmark numbers are test fixture assertions only (I-04 clock).
- **D-13 — Transaction boundaries.** Separate transaction service beans or `TransactionTemplate` for `REQUIRES_NEW` work to avoid self-invocation; explicit `PROCESSING` persistence and `FAILED` finalisation (DATA_MODEL.md §7).
- **D-14 — Server-side pagination.** All list endpoints paged; errors default 50, students default 25, max 100 (T-4).
- **D-16 — Single container for SPA + API.** Multi-stage Dockerfile; Compose adds PostgreSQL; port `8080:8080` published.

## 3. Superseded identifiers (history preserved — no longer active)

| Superseded ID | Originally described | Superseded by | Reason |
|---|---|---|---|
| D-06 | Injected `Clock`; benchmark fixed at 2026-11-19 | **I-04** | Same decision, owner ratified under I-04 |
| D-08 | Duplicate handling (in-file + cross-file) | **I-02** (in-file reservation) and **U-02** (cross-file upsert) | Split and ratified separately |
| D-15 | No authentication in v1 | **U-03** | Same decision, owner ratified under U-03 |
| U-05 | Grading bands and pass threshold sign-off | **D-07** | Same decision, owner ratified under D-07 |

These identifiers are retained here so no decision history is silently lost; they must not be used as active references in other documents.

## 4. Unverified items and open technical spikes

- **C-4 — Physical line numbers (UNRESOLVED PRE-IMPLEMENTATION SPIKE).** Not claimed solved. Must be proven against the chosen Commons CSV version before production ingestion; see TEST_STRATEGY.md §2 and DEVELOPMENT.md gate G2→G3. A counting `Reader` is not acceptable without testing parser buffering/read-ahead. If the spike cannot prove the behaviour, this remains unresolved and the line-number feature must be redesigned before phase 3 completion.
- **U-04 residual:** the actual contents of the unseen CSV are unknown; only the README-listed variations can be designed for.
- **U-01 residual:** plus-addresses and subdomains are accepted by design; other unusual valid forms may still be rejected.
- **U-06 residual:** whether a real unseen file exceeds the configurable limit is unknown; the limit is an operator setting.
