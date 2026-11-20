# ClearGrade — Development Plan

Status: **Phase 3B.2.1 PostgreSQL schema and migration test foundation implemented; Phase 3B.1 domain model is merged on `main` via PR #4.** JPA persistence, CSV ingestion, REST APIs and the SPA remain planned. Owner decisions ratified 2026-11-19 (DECISIONS.md §1).

## 1. Planned development sequence

| Phase | Scope | Exit criterion |
|---|---|---|
| 0 — Specification & design | PDF/CSV analysis, documentation, owner ratification | **G0→G1 passed (§6)** |
| 1 — Foundation **(scaffolded, no migrations yet)** | Maven/Spring Boot bootstrap (Java 21, Spring Boot 4.1.2, `mvnw`), Docker Compose + Postgres, profile-based configuration (§3), injected `Clock` (I-04), configurable 10 MiB limit (U-06) | Skeleton starts with the local profile; `/actuator/health` answers; minimal context/health verification test green. **Flyway migrations and the app Docker image remain for later phases.** |
| 2 — Domain **(3B.1 implemented + reviewed)** | Pure model with `BigDecimal` marks (C-3), validation, grading (D-07), error-code vocabulary; review remediation for count overflow, row-error line, batch/outcome construction and documentation | Domain tests green; source-scanning framework-independence test green; **C-4 spike evidence merged on `main` (PR #3), formal G2→G3 sign-off pending** |
| 3 — Ingestion **(3B.2.1 schema slice implemented)** | Flyway V1 schema + Testcontainers migration tests done; CSV adapter, ports, import use case, digest reservation, accepted-only upsert, counters, JPA adapters still planned | Schema/index/constraint migration tests green; **C-4 gate signed off from PR #3 evidence required**; idempotency/concurrency tests green; benchmarks 16/9 and 4,685/315/348 with fixed clock |
| 4 — REST API | Controllers, DTOs, global error handler, `201/200/409/413` upload paths, management-path separation (T-5) | API contract tests green; no entity leakage; Actuator health-only |
| 5 — Frontend | React Router + TanStack Query, three screens, all states, design tokens (UX_SPEC §2) | Build + typecheck pass; screens work end to end |
| 6 — Seed & bonus | Seed generator posting CSV via `POST /api/imports` (T-1); bonus features only if owner requests (D-17) | Seed works through real pipeline; core not regressed |
| 7 — Hardening & handover | Security controls, performance checks, `NOTES.md` (reserved for final submission), final README | One-command test/run verified; docs accurate |

Phases are sequential; a failed gate stops the next phase. Bonus work starts only after phase 5 passes (D-17).

## 2. Code conventions

- **Java 21**, package root `dev.cleargrade`; layers `domain`, `application` (use cases, ports, **separate transaction service beans / `TransactionTemplate`**), `adapters.in.web`, `adapters.in.csv`, `adapters.out.persistence`, `configuration` (the boundary names mandated for Phase 2B; ARCHITECTURE.md §8 is the earlier proposed naming). **No `REQUIRES_NEW` self-invocation.**
- Domain has no Spring/JPA/Commons CSV imports. Marks use `BigDecimal`; no silent rounding (C-3). Adapters depend inward on ports. Flyway owns the schema (`V1__create_core_schema.sql`); Hibernate remains `ddl-auto=validate`. Mark columns are unconstrained `NUMERIC` (D-24). Migration integration tests use a shared Testcontainers `postgres:16-alpine` container with explicit table truncation.
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
- Git was initialized during Phase 2A and the documentation blueprint committed; the application foundation and reviewed domain model are committed, and PR #4 was squash-merged to `main` as `a1605ec`. Phase 3B.2.1 work stays on `feat/database-persistence-foundation`.

## 5. Commands

| Intent | Command | Status |
|---|---|---|
| Backend compile + tests | `cd backend; .\mvnw.cmd -B test` | Runnable (115 tests) |
| PostgreSQL migration integration tests | `cd backend; .\mvnw.cmd -B -Dtest=CoreSchemaMigrationTest test` | Runnable (Testcontainers `postgres:16-alpine`, Docker daemon required) |
| Full backend verification | `cd backend; .\mvnw.cmd -B verify` | Runnable |
| Frontend dependency install | `cd frontend; npm ci` | Runnable |
| Frontend typecheck | `cd frontend; npm run typecheck` | Runnable |
| Frontend production build | `cd frontend; npm run build` | Runnable |
| Compose config check | `docker compose config --quiet` | Runnable |
| Start PostgreSQL | `docker compose up -d` | Runnable |
| Run locally with private profile | `Set-Location backend` → `$env:SPRING_PROFILES_ACTIVE = "local"` → `.\mvnw.cmd spring-boot:run` (§3) | Runnable |
| Frontend unit tests | `npm test` | Planned |
| Single-command full test | `./scripts/test.sh` | Planned (phase 7) |
| Seed messy data | script posts generated CSV to `POST /api/imports` | Planned (phase 6) |

Exact script names may change once the build exists; the final README is authoritative.

## 6. Verification gates (T-2, C-4)

| Gate | Check |
|---|---|
| **G0 → G1** | **All blocking decisions approved** — D-07, U-01, U-02, U-03, U-04, U-06, I-01, I-02, I-03, I-04, D-17 — **wherever they appear in DECISIONS.md (§1 and §2), not only §2**. No active superseded identifiers. C-4 recorded as a tracked spike. |
| G1 → G2 | Docker boot + migration + health check succeed; port `8080:8080` reachable; Actuator exposes health only; external local config verified (§3): private file outside `src/main/resources`, excluded from Git and Docker context, not activated in Docker |
| **G2 → G3** | Domain tests green; architecture boundary test green; **C-4 physical-line-number spike evidence on `main` from PR #3** (TEST_STRATEGY.md §2.2) formally signed off — otherwise phase 3 is blocked |
| G3 → G4 | Benchmarks green with fixed clock; error occurrence counts correct; idempotency (`200`/`409`/retry), partial-unique concurrency and new/changed/unchanged counters green |
| G4 → G5 | API contract, security and management-path tests green |
| G5 → G6 | Three screens work end to end; all specified states present; design tokens applied |
| G6 → G7 | Seed works through the import API; any enabled bonus does not regress core |
| G7 → handover | One-command test/run verified; README and reserved `NOTES.md` match reality |

C-4 remains a hard gate: PR #3 provides the physical-line-number evidence, but formal G2→G3 sign-off is still required before ingestion proceeds. If that evidence is rejected, phase 3 must redesign the approach.

## 7. Definition of done

Three screens end to end, graceful bad-file/bad-row handling, one-command tests, Dockerfile + Compose, seed script through the import API, honest README and one-page `NOTES.md`, clean history, no committed secrets. Bonus features remain optional (D-17).
