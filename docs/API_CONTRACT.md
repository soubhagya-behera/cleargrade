# ClearGrade — API Contract

Status: **Implemented; current API includes import history and two per-batch CSV exports.** Numeric values in examples are illustrative; all counts are derived from the uploaded file (D-12). Row-level error codes are distinct from file/API-level codes. `/errors.csv` was an earlier **proposed** export route and is **not** implemented; the shipped exports are `/api/imports/{id}/report` and `/api/imports/{id}/row-problems` (§6). Physical line tracking uses the approved C-4 mechanism (§4). These endpoints are delivered by the companion application PR #12 and are documented here for the merged system.

## 1. Endpoints

### Application API — base path `/api`

| Method | Path | Purpose | Success | Principal failures |
|---|---|---|---|---|
| POST | `/api/imports` | Upload CSV (multipart field `file`) | `201` new batch; `200` deduplicated | `400`, `409`, `413`, `415`, `500` |
| GET | `/api/imports?page&size` | Newest-first, server-paginated import history | `200` | `400` |
| GET | `/api/imports/{id}` | Batch summary | `200` | `404` |
| GET | `/api/imports/{id}/errors?page&size&code` | Paged row problems (JSON) | `200` | `400`, `404` |
| GET | `/api/imports/{id}/report` | Import Report CSV: one persisted summary record | `200 text/csv` | `404` |
| GET | `/api/imports/{id}/row-problems` | Row Problems CSV: one record per persisted validation issue | `200 text/csv` | `404` |
| GET | `/api/students?query&grade&section&page&size&sort` | Paged directory | `200` | `400` |
| GET | `/api/students/{studentId}` | Student report | `200` | `404` |
| GET | `/api/ai/status` | Safe provider availability/routing snapshot | `200` | — |
| POST | `/api/imports/{id}/insights?refresh` | Explicit aggregate analysis (no request body) | `200` | `404`, `409`, `429`, `502`, `503` |

`/api/imports/{id}/errors.csv` was proposed in an earlier draft of this contract but was **never implemented**; use the two CSV routes above instead.

### Management endpoints — **not** under `/api` (T-5)

| Method | Path | Purpose |
|---|---|---|
| GET | `/actuator/health` | Liveness/readiness |

Only `/actuator/health` is exposed; all other Actuator endpoints are disabled (SECURITY.md §3). Management paths must not be routed through the `/api` prefix.

## 2. Upload behaviour — `POST /api/imports`

Request: `multipart/form-data`, field name `file`, filename with `.csv` extension. Size limit: **configurable, default 10 MiB** (U-06).

Flow (ARCHITECTURE.md §3–6): read upload bytes and reject non-UTF-8 → SHA-256 → digest lookup → reserve batch → parse → validate → accepted-only upsert → finalise. The controller reads the multipart bytes into memory (bounded by the configurable upload cap); no temporary file is created.

| Outcome | HTTP | Body |
|---|---|---|
| New batch processed | `201 Created` | Summary with `deduplicated: false`; `Location: /api/imports/{id}` |
| Identical **COMPLETED** upload | `200 OK` | Existing summary with `deduplicated: true`; no new batch, no rows written |
| Identical **RECEIVED/PROCESSING** upload | `409 Conflict` | `code: BATCH_PROCESSING`, `batchId`, friendly message |
| Missing required column / unreadable encoding / empty file | `400` | `MISSING_REQUIRED_COLUMNS` / `INVALID_ENCODING` / `EMPTY_FILE` |
| Upload exceeds configurable limit | `413` | `FILE_TOO_LARGE` |
| Wrong type | `415` | `INVALID_FILE_TYPE` |
| Unexpected failure | `500` | `INTERNAL_ERROR` with correlation id; batch `FAILED` |

Row-level problems are **never** HTTP errors: the batch completes `COMPLETED` with non-zero `rejected`/`errorCount`. A `FAILED` batch retains its record and the same content may be retried (creating a new batch).

Example `201` response:

```json
{
  "id": 12,
  "filename": "students_sample.csv",
  "status": "COMPLETED",
  "rowsRead": 1000,
  "imported": 952,
  "newCount": 940,
  "changedCount": 8,
  "unchangedCount": 4,
  "rejected": 48,
  "errorCount": 61,
  "startedAt": "2026-11-19T09:15:00Z",
  "finishedAt": "2026-11-19T09:15:01Z",
  "durationMs": 812,
  "deduplicated": false
}
```

