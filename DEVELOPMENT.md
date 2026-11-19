# ClearGrade — Development Plan

Status: **Planning document — implementation has not started.** Owner decisions ratified 2026-11-19 (DECISIONS.md §1).

## 1. Planned development sequence

| Phase | Scope | Exit criterion |
|---|---|---|
| 0 — Specification & design | PDF/CSV analysis, documentation, owner ratification | **G0→G1 passed (§5)** |
| 1 — Foundation | Maven/Spring Boot bootstrap, Docker Compose + Postgres (port `8080:8080`), Flyway `V1`, config, injected `Clock` (I-04), configurable 10 MiB limit (U-06) | App starts in Docker; schema migrates; `/actuator/health` answers |
| 2 — Domain | Pure model with `BigDecimal` marks (C-3), validation, grading (D-07), error-code vocabulary | Domain tests green; no framework imports in `domain`; **C-4 spike attempted** |
| 3 — Ingestion | CSV adapter, ports, import use case, digest reservation + partial unique index (C-1/C-2), accepted-only upsert (U-02), new/changed/unchanged counters, persistence | **C-4 spike PASSED**; idempotency/concurrency tests green; benchmarks 16/9 and 4,685/315/348 with fixed clock |
| 4 — REST API | Controllers, DTOs, global error handler, `201/200/409/413` upload paths, management-path separation (T-5) | API contract tests green; no entity leakage; Actuator health-only |
| 5 — Frontend | React Router + TanStack Query, three screens, all states, design tokens (UX_SPEC §2) | Build + typecheck pass; screens work end to end |
| 6 — Seed & bonus | Seed generator posting CSV via `POST /api/imports` (T-1); bonus features only if owner requests (D-17) | Seed works through real pipeline; core not regressed |
| 7 — Hardening & handover | Security controls, performance checks, `NOTES.md` (reserved for final submission), final README | One-command test/run verified; docs accurate |

Phases are sequential; a failed gate stops the next phase. Bonus work starts only after phase 5 passes (D-17).

## 2. Code conventions

- **Java 21**, package root `com.esmagico.cleargrade`; layers `domain`, `application` (use cases, ports, **separate transaction service beans / `TransactionTemplate`**), `adapter/in/rest`, `adapter/out/csv`, `adapter/out/persistence`, `config` (ARCHITECTURE.md §8). **No `REQUIRES_NEW` self-invocation.**
- Domain has no Spring/JPA/Commons CSV imports. Marks use `BigDecimal`; no silent rounding (C-3). Adapters depend inward on ports.
- Controllers accept/return DTOs only. Validation returns a complete error-occurrence list; never fail fast on the first field error.
- Use injected `Clock` everywhere (I-04). No `LocalDate.now()` in domain/application.
- **TypeScript strict mode**; `src/types` for API types, `src/api` for the client; **React Router** routing, **TanStack Query** for server state, local component state for UI; **no Redux** without justification. Tailwind utilities with UX_SPEC tokens.
- Conventional commits (`feat:`, `fix:`, `docs:`, `test:`, `refactor:`). No secrets, sample CSV data or PDF in the repo.

## 3. Git workflow

- `main` protected baseline; short-lived feature branches; one reviewed PR per phase with tests passing.
- No generated files, build output or `.env` committed. Access shared with reviewers at handover.
- Git is not initialized in this documentation phase.

## 4. Testing commands (planned — not yet runnable)

| Intent | Command (planned) |
|---|---|
| Backend unit + integration tests | `mvn test` |
| Full backend verification | `mvn verify` |
| Frontend unit tests | `npm --prefix frontend test` |
| Frontend typecheck + build | `npm --prefix frontend run build` |
| Single-command full test | `./scripts/test.sh` (phase 7) |
| Run whole app | `docker compose up --build` then `http://localhost:8080` |
| Seed messy data | script posts generated CSV to `POST /api/imports` (phase 6) |

Exact script names may change once the build exists; the final README is authoritative.

## 5. Verification gates (T-2, C-4)

| Gate | Check |
|---|---|
| **G0 → G1** | **All blocking decisions approved** — D-07, U-01, U-02, U-03, U-04, U-06, I-01, I-02, I-03, I-04, D-17 — **wherever they appear in DECISIONS.md (§1 and §2), not only §2**. No active superseded identifiers. C-4 recorded as a tracked spike. |
| G1 → G2 | Docker boot + migration + health check succeed; port `8080:8080` reachable; Actuator exposes health only |
| **G2 → G3** | Domain tests green; architecture boundary test green; **C-4 physical-line-number spike PASSED** (TEST_STRATEGY.md §2.2) — otherwise phase 3 is blocked |
| G3 → G4 | Benchmarks green with fixed clock; error occurrence counts correct; idempotency (`200`/`409`/retry), partial-unique concurrency and new/changed/unchanged counters green |
| G4 → G5 | API contract, security and management-path tests green |
| G5 → G6 | Three screens work end to end; all specified states present; design tokens applied |
| G6 → G7 | Seed works through the import API; any enabled bonus does not regress core |
| G7 → handover | One-command test/run verified; README and reserved `NOTES.md` match reality |

C-4 is a hard blocker: if the spike cannot prove physical line numbering, phase 3 must redesign the approach before proceeding.

## 6. Definition of done

Three screens end to end, graceful bad-file/bad-row handling, one-command tests, Dockerfile + Compose, seed script through the import API, honest README and one-page `NOTES.md`, clean history, no committed secrets. Bonus features remain optional (D-17).
