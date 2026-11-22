# ClearGrade — Development Plan

Status: **G7→handover passed; all eight phases (0–7) complete.** REST API, real-data frontend, Docker/Compose delivery, HTTP seed workflow, one-command scripts, final README and `NOTES.md` are implemented and verified. Owner decisions ratified 2026-11-19, transaction boundary resolved 2026-11-20 (DECISIONS.md §1/§2).

## 1. Planned development sequence

| Phase | Scope | Exit criterion |
|---|---|---|
| 0 — Specification & design | PDF/CSV analysis, documentation, owner ratification | **G0→G1 passed (§6)** |
| 1 — Foundation **(scaffolded, no migrations yet)** | Maven/Spring Boot bootstrap (Java 21, Spring Boot 4.1.2, `mvnw`), Docker Compose + Postgres, profile-based configuration (§3), injected `Clock` (I-04), configurable 10 MiB limit (U-06) | Skeleton starts with the local profile; `/actuator/health` answers; minimal context/health verification test green. **Flyway migrations and the app Docker image remain for later phases.** |
| 2 — Domain **(3B.1 implemented + reviewed)** | Pure model with `BigDecimal` marks (C-3), validation, grading (D-07), error-code vocabulary; review remediation for count overflow, row-error line, batch/outcome construction and documentation | Domain tests green; source-scanning framework-independence test green; **C-4 spike evidence approved and G2→G3 passed by owner 2026-11-20** |
| 3 — Ingestion **(schema + persistence + application services + production CSV + import use case implemented; G3→G4 passed)** | Flyway V1 schema, JPA entities/repositories/mappers, application output ports, T1–T4 transaction services, exact digest recovery, D-28 upsert, C-4 physical-line CSV adapter, domain-backed row validation/error occurrences and cohesive import use case | Migration, persistence and application transaction tests green; **C-4 gate passed (owner 2026-11-20), all six limitations carried forward**; idempotency/concurrency tests green; benchmarks 16/9 and 4,685/315/348 with fixed clock — **achieved 2026-11-20** |
| 4 — REST API **(implemented; G4→G5 passed)** | Controllers, DTOs, global error handler, `201/200/409/400/413/415/500` upload paths, batch/error/student reads, management-path separation (T-5) | Historical phase-gate result: 11 real-PostgreSQL integration tests; current `RestApiIntegrationTest`: 15 tests. No entity leakage; Actuator health-only — **achieved 2026-11-20** |
| 5 — Frontend **(implemented; G4→G5 passed)** | React Router + TanStack Query, real-data screens with all loading/empty/error states, design tokens (UX_SPEC §2); later extended with the Import History tab and CSV downloads in PR #12 | Historical exit criterion: 14 frontend tests, typecheck and production build pass; **current suite: 77 tests** — achieved 2026-11-20 |
| 6 — Seed & bonus **(complete; G6→G7 passed)** | Deterministic PII-free CSVs posted through `POST /api/imports` by `scripts/seed.*`; no direct SQL. Error CSV download and class/grade filters were implemented in PR #12; only per-subject mark bars and the printable report remain deferred (D-17) | Seed works through the real pipeline with new/changed/unchanged/rejected outcomes and clear idempotency; core not regressed — **achieved 2026-11-20** |
| 7 — Hardening & handover **(complete; G7→handover passed)** | Multi-stage Docker images, Compose full stack, one-command scripts, security audit, `NOTES.md`, final README | One-command startup/test verified; docs accurate; no committed secrets; no critical/high security issue — **achieved 2026-11-20** |

Phases are sequential; a failed gate stops the next phase. Bonus work starts only after phase 5 passes (D-17).

## 2. Code conventions

