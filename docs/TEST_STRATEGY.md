# ClearGrade — Test Strategy

Status: **Planned tests only — no test code exists.** Commands are illustrative until the build exists (DEVELOPMENT.md). Goal: convince a teammate the import is correct, including cases the supplied files do not contain. `[REQ]`

## 1. Validation unit tests (domain, pure JUnit 5)

| Rule | Cases |
|---|---|
| `student_id` | 3 and 20 chars valid; 2 and 21 invalid; letters/digits/hyphen; underscore/space invalid; lowercase → uppercase; DB normalized CHECK alignment |
| `full_name` | 2 and 100 chars valid; 1 and 101 invalid; trim + collapse internal spaces; Unicode, apostrophe, comma (`Doe, Jane`) |
| `email` (U-01) | Plus-address (`name+tag@example.edu`), subdomain (`name@mail.example.edu`), normal labels accepted; missing `@`/domain dot, double `@@`, leading `@`, whitespace, bare words rejected; lowercase storage; **no RFC-compliance claim** |
| `date_of_birth` | Both formats; impossible dates (`31/02/2023`) rejected; wrong separators rejected; **inclusive 5 and 25 accepted, 4 and 26 rejected** (I-03) against fixed clock |
| `grade_level` | 1 and 12 valid; 0 and 13 out of range; `09` → `9`; `IX`, `-2`, `9th` invalid |
| `section` | A–F any case → uppercase; `Z`, `G`, `AB`, `1`, empty invalid |
| marks (C-3) | `BigDecimal` parsing; 0 and 100 valid; `-1`, `101`, `999`, `100.01` out of range; `unknown`, `text`, `ninety`, `90/100` invalid; scientific notation classified deterministically; **high-precision decimal (e.g. 99.999) stored unchanged — no rounding**; excessive fractional digits rejected by configured limit |
| error codes | Every failure maps to one of the five codes |
| grading (D-07) | Bands 80/70/60/50 boundaries; A≥80…F<50; PASS exactly at 50 and above; **no per-subject threshold**; HALF_UP only for display percentage |
| clock (I-04) | Age results depend only on injected `Clock`, never host date |

Tests assert the complete error list per row, covering multi-error behaviour (D-10).

## 2. CSV parsing edge cases and the C-4 spike

### 2.1 General edge cases (adapter)
- UTF-8 with and without BOM; CRLF, LF and mixed endings. `[REQ, U-04]`
- Quoted commas, apostrophes, escaped quotes, Unicode names; reordered/mixed-case/spaced headers; extra columns ignored. `[REQ]`
- Blank lines and all-empty rows skipped but still consumed in line numbering. `[REQ]`
- Wrong field counts → single `WRONG_FIELD_COUNT`, rest of file continues; field count compares to **actual header length** (I-01).
- Header-only and completely empty files → clean file-level failure.
- Case-variant IDs; first occurrence reserves ID even when that row has other errors (I-02).

### 2.2 C-4 — PHYSICAL LINE NUMBER VERIFICATION GATE (EVIDENCE APPROVED; G2→G3 PASSED)
**Status: evidence approved by owner 2026-11-20 (gate G2→G3 passed).** CSV record numbers are not physical line numbers. The merged PR #3 spike verified the **chosen Commons CSV version** with fixtures containing, in order:
1. a quoted field with an **embedded newline**;
2. **blank physical lines**;
3. a **malformed row after** the embedded newline;
4. a **subsequent valid row**.

Acceptance: the reported source line is the **physical starting line of the problematic record**, with **header = 1** and blank lines counted. A simple counting `Reader` is **not acceptable** without testing parser buffering/read-ahead. All documented spike limitations are carried forward into production CSV implementation, which remains future work.

## 3. Import integration tests

