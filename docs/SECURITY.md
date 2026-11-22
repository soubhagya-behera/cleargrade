# ClearGrade — Security Considerations

Status: **Upload, parameter, management-path, PII-logging, CSV-export and AI trust-boundary controls implemented.** No authentication is implemented (U-03): the app is for **local/trusted evaluation only** and **must not be publicly exposed without authentication**. The documented CSV exports and import history were delivered by merged application PR #12 (`ae90408`) and are described here for the merged system.

## 1. Threat considerations

| Threat | Concern | Control |
|---|---|---|
| Denial of service | Large upload, excessive columns, huge fields | Configurable 10 MiB cap (U-06) enforced by both servlet limits and a controller-side guard before parsing |
| Path traversal | Malicious `filename` | Filename is sanitised to a display name and never used to build a filesystem path; no client-supplied temp name |
| CSV/formula injection | Exported CSV evaluated as formulas | `= + - @` leading values are apostrophe-prefixed in **both shipped exports** (§4, §8) |
| SQL injection | Dynamic search/sort | Bound JPA parameters; `sort` allowlist |
| XSS | Names/emails rendered in SPA | React escaping; ban `dangerouslySetInnerHTML` |
| Mass assignment | Extra client fields | Explicit DTOs; never bind JPA entities |
| Secret leakage | Credentials in repo/image/JAR | Env-injected secrets; git-ignored `.env`; private local config in `backend/config/` outside Maven resources; `.dockerignore` excludes it; placeholders only (D-19, D-20) |
| Unauthorised access | No login | Local/trusted deployment only (U-03) |
| Duplicate-resource race | Concurrent identical uploads | Partial unique index + digest reservation (DATA_MODEL.md §5) |

## 2. Upload restrictions

- Accept `multipart/form-data`, field `file`, `.csv` extension; content type is a secondary signal only. Require successful header parse before any persistence.
- **Configurable size limit, default 10 MiB (U-06):** reject with HTTP `413 FILE_TOO_LARGE` before full parsing where possible.
- Total upload size is capped at the configurable 10 MiB by the servlet limits and the controller-side guard before parsing; the CSV format is fixed (comma separator, `"` quote, no delimiter auto-detection). No separate per-field or per-column ceiling is enforced; wrong field counts are reported as row problems.
- The controller reads the multipart bytes into memory, bounds them by the configured cap, and passes a sanitised display filename downstream; no filesystem path is derived from the client filename and no temp file is created.
- The UI is consumed same-origin (nginx in Docker, Vite proxy in native development); no separate CORS policy is configured or required.
- Identical digest handling: `200` completed summary, `409 BATCH_PROCESSING` for an active batch, retry allowed after `FAILED` (ARCHITECTURE.md §4).

## 3. Actuator and management exposure (T-5)

- Application API is under **`/api`**. Actuator is a **separate management path**, not under `/api`.
- **Expose `/actuator/health` only.** Disable env, beans, configprops, heapdump, mappings and all other endpoints.
- Management port/endpoint must not be published beyond the local deployment; no sensitive operational data in health output.

## 4. CSV parsing safety

- Apache Commons CSV with a fixed format (comma separator, `"` quote, BOM-aware reader); no delimiter auto-detection from user input.
- Treat all field content as **data**, never as expressions or code.
- For **both shipped CSV exports** (`/api/imports/{id}/report` and `/api/imports/{id}/row-problems`), prefix values beginning with `= + - @` (after optional whitespace) with an apostrophe and quote fields containing commas, quotes or newlines. Exported values are therefore neutralised before spreadsheet use (§8).
- BOM, CRLF/LF, quoted commas, Unicode and reordered/extra headers are normalised, not executed (U-04).
- Raw invalid identifiers are stored in `TEXT` columns to avoid silent truncation (DATA_MODEL.md §1).

## 5. PII protection and logging

Names, emails and dates of birth are personal data even though the supplied sample is synthetic.

- **Never log raw CSV records, email addresses, dates of birth or error-message bodies.** Log only batch id, status, line numbers, error codes, counts and duration.
- Row-problem `message` values may contain an invalid raw value so the administrator can see what was wrong; these are stored in `import_row_error` and rendered by React, but **must not be written to logs**.
- Keep messages useful without unnecessary PII: for a marks error, name the value and field; do not repeat a valid email or DOB.
- Do not commit sample CSVs, screenshots containing personal data, or the assignment PDF.
- Use HTTPS in any non-local deployment; restrict database network access to the app container. No analytics, telemetry or third-party scripts.

## 6. Database and secrets

- Least-privilege application role (CRUD on app tables); DDL via Flyway only; `ddl-auto=validate`. Docker/deployment credentials come from environment variables supplied via the git-ignored root `.env`. For local IDE runs, credentials live only in the git-ignored `backend/config/application-local.properties` (D-20). No credentials in committed source, images or documentation.
- Secrets are rotatable by environment change (T-6); no secret values in logs, error responses or Docker layers.
- Bound parameters and sort allowlist; DB CHECK constraints backstop domain validation (DATA_MODEL.md §3).

## 7. Docker hardening

