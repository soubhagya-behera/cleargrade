# ClearGrade — Project Status

Last updated: 2026-11-20 · **Release engineering complete: all eight phases (0–7) passed.** PR #8 (G3→G4) is merged to `main` as `acc83a1`. This branch adds the REST API, real-data SPA, multi-stage Docker images, full Compose stack, HTTP seed workflow, one-command scripts, final README and `NOTES.md`. No authentication exists; deployment is local/trusted only.

## 1. Completed

- Phase 2A: Git initialized on `main`, remote `origin` set to the private repository, documentation blueprint committed (`706dfd5`), configuration-hardening change **squash-merged as PR #1** (`164c37b`). No `reference/` files, `.env` or credentials were committed.
- Phase 2B foundation:
  - Spring Boot 4.1.2 / Java 21 / Maven wrapper 3.9.14 backend with package root `dev.cleargrade` and the six required boundary packages.
  - `application.properties` (non-secret defaults only), committed `backend/config/application-local.example.properties`, git-ignored private local profile and root `.env`.
  - PostgreSQL 16 Compose service with named volume, `pg_isready` healthcheck, env-driven credentials, configurable host port and `restart: unless-stopped`.
  - React 19 / TypeScript 5.9 / Vite 7 / Tailwind 4 / React Router 7 / TanStack Query 5 / Lucide React shell with responsive sidebar, top bar, design tokens, accessible focus states, routes `/import`, `/students`, `/students/:studentId`, reusable API client and dev proxy.
  - `.github/workflows/ci.yml` verifying the Maven build, backend tests and frontend typecheck/build.
 - Phase 3B.1 domain model (`dev.cleargrade.domain`): student value objects and validation, exact grading policy, import status/count/line/error vocabulary. Framework-free; no migrations, JPA entities, repositories or endpoints added.
 - Phase 3B.1 review remediation: overflow-safe import count aggregates, row-error data-line guard, batch/outcome construction invariants, explicit `RECEIVED → FAILED` and leap-day decisions, `mark(null)` guard, stronger framework-independence source-set assertion.
 - Phase 3B.2.1: Flyway `V1__create_core_schema.sql` for `import_batch`, `student`, `import_row_error` with checks, foreign keys and the partial SHA-256 index (D-23–D-28); Testcontainers PostgreSQL integration tests for migration idempotency, schema objects, index semantics and unconstrained `NUMERIC`.
 - Phase 3B.2.1 review remediation: database CHECK makes final counts exclusive to `COMPLETED` (matching `ImportBatch`), with SQLSTATE-level tests for all batch statuses and for malformed/NULL SHA-256 digests.
 - Phase 3B.2.1 merged on `main` via PR #5 as squash commit `6cf4a4f`.
 - Phase 3B.2.2: JPA entities for `import_batch`, `student` and `import_row_error` mapped to V1 (BIGINT ids, `CHAR` JDBC codes, unconstrained `NUMERIC`, `TIMESTAMPTZ`→`Instant`, `DATE`→`LocalDate`, enum statuses); focused Spring Data repositories; explicit reflection-free mappers; `spring-boot-flyway` added because Spring Boot 4.1 moved Flyway auto-configuration out of `spring-boot-autoconfigure`, so `spring.flyway.enabled=true` was previously inert. No application output ports exist, so no port-implementing adapter was invented.
 - Phase 3B.2.2 verification: real Testcontainers PostgreSQL test proves Flyway migration, Hibernate `validate`, repository beans and save/find round trips (45-decimal marks, Unicode names, nullable counts, enum representation, FK rejection).
 - Phase 3B.2.2 merged on `main` via PR #6 as squash commit `dce122f`.
 - Owner decision 2026-11-20: G2→G3 gate passed, and the D-25/D-26 transaction boundary resolved as T1 `RECEIVED` reservation, T2 `PROCESSING`, T3 atomic upserts/errors/counts/`COMPLETED`, T4 separate `FAILED`; duplicate recovery outside the failed transaction on the exact active-digest `23505` (DECISIONS.md §2 owner resolution).
 - Application services slice: narrow output ports (`ImportBatchOutputPort`, `StudentOutputPort`, `ImportRowErrorOutputPort`), application-owned input/result types, adapters implementing the ports, and four distinct proxied `REQUIRES_NEW` transaction beans plus a fresh-transaction digest lookup. D-28 upsert uses sorted `INSERT … ON CONFLICT DO NOTHING`, `SELECT … FOR UPDATE` on conflict, normalized-field comparison, and no update for unchanged rows.
 - `ClearGradeApplicationHealthTest` now starts the full context against real PostgreSQL (Flyway + Hibernate `validate`) instead of excluding datasource auto-configuration.
 - Application services slice merged on `main` via PR #7 as squash commit `85e3df9`; final diagram alignment recorded in `a4678bc`.
 - CSV ingestion slice: production `CommonsCsvFileParser` in `adapters.in.csv` (BOM-aware, explicit Commons CSV format, header mapping, extra-column support) adopts the approved C-4 physical-line mechanism; `CsvRowValidator` reuses existing domain validators and collects one `RowError` per occurrence. `ImportApplicationService.importFile(byte[], String)` computes SHA-256 from original bytes and owns T1→T2→parse/validate→T3, with T4 on fatal failure. `commons-csv` moved to compile scope (D-04).
 - Production CSV adapter details: explicit `CSVFormat` (comment markers disabled), BOM stripping before decoding to text, header normalization/uniqueness/width checks, C-4 `CSVRecord.getCharacterPosition()` plus independent `PhysicalLineMap`, all-empty/blank row skipping, recoverable `WRONG_FIELD_COUNT` rows, and file-level fatal failures for unterminated quotes/empty/header-only files. Safe administrator messages never carry raw CSV or PII; the original parser cause is retained.
 - Row validation via `CsvRowValidator` reuses existing domain value objects (including the canonical U-01 email rule), collects every applicable error occurrence, reserves the first normalized ID even when another field is invalid (I-02), and pins every occurrence to the physical starting line. Counts are overflow-guarded in T3 before narrowing to persisted integers.
 - Benchmark acceptance is mandatory and self-contained: `CsvImportBenchmarkTest` generates deterministic, PII-free synthetic CSVs inside test code (no `reference/` dependency, no `assumeTrue`, no skips), then exercises the real parser/validator/use case/PostgreSQL path. It produces exactly **25/16/9** and **5000/4685/315 rejected/348 occurrences**. Measured 2026-11-20 with JDK 21 in the final `verify` run: small **158 ms**, sample **4,291 ms** (informational, no invented SLA). The original supplied pack remains git-ignored and is never required for CI.
 - Review follow-ups closed: T2 (`PROCESSING` transition) now runs inside the guarded failure path, so a T2 failure triggers T4 and durably marks the reserved batch `FAILED` via the documented `RECEIVED → FAILED` transition; the original exception remains primary with any T4 failure attached as suppressed.
 - G4→G5 REST API: `POST /api/imports` (multipart `file`, `201`/`200` deduplicated/`409`/`400`/`413`/`415`/`500`), `GET /api/imports/{id}`, `GET /api/imports/{id}/errors`, `GET /api/students`, `GET /api/students/{studentId}`. Dedicated DTOs, allowlisted sort, bounded pagination (errors 50/100, students 25/100), strict UTF-8 and filename checks, and a consistent error envelope with correlation id for `500`. Servlet multipart limits are derived from `cleargrade.upload.max-file-size` (default `10485760` bytes = 10 MiB). No JPA entity is returned; Actuator remains health-only outside `/api`.
  - G4→G5 frontend: typed API client (`get`/`upload`, `ApiError` with code/details/correlation id), TanStack Query hooks, and real-data screens — upload with busy/empty/failure states and summary (rows read, new/changed/unchanged, rejected vs problem occurrences, code breakdown), paged row problems with physical lines, batch lookup by id, searchable/filterable/sortable student directory with loading/empty/error states, and student report with derived grade/percentage and PASS badge. Reusable `StatCard`, `DataQualitySummary`, `ProblemTable`, `Pagination`, `StudentTable`, `ResultBadge`, `FileDropzone` and state components; no mock records or fabricated metrics.
  - Phase 6/7: multi-stage non-root backend/frontend Docker images; Compose stack (PostgreSQL + backend + frontend) with healthcheck-ordered startup and persistent volume; unprivileged nginx proxies `/api` and `/actuator/health` to the backend; `scripts/start.*`, `scripts/test.*` and `scripts/seed.*` provide one-command startup, release checks and HTTP seeding. Deterministic PII-free seed CSVs demonstrate new/changed/unchanged/rejected outcomes; repeated uploads are reported as deduplicated/conflicting. Frontend visual system unified around UX_SPEC tokens with shared component classes, skip link, semantic tables/captions, breadcrumb, visible focus states and reduced-motion support. Final README, `NOTES.md` and pre-publication security audit complete; CI now runs frontend tests.


