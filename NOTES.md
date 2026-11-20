# ClearGrade — Release Notes

Final handover notes for the assignment. Decisions are recorded in [DECISIONS.md](DECISIONS.md); this file summarises how they were implemented and what remains.

## Architecture

Modular monolith with inward dependencies: `adapters → application → domain`. The framework-free domain owns validation, value objects and grading; application services own orchestration and ports; `adapters.in.csv` parses uploads; `adapters.in.web` maps HTTP to application calls and DTOs; `adapters.out.persistence` owns JPA/JDBC and never exposes entities. No microservices, brokers or agent frameworks. The post-release `adapters.out.ai` providers are optional and isolated behind an application-owned port; `application.service` owns routing, caching and the offline rule engine.

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

## ClearGrade Intelligence (post-release enhancement)

`POST /api/imports/{batchId}/insights` requires an existing `COMPLETED` batch and builds a privacy-safe
`ImportQualityContext` from batch counters plus `countByCode` error occurrences. `InsightProviderRouter` tries
Gemini (Interactions REST, `store=false`, explicit JSON schema, no retries), then verified-available Ollama
(native `/api/chat`, non-streaming, JSON schema), then a deterministic Java rule engine. Auth/configuration
failures remain controlled `503`s; quota/timeouts/5xx/unusable output fall forward safely. Every report names
the engine that generated it and carries a safe fallback category, never a provider body or key.

Only counts, backend percentages/ratios, the five code counts, `allowedEvidenceValues` and static schema
metadata reach a provider. Handler tests capture real outbound payloads and assert sensitive sentinels are
absent; strict response validation rejects unknown fields, unsupported metrics/severities, oversized content,
array overflow and blocked markers, and replaces model-supplied evidence values with backend facts. Reports are
cached in a bounded TTL/LRU map keyed by batch + context hash + model version, with concurrent-request
coalescing; analysis is explicit only, and there is no insight table or migration. See
[docs/AI_INTELLIGENCE.md](docs/AI_INTELLIGENCE.md).

## Security audit (pre-publication)

- No tracked `.env` or committed credentials; `.env.example` contains placeholders only; private local profiles are git-ignored and excluded from Maven resources and Docker contexts.
- No real secret was found in tracked source, CI workflow or image definitions. CI uses read-only `contents` permission and no secrets.
- Container users are non-root; PostgreSQL is bound to loopback for local development; Actuator exposes health only; CORS is not configured (same-origin only).
- **No authentication or multi-tenancy.** The application is for local/trusted evaluation only and must not be publicly exposed without authentication and network controls.
- Seed/sample data is synthetic; no assignment PDF or supplied CSVs are tracked.
- AI follows the same trust boundary: the Gemini key is server-side only, never returned by status/DTOs, never exposed to Vite or nginx logs, and Compose passes it at runtime with an empty default. Providers are read-only and receive aggregates only; `store=false` is not a claim that Google has no processing or retention obligations. Authentication is still absent — local/trusted only.

## Operational limitations and deferred work

- D-21 stale-`RECEIVED` recovery sweep is not implemented; `FAILED` retry is available.
- D-17 bonus features (error-CSV download, mark bars, print) remain deferred.
- U-01 exact pragmatic email grammar boundary remains an owner decision; the canonical validator was not changed.
- No batch-list endpoint is documented; the UI offers batch-ID lookup and current-session results.
- Benchmark counts are date-sensitive under the injected clock; measured values are informational, not SLAs.

## Verification summary

Backend `mvn verify`: **257 tests**, zero failures/errors, 1 skipped (the opt-in live Gemini smoke test; no key required). The original 206 tests plus Testcontainers DB and mock-HTTP AI tests are green. Frontend: **26 tests**, typecheck and production build pass. No real key or Ollama installation was used. Docker image builds, Compose validation, fresh-stack startup, POST readiness, HTTP seed and persistence checks for the original release are recorded in [PROJECT_STATUS.md](PROJECT_STATUS.md); the enhancement's command evidence is in §8 there.