- Multi-stage build; small runtime JRE image; no build tools or source in the final layer.
- Non-root process in both runtime images (backend `USER cleargrade`, unprivileged nginx image); no `--privileged` or host mounts in Compose. Capability dropping and a read-only root filesystem are not currently configured.
- **Three-service topology:** `postgres` (PostgreSQL 16), `backend` (Spring Boot) and `frontend` (unprivileged nginx serving the React SPA). Healthchecks gate startup; a named volume holds database data.
- **Published host ports are all loopback-bound:** frontend `127.0.0.1:${FRONTEND_PORT:-3000}:8080` (nginx), backend `127.0.0.1:${APP_PORT:-8080}:8080` and PostgreSQL `127.0.0.1:${POSTGRES_PORT:-5432}:5432`. No port is bound to LAN interfaces (ARCHITECTURE.md §7). The container-internal backend address is `0.0.0.0` only so nginx can proxy to it inside the Compose network.
- Actuator exposes only `GET /actuator/health` with `show-details: never`, through the loopback backend port; no management endpoint is published separately.
- Root `.dockerignore` excludes `.env`, `reference/`, `backend/config/application-local.*` and local secrets/artifacts from the build context (D-20); credentials are injected as environment variables at runtime, never through private property files.
- The `local` profile is not active in images or deployment; no credentials are baked into layers. Optional image scanning in CI.

## 8. Security-relevant verification coverage

Items marked **automated** are covered by tests in the repository. Items marked **design** are enforced in code or configuration but have no dedicated automated test. The JAR/Docker checks were executed during handover and are recorded as historical evidence in PROJECT_STATUS.md. No OWASP dependency-check or other dependency vulnerability scan is configured in CI.

| Behaviour | Expectation | Evidence |
|---|---|---|
| Upload > configured limit | `413 FILE_TOO_LARGE` | **automated** — `RestApiIntegrationTest` and the controller guard |
| Non-`.csv` filename / content type | `415 INVALID_FILE_TYPE` | **automated** — `RestApiIntegrationTest` |
| Path-like filename | Sanitised display name; no path traversal | **design** — `ImportController.sanitize` |
| `=HYPERLINK(...)` and other formula-like values in **both exports** | Apostrophe prefix + CSV quoting | **automated** — `ImportCsvExportServiceTest` (report and row-problems) |
| `sort=id; DROP TABLE student` | `400` or allowlisted fallback; no SQL executed | **automated** — `StudentQueryService` allowlist and `RestApiIntegrationTest` invalid-parameter cases |
| Name containing `<script>` | Rendered as text, not HTML | **design** — React escaping; no `dangerouslySetInnerHTML` in the SPA |
| PII in AI outbound bodies | No names, IDs, emails, DOB, marks, raw rows or messages | **automated** — `PrivacyContractTest` inspects real outbound bodies |
| PII in logs | Codes/lines/counts only, never raw bodies or keys | **design** — the only production error log records a correlation id, exception class and URI |
| Concurrent identical uploads | One import; loser gets `200` deduplicated or `409` | **automated** — `ApplicationTransactionIntegrationTest` |
| Actuator probe | `/actuator/health` available; other endpoints absent | **automated** — `ClearGradeApplicationHealthTest` / `RestApiIntegrationTest` |
| JAR packaging / Docker context | Private profile and `.env` excluded | **historical** — `.dockerignore` and JAR inspection in PROJECT_STATUS.md |
| Container user | Non-root process | **design** — backend and frontend Dockerfiles |

There is no authentication test because authentication is intentionally absent (U-03); this remains a deployment restriction, not a control. Frontend and API behaviour is further described in TEST_STRATEGY.md §5–6.

## 9. AI copilot trust boundary (post-release)

- The Gemini key stays server-side: injected only from `GEMINI_API_KEY` into backend configuration, never into
  Docker build arguments, Vite/frontend variables, nginx logs, responses or `/api/ai/status`. Only presence of a
  non-blank value is checked; no code prints or validates the value itself. `.env` remains git-ignored and its
  existing password is never regenerated by this feature.
- Every provider receives the same purpose-built `ImportQualityContext`: counts, backend ratios, five error-code
  counts, `canonicalEvidenceValues` and static schema metadata. No names, IDs, emails, DOB, marks, raw CSV, row
  messages, physical lines, entities or upload filenames are sent. Handler tests inspect actual outbound bodies.
- Gemini uses `store=false`, which avoids stateful interaction reuse but **does not** eliminate processing or
  retention obligations. Free and paid Gemini API tiers have different terms and quotas; review the current
  Google terms before enabling with real data. Ollama is local and optional, but the same privacy contract applies.
- `CLEARGRADE_AI_ENABLED=false` disables both AI providers; with no key and no Ollama the rule engine still
  works offline. Authentication errors remain visible as controlled failures; quota errors never leak a body.
- The AI path is read-only: no model can change marks, grades, pass/fail, rows, counters or trigger imports.
- **Authentication is still absent.** This feature does not make the application suitable for public deployment;
  keep it local/trusted until an authentication layer and network controls exist.