## 2. Owner-ratified decisions (recorded in DECISIONS.md §1)

`D-07` grading (A≥80/B≥70/C≥60/D≥50/F<50; PASS ≥50; no subject threshold), `U-01` email (pragmatic validation, plus-addressing/subdomains; no RFC claim), `U-02` cross-file upsert (accepted rows only; preserve first-seen; new/changed/unchanged), `U-03` no authentication (local/trusted only), `U-04` support all README variations, `U-06` configurable 10 MiB/413, `I-01` header-width field count, `I-02` first occurrence reserves ID, `I-03` inclusive age 5–25, `I-04` injected clock with 2026-11-19 benchmark, `D-17` bonus deferred, `D-19` profile-based configuration (location revised by D-20), `D-20` external local configuration directory.

## 3. Open technical spike (blocker for phase 3)

| Spike | Status | Gate |
|---|---|---|
| **C-4 physical line numbers** — prove the chosen CSV library reports the physical starting line with header = 1 across embedded newlines, blank lines, a malformed row after the newline and a subsequent valid row; counting `Reader` not accepted untested | **EVIDENCE APPROVED by owner 2026-11-20** (PR #3 `e5181c1`: 18 spike tests green); all documented spike limitations carried forward into production CSV implementation | **G2→G3 PASSED 2026-11-20** (phase 3 unblocked) |

## 4. Verification evidence (executed 2026-11-20)

| Check | Command | Result |
|---|---|---|
| Spring Boot / Java compatibility | Spring Initializr metadata + Maven Central | `4.1.2` GA; Java `21` supported |
| Backend compile + tests | `.\mvnw.cmd -B test` (JDK 21) | **206 tests, 0 failures/errors/skips** (195 prior + 11 REST API integration tests against real PostgreSQL) |
| Focused CSV/validation tests | `.\mvnw.cmd -B "-Dtest=CommonsCsvFileParserTest,CsvRowValidatorTest,ImportApplicationServiceUnitTest,BatchFinalisationTxTest,PhysicalLineTrackingSpikeTest" test` | 41 tests, 0 failures/errors |
| CSV + benchmark integration | `.\mvnw.cmd -B "-Dtest=CsvImportIntegrationTest,CsvImportBenchmarkTest" test` | 14 tests, 0 failures/errors/skips; both mandatory synthetic benchmarks executed with exact counts |
| PostgreSQL migration integration | `.\mvnw.cmd -B -Dtest=CoreSchemaMigrationTest test` (JDK 21, `postgres:16-alpine`) | 9 tests, 0 failures/errors against a real Testcontainer, including status/count and SHA-256 SQLSTATE assertions |
| Persistence integration | `.\mvnw.cmd -B "-Dtest=PersistenceMapperTest,PersistenceIntegrationTest" test` (JDK 21, `postgres:16-alpine`) | 18 tests, 0 failures/errors; Flyway + Hibernate `validate` + repository round trips |
| Application transaction integration | `.\mvnw.cmd -B "-Dtest=ApplicationTransactionIntegrationTest,ImportApplicationServiceUnitTest,DigestViolationClassificationTest" test` (JDK 21, `postgres:16-alpine`) | 18 tests, 0 failures/errors; T1–T4 boundaries (including T2 failure → T4), exact digest index, upsert counts, concurrency and suppressed failure-finalisation error |
| Backend full verify | `.\mvnw.cmd -B verify` | Passed, 206 tests green, 0 skipped |
| REST API integration | `.\mvnw.cmd -B "-Dtest=RestApiIntegrationTest" test` | 11 tests, 0 failures/errors; real MockMvc → application → PostgreSQL path for upload outcomes, batch/error reads, directory/report and health |
| Frontend tests | `npm test` (Vitest + Testing Library) | 14 tests, 0 failures; API client, upload workflow/states, row problems, directory search/filter/pagination/states, report/not-found |
| Frontend typecheck | `npm run typecheck` | Passed |
| Frontend production build | `npm run build` | Passed (Vite build, 2,212 modules) |
| Packaged JAR config safety | `jar tf` on built JAR | Only `application.properties` packaged; no local profile |
| Application startup + DB connection | `java -jar … --spring.profiles.active=local` | `/actuator/health` → `200`, status `UP` |
| Actuator restriction | HTTP probes of `/actuator/env`, `/actuator/beans`, `/actuator/info` | All `404`; only health exposed |
| Compose config | `docker compose config --quiet` | Passed |
| PostgreSQL health | `docker compose ps` / `docker inspect` | `healthy`; `pg_isready` accepting connections |
| Frontend install | `npm ci` | Passed, 0 vulnerabilities |
| Frontend typecheck | `npm run typecheck` | Passed |
| Frontend production build | `npm run build` | Passed |
| Docker image builds | `docker compose build` | Passed — multi-stage `cleargrade/backend:local` (Maven → JRE, non-root) and `cleargrade/frontend:local` (Node → unprivileged nginx) |
| Compose config | `docker compose config --quiet` | Passed (PostgreSQL + backend + frontend, healthcheck-ordered, named volume) |
| Fresh Compose startup | `docker compose down -v; docker compose up -d` | Passed — PostgreSQL `healthy` (`pg_isready`), backend `healthy` (actuator), frontend `healthy`; `GET /` → 200, `/actuator/health` via nginx proxy → 200, SPA deep route → 200 |
| HTTP seed from clean DB | `powershell -File scripts/seed.ps1` | `initial.csv` 201: rowsRead 12, imported 8 new, rejected 4, problems 4; `update.csv` 201: rowsRead 12, 3 changed + 5 unchanged, rejected 4, problems 4 |
| Idempotent replay | re-run `scripts/seed.ps1` | Both fixtures returned `200 deduplicated`; no third batch (`GET /api/imports/3` → 404) |
| Restart persistence | `docker compose down; docker compose up -d` | Students 8 and batch 2 `COMPLETED` retained from the named volume; all containers healthy |
| One-command scripts | `scripts/start.ps1`, `scripts/test.ps1` | Passed (start waits for health, prints URLs; release checks run backend verify + frontend tests/typecheck/build + compose config + diff check) |
| Public-repo audit | tracked-file/history scan | No tracked `.env` or real credentials; no key/token patterns in history; CI uses no secrets; no critical/high issue found (no auth by design) |
| Secret handling | Hash-equality check without printing values | Same generated password in `.env` and local profile; both git-ignored |

Toolchain: JDK `21.0.10` (installed at `C:\Program Files\Java\jdk-21.0.10`, selected via `JAVA_HOME`; PATH default is JDK 17), Maven `3.9.14`, Node `24.11.1`, npm `11.6.2`, Docker `29.4.3`, Compose `v5.1.3`.

## 5. Residual risks

| Risk | Status | Mitigation / note |
|---|---|---|
| Benchmark counts drift with real date | Mitigated | Fixed clock `2026-11-19` in tests (I-04); document in `NOTES.md` |
| Unseen CSV contents unknown | Open | U-04 design + generated fixtures (TEST_STRATEGY §8) |
| Unusual valid email forms | Open | U-01 rule documented; plus-address/subdomain supported |
| Concurrent identical uploads | Implemented & tested | Partial unique index + digest reservation (C-1/C-2); real PostgreSQL simultaneous-reservation and divergent D-28 upsert tests green |
| Decimal precision | Implemented in domain | `BigDecimal` + unconstrained `NUMERIC`, no rounding (C-3) |
| Private config leaking into builds | Mitigated | D-20: outside Maven resources, Git-ignored, excluded by `.dockerignore`, absent from built JAR |
| Host port 5432 occupied locally | Mitigated | `POSTGRES_PORT` made configurable; local `.env` uses a free host port |
| No authentication | Ratified | Local/trusted evaluation only; must not be publicly exposed (U-03) |
| Domain boundary erosion | Mitigated | Source-scanning framework-independence test now asserts the exact production source set; no ArchUnit dependency added |

## 6. Next actions

1. Phase 3B.2.1 schema slice is merged on `main` via PR #5; Phase 3B.2.2 is merged on `main` via PR #6 (`dce122f`).
2. **G2→G3 formally passed by owner 2026-11-20** on the merged PR #3 C-4 evidence, with all documented spike limitations carried forward.
3. Application output ports and T1–T4 transaction/digest/upsert services are implemented per D-23–D-28; no schema changes were needed.
4. **Production CSV adapter, import use case and G3→G4 acceptance are complete** on `feat/csv-import-adapter`; all C-4 limitations carried forward; no migration/schema changes. **G3→G4 now passes.**
5. **G4→G5 complete on `feat/rest-api-and-frontend-integration`**: documented `/api` endpoints and three real-data screens verified; no schema changes, no authentication introduced.
6. **Phase 6/7 complete**: multi-stage Docker images, full Compose stack, HTTP seed workflow, one-command scripts, final README and `NOTES.md` implemented; G6→G7 and G7→handover passed.
7. Deferred: D-21 stale-`RECEIVED` recovery sweep; bonus D-17 error-CSV download, mark bars and print; U-01 exact pragmatic email grammar boundary remains an owner decision (the canonical domain validator is reused unchanged).

## 7. Scope notes

Current-result snapshot only (no term/year invented, D-11). Benchmark numbers are regression fixtures, never application logic (D-12). `NOTES.md` is delivered as the final handover note. No supplied sample CSV data or PDF content is reproduced; the only committed CSV fixtures are deterministic synthetic seed data with no PII. Domain rules, V1 schema, JPA adapters, application ports/T1–T4 services, the production CSV adapter, import use case, REST API, real-data frontend, Docker/Compose delivery and HTTP seeding are implemented. No batch-list endpoint is documented, so the UI offers batch-id lookup plus the current session result rather than a fabricated history. Real credentials are not committed. The six C-4 limitations remain carried forward unchanged: file-level unterminated quotes, disabled comment markers, `read(Path)` BOM stripping, caller-side BOM stripping for string input, UTF-16 offset semantics and explicit header-width consistency (no `isConsistent()` reliance).
