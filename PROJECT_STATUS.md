# ClearGrade — Project Status

Last updated: 2026-11-22 · **Release engineering complete: all eight phases (0–7) passed.** PR #8 (G3→G4) is merged to `main` as `acc83a1`. The eight-phase application is complete, including the REST API, real-data SPA, multi-stage Docker images, full Compose stack, HTTP seed workflow and one-command scripts.

Current work is split by design: **PR #11** (`docs/readme-evaluator-polish`, this branch) contains documentation and screenshots only; **PR #12** (`fix/consistent-interactive-cursors`) delivered the application changes for Import History, per-batch CSV exports and UI polish and is already merged to `main` as `ae90408`. The documented features therefore exist on `main`; PR #11 adds documentation on top. Historical phase results below are retained verbatim and labelled as historical where their figures have since moved. No authentication exists; deployment is local/trusted only.

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
 - Benchmark acceptance is mandatory and self-contained: `CsvImportBenchmarkTest` generates deterministic, PII-free synthetic CSVs inside test code (no `reference/` dependency, no `assumeTrue`, no skips), then exercises the real parser/validator/use case/PostgreSQL path. It produces exactly **25/16/9** and **5000/4685/315 rejected/348 occurrences**. **Historical measurements:** small **158 ms** / sample **4,291 ms**, small **130 ms** / sample **3,892 ms**, and small **163 ms** / sample **5,407 ms**. The **latest** Surefire report records **131 ms / 3,334 ms**. Timings are environment-dependent observations, not an invented SLA. The original supplied pack remains git-ignored and is never required for CI.
 - Review follow-ups closed: T2 (`PROCESSING` transition) now runs inside the guarded failure path, so a T2 failure triggers T4 and durably marks the reserved batch `FAILED` via the documented `RECEIVED → FAILED` transition; the original exception remains primary with any T4 failure attached as suppressed.
  - G4→G5 REST API: `POST /api/imports` (multipart `file`, `201`/`200` deduplicated/`409`/`400`/`413`/`415`/`500`), `GET /api/imports/{id}`, `GET /api/imports/{id}/errors`, `GET /api/students`, `GET /api/students/{studentId}`. Dedicated DTOs, allowlisted sort, bounded pagination (errors 50/100, students 25/100), strict UTF-8 and filename checks, and a consistent error envelope with correlation id for `500`. Servlet multipart limits are derived from `cleargrade.upload.max-file-size` (default `10485760` bytes = 10 MiB). No JPA entity is returned; Actuator remains health-only outside `/api`.
   - Current REST API (merged PR #12, `ae90408`) also includes `GET /api/imports?page&size` (newest-first history), `GET /api/imports/{id}/report` (one persisted summary record as CSV) and `GET /api/imports/{id}/row-problems` (one CSV record per persisted validation issue). The earlier proposed `/errors.csv` route was never implemented. CSV exports neutralise formula-like values and contain persisted summary/issue data, not reconstructed rejected source rows.
  - G4→G5 frontend: typed API client (`get`/`upload`, `ApiError` with code/details/correlation id), TanStack Query hooks, and real-data screens — upload with busy/empty/failure states and summary (rows read, new/changed/unchanged, rejected vs problem occurrences, code breakdown), paged row problems with physical lines, batch lookup by id, searchable/filterable/sortable student directory with loading/empty/error states, and student report with derived grade/percentage and PASS badge. Reusable `StatCard`, `DataQualitySummary`, `ProblemTable`, `Pagination`, `StudentTable`, `ResultBadge`, `FileDropzone` and state components; no mock records or fabricated metrics.
  - Phase 6/7: multi-stage non-root backend/frontend Docker images; Compose stack (PostgreSQL + backend + frontend) with healthcheck-ordered startup and persistent volume; unprivileged nginx proxies `/api` and `/actuator/health` to the backend; `scripts/start.*`, `scripts/test.*` and `scripts/seed.*` provide one-command startup, release checks and HTTP seeding. Deterministic PII-free seed CSVs demonstrate new/changed/unchanged/rejected outcomes; repeated uploads are reported as deduplicated/conflicting. Frontend visual system unified around UX_SPEC tokens with shared component classes, skip link, semantic tables/captions, breadcrumb, visible focus states and reduced-motion support. Final README, `NOTES.md` and pre-publication security audit complete; CI now runs frontend tests.


## 2. Owner-ratified decisions (recorded in DECISIONS.md §1)

`D-07` grading (A≥80/B≥70/C≥60/D≥50/F<50; PASS ≥50; no subject threshold), `U-01` email (pragmatic validation, plus-addressing/subdomains; no RFC claim), `U-02` cross-file upsert (accepted rows only; preserve first-seen; new/changed/unchanged), `U-03` no authentication (local/trusted only), `U-04` support all README variations, `U-06` configurable 10 MiB/413, `I-01` header-width field count, `I-02` first occurrence reserves ID, `I-03` inclusive age 5–25, `I-04` injected clock with 2026-11-19 benchmark, `D-17` bonus originally deferred (error CSV and filter later implemented in PR #12; mark bars and print still deferred), `D-19` profile-based configuration (location revised by D-20), `D-20` external local configuration directory.

## 3. Resolved technical spike (C-4)

| Spike | Status | Gate |
|---|---|---|
| **C-4 physical line numbers** — prove the chosen CSV library reports the physical starting line with header = 1 across embedded newlines, blank lines, a malformed row after the newline and a subsequent valid row; counting `Reader` not accepted untested | **EVIDENCE APPROVED by owner 2026-11-20** (PR #3 `e5181c1`: 18 spike tests green); all documented spike limitations carried forward into production CSV implementation | **G2→G3 PASSED 2026-11-20** (phase 3 unblocked) |

## 4. Verification evidence

The rows dated 2026-11-20 are **historical milestone evidence** retained for traceability. Current figures are the first four rows below.

| Check | Command | Result |
|---|---|---|
| Current backend suite | Latest Surefire reports in `backend/target/surefire-reports` | **294 tests: 293 passed, 0 failures, 0 errors, 1 skipped** (`LiveGeminiSmokeTest`) across **41 test classes** |
| Current frontend tests | `npm test` (77 `it(...)` definitions in 11 files) | **77 tests** |
| Current benchmark evidence | `CsvImportBenchmarkTest` Surefire report | **131 ms** small / **3,334 ms** 5,000-row; counts **25/16/9** and **5,000/4,685/315 rejected/348 occurrences**; timings are environment-dependent |
| Current CI | GitHub Actions on merged PR #12 commit `ae90408` (run `45039297026`); PR #11 head `b9fb4d3` earlier | Backend and Frontend checks **SUCCESS** on `ae90408`; both jobs also passed on `b9fb4d3` earlier |
| Spring Boot / Java compatibility | Spring Initializr metadata + Maven Central | `4.1.2` GA; Java `21` supported |
| Backend compile + tests | `.\mvnw.cmd -B test` (JDK 21) | **Historical (2026-11-20):** 206 tests, 0 failures/errors/skips (195 prior + 11 REST API integration tests against real PostgreSQL) |
| Focused CSV/validation tests | `.\mvnw.cmd -B "-Dtest=CommonsCsvFileParserTest,CsvRowValidatorTest,ImportApplicationServiceUnitTest,BatchFinalisationTxTest,PhysicalLineTrackingSpikeTest" test` | 41 tests, 0 failures/errors |
| CSV + benchmark integration | `.\mvnw.cmd -B "-Dtest=CsvImportIntegrationTest,CsvImportBenchmarkTest" test` | 14 tests, 0 failures/errors/skips; both mandatory synthetic benchmarks executed with exact counts |
| PostgreSQL migration integration | `.\mvnw.cmd -B -Dtest=CoreSchemaMigrationTest test` (JDK 21, `postgres:16-alpine`) | 9 tests, 0 failures/errors against a real Testcontainer, including status/count and SHA-256 SQLSTATE assertions |
| Persistence integration | `.\mvnw.cmd -B "-Dtest=PersistenceMapperTest,PersistenceIntegrationTest" test` (JDK 21, `postgres:16-alpine`) | 18 tests, 0 failures/errors; Flyway + Hibernate `validate` + repository round trips |
| Application transaction integration | `.\mvnw.cmd -B "-Dtest=ApplicationTransactionIntegrationTest,ImportApplicationServiceUnitTest,DigestViolationClassificationTest" test` (JDK 21, `postgres:16-alpine`) | 18 tests, 0 failures/errors; T1–T4 boundaries (including T2 failure → T4), exact digest index, upsert counts, concurrency and suppressed failure-finalisation error |
| Backend full verify | `.\mvnw.cmd -B verify` | **Historical (2026-11-20):** passed, 206 tests green, 0 skipped |
| REST API integration | `.\mvnw.cmd -B "-Dtest=RestApiIntegrationTest" test` | 15 tests, 0 failures/errors; real MockMvc → application → PostgreSQL path for upload outcomes, batch/error reads, history, CSV exports, directory/report and health |
| Frontend tests | `npm test` (Vitest + Testing Library) | **Historical (2026-11-20):** 45 tests, 0 failures; API client, upload workflow/states, row problems, directory search/filter/pagination/states, report/not-found, student report finder and intelligence panel |
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
| Benchmark counts drift with real date | Mitigated | Injected `Clock` in date-dependent tests (I-04); benchmark fixtures are generated deterministically; document in `NOTES.md` |
| Unseen CSV contents unknown | Open | U-04 design + generated fixtures (TEST_STRATEGY §8) |
| Unusual valid email forms | Open | U-01 rule documented; plus-address/subdomain supported |
| Concurrent identical uploads | Implemented & tested | Partial unique index + digest reservation (C-1/C-2); real PostgreSQL simultaneous-reservation and divergent D-28 upsert tests green |
| Decimal precision | Implemented in domain | `BigDecimal` + unconstrained `NUMERIC`, no rounding (C-3) |
| Private config leaking into builds | Mitigated | D-20: outside Maven resources, Git-ignored, excluded by `.dockerignore`, absent from built JAR |
| Host port 5432 occupied locally | Mitigated | `POSTGRES_PORT` made configurable; local `.env` uses a free host port |
| No authentication | Ratified | Local/trusted evaluation only; must not be publicly exposed (U-03) |
| Domain boundary erosion | Mitigated | Source-scanning framework-independence test now asserts the exact production source set; no ArchUnit dependency added |

## 6. Next actions

Current (2026-11-22), remaining Es Magico checklist:

1. The approximately 52-second product walkthrough and thumbnail are integrated in README through **PR #11**, with the existing eight-image gallery preserved. The owner has confirmed the updated Google Drive link and that the file is shared as **Anyone with the link → Viewer**; the README thumbnail opens the confirmed video.
2. Complete final review of PR #11 (documentation/screenshots); PR #12 (application) is already merged to `main` (`ae90408`), so `main` already contains the documented features.
3. Send the Es Magico submission email with the README, both PRs, CI results and the video.
4. Deferred, not part of submission: D-21 stale-`RECEIVED` recovery sweep; D-17 mark bars and printable report; a persistent privacy-safe AI cache; live Gemini verification with a user key; U-01 exact pragmatic email grammar boundary (the canonical domain validator is reused unchanged).

**Historical next-action list (2026-11-20, retained for traceability):** schema PRs #5/#6 merged; G2→G3 passed on PR #3 C-4 evidence; application ports and T1–T4 services implemented per D-23–D-28; production CSV adapter and G3→G4 complete on `feat/csv-import-adapter`; G4→G5 complete on `feat/rest-api-and-frontend-integration`; phases 6/7 delivered Docker/Compose, seed scripts, README and `NOTES.md`. All of these are now completed, not outstanding work.

## 7. Scope notes

Current-result snapshot only (no term/year invented, D-11). Benchmark numbers are regression fixtures, never application logic (D-12). `NOTES.md` is delivered as the final handover note. No supplied sample CSV data or PDF content is reproduced; the only committed CSV fixtures are deterministic synthetic seed data with no PII. Domain rules, V1 schema, JPA adapters, application ports/T1–T4 services, the production CSV adapter, import use case, REST API, real-data frontend, Docker/Compose delivery and HTTP seeding are implemented. A **newest-first, server-paginated import history** (`GET /api/imports`) and two **per-batch CSV exports** are implemented in merged PR #12 (`ae90408`) and displayed as a tab inside `/import`; the UI also retains batch-id lookup and current-session restoration. The persisted batch history is metadata about imports — it is **not** academic history for students, which remains out of scope (current-result snapshot only, D-11). Real credentials are not committed. The six C-4 limitations remain carried forward unchanged: file-level unterminated quotes, disabled comment markers, `read(Path)` BOM stripping, caller-side BOM stripping for string input, UTF-16 offset semantics and explicit header-width consistency (no `isConsistent()` reliance).

## 8. Post-release enhancement — ClearGrade Intelligence (2026-11-20)

The eight phases above remain complete and unchanged. This section records the new work only.

| Requirement | Status | Evidence |
|---|---|---|
| Provider-independent architecture | Complete | `application/port/out/ImportInsightProvider`, `ImportInsightService`, `InsightProviderRouter`, `ImportQualityContextBuilder`, `DeterministicInsightService`; Gemini/Ollama wire code only in `adapters/out/ai/*`; no AI framework dependency |
| Gemini primary | Complete | Interactions REST `POST /v1beta/interactions`, `x-goog-api-key`, `store=false`, explicit JSON schema, `Api-Revision: 2026-05-20`, 10 s connect / configurable read timeout, no POST retries/history; default `gemini-3.8-flash` via `GEMINI_MODEL` |
| Ollama fallback | Complete | Native `/api/chat`, `stream=false`, `format` JSON schema, `/api/tags` availability verification, optional Compose/runtime config, no startup or image model download |
| Routing and fallback | Complete | Quota/5xx/timeout/connection/unusable output → Ollama → rule engine; auth/malformed configuration remains a controlled `503`; safe reason categories and engine labels; no simultaneous provider calls |
| Deterministic engine | Complete | Offline Java thresholds across total/imported/rejected/new/changed/unchanged rows, five error codes, errors per rejected row, duplicate share; evidence-valued findings; reproducible output; never labelled AI |
| Privacy contract | Complete | Aggregate DTO only; PII sentinel tests capture real outbound payloads for both providers; blocked output markers; provider terms and `store=false` limits documented |
| Validation and separation | Complete | Strict JSON schema, allowlisted metrics, exact backend evidence values, bounded text/arrays, unknown-field rejection; measured facts separate from interpretation/advice |
| Cost control | Complete | Explicit CTA only, bounded TTL/LRU cache, context+model key, coalescing, optional cooldown, fresh/cached indicator, no insight table or migration |
| Endpoints | Complete | `GET /api/ai/status`, `POST /api/imports/{batchId}/insights` with `200/404/409/429/502/503` mapping and safe error envelope |
| Premium UI | Complete | Intelligence panel on completed imports: provider status, privacy disclosure, CTA, loading/error/retry, measured metrics, severity grouping, separated evidence, engine label, cache indicator, advisory disclaimer, offline state; UX_SPEC tokens |
| Configuration and docs | Complete | Optional env vars in `.env.example`, Compose and local profile; README, NOTES, PROJECT_STATUS, API_CONTRACT, ARCHITECTURE, UX_SPEC, SECURITY, TEST_STRATEGY and [AI_INTELLIGENCE.md](docs/AI_INTELLIGENCE.md) updated |
| Tests | Complete | Mock HTTP protocol/PII tests, routing/fallback, validation, cache/coalescing, deterministic engine, MockMvc status/error mapping, Testcontainers aggregate query, opt-in live Gemini smoke test; frontend provider/engine/state tests |

Enhancement verification (JDK 21, no `GEMINI_API_KEY`, no Ollama installed):

| Check | Command | Result |
|---|---|---|
| Focused AI suite | `.\mvnw.cmd -B "-Dtest=ProviderHttpTest,PrivacyContractTest,InsightResponseParserTest,DeterministicInsightServiceTest,InsightProviderRouterTest,ImportInsightServiceTest,AiEndpointWebTest,AiControllerTest,LiveGeminiSmokeTest" test` | 65 tests, 0 failures/errors, 1 opt-in live smoke skipped |
| Backend complete suite | `.\mvnw.cmd -B test` | **Historical (2026-11-20):** 257 tests, 0 failures/errors, 1 skipped; Testcontainers PostgreSQL green. Current suite: 294 tests (293 passed, 1 skipped) |
| Backend verify | `.\mvnw.cmd -B verify` | **Historical (2026-11-20):** build success, JAR packaged, 257 tests green |
| Frontend | `npm test`, `npm run typecheck`, `npm run build` | **Historical (2026-11-20):** 26 tests green; typecheck and Vite production build pass. Current suite: 77 tests |
| Compose config | `docker compose config --quiet` | Passed with optional AI env vars empty |
| Whitespace | `git diff --check` | No whitespace errors |

Enhancement milestone completion: **100% of the specified enhancement scope**. Authentication remains absent by
design; deployment stays local/trusted. No key, Ollama installation or live AI call is required for CI or normal
imports. Final command evidence, branch and commit SHA are recorded in the handover report and the single local
commit on `feat/cleargrade-intelligence`.
