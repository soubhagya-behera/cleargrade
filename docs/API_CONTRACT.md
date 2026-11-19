# ClearGrade — API Contract

Status: **Proposed contract — no endpoint is implemented.** Numeric values in examples are illustrative; all counts are derived from the uploaded file (D-12). Row-level error codes are distinct from file/API-level codes.

## 1. Endpoints

### Application API — base path `/api`

| Method | Path | Purpose | Success | Principal failures |
|---|---|---|---|---|
| POST | `/api/imports` | Upload CSV (multipart field `file`) | `201` new batch; `200` deduplicated | `400`, `409`, `413`, `415`, `500` |
| GET | `/api/imports/{id}` | Batch summary | `200` | `404` |
| GET | `/api/imports/{id}/errors?page&size&code` | Paged row problems | `200` | `404` |
| GET | `/api/imports/{id}/errors.csv` | Download error report (**bonus, deferred** — D-17) | `200 text/csv` | `404` |
| GET | `/api/students?query&grade&section&page&size&sort` | Paged directory | `200` | `400` |
| GET | `/api/students/{studentId}` | Student report | `200` | `404` |

### Management endpoints — **not** under `/api` (T-5)

| Method | Path | Purpose |
|---|---|---|
| GET | `/actuator/health` | Liveness/readiness |

Only `/actuator/health` is exposed; all other Actuator endpoints are disabled (SECURITY.md §3). Management paths must not be routed through the `/api` prefix.

## 2. Upload behaviour — `POST /api/imports`

Request: `multipart/form-data`, field name `file`, filename with `.csv` extension. Size limit: **configurable, default 10 MiB** (U-06).

Flow (ARCHITECTURE.md §3–6): stream to temp file → SHA-256 → digest lookup → reserve batch → parse → validate → accepted-only upsert → finalise.

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

Query params: `page` (0-based), `size` (**default 50**, T-4), optional `code` filter (one of the five row-level codes). Ordered by `line_number` ascending, then field name. `line_number` is the **physical file line with header as line 1 and blank lines counted** `[REQ]` — implementation subject to the open C-4 spike.

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

## 5. Student directory — `GET /api/students`

| Param | Meaning |
|---|---|
| `query` | Case-insensitive match on uppercase `student_id` or `full_name` |
| `grade` | Optional 1–12 filter (bonus UI feature, D-17) |
| `section` | Optional A–F filter (bonus UI feature, D-17) |
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

## 6. Student report — `GET /api/students/{studentId}`

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

## 7. Pagination contract (T-4)

| Endpoint family | Default `size` | Maximum |
|---|---|---|
| `/api/imports/{id}/errors` | **50** | 100 |
| `/api/students` | **25** | 100 |

- `page` is 0-based. Invalid values → `400 INVALID_PARAMETER`.
- Every paged response contains `content`, `page`, `size`, `totalElements`, `totalPages`.
- Directory results are stably sorted by `student_id` ascending by default.
- No unbounded collection is returned; the bonus CSV export is the sole deliberate exception (D-17).

## 8. Error response conventions

```json
{
  "timestamp": "2026-11-19T09:15:00Z",
  "status": 409,
  "code": "BATCH_PROCESSING",
  "message": "This file is already being imported.",
  "details": ["batchId: 12"],
  "correlationId": "7f1c9b2e",
  "path": "/api/imports"
}
```

| Code | HTTP | Meaning |
|---|---|---|
| `MISSING_REQUIRED_COLUMNS` | 400 | Header lacks one or more required columns |
| `EMPTY_FILE` | 400 | No header, or no data rows |
| `INVALID_ENCODING` | 400 | Not decodable as UTF-8 |
| `INVALID_FILE_TYPE` | 415 | Missing `.csv` extension / wrong content type |
| `FILE_TOO_LARGE` | 413 | Exceeds the configurable 10 MiB default |
| `BATCH_PROCESSING` | 409 | Identical digest is active (`RECEIVED`/`PROCESSING`); includes `batchId` |
| `INVALID_PARAMETER` | 400 | Bad pagination/filter/sort value |
| `BATCH_NOT_FOUND` | 404 | Unknown import batch id |
| `STUDENT_NOT_FOUND` | 404 | Unknown student id |
| `INTERNAL_ERROR` | 500 | Unexpected failure; correlation id logged |

Row-level duplicates are reported only through `DUPLICATE_ID` in `import_row_error`, never as an API-level error.

## 9. Content and security notes

All responses use UTF-8. Dates are ISO-8601 (`YYYY-MM-DD`); timestamps are UTC ISO-8601. CORS restricted to the app origin. Error bodies never include secrets or logged PII bodies (SECURITY.md §5).
