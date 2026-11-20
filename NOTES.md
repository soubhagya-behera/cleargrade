# ClearGrade — Release Notes

Final handover notes for the assignment. Decisions are recorded in [DECISIONS.md](DECISIONS.md); this file summarises how they were implemented and what remains.

## Architecture

Modular monolith with inward dependencies: `adapters → application → domain`. The framework-free domain owns validation, value objects and grading; application services own orchestration and ports; `adapters.in.csv` parses uploads; `adapters.in.web` maps HTTP to application calls and DTOs; `adapters.out.persistence` owns JPA/JDBC and never exposes entities. No microservices, brokers, caches or AI.

## Import lifecycle and transactions

1. SHA-256 is computed from the original uploaded bytes before any persistence.
2. **T1** (`REQUIRES_NEW`) commits `RECEIVED` and reserves the digest via the partial unique index `ux_import_batch_active_sha256`.
3. **T2** (`REQUIRES_NEW`) commits `PROCESSING`.
4. The file is parsed and validated outside any transaction.
5. **T3** (`REQUIRES_NEW`) atomically commits accepted student upserts, row-error occurrences, final counts and `COMPLETED`.
6. **T4** (`REQUIRES_NEW`) commits `FAILED` after a T2/T3/parse failure; the original exception is preserved and any T4 failure is attached as suppressed. T2 is inside the guarded path, so a failed transition also reaches T4 (`RECEIVED → FAILED`).

Duplicate-digest recovery re-reads the digest in a fresh transaction and classifies only SQLSTATE `23505` on the exact active-digest index; `200` for COMPLETED, `409 BATCH_PROCESSING` for RECEIVED/PROCESSING, retry after FAILED.

## Physical line tracking (C-4)

`CSVRecord.getCharacterPosition()` is captured before blank lines are skipped and mapped through an independent `PhysicalLineMap` built from the same decoded text. Header is line 1, blank lines and newlines inside quoted fields are counted. All six spike limitations remain: unterminated quotes are file-level failures; comment markers stay disabled; byte/file input strips the BOM before mapping; string callers must strip the BOM; offsets are UTF-16 code units; field-count consistency uses the parsed header width, not `isConsistent()`.

## Validation and accounting

Domain value objects (including the canonical email validator) drive validation; the application only classifies error codes and collects every applicable occurrence. First valid normalized student ID reserves the ID even if its row is rejected; later occurrences get `DUPLICATE_ID`. One `import_row_error` row is persisted per occurrence, each pinned to its physical line. `imported = new + changed + unchanged`, `rows_read = imported + rejected`, `error_count >= rejected_count`; blank rows are excluded. Upserts preserve `first_seen_batch_id`, advance `last_updated_batch_id` only on accepted changes, and use row locks for concurrent divergent updates (D-28).

## Identical files and concurrency

Partial unique index plus deterministic digest reservation means simultaneous identical uploads produce one winner; the loser re-reads and returns the documented `200`/`409` outcome. Failed batches are excluded from the index, so retry creates a new batch. Verified with real PostgreSQL Testcontainers.

## Delivery and seed

Multi-stage backend (Maven build → JRE runtime) and frontend (Node build → unprivileged nginx) images; Compose starts PostgreSQL, backend and frontend with healthcheck-based ordering and a persistent named volume. `scripts/start.*` is the one-command entry point. Seed data is posted through `POST /api/imports` using `scripts/seed.*`; no direct SQL inserts and no fabricated database state. Repeated seed uploads are reported as deduplicated/conflicting, never silently as new imports.

## Security audit (pre-publication)

- No tracked `.env` or committed credentials; `.env.example` contains placeholders only; private local profiles are git-ignored and excluded from Maven resources and Docker contexts.
- No real secret was found in tracked source, CI workflow or image definitions. CI uses read-only `contents` permission and no secrets.
- Container users are non-root; PostgreSQL is bound to loopback for local development; Actuator exposes health only; CORS is not configured (same-origin only).
- **No authentication or multi-tenancy.** The application is for local/trusted evaluation only and must not be publicly exposed without authentication and network controls.
- Seed/sample data is synthetic; no assignment PDF or supplied CSVs are tracked.

## Operational limitations and deferred work

- D-21 stale-`RECEIVED` recovery sweep is not implemented; `FAILED` retry is available.
- D-17 bonus features (error-CSV download, mark bars, print) remain deferred.
- U-01 exact pragmatic email grammar boundary remains an owner decision; the canonical validator was not changed.
- No batch-list endpoint is documented; the UI offers batch-ID lookup and current-session results.
- Benchmark counts are date-sensitive under the injected clock; measured values are informational, not SLAs.

## Verification summary

Backend `mvn verify`: 206 tests, zero failures/errors/skips. Frontend: 14 tests, typecheck and production build pass. Docker image builds, Compose validation, fresh-stack startup, POST readiness, HTTP seed and persistence checks are recorded in [PROJECT_STATUS.md](PROJECT_STATUS.md).
