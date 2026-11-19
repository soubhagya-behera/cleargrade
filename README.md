# ClearGrade

**Student Results & Data Quality Portal** — planned take-home implementation for the Es Magico Full-stack Intern assignment.

> Status: **Phase 2B — application foundation in place.** The Spring Boot backend, React shell and local PostgreSQL Compose service are scaffolded. Business functionality (CSV import, domain rules, REST API, real screens) is **not implemented yet** and is explicitly listed as unfinished below.

## What the product will do

An administrator uploads a messy CSV of student results. ClearGrade will:

- import valid rows into a searchable, paginated student directory;
- explain every rejected row in plain language with physical line numbers and error codes;
- show a report with marks, total, percentage, grade and pass/fail;
- keep each upload traceable as an import batch, with concurrency-safe identical-file detection (SHA-256);
- distinguish rows rejected from validation problems found.

The source format has no term/year field, so the portal deliberately models a **current-result snapshot** rather than inventing history.

## Stack

| Layer | Technology | Status |
|---|---|---|
| Backend | Java 21, Spring Boot 4.1.2, Maven (wrapper 3.9.14) | Scaffolded |
| Persistence | PostgreSQL 16, Spring Data JPA, Flyway (enabled, `ddl-auto=validate`) | Scaffolded; no migrations yet |
| CSV | Apache Commons CSV | Planned (phase 3) |
| Frontend | React 19, TypeScript 5.9, Vite 7, Tailwind CSS 4, React Router 7, TanStack Query 5, Lucide React | Scaffolded |
| Testing | JUnit 5, Mockito | Backend context/health test in place |
| Delivery | Docker Compose for PostgreSQL; multi-stage app Dockerfile | Compose scaffolded; app image planned |

Architecture: a **modular monolith** — framework-free domain, application use cases/ports, CSV and persistence adapters, REST API and one React SPA served from the same origin. No microservices, message brokers, caches or AI features. Actuator exposes health only, on a separate management path.

## Ratified design decisions (summary)

- **Grading (D-07):** A ≥ 80, B ≥ 70, C ≥ 60, D ≥ 50, F < 50; PASS at overall ≥ 50; no subject threshold. A deliberately simplified chosen scheme.
- **Validation:** pragmatic email rule with plus-addressing/subdomains (U-01); inclusive age 5–25 (I-03); header-width field count (I-01); first ID occurrence reserves even if rejected (I-02).
- **Duplicates:** first-wins in-file; accepted-only upsert across files with new/changed/unchanged counters (U-02).
- **Repeat uploads:** concurrency-safe digest reservation via partial unique index; `200` completed, `409` processing, retry after failure (C-1/C-2).
- **Precision:** `BigDecimal`/`NUMERIC` with no silent rounding (C-3).
- **Limits:** configurable 10 MiB upload cap → HTTP 413 (U-06).
- **Auth:** none in this assignment — **local/trusted evaluation only; do not publicly expose** (U-03).
- **Bonus features deferred** until core works (D-17).

Full rationale, alternatives and superseded identifiers are in [DECISIONS.md](DECISIONS.md).

## Documentation index

- [Architecture](docs/ARCHITECTURE.md) — boundaries, data flow, idempotency, transactions, deployment, C-4 spike
- [Data Model](docs/DATA_MODEL.md) — entities, partial unique index, constraints, transaction sequence
- [API Contract](docs/API_CONTRACT.md) — endpoints, 200/201/409/413, JSON examples, pagination defaults
- [UX Specification](docs/UX_SPEC.md) — design tokens, three screens, states, accessibility
- [Security](docs/SECURITY.md) — uploads, PII/logging, Actuator health-only, Docker, secrets
- [Test Strategy](docs/TEST_STRATEGY.md) — validation, C-4 gate, idempotency/concurrency, benchmarks
- [Decisions](DECISIONS.md) — approved decisions, engineering choices, superseded IDs, open items
- [Development](DEVELOPMENT.md) — phases, conventions, gates (including C-4)
- [Project Status](PROJECT_STATUS.md) — current state, spike, risks, next actions

## Current foundation layout

```text
backend/    Spring Boot modular-monolith skeleton (Java 21, Maven wrapper)
frontend/   React + TypeScript + Vite shell (feature-oriented src/)
docker-compose.yml   Local PostgreSQL 16 with named volume and pg_isready healthcheck
.github/workflows/ci.yml   Maven build/tests + frontend typecheck/build
```

Backend packages are rooted at `dev.cleargrade`: `domain`, `application`, `adapters.in.web`, `adapters.in.csv`, `adapters.out.persistence`, `configuration`. Only `application.properties` lives under Maven resources; the private local profile file lives in `backend/config/` (D-20).

## Running the foundation locally

Prerequisites: Java 21, Node.js 20.19+ / 22.12+, Docker with Compose.

```powershell
# 1. Local secrets (never committed): generate a local password and place the
#    SAME value in .env (POSTGRES_PASSWORD) and
#    backend/config/application-local.properties (spring.datasource.password).
#    See DEVELOPMENT.md §3. Copy the committed examples first.

# 2. Start PostgreSQL
docker compose up -d
docker compose ps        # status should be "healthy"

# 3. Backend (from backend/)
$env:SPRING_PROFILES_ACTIVE = "local"
.\mvnw.cmd spring-boot:run
# GET http://localhost:8080/actuator/health

# 4. Frontend (from frontend/)
npm ci
npm run dev              # dev server proxies /api and /actuator/health to :8080
```

If host port 5432 is already in use, set `POSTGRES_PORT` in the git-ignored root `.env` and the datasource URL in `backend/config/application-local.properties` to the same free port.

## Verification

```powershell
# Backend
cd backend; .\mvnw.cmd -B verify

# Frontend
cd frontend; npm ci; npm run typecheck; npm run build

# Database / config
docker compose config --quiet
```

Full evidence and the exact commands used are recorded in [PROJECT_STATUS.md](PROJECT_STATUS.md).

## Explicitly unfinished

- No CSV parsing, validation, grading or domain model.
- No Flyway migrations, JPA entities or repository adapters.
- No import REST API, DTOs, pagination or error handling.
- Frontend screens are honest placeholders; the sidebar's Student Report entry is disabled until a student is selected.
- No multi-stage application Dockerfile, seed script, `NOTES.md` or one-command test script.
- No authentication by design (U-03): local/trusted evaluation only; do not publicly expose.

The supplied sample CSVs and assignment PDF are intentionally not included here. `NOTES.md`, required by the assignment, is reserved for the final submission.