`imported = newCount + changedCount + unchangedCount` (U-02). `rejected` counts **rows**; `errorCount` counts **occurrences** and may be larger (D-10). `changedCount` reflects accepted rows whose normalized values actually differ; `unchangedCount` reflects accepted rows identical to the stored record. Only accepted rows can modify a student (U-02).

## 3. Batch summary — `GET /api/imports/{id}`

Same shape as above, `200`; `404 BATCH_NOT_FOUND` for an unknown id.

## 4. Row problems — `GET /api/imports/{id}/errors`

Query params: `page` (0-based), `size` (**default 50**, max 100, T-4), optional `code` filter (one of the five row-level codes). Ordered by `line_number` ascending, then field name. `line_number` is the **physical file line with header as line 1 and blank lines counted** `[REQ]`, produced by the approved and production-adopted C-4 mechanism: `CSVRecord.getCharacterPosition()` plus an independent `PhysicalLineMap` (§9 of ARCHITECTURE.md; C-4 is no longer an open spike).

```json
{
  "content": [
    { "lineNumber": 14, "rawStudentId": "STU10023", "field": "email", "errorCode": "INVALID_FORMAT", "message": "Not a valid email address" },
    { "lineNumber": 27, "rawStudentId": null, "field": "full_name", "errorCode": "MISSING_REQUIRED", "message": "Required value is missing" },
    { "lineNumber": 58, "rawStudentId": "STU10019", "field": "student_id", "errorCode": "DUPLICATE_ID", "message": "Duplicate of an earlier row in this file" }
  ],
  "page": 0, "size": 50, "totalElements": 61, "totalPages": 2
}
```

`rawStudentId: null` renders as `(blank)`; `(whole row)` is used for `WRONG_FIELD_COUNT`. Messages are administrator-friendly and may include an invalid raw value but are never logged (SECURITY.md §5).

## 5. Import history — `GET /api/imports`

Query params: `page` (0-based, default `0`) and `size` (default `10`, max `100`). Results are **newest first**, ordered by `started_at DESC, id DESC`, and contain persisted batch metadata only — not a second copy of student results. The response uses the standard paged envelope (`content`, `page`, `size`, `totalElements`, `totalPages`); each `content` element has `id`, `filename`, `status`, `rowsRead`, `imported`, `newCount`, `changedCount`, `unchangedCount`, `rejected`, `errorCount`, `startedAt`, `finishedAt` and `durationMs`. Invalid pagination values return `400 INVALID_PARAMETER`.

```json
{
  "content": [
    { "id": 12, "filename": "students_sample.csv", "status": "COMPLETED", "rowsRead": 1000,
      "imported": 952, "newCount": 940, "changedCount": 8, "unchangedCount": 4,
      "rejected": 48, "errorCount": 61,
      "startedAt": "2026-11-19T09:15:00Z", "finishedAt": "2026-11-19T09:15:01Z", "durationMs": 812 }
  ],
  "page": 0, "size": 10, "totalElements": 3, "totalPages": 1
}
```

## 6. Per-batch CSV exports

| Endpoint | File | Record structure |
|---|---|---|
| `GET /api/imports/{id}/report` | `cleargrade-import-{id}-report.csv` | Header plus **one** record of persisted batch metadata and outcome counts |
| `GET /api/imports/{id}/row-problems` | `cleargrade-import-{id}-row-problems.csv` | Header plus **one record per persisted validation issue** |

Both endpoints return `text/csv;charset=UTF-8` as a streaming attachment with `Content-Disposition: attachment; filename*=UTF-8''…`. An unknown batch id returns `404 BATCH_NOT_FOUND`. Row Problems always writes its header; for a batch that is not `COMPLETED` it stops there (header only). For a `COMPLETED` batch it reads all persisted issues in pages of 100 and streams every one of them, so a rejected row with several problems produces several CSV records. The Row Problems columns are Batch ID, Filename, Line Number, Student ID, Field, Error Code and administrator-friendly Message; the Report has 13 columns of batch metadata and outcome counts. Line numbers use the physical-line mechanism described in §4. These exports describe **persisted summary and issue information only**: the complete original rejected CSV rows are **not** reconstructed or included.

Values that begin with `=`, `+`, `-` or `@` (after optional whitespace) are prefixed with an apostrophe before CSV quoting, so spreadsheet applications cannot evaluate them as formulas; fields containing commas, quotes or newlines are quoted. The sanitised original filename written into the CSV body is passed through the same neutraliser. The download filename itself is generated from the batch id and cannot be formula-like. The exports are deliberately unbounded streaming responses rather than JSON page collections; the CSV export test suite covers escaping, formula neutralisation and complete results beyond the 100-issue read page.

## 7. Student directory — `GET /api/students`