- **Java 21**, package root `dev.cleargrade`; layers `domain`, `application` (use cases, ports, **separate transaction service beans / `TransactionTemplate`**), `adapters.in.web`, `adapters.in.csv`, `adapters.out.persistence`, `configuration` (the boundary names mandated for Phase 2B; ARCHITECTURE.md §8 is the earlier proposed naming). **No `REQUIRES_NEW` self-invocation.**
- Domain has no Spring/JPA/Commons CSV imports. Marks use `BigDecimal`; no silent rounding (C-3). Adapters depend inward on ports. Flyway owns the schema (`V1__create_core_schema.sql`); Hibernate remains `ddl-auto=validate`. Mark columns are unconstrained `NUMERIC` (D-24). Final count columns must be NULL unless batch status is `COMPLETED`, matching `ImportBatch`. Migration integration tests use a shared Testcontainers `postgres:16-alpine` container with explicit table truncation.
- JPA entities, repositories and explicit mappers live under `adapters.out.persistence.{entity,repository,mapper}`; `CHAR` columns use `@JdbcTypeCode(SqlTypes.CHAR)` so Hibernate `validate` matches PostgreSQL `bpchar`. Spring Boot 4.1 Flyway auto-configuration requires `spring-boot-flyway`.
- Application output ports live under `application.port.out`; adapters in `adapters.out.persistence` implement them and never expose entities. T1/T2/T3/T4 are distinct proxied `@Transactional(REQUIRES_NEW)` beans (`application.service.*Tx`) so no self-invocation bypasses a boundary; duplicate recovery re-reads the digest in a separate fresh-transaction bean. D-28 upsert uses sorted `INSERT … ON CONFLICT DO NOTHING` plus `SELECT … FOR UPDATE` on conflict. The full Spring context startup test runs against real PostgreSQL.
- CSV ingestion: `adapters.in.csv.CommonsCsvFileParser` implements the application-owned `port.in.CsvFileParser` with explicit `CSVFormat`, BOM stripping, header mapping and the C-4 physical-line map; it returns only application-owned `ParsedCsv` types and performs no business validation or transaction handling. `CsvRowValidator` (application) reuses domain value objects and returns application-owned `ValidatedRows`; `ImportApplicationService` owns the T1→T2→parse/validate→T3 (or T4) sequence; T2 is inside the guarded path so a failed `PROCESSING` transition also routes to T4 (`RECEIVED → FAILED`), with the original exception preserved and any T4 failure suppressed. No CSV library or JPA type crosses into the domain.
- Controllers accept/return DTOs only. Validation returns a complete error-occurrence list; never fail fast on the first field error.
- Use injected `Clock` everywhere (I-04). No `LocalDate.now()` in domain/application.
- **TypeScript strict mode**; `src/types` for API types, `src/api` for the client; **React Router** routing, **TanStack Query** for server state, local component state for UI; **no Redux** without justification. Tailwind utilities with UX_SPEC tokens.
- Conventional commits (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`). No secrets, sample CSV data or PDF in the repo.

## 3. Configuration strategy (profile-based local, environment-based deployment)

Decisions D-19 (profile strategy) and D-20 (external location correction). The shared defaults file, the committed example, the git-ignored private local file and the root `.env` are now created; the private files are never committed.

Required structure:

```text
backend/
├── src/main/resources/
│   └── application.properties                  # committed, non-secret shared defaults
└── config/
    ├── application-local.properties            # PRIVATE — git-ignored, never packaged
    └── application-local.example.properties    # committed template, placeholders only
```

| File | Committed? | Contents |
|---|---|---|
| `backend/src/main/resources/application.properties` | Yes | Shared, **non-secret** defaults only: server port, Flyway enabled, `spring.jpa.hibernate.ddl-auto=validate`, upload limit default |
| `backend/config/application-local.example.properties` | Yes | Placeholder datasource URL/username/password only — template, never real values |
| `backend/config/application-local.properties` | **No — git-ignored** | Operator's actual local PostgreSQL URL/username/password |

Rules:

1. `application.properties` holds shared non-secret defaults **only** — never actual credentials.
2. The private `application-local.properties` lives in **`backend/config/`**, outside `src/main/resources`, so Maven's default resource packaging cannot copy it into the JAR.
3. `backend/config/application-local.properties` is git-ignored; the sibling `.example` file is committed with placeholders only.
4. Docker Compose receives database credentials through the **git-ignored root `.env`** as environment variables — never through private property files.
5. `.dockerignore` excludes root `.env`, `reference/` and `backend/config/application-local.*` from the Docker build context; no credentials are baked into images.
6. Flyway manages all schema changes; Hibernate stays at `ddl-auto=validate` so the application never mutates schema.
7. **Spring Boot discovery:** when the process working directory is `backend/`, Spring Boot's default config-data search includes `file:./config/`. With the `local` profile active, it loads `backend/config/application-local.properties` from there; the file is external to the classpath and is not part of the packaged application. No `spring.config.import` of a private file is needed.
8. **Local activation (Windows PowerShell):**

   ```powershell
   Set-Location backend
   $env:SPRING_PROFILES_ACTIVE = "local"
   mvn spring-boot:run
   ```

   Copy `config/application-local.example.properties` to `config/application-local.properties` and fill in local values first. The JVM equivalent is `-Dspring.profiles.active=local`.
9. The `local` profile is **never active by default**: Docker Compose, images and deployment do not set `SPRING_PROFILES_ACTIVE=local`. Credentials there come solely from environment variables.
10. The Compose host port is configurable via `POSTGRES_PORT` in the root `.env`. Keep the datasource URL in `backend/config/application-local.properties` on the same port. This avoids conflicts with any PostgreSQL already listening on host port 5432.

## 4. Git workflow

- `main` protected baseline; short-lived feature branches; one reviewed PR per phase with tests passing.
- No build output, `.env`, `backend/config/application-local.properties` or other credentials committed. Access shared with reviewers at handover.
- Git was initialized during Phase 2A and the documentation blueprint committed; PR #4 was squash-merged to `main` as `a1605ec` and PR #5 as `6cf4a4f`. Phase 3B.2.2 work stays on `feat/database-persistence-adapters`.

## 5. Commands

| Intent | Command | Status |
|---|---|---|
| Backend compile + tests | `cd backend; .\mvnw.cmd -B test` | Runnable (current suite: 294 tests across 41 classes; 293 passed, 1 skipped) |
| PostgreSQL migration integration tests | `cd backend; .\mvnw.cmd -B -Dtest=CoreSchemaMigrationTest test` | Runnable (Testcontainers `postgres:16-alpine`, Docker daemon required; 9 tests) |
| Persistence integration tests | `cd backend; .\mvnw.cmd -B "-Dtest=PersistenceMapperTest,PersistenceIntegrationTest" test` | Runnable (18 tests; real Testcontainer, Flyway + Hibernate `validate`) |
| Application transaction tests | `cd backend; .\mvnw.cmd -B "-Dtest=ApplicationTransactionIntegrationTest,ImportApplicationServiceUnitTest,DigestViolationClassificationTest" test` | Runnable (18 tests; real Testcontainer for T1–T4/exact digest/upsert concurrency, unit test for suppressed failure-finalisation error) |
| Focused CSV parser/validation tests | `cd backend; .\mvnw.cmd -B "-Dtest=CommonsCsvFileParserTest,CsvRowValidatorTest,ImportApplicationServiceUnitTest,BatchFinalisationTxTest,PhysicalLineTrackingSpikeTest" test` | Runnable (41 tests; no Docker needed) |
| CSV import + benchmark acceptance | `cd backend; .\mvnw.cmd -B "-Dtest=CsvImportIntegrationTest,CsvImportBenchmarkTest" test` | Runnable (14 tests, 0 skipped; Testcontainers PostgreSQL; deterministic synthetic fixtures generated in test code — 25/16/9 and 5000/4685/315/348) |
| REST API integration tests | `cd backend; .\mvnw.cmd -B "-Dtest=RestApiIntegrationTest" test` | Runnable (15 tests, real PostgreSQL Testcontainers + MockMvc) |
| Full backend verification | `cd backend; .\mvnw.cmd -B verify` | Runnable (current: 294 tests, 293 passed, 1 opt-in skip) |
| Frontend dependency install | `cd frontend; npm ci` | Runnable |
| Frontend tests | `cd frontend; npm test` | Runnable (current: 77 Vitest/Testing Library tests) |
| Frontend typecheck | `cd frontend; npm run typecheck` | Runnable |
| Frontend production build | `cd frontend; npm run build` | Runnable |
| One-command startup (Windows) | `powershell -ExecutionPolicy Bypass -File scripts/start.ps1` | Runnable (builds + starts full Compose stack, waits for health) |
| One-command startup (Unix) | `bash scripts/start.sh` | Runnable |
| One-command release checks | `powershell -File scripts/test.ps1` / `bash scripts/test.sh` | Runnable (backend verify, frontend tests/typecheck/build, compose config, diff check) |
| HTTP seed | `powershell -File scripts/seed.ps1` / `bash scripts/seed.sh` | Runnable (real `POST /api/imports`, idempotency-aware) |
| Docker image builds | `docker compose build` | Runnable (multi-stage backend/frontend, non-root runtime) |
| Compose config check | `docker compose config --quiet` | Runnable |
| Start PostgreSQL | `docker compose up -d` | Runnable |
| Run locally with private profile | `Set-Location backend` → `$env:SPRING_PROFILES_ACTIVE = "local"` → `.\mvnw.cmd spring-boot:run` (§3) | Runnable |
| Frontend unit tests | `npm test` | Runnable (77 tests) |
| Single-command full test | `./scripts/test.sh` | Runnable (phase 7 complete) |
| Seed messy data | `scripts/seed.ps1` / `scripts/seed.sh` posts generated CSV to `POST /api/imports` | Runnable (phase 6 complete) |

Exact script names may change once the build exists; the final README is authoritative.

## 6. Verification gates (T-2, C-4)

| Gate | Check |
|---|---|
| **G0 → G1** | **All blocking decisions approved** — D-07, U-01, U-02, U-03, U-04, U-06, I-01, I-02, I-03, I-04, D-17 — **wherever they appear in DECISIONS.md (§1 and §2), not only §2**. No active superseded identifiers. C-4 recorded as a tracked spike. |
| G1 → G2 | Docker boot + migration + health check succeed; three Compose services healthy with loopback host ports `3000` (nginx frontend), `8080` (backend) and `5432` (PostgreSQL, configurable); Actuator exposes health only; external local config verified (§3): private file outside `src/main/resources`, excluded from Git and Docker context, not activated in Docker |
| **G2 → G3 — PASSED 2026-11-20** | Domain tests green; architecture boundary test green; **C-4 physical-line-number spike evidence on `main` from PR #3** (TEST_STRATEGY.md §2.2) formally signed off by owner; all documented spike limitations carried forward into production CSV implementation |
| **G3 → G4 — PASSED 2026-11-20** | Benchmarks green with fixed clock (25/16/9; 5000/4685/315/348); error occurrence counts correct; idempotency (completed → `200` semantics via `DigestConflictException(COMPLETED)`, active → `409` semantics, FAILED retry), real-PostgreSQL partial-unique concurrency and new/changed/unchanged counters green — **all verified on `feat/csv-import-adapter`** |
| **G4 → G5 — PASSED 2026-11-20** | API contract, upload/size/duplicate/failure mapping, paginated row errors, directory filtering and report tests green; the original three screens use real API data; no entity leakage; Actuator health-only — **verified on `feat/rest-api-and-frontend-integration`**. Later extended by PR #12 with the Import History tab and CSV downloads |
| G5 → G6 | The original three screens work end to end; all specified states present; design tokens applied; current UI additionally includes the Import History tab and two CSV downloads |
| **G6 → G7 — PASSED 2026-11-20** | Seed posts through the real import API with new/changed/unchanged/rejected outcomes; repeated uploads reported as deduplicated; no bonus enabled, core not regressed |
| **G7 → handover — PASSED 2026-11-20** | One-command startup and release checks verified; Docker images, Compose startup, readiness and persistence verified; README and `NOTES.md` match reality; no committed secrets |

C-4 was a hard gate: PR #3 provides the physical-line-number evidence, which the owner formally approved on 2026-11-20, passing G2→G3. The documented spike limitations (`backend/src/test/resources/csv-spike/README.md`) must be carried into the production CSV adapter.

The owner also resolved the D-25/D-26 transaction-boundary conflict on 2026-11-20: T1 commits `RECEIVED` reservation, T2 commits `PROCESSING`, T3 atomically commits upserts/errors/final counts/`COMPLETED`, and T4 separately commits `FAILED` after a T3 rollback; duplicate recovery occurs outside the failed transaction on the exact active-digest `23505` (DECISIONS.md §2).

## 7. Definition of done

Import Quality Center (including the Import History tab and both CSV downloads), Student Directory, Student Report Finder and Individual Student Report working end to end; graceful bad-file/bad-row handling; one-command tests; Dockerfiles + three-service Compose; seed script through the import API; honest README and one-page `NOTES.md`; clean history; no committed secrets. Per-subject mark bars and the printable report remain deferred (D-17).

PR #12 (application changes for history and CSV exports) is merged to `main` as `ae90408`; PR #11 contains documentation/screenshots on top of those changes. The stale-`RECEIVED` recovery sweep is still deferred (D-21).