- Supplied `students_small.csv` with fixed clock `2026-11-19`: **25 / 16 / 9**. Supplied `students_sample.csv`: **5,000 / 4,685 / 315 rejected / 348 occurrences**. `[REQ benchmark]`
- Aggregated counters satisfy `imported = new + changed + unchanged` (U-02).
- **Only accepted rows update:** a rejected later row for an existing ID leaves the stored student unchanged; `first_seen_batch_id` preserved, `last_updated_batch_id` advanced only on accepted change.
- **Change detection:** accepted identical row counts as `unchanged`; accepted modified row counts as `changed`.
- **Idempotency (C-1/C-2):** same bytes → `200` existing summary, no new batch; active batch → `409 BATCH_PROCESSING` with batch id; `FAILED` batch → retry creates a new batch, failed record retained.
- **Concurrency (C-2):** two simultaneous identical uploads → one import; unique-violation path re-reads and returns correct status.
- Bad row does not prevent later valid rows; missing required column fails the whole file without student writes.
- Error rows persisted with physical line numbers and one row per occurrence (D-10, C-4 gate).
- Batch transitions `RECEIVED → PROCESSING → COMPLETED/FAILED`; `PROCESSING` explicitly persisted; fatal failure rolls back students but keeps the batch; `FAILED` finalisation recorded.

Benchmark numbers are **regression fixtures only**, never parameters to validation logic (D-12).

## 4. Database tests

- Unique `student_id` and normalized CHECK; upsert behaviour; partial unique index on active `sha256`; excluded FAILED digest permits retry.
- `NUMERIC` columns preserve decimal precision with **no rounding** (C-3); `DATE`/`TIMESTAMPTZ` mappings; `TEXT` raw identifier column.
- Index-usage check via query plan for directory search and error lookup (or migration assertion).
- Transaction test: rollback of T3 leaves no students, no error occurrences, no `COMPLETED` status and no partial counts; T4 separately commits `FAILED`; `REQUIRES_NEW` executes in an independent transaction (proves no self-invocation bug).
- Flyway migration from empty schema and idempotent second startup.

## 5. API tests

- `POST /api/imports`: `201`, `200` deduplicated, `409 BATCH_PROCESSING`, `400`, `413`, `415`, `500` with correlation id; counters shape correct.
- `GET /api/imports/{id}` and `/errors`: `200` shapes, **default size 50**, filter, `404`.
- `GET /api/students`: **default size 25**, custom paging, search by ID/name, grade/section filters, invalid params → `400`.
- `GET /api/students/{id}`: `200` report with no per-subject pass flag, case-insensitive ID, `404`.
- Contract checks: JSON names/types match API_CONTRACT.md; no JPA entity leakage.
- Management path: `/actuator/health` available; other Actuator endpoints disabled (T-5).
- Security subset from SECURITY.md §8.

## 6. Frontend tests

- Component tests: `FileDropzone`, `StatCard`, `DataQualitySummary`, `ProblemTable`, `StudentTable`, `Pagination`, `ResultBadge`.
- State coverage: upload loading/success/error/already-processing; directory loading/empty/error; report not-found.
- Interaction: search updates results, pagination changes page, report link uses `/students/:id`; TanStack Query loading/error states.
- Type-check and production build as gates. No decorative snapshot suite initially.

## 7. Performance verification

- Import 5,000-row fixture with fixed clock; record duration (few seconds target, not an SLA).
- Directory first/middle/last pages at 5,000+ rows; verify index use.
- Monitor memory during streaming import; no unbounded accumulation.
- Re-run with a larger generated seed (through the import API, T-1) to check unseen-file behaviour.
- Report measured numbers; no invented thresholds; failures trigger indexing/streaming work, not premature caching.

## 8. Regression benchmark strategy

- Supplied CSVs are fixtures outside application logic; fixed clock `2026-11-19` (I-04) makes counts reproducible.
- Assert row counts, occurrence counts and spot-check codes/lines.
- Generated messy fixtures cover README-listed variations (U-04) absent from the sample: embedded newline (C-4), reordered header, BOM-less CRLF, plus-address email, high-precision marks, 25-year-old boundary.
- Seed generator submits generated CSV via `POST /api/imports` (T-1) so tests exercise the real pipeline.
- If grading or validation rules change, update DECISIONS.md and tests deliberately; never adjust fixtures to make tests pass.
