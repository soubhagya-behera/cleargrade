# ClearGrade — Project Status

Last updated: 2026-11-20 · **Current phase: Phase 3B.2.2 JPA persistence foundation implemented and verified against real PostgreSQL.** PR #5 (V1 schema) is merged on `main`. No application ports/services, CSV ingestion, REST APIs or frontend business logic is implemented.

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

## 2. Owner-ratified decisions (recorded in DECISIONS.md §1)

`D-07` grading (A≥80/B≥70/C≥60/D≥50/F<50; PASS ≥50; no subject threshold), `U-01` email (pragmatic validation, plus-addressing/subdomains; no RFC claim), `U-02` cross-file upsert (accepted rows only; preserve first-seen; new/changed/unchanged), `U-03` no authentication (local/trusted only), `U-04` support all README variations, `U-06` configurable 10 MiB/413, `I-01` header-width field count, `I-02` first occurrence reserves ID, `I-03` inclusive age 5–25, `I-04` injected clock with 2026-11-19 benchmark, `D-17` bonus deferred, `D-19` profile-based configuration (location revised by D-20), `D-20` external local configuration directory.

## 3. Open technical spike (blocker for phase 3)

| Spike | Status | Gate |
|---|---|---|
| **C-4 physical line numbers** — prove the chosen CSV library reports the physical starting line with header = 1 across embedded newlines, blank lines, a malformed row after the newline and a subsequent valid row; counting `Reader` not accepted untested | **EVIDENCE MERGED on `main` (PR #3: 18 spike tests green); formal G2→G3 sign-off pending** | G2→G3 (phase 3 blocked until signed off) |

## 4. Verification evidence (executed 2026-11-20)

| Check | Command | Result |
|---|---|---|
| Spring Boot / Java compatibility | Spring Initializr metadata + Maven Central | `4.1.2` GA; Java `21` supported |
| Backend compile + tests | `.\mvnw.cmd -B test` (JDK 21) | 134 tests, 0 failures/errors (87 domain, 20 CSV spike, 9 migration, 2 health, 9 mapper, 7 persistence integration) |
| PostgreSQL migration integration | `.\mvnw.cmd -B -Dtest=CoreSchemaMigrationTest test` (JDK 21, `postgres:16-alpine`) | 9 tests, 0 failures/errors against a real Testcontainer, including status/count and SHA-256 SQLSTATE assertions |
| Persistence integration | `.\mvnw.cmd -B "-Dtest=PersistenceMapperTest,PersistenceIntegrationTest" test` (JDK 21, `postgres:16-alpine`) | 16 tests, 0 failures/errors; Flyway + Hibernate `validate` + repository round trips |
| Backend full verify | `.\mvnw.cmd -B verify` | Passed, 134 tests green |
| Packaged JAR config safety | `jar tf` on built JAR | Only `application.properties` packaged; no local profile |
| Application startup + DB connection | `java -jar … --spring.profiles.active=local` | `/actuator/health` → `200`, status `UP` |
| Actuator restriction | HTTP probes of `/actuator/env`, `/actuator/beans`, `/actuator/info` | All `404`; only health exposed |
| Compose config | `docker compose config --quiet` | Passed |
| PostgreSQL health | `docker compose ps` / `docker inspect` | `healthy`; `pg_isready` accepting connections |
| Frontend install | `npm ci` | Passed, 0 vulnerabilities |
| Frontend typecheck | `npm run typecheck` | Passed |
| Frontend production build | `npm run build` | Passed |
| Secret handling | Hash-equality check without printing values | Same generated password in `.env` and local profile; both git-ignored |

Toolchain: JDK `21.0.10` (installed at `C:\Program Files\Java\jdk-21.0.10`, selected via `JAVA_HOME`; PATH default is JDK 17), Maven `3.9.14`, Node `24.11.1`, npm `11.6.2`, Docker `29.4.3`, Compose `v5.1.3`.

## 5. Residual risks

| Risk | Status | Mitigation / note |
|---|---|---|
| Benchmark counts drift with real date | Mitigated | Fixed clock `2026-11-19` in tests (I-04); document in `NOTES.md` |
| Unseen CSV contents unknown | Open | U-04 design + generated fixtures (TEST_STRATEGY §8) |
| Unusual valid email forms | Open | U-01 rule documented; plus-address/subdomain supported |
| Concurrent identical uploads | Designed | Partial unique index + digest reservation (C-1/C-2); test in phase 3 |
| Decimal precision | Implemented in domain | `BigDecimal` + unconstrained `NUMERIC`, no rounding (C-3) |
| Private config leaking into builds | Mitigated | D-20: outside Maven resources, Git-ignored, excluded by `.dockerignore`, absent from built JAR |
| Host port 5432 occupied locally | Mitigated | `POSTGRES_PORT` made configurable; local `.env` uses a free host port |
| No authentication | Ratified | Local/trusted evaluation only; must not be publicly exposed (U-03) |
| Domain boundary erosion | Mitigated | Source-scanning framework-independence test now asserts the exact production source set; no ArchUnit dependency added |

## 6. Next actions

1. Phase 3B.2.1 schema slice is merged on `main` via PR #5; Phase 3B.2.2 awaits review on `feat/database-persistence-adapters`.
2. Formally sign off **G2→G3** using the merged PR #3 C-4 spike evidence before starting ingestion work.
3. Define application output ports and the transaction/digest/upsert services per D-23–D-28 (no schema changes expected in this slice).
4. Implement CSV ingestion, REST API, then the three real screens; application services and ports are still required.
5. Add the multi-stage app Dockerfile, seed script, one-command test script and `NOTES.md`.

## 7. Scope notes

Current-result snapshot only (no term/year invented, D-11). Benchmark numbers are regression fixtures, never application logic (D-12). `NOTES.md` is reserved for final assignment submission. No sample CSV data or PDF content is reproduced in documentation. Domain business rules, the V1 schema migration and JPA entities/repositories/mappers are implemented; application ports/services, CSV ingestion, APIs and real credentials are not committed.
