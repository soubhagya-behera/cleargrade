# ClearGrade — Engineering Handover

ClearGrade turns inconsistent student-results CSVs into validated records, explainable import reports and optional, privacy-safe quality insights. Decisions: [DECISIONS.md](DECISIONS.md); detail: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md), [docs/DATA_MODEL.md](docs/DATA_MODEL.md), [PROJECT_STATUS.md](PROJECT_STATUS.md).

## Technology stack and rationale

Java 21, Spring Boot and Maven back the server; PostgreSQL stores data; React, TypeScript and Vite drive the SPA; Docker packages the stack. This conservative, mainstream choice suits correctness-critical CSV ingestion: static typing and a transactional database fit the work, and containers make evaluation reproducible. No exotic infrastructure.

## Architecture

A modular monolith with inward dependencies (`adapters → application → domain`): a framework-free domain owns validation, value objects and grading; application services own use cases, ports and transactions; adapters handle CSV, REST, persistence and AI providers. Entities and provider SDKs stay behind their adapters.

## Important decisions

- **Validation and row-level errors:** domain value objects drive validation; each problem is stored as its own row-error entry with the physical file line, code and readable message. Rejected rows and problem occurrences are counted separately.
- **Duplicate files:** SHA-256 over the original bytes plus a PostgreSQL partial unique index on active imports detect identical uploads. A completed identical upload returns `200` as deduplicated; an identical upload while an identical import is active returns `409`; a failed import can be retried.
- **Student ID upsert:** normalized IDs distinguish new, changed and unchanged students. Only accepted rows may update a record; the original import attribution is retained, and the most recent attribution changes only when an accepted record actually changes.
- **Transactional finalisation:** four independent transactions keep imports safe: reserve the batch; mark it processing; atomically commit accepted students, row errors, counts and completed status; then record failure separately after rollback so the failure status survives.
- **Grading policy (chosen for this assignment):** A ≥ 80, B ≥ 70, C ≥ 60, D ≥ 50, F < 50, and PASS at ≥ 50%. This is a simplified assignment-specific policy, not an official grading standard, and no per-subject pass threshold is applied. Values derive from exact decimal arithmetic.
- **Benchmark reproducibility:** regression benchmarks pin the clock to 2026-11-19, so their counts are date-sensitive; measured timings are environment-dependent observations, not acceptance thresholds.

## AI and privacy

Intelligence is optional and explicitly triggered (`Analyse import`); import never calls a provider. Java owns every finding, severity and recommendation; providers only word approved explanations. Only aggregates — counts, backend ratios, error-code counts — reach providers; never names, IDs, marks or raw rows. Routing is Gemini → Ollama → deterministic Java fallback, with engine attribution and no write path: AI cannot change records, grades or counters. See [docs/AI_INTELLIGENCE.md](docs/AI_INTELLIGENCE.md).

## Scope

The delivered scope covers the import workflow (including Import History and two per-batch CSV exports), the student directory, the report finder and the individual report, plus reliable CSV processing, automated tests, synthetic seed data and three-service Docker delivery. Authentication, multi-tenancy and term-based academic history were deliberately excluded from this local-evaluation scope.

## Limitations and next steps

The application is local/trusted only; authentication and network controls are required before any non-local deployment ([docs/SECURITY.md](docs/SECURITY.md)). The CSV has no term or year columns, so academic history is not invented (persisted import-batch history is a different concept). Live Gemini generation remains unverified. The batch-history endpoint and per-batch CSV exports listed as future work in earlier notes are now implemented; still deferred are authentication, a persistent privacy-safe insight cache, the stale-import recovery sweep, per-subject mark bars and the printable report.
