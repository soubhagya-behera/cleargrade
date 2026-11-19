# ClearGrade

**Student Results & Data Quality Portal** — planned take-home implementation for the Es Magico Full-stack Intern assignment.

> Status: **Phase 1.6 — specification, architecture and owner-ratified design complete. No application code, build files, migrations or dependencies have been created.** Run instructions will be added when implementation is complete.

## What the product will do

An administrator uploads a messy CSV of student results. ClearGrade will:

- import valid rows into a searchable, paginated student directory;
- explain every rejected row in plain language with physical line numbers and error codes;
- show a report with marks, total, percentage, grade and pass/fail;
- keep each upload traceable as an import batch, with concurrency-safe identical-file detection (SHA-256);
- distinguish rows rejected from validation problems found.

The source format has no term/year field, so the portal deliberately models a **current-result snapshot** rather than inventing history.

## Planned stack

| Layer | Technology |
|---|---|
| Backend | Java 21, Spring Boot, Maven |
| Persistence | PostgreSQL, Spring Data JPA, Flyway |
| CSV | Apache Commons CSV |
| Frontend | React, React Router, TanStack Query, TypeScript, Tailwind CSS |
| Testing | JUnit 5, Mockito |
| Delivery | Multi-stage Dockerfile, Docker Compose (`8080:8080`) |

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

## Running it

**Pending.** Final instructions will document prerequisites, `docker compose up --build`, `http://localhost:8080`, the seed command (which posts generated CSV through the import API) and the one-command test script. The supplied sample CSVs and assignment PDF are intentionally not included here.

`NOTES.md`, required by the assignment, is reserved for the final submission.