| Param | Meaning |
|---|---|
| `query` | Case-insensitive match on uppercase `student_id` or `full_name` |
| `grade` | Optional 1–12 filter (validated and applied server-side; exposed in the directory UI) |
| `section` | Optional A–F filter (validated and applied server-side; exposed in the directory UI) |
| `page`, `size` | Pagination (**default 25**, T-4) |
| `sort` | `property,asc|desc`; default `studentId,asc` |

```json
{
  "content": [
    { "studentId": "STU10001", "fullName": "Jane Doe", "gradeLevel": 9, "section": "D", "percentage": 49.1, "grade": "D" },
    { "studentId": "STU10002", "fullName": "John Smith", "gradeLevel": 11, "section": "F", "percentage": 70.3, "grade": "B" }
  ],
  "page": 0, "size": 25, "totalElements": 1000, "totalPages": 40
}
```

`percentage` and `grade` are computed on read (D-07), never stored. Example values follow D-07 bands (49.1 → D, 70.3 → B).

## 8. Student report — `GET /api/students/{studentId}`

Case-insensitive ID match (stored form uppercase). Unknown id → `404 STUDENT_NOT_FOUND` with friendly body; SPA shows the not-found state.

```json
{
  "studentId": "STU10023",
  "fullName": "Alex Doe",
  "email": "alex.doe@example.com",
  "dateOfBirth": "2012-03-14",
  "gradeLevel": 9,
  "section": "B",
  "marks": {
    "maths": 82.0,
    "science": 74.0,
    "english": 79.0,
    "socialStudies": 71.0,
    "secondLanguage": 86.0
  },
  "total": 392.0,
  "maximum": 500,
  "percentage": 78.4,
  "grade": "B",
  "passed": true
}
```

Marks are JSON numbers preserving submitted decimal precision within the configured digit limit; **no silent rounding** (C-3). `passed` follows D-07 (percentage ≥ 50); there is no per-subject threshold.

## 9. Pagination contract (T-4)

| Endpoint family | Default `size` | Maximum |
|---|---|---|
| `/api/imports` (history) | **10** | 100 |
| `/api/imports/{id}/errors` | **50** | 100 |
| `/api/students` | **25** | 100 |

- `page` is 0-based. Invalid values → `400 INVALID_PARAMETER`.
- Every paged JSON response contains `content`, `page`, `size`, `totalElements`, `totalPages`.
- Directory results are stably sorted by `student_id` ascending by default; history is newest first.
- No unbounded JSON collection is returned. The two streaming CSV exports (§6) deliberately write every persisted issue rather than returning a JSON page.

## 10. Error response conventions

```json
{
  "timestamp": "2026-11-19T09:15:00Z",
  "status": 409,
  "code": "BATCH_PROCESSING",
  "message": "This file is already being imported.",
  "details": ["batchId: 12"],
  "correlationId": null,
  "path": "/api/imports"
}
```

| Code | HTTP | Meaning |
|---|---|---|
| `MISSING_REQUIRED_COLUMNS` | 400 | Header lacks one or more required columns, or has duplicate columns |
| `MALFORMED_CSV` | 400 | CSV cannot be parsed (for example, an unterminated quoted field); safe message only |
| `EMPTY_FILE` | 400 | No header, or no data rows |
| `INVALID_ENCODING` | 400 | Not decodable as UTF-8 |
| `INVALID_FILE_TYPE` | 415 | Missing `.csv` extension / wrong content type |
| `FILE_TOO_LARGE` | 413 | Exceeds the configurable 10 MiB default |
| `BATCH_PROCESSING` | 409 | Identical digest is active (`RECEIVED`/`PROCESSING`); includes `batchId` |
| `INVALID_PARAMETER` | 400 | Bad pagination/filter/sort value |
| `BATCH_NOT_FOUND` | 404 | Unknown import batch id |
| `STUDENT_NOT_FOUND` | 404 | Unknown student id |
| `INTERNAL_ERROR` | 500 | Unexpected failure; correlation id logged |

Only `INTERNAL_ERROR` (500) responses carry a non-null `correlationId`; all other error envelopes return `correlationId: null`. A 500 body's message asks the user to quote that id.

Row-level duplicates are reported only through `DUPLICATE_ID` in `import_row_error`, never as an API-level error.

## 11. Content and security notes

All responses use UTF-8. Dates are ISO-8601 (`YYYY-MM-DD`); timestamps are UTC ISO-8601. The application is consumed same-origin (nginx in Docker, the Vite proxy in native development); no separate CORS policy is configured. Error bodies never include secrets or logged PII bodies (SECURITY.md §5).

