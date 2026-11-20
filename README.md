# ClearGrade

**Student Results & Data Quality Portal** — upload a messy results CSV, import the valid rows, and see a plain-language explanation for every rejected row.

> **Release status:** Phases 0–7 complete. Backend, REST API and the three-screen React app are implemented and tested; Docker Compose runs the full stack; seed data is imported through the real HTTP API. No authentication is implemented — see [Security and trust boundary](#security-and-trust-boundary).

## Contents

- [Product overview](#product-overview)
- [Screenshots](#screenshots)
- [Features](#features)
- [Architecture and tech stack](#architecture-and-tech-stack)
- [Prerequisites](#prerequisites)
- [One-command startup](#one-command-startup)
- [Environment configuration](#environment-configuration)
- [Seed workflow](#seed-workflow)
- [API endpoints](#api-endpoints)
- [Testing](#testing)
- [Project structure](#project-structure)
- [Security and trust boundary](#security-and-trust-boundary)
- [Limitations](#limitations)
- [Troubleshooting](#troubleshooting)

## Product overview

An administrator uploads a student-results CSV. ClearGrade:

- imports valid rows into a searchable, paginated student directory;
- explains every rejected row with its **physical file line**, field, error code and message;
- reports *rows rejected* separately from *problem occurrences*, because one row can have several problems;
- computes total, percentage, grade and PASS/FAIL on read using the ratified D-07 scheme;
- records each upload as an import batch with counts and concurrency-safe identical-file detection (SHA-256).

The source format has no term/year field, so the portal stores a **current-result snapshot** only (D-11).

## Screenshots

> Placeholder — real screenshots have not been captured in this environment.
>
> | Screen | Placeholder |
> |---|---|
> | Import workspace (`/import`) | *Pending capture: dropzone, upload result summary, row-problem table* |
> | Student directory (`/students`) | *Pending capture: search, filters, pagination, derived grade column* |
> | Student report (`/students/:id`) | *Pending capture: identity, overall result, marks by subject* |

No screenshot images are committed; the above placeholders are intentionally explicit.

## Features

- **CSV ingestion** — header-based mapping (order/case/whitespace tolerant, extra columns ignored), UTF-8 with/without BOM, LF/CRLF/mixed/lone-CR, quoted commas and embedded newlines, blank/all-empty rows, Unicode names, supplementary characters.
- **Physical line numbers** — header is line 1; blank lines and embedded newlines are counted using `CSVRecord.getCharacterPosition()` plus an independent physical-line map (adopted from the approved C-4 spike).
- **Validation** — required fields, existing domain validators, five exact row error codes, complete error lists, first-normalized-ID-wins duplicate reservation.
- **Transactions** — T1 `RECEIVED` reservation → T2 `PROCESSING` → parse/validate → T3 atomic upserts/errors/counts/`COMPLETED`; T4 commits `FAILED` after any failure (including T2 failure) with no partial writes.
- **Idempotency and concurrency** — partial unique index on active SHA-256 digests; `200` completed replay, `409` active digest, retry after `FAILED`, real PostgreSQL concurrency coverage.
- **REST API** — multipart upload, batch summary, paginated row errors, paginated student directory with filters/sort, student report, consistent error envelope.
- **Frontend** — TanStack Query state, file dropzone, upload/completion/failure states, per-code problem breakdown, physical-line problem table, batch-ID lookup, searchable directory with filters and pagination, report with derived grade and PASS/FAIL.

Bonus features (error-CSV download, mark bars, print report) are deferred per D-17.

## Architecture and tech stack

Modular monolith; dependencies point inward (`adapters → application → domain`).

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot 4.1.2, Maven wrapper 3.9.14 |
| Persistence | PostgreSQL 16, Flyway (`ddl-auto=validate`), Spring Data JPA + JdbcTemplate adapters |
| CSV | Apache Commons CSV 1.14.1 with explicit format and BOM-aware input |
| Frontend | React 19, TypeScript 5.9 (strict), Vite 7, Tailwind CSS 4, React Router 7, TanStack Query 5, Lucide React |
| Tests | JUnit 5, Mockito, MockMvc, Testcontainers PostgreSQL; Vitest + Testing Library |
| Delivery | Multi-stage Dockerfiles, nginx unprivileged static host, Docker Compose v2 |

The SPA and API are served from one origin in containers: nginx proxies `/api` and `/actuator/health` to the backend. Actuator exposes **health only** and is never under `/api`.

Detailed decisions: [ARCHITECTURE.md](docs/ARCHITECTURE.md), [DATA_MODEL.md](docs/DATA_MODEL.md), [DECISIONS.md](DECISIONS.md), [API_CONTRACT.md](docs/API_CONTRACT.md), [UX_SPEC.md](docs/UX_SPEC.md), [SECURITY.md](docs/SECURITY.md), [TEST_STRATEGY.md](docs/TEST_STRATEGY.md), [NOTES.md](NOTES.md).

## Prerequisites

- Docker Engine 24+ with Compose v2 (one-command path — **this is the recommended path**).
- For local IDE development only: JDK 21, Node.js 22+, Maven wrapper and npm are included/pinned.
- Ports `3000` (frontend), `8080` (backend) and `5432` (PostgreSQL, loopback only) available, or override them in `.env`.

## One-command startup

Windows (PowerShell):

```powershell
powershell -ExecutionPolicy Bypass -File scripts/start.ps1
```

Unix-like:

```bash
bash scripts/start.sh
```

The script:

1. checks Docker and Compose;
2. creates a git-ignored `.env` from `.env.example` with a generated local password if absent;
3. builds and starts PostgreSQL, backend and frontend;
4. waits for `GET /actuator/health` to return `200` (database readiness is enforced by Compose healthchecks, not just container start);
5. prints the frontend, health and API URLs plus the seed command;
6. exits non-zero on any failure.

Then:

- Frontend: <http://localhost:3000>
- Backend health: <http://localhost:8080/actuator/health>
- API base: <http://localhost:8080/api>

Stop with `docker compose down`; delete the named PostgreSQL volume with `docker compose down -v` for a clean database.

## Environment configuration

Copy `.env.example` to `.env` (git-ignored). Placeholders only are committed.

| Variable | Default | Purpose |
|---|---|---|
| `POSTGRES_DB` | `cleargrade` | Database name |
| `POSTGRES_USER` | `cleargrade_app` | Database user |
| `POSTGRES_PASSWORD` | generated by start script | Local password; never committed |
| `POSTGRES_PORT` | `5432` | Host loopback port for PostgreSQL |
| `APP_PORT` | `8080` | Host port for backend API/health |
| `FRONTEND_PORT` | `3000` | Host port for the SPA |
| `CLEARGRADE_UPLOAD_MAX_FILE_SIZE` | `10485760` | Upload cap in bytes (10 MiB); also drives Spring multipart limits |

For IDE-local runs without Docker, use the external profile described in [DEVELOPMENT.md](DEVELOPMENT.md) (`backend/config/application-local.properties`, git-ignored and outside Maven resources).

## Seed workflow

Seed data is imported **through `POST /api/imports`** — there are no direct SQL inserts.

```powershell
powershell -ExecutionPolicy Bypass -File scripts/seed.ps1
```

```bash
bash scripts/seed.sh
```

The two committed fixtures are deterministic, synthetic and PII-free:

| Fixture | Demonstrates | Expected outcome (clean DB) |
|---|---|---|
| `scripts/seed/initial.csv` | 8 new students, invalid email, invalid section, out-of-range mark, duplicate ID | `201`, rowsRead 12, imported 8, rejected 4, problems 4 |
| `scripts/seed/update.csv` | 3 changed students, 5 unchanged students, invalid email, invalid section, wrong field count, duplicate ID | `201`, rowsRead 12, imported 8 (3 changed + 5 unchanged), rejected 4, problems 4 |

Re-running the same fixture is **not** a new import: the SHA-256 reservation returns `200 deduplicated` when the prior batch is `COMPLETED`, or `409 BATCH_PROCESSING` while one is active. The script prints the status and counts and never hides either case. To replay as new imports, reset with `docker compose down -v`, restart and seed again.

Note: dates of birth in the seed are 2010; age validation uses the real clock, so re-seed expected counts hold while those students remain 5–25 years old.

## API endpoints

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/imports` | Multipart CSV upload (`file` field). `201` new, `200` deduplicated, `409` active digest, `400/413/415/500` failures |
| GET | `/api/imports/{id}` | Batch summary and counters |
| GET | `/api/imports/{id}/errors?page&size&code` | Paged row problems (default size 50, max 100) |
| GET | `/api/students?query&grade&section&page&size&sort` | Paged directory (default size 25, max 100) |
| GET | `/api/students/{studentId}` | Student report with derived grading |
| GET | `/actuator/health` | Health (outside `/api`; health only) |

Full request/response examples, the error envelope and pagination contract are in [API_CONTRACT.md](docs/API_CONTRACT.md).

## Testing

One command for all release checks:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/test.ps1
```

```bash
bash scripts/test.sh
```

Individual commands:

```powershell
cd backend; .\mvnw.cmd -B test
cd backend; .\mvnw.cmd -B verify
cd frontend; npm ci; npm test; npm run typecheck; npm run build
docker compose config --quiet
git diff --check
```

Current evidence: **206 backend tests** and **14 frontend tests**, zero failures/errors/skips.

## Project structure

```text
backend/    Java 21 Spring Boot modular monolith (domain, application, adapters.in.web/csv, adapters.out.persistence)
frontend/   React + TypeScript SPA (api client, types, reusable components, three feature pages)
scripts/    One-command startup, release checks, HTTP seed workflow and PII-free seed CSVs
docs/       Architecture, data model, API, UX, security and test strategy
reference/  Supplied assignment pack (git-ignored; never committed)
```

## Security and trust boundary

**There is no authentication or multi-tenancy.** This application is intended for **local/trusted evaluation only** and must not be publicly exposed without an authentication layer and network controls. Do not describe it as a secure multi-tenant SaaS.

Controls that do exist: configurable 10 MiB server-side upload cap enforced by both Spring multipart settings and the controller, strict UTF-8 check, `.csv` extension/content-type validation, sanitised display filename, bounded pagination and sort allowlists, DTO-only responses (no JPA entity exposure), safe error messages, no raw CSV/PII logging, health-only Actuator, PostgreSQL bound to `127.0.0.1` for local development, non-root container users, and build contexts that exclude `.env` and local profiles. See [SECURITY.md](docs/SECURITY.md) and the audit section of [NOTES.md](NOTES.md).

## Limitations

- No authentication (U-03); local/trusted deployment only.
- No batch-list endpoint in the contract: the UI shows the current upload result and supports batch-ID lookup, not fabricated history.
- Bonus D-17 items (error-CSV download, mark bars, print) are deferred.
- D-21 stale-`RECEIVED` recovery sweep is deferred; a failed reservation is retriable via `FAILED`.
- U-01 exact email grammar boundary remains a future owner decision; the canonical domain validator is reused unchanged.
- Seed fixtures are date-sensitive (age validation); see [Seed workflow](#seed-workflow).

## Troubleshooting

| Symptom | Action |
|---|---|
| `POSTGRES_PASSWORD must be set` | Ensure `.env` exists (`scripts/start.*` creates it) or copy `.env.example`. |
| Backend not healthy after startup | `docker compose logs backend`; confirm Flyway migrated and datasource env vars match `.env`. |
| Frontend shows “Backend unavailable” | Health proxy requires the backend container to be healthy; check `docker compose ps`. |
| `413 FILE_TOO_LARGE` | File exceeds `CLEARGRADE_UPLOAD_MAX_FILE_SIZE`; raise it in `.env` and restart. |
| `409 BATCH_PROCESSING` on re-upload | Identical digest is active; wait for completion or retry after a `FAILED` batch. |
| Port conflict | Override `POSTGRES_PORT`, `APP_PORT` or `FRONTEND_PORT` in `.env`, then restart. |
| Seed returns `200` | Expected idempotent replay; reset with `docker compose down -v` for new batches. |
