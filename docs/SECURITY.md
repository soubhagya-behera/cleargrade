# ClearGrade — Security Considerations

Status: **Proposed controls — none implemented.** No authentication is implemented (U-03): the app is for **local/trusted evaluation only** and **must not be publicly exposed without authentication**.

## 1. Threat considerations

| Threat | Concern | Planned control |
|---|---|---|
| Denial of service | Large upload, excessive columns, huge fields | Configurable 10 MiB cap (U-06), field/column ceilings, streaming parse |
| Path traversal | Malicious `filename` | Server-generated temp names; filename is display data only |
| CSV/formula injection | Error-report CSV evaluated as formulas | Neutralise `= + - @` leading values in bonus export (§3) |
| SQL injection | Dynamic search/sort | Bound JPA parameters; `sort` allowlist |
| XSS | Names/emails rendered in SPA | React escaping; ban `dangerouslySetInnerHTML` |
| Mass assignment | Extra client fields | Explicit DTOs; never bind JPA entities |
| Secret leakage | Credentials in repo/image/JAR | Env-injected secrets; git-ignored `.env`; private local config in `backend/config/` outside Maven resources; `.dockerignore` excludes it; placeholders only (D-19, D-20) |
| Unauthorised access | No login | Local/trusted deployment only (U-03) |
| Duplicate-resource race | Concurrent identical uploads | Partial unique index + digest reservation (DATA_MODEL.md §5) |

## 2. Upload restrictions

- Accept `multipart/form-data`, field `file`, `.csv` extension; content type is a secondary signal only. Require successful header parse before any persistence.
- **Configurable size limit, default 10 MiB (U-06):** reject with HTTP `413 FILE_TOO_LARGE` before full parsing where possible.
- Bound field count and field length; reject or skip pathological records without unbounded allocation.
- Stream to a temp file with a **generated** name; delete it in a `finally` block. Never derive filesystem paths from the client filename.
- Same-origin only; CORS restricted to the app origin.
- Identical digest handling: `200` completed summary, `409 BATCH_PROCESSING` for an active batch, retry allowed after `FAILED` (ARCHITECTURE.md §4).

## 3. Actuator and management exposure (T-5)

- Application API is under **`/api`**. Actuator is a **separate management path**, not under `/api`.
- **Expose `/actuator/health` only.** Disable env, beans, configprops, heapdump, mappings and all other endpoints.
- Management port/endpoint must not be published beyond the local deployment; no sensitive operational data in health output.

## 4. CSV parsing safety

- Apache Commons CSV with a fixed format (comma separator, `"` quote, BOM-aware reader); no delimiter auto-detection from user input.
- Treat all field content as **data**, never as expressions or code.
- For the deferred bonus error-CSV export, prefix values beginning with `= + - @` (after optional whitespace) with an apostrophe and quote fields containing commas/newlines.
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
- Non-root user; no `--privileged`; drop capabilities where practical; read-only root filesystem with an explicitly writable temp directory.
- Healthchecks, resource limits, named DB volume. Only host port `8080` → container `8080` is published. PostgreSQL `5432` is reachable internally over the Compose network and, for local development, is published on the **host loopback interface only** (`127.0.0.1:${POSTGRES_PORT}:5432`); it is not bound to LAN interfaces (ARCHITECTURE.md §7).
- Root `.dockerignore` excludes `.env`, `reference/`, `backend/config/application-local.*` and local secrets/artifacts from the build context (D-20); credentials are injected as environment variables at runtime, never through private property files.
- The `local` profile is not active in images or deployment; no credentials are baked into layers. Optional image scanning in CI.

## 8. Security test cases (planned)

| Test | Expectation |
|---|---|
| Upload > configured limit | `413`, no temp file left behind |
| Non-`.csv` filename / content type | `415` |
| Filename `../../etc/passwd` | Sanitised display name; no path traversal |
| Error CSV with `=HYPERLINK(...)` | Exported with neutralising prefix (bonus if enabled) |
| `sort=id; DROP TABLE student` | `400` or whitelisted fallback; no SQL executed |
| Name containing `<script>` | Rendered as text, not HTML |
| Failing row with invalid email | Logs contain code/line only — no email, no message body |
| Concurrent identical uploads | One `201`, other `409`/`200`; only one import performed |
| Actuator probe | `/actuator/health` available; `/actuator/env` and others disabled/absent |
| JAR packaging check | `application-local.properties` absent from built JAR (D-20) |
| Docker context check | `.env` and `backend/config/application-local.properties` not in build context |
| Container user check | Process runs as non-root |

Frontend/API security behaviour is verified in TEST_STRATEGY.md §5–6; these are the security-focused cases.