Implementation notes (G4→G5): the configurable limit is `cleargrade.upload.max-file-size` (default `10485760` bytes = 10 MiB); it drives both `spring.servlet.multipart.max-file-size`/`max-request-size` and a controller-side guard. Non-UTF-8 payloads are rejected before parsing. `page` is 0-based; `size` is 1–100 (defaults 10 for import history, 50 for row errors, 25 for students). `sort` accepts only `studentId`, `fullName`, `gradeLevel` or `section` with `asc|desc`. Duplicate COMPLETED digests return `200` with `deduplicated: true`; active digests return `409 BATCH_PROCESSING`. No authentication is implemented (U-03): deployment is local/trusted only.

## 12. ClearGrade Intelligence (post-release)

`GET /api/ai/status` returns `enabled`, `geminiConfigured`, `geminiModel`, `ollamaConfigured`,
`ollamaReachable`, `ollamaModelPresent`, `ollamaModel`, `inferenceTested`, `deterministicAvailable`,
`defaultEngine` (`GEMINI|OLLAMA|RULE_BASED`), `routing`, safe `routingReasons`, `lastAnalysisEngine`,
`lastAnalysisReason`, `lastAnalysisUsage` and `lastAnalysisFailures`. Configuration, reachability, model
presence and inference testing are deliberately distinct; `inferenceTested` is always `false` because status
never performs an inference. The response never contains a key, endpoint, provider body or credential.

`POST /api/imports/{id}/insights` has **no request body** and never persists a report. It requires a completed
batch, reads batch counters and persisted error-occurrence counts, builds the aggregate privacy context and
routes it through Gemini → Ollama → deterministic rules. Example response (illustrative):

```json
{
  "batchId": 12,
  "engine": "OLLAMA",
  "engineModel": "llama3.2:1b",
  "fallbackReason": "GEMINI:GEMINI_SERVER",
  "cached": false,
  "measured": {
    "rowsRead": 1000,
    "importedCount": 952,
    "rejectedCount": 48,
    "newCount": 940,
    "changedCount": 8,
    "unchangedCount": 4,
    "errorCount": 61,
    "importedPercentage": "95.2",
    "rejectedPercentage": "4.8",
    "errorsPerRejectedRow": "1.27",
    "errorCountsByCode": {
      "MISSING_REQUIRED": 20, "INVALID_FORMAT": 15, "OUT_OF_RANGE": 10,
      "DUPLICATE_ID": 10, "WRONG_FIELD_COUNT": 6
    },
    "schemaColumns": ["student_id", "full_name", "email", "date_of_birth", "grade_level", "section",
      "maths", "science", "english", "social_studies", "second_language"]
  },
  "summary": "Most rows imported cleanly; rejected rows cluster around identifier checks.",
  "overallAssessment": "Aggregate evidence points to source-export formatting issues.",
  "findings": [{
    "title": "Duplicate student identifiers",
    "severity": "MEDIUM",
    "evidenceMetric": "DUPLICATE_ID_COUNT",
    "evidenceValue": "10",
    "explanation": "Ten later occurrences of an existing identifier were rejected.",
    "recommendedAction": "De-duplicate the source export."
  }],
  "recommendedActions": ["De-duplicate the source export."],
  "limitations": "Aggregate counters only; row content was not inspected by the provider.",
  "advisoryOnly": true
}
```

`measured` is always Java-measured. Finding selection, severity, evidence, `summary`, `overallAssessment`,
`limitations` and `recommendedActions` are Java-authored; a provider may only supply each finding's
`explanation` wording. `engine` names the actual generator. `fallbackReason` is a safe engine:code chain (for
example `GEMINI:GEMINI_SERVER`) or `null`. `cached` distinguishes cache hits from fresh analysis.

| Code | HTTP | Meaning |
|---|---|---|
| `BATCH_NOT_FOUND` | 404 | Unknown batch id |
| `BATCH_NOT_COMPLETED` | 409 | Batch has not reached `COMPLETED` |
| `ANALYSIS_COOLDOWN` | 429 | Optional per-batch cooldown is active |
| `ANALYSIS_PROVIDER_FAILED` | 502 | Non-configuration provider failure with no usable result (mapped by `GlobalExceptionHandler`) |
| `AI_CONFIGURATION_ERROR` | 503 | Provider authentication/configuration error (never falls back) |
| `ANALYSIS_UNAVAILABLE` | 503 | No analysis engine is available (both AI providers and deterministic engine disabled) |

Full routing, privacy and provider-term details: [AI_INTELLIGENCE.md](AI_INTELLIGENCE.md).
