# ClearGrade Intelligence — AI Data Quality Copilot

ClearGrade Intelligence is an **explicit-trigger, advisory-only** post-release enhancement. It interprets
*measured* import aggregates for a completed batch. It is an enterprise-style analysis panel, not a chatbot,
and it never changes marks, grades, pass/fail decisions, accepted/rejected rows or batch counters.

The original eight development phases (0–7) remain recorded as complete in [PROJECT_STATUS.md](../PROJECT_STATUS.md).
Nothing in this milestone rewrites that history.

## 1. Architecture and provider routing

Dependencies point inward: `adapters → application → domain`. Provider SDKs, HTTP clients and wire DTOs live
only in `adapters.out.ai.*`; the application and domain layers contain no provider-specific code. No
LangChain, Spring AI or agent framework is used.

```text
POST /api/imports/{batchId}/insights
        │
        ▼
ImportInsightService            batch guard, privacy context, bounded TTL cache, coalescing
        │
        ├── DeterministicInsightService.assess()   ← Java-owned quality policy
        │
        ▼
InsightProviderRouter           Gemini → Ollama → Deterministic
        │
        ├── GeminiInteractionsAdapter   (Gemini Interactions REST API; store=false, stream=false, no POST retries)
        ├── OllamaChatAdapter           (Ollama native chat API; /api/tags verification, /api/chat non-streaming, format JSON schema)
        └── DeterministicInsightService (authoritative Java findings, no network)
```

`QualityAssessment` is the only source of findings, severity, evidence metrics, canonical values and
recommendations. AI providers receive that assessment and may only explain approved findings and improve
their wording; they cannot add, remove, re-severity or re-metric a finding.

`GET /api/ai/status` reports only safe configuration/reachability flags, model names, routing state, the
last analysis result and reason codes — never a key, endpoint, response body or credential. It deliberately
does **not** report "AI providers available": `geminiConfigured` only means a key is present,
`ollamaReachable`/`ollamaModelPresent` come from `/api/tags`, and `inferenceTested` is always `false`
because status never performs a quota-consuming inference.

| Priority | Engine | Source label | When used |
|---|---|---|---|
| 1 | Gemini Cloud AI | `GEMINI` | API key configured and a request succeeds (HTTP 2xx, no network/timeout error) |
| 2 | Ollama Local AI | `OLLAMA` | Gemini is absent, or Gemini has a fallback-class failure (5xx, 429, connection failure, timeout, or semantic rejection) and Ollama is verified available via `/api/tags` |
| 3 | Java rule engine | `RULE_BASED` | Both AI providers are absent/unavailable, or Ollama fails after Gemini (semantic rejection, size failure, network error, or incomplete output) |

Routing details:

- HTTP `429 RESOURCE_EXHAUSTED`, temporary `5xx`, connection failure, timeout, incomplete interaction,
  unusable structured output, or a structurally valid response that fails semantic validation →
  `FALLBACK` → try Ollama, then the rule engine. Semantic rejections use `GEMINI_SEMANTIC_REJECTED` /
  `OLLAMA_SEMANTIC_REJECTED`.
- HTTP `401/403`, invalid configuration and other non-quota `4xx` → `CONFIGURATION` → **no fallback**;
  surfaced as a controlled `503 AI_CONFIGURATION_ERROR`. A quota response is never treated as an auth error,
  and there are no automatic Gemini POST retries.
- Ollama reports `OLLAMA` only when `/api/tags` confirms the configured model is actually installed and
  reachable. A configured URL alone never means "available".
- Exactly one cloud provider and one local provider are considered, strictly sequentially — never concurrently.
- `GEMINI_NETWORK` (no response/timeout) and `GEMINI_SERVER` (upstream 5xx) are fallback-class failures that
  continue the chain; `GEMINI_QUOTA` (429) is also fallback-class with no automatic POST retries.
- `GEMINI_AUTH` (401/403) and other invalid configuration are `CONFIGURATION` class with no fallback,
  surfaced as `503 AI_CONFIGURATION_ERROR`. A quota response is never treated as an auth error.
- Provider output that fails structural or semantic Java validation (`PROVIDER_VALIDATION`, `MALFORMED_JSON`,
  `GEMINI_SEMANTIC_REJECTED` / `OLLAMA_SEMANTIC_REJECTED`) is a `FALLBACK`; the router continues to the next
  provider. Ollama failures use codes such as `OLLAMA_UNAVAILABLE`, `OLLAMA_NETWORK`, `OLLAMA_INCOMPLETE`
  and `OLLAMA_NO_TEXT`.
- `fallbackReason` is a safe code chain such as `GEMINI:GEMINI_QUOTA`,
  `GEMINI:GEMINI_SERVER;OLLAMA:OLLAMA_UNAVAILABLE`, `NO_AI_PROVIDERS_AVAILABLE` or `AI_DISABLED`.
  Raw provider text, response bodies and credentials are never returned.
- Every response labels the engine that actually produced it. Safe usage metadata (input/output/thinking/total
  tokens, model, provider status, interaction status, duration) is returned when the provider supplies it;
  missing metadata is reported as `Usage unavailable` and never estimated.
- Sanitized failure telemetry (provider, safe code, HTTP status category/exact status when available, duration
  and whether fallback succeeded) is retained for Gemini even when Ollama produces the report. No API key,
  authorization header, response body or environment value is logged or returned.

## 2. Gemini configuration and model choice

The existing environment variable name is reused; no new secret store is introduced.

| Variable | Default | Purpose |
|---|---|---|
| `GEMINI_API_KEY` | *(blank)* | Optional: enables Gemini. Never logged, returned by the API, sent to the browser or used in a Docker build argument. |
| `GEMINI_MODEL` | `gemini-3.8-flash` | Supported model; `gemini-2.5-flash` is **not** the default. |
| `GEMINI_ENDPOINT` | `https://generativelanguage.googleapis.com/v1beta/interactions` | Interactions REST endpoint. |
| `GEMINI_TIMEOUT_READ_MS` | `60000` | Response timeout. Connect timeout is fixed at 10 s. |
| `cleargrade.ai.gemini.thinking-level` | `low` | Bound reasoning cost; high reasoning is unnecessary for aggregate analysis. |
| `cleargrade.ai.gemini.max-output-tokens` | `2048` | Output cap; smaller than the strict parser's worst case only if explicitly misconfigured. |

The request uses the official Interactions API shape (`POST /v1beta/interactions`, `x-goog-api-key`,
`Api-Revision: 2026-05-20`, `model`, `input`, top-level `store: false` and `stream: false`,
`generation_config` containing `thinking_level` and `max_output_tokens`, and `response_format` with
`mime_type: application/json` and an explicit JSON schema). It is a stateless single request: no
`previous_interaction_id`, no conversation history, no automatic POST retry. See
[Getting started](https://ai.google.dev/gemini-api/docs/get-started) and
[Structured output](https://ai.google.dev/gemini-api/docs/structured-output).

Google announced that Gemini 2.5 Flash retires on **1 December 2026**, with requests routing to Gemini 3.5
Flash-Lite. ClearGrade therefore defaults to a supported model directly and makes it configurable via
`GEMINI_MODEL`; a DevScout value of `gemini-2.5-flash` should not be copied over.

### Reusing an existing key safely

If the key exists only in another project's local environment, copy its **value** into ClearGrade's already
git-ignored root `.env` yourself, as `GEMINI_API_KEY=…`, using your own local editor or shell. Do not paste it
into chat, tickets, logs, `docker compose config` output, frontend `VITE_*` variables or Docker build arguments.
Nothing in this repository reads, prints or validates the key's value; only whether a non-blank value is present.
The key is never committed. Docker Compose passes it through at runtime with an empty default, so it is optional.

### `store=false` and provider terms

`store=false` asks the Interactions API not to persist the interaction for stateful reuse. It **does not**
eliminate all processing, transmission or retention obligations. Provider quota, abuse monitoring, logging and
data-handling terms still apply, and they differ between **free** and **paid** Gemini API tiers. Free-tier traffic
may be used for service improvement under Google's terms; paid tiers generally offer different processing terms
and higher quotas. Review the current Google AI terms and your billing configuration before enabling the
feature with real data — even though the payload is aggregate-only. ClearGrade makes no representation beyond
the privacy contract in §4.

## 3. Ollama local fallback

| Variable | Default | Purpose |
|---|---|---|
| `OLLAMA_BASE_URL` | *(blank)* | Optional base URL, e.g. `http://localhost:11434`. |
| `OLLAMA_MODEL` | *(blank)* | Optional installed model name, e.g. `llama3.2:1b`. |
| `OLLAMA_TIMEOUT_READ_MS` | `60000` | Response timeout. Connect timeout is 5 s. |

### Installed on this machine (verified)

- Official distribution: `winget install --id Ollama.Ollama --exact` — **Ollama 0.40.16**, installed to
  `%LOCALAPPDATA%\Programs\Ollama`.
- Selected model: **`llama3.2:1b`** (1.2 B parameters, 1.23 GB download, ~2 GB RAM recommended). It supports
  Ollama's JSON-schema `format` output and fits this 15.7 GB machine alongside Docker Desktop.
- Service: `ollama.exe serve`, verified on `127.0.0.1:11434` only — never publicly bound.
- The backend uses `OLLAMA_BASE_URL=http://host.docker.internal:11434` from the ignored root `.env`.
  `docker-compose.yml` maps `host.docker.internal` to `host-gateway`, which also works on Linux.
- If Ollama is not running after a reboot, start it with
  `& "$env:LOCALAPPDATA\Programs\Ollama\ollama.exe" serve` or launch the Ollama app. No model is downloaded by
  ClearGrade at startup or during image builds; Ollama is not a Compose service.

To verify manually:

```powershell
Invoke-RestMethod http://127.0.0.1:11434/api/tags
Invoke-RestMethod http://localhost:3000/api/ai/status
```

The first shows the installed model; the second reports `ollamaReachable: true`, `ollamaModelPresent: true`,
`ollamaModel: llama3.2:1b` and `defaultEngine: OLLAMA` while `GEMINI_API_KEY` is blank. If the model has not
been pulled, `ollamaModelPresent` is `false` and the rule engine is used. A configured URL alone never counts
as availability; `inferenceTested` stays `false` because status never performs an inference.

**Historical observation (2026-11-20):** the 1 B model occasionally returned text that failed ClearGrade's
then-current evidence/schema validation (for example on a clean import with all-zero counters). The router
labelled the report `RULE_BASED` with reason `OLLAMA_UNAVAILABLE`; no fabricated AI text was shown. Current
validation rules are described in §5 and current verification status in [README.md](../README.md) §Testing
and Quality; no post-hardening live Ollama report is claimed here.

## 4. Privacy data contract

Every provider receives the **same** purpose-built DTO built by `ImportQualityContextBuilder`. No free text
from an upload reaches a provider. The exact outbound aggregate fields are:

- `rowsRead`, `importedCount`, `rejectedCount`
- `newCount`, `changedCount`, `unchangedCount`
- `errorCount`
- counts for the five permitted codes: `MISSING_REQUIRED`, `INVALID_FORMAT`, `OUT_OF_RANGE`, `DUPLICATE_ID`,
  `WRONG_FIELD_COUNT`
- backend-calculated `importedPercentage`, `rejectedPercentage`, `errorsPerRejectedRow`, `newPercentage`,
  `changedPercentage`, `unchangedPercentage`
- the `canonicalEvidenceValues` map (the same backend-computed strings the validator will accept)
- a static, non-identifying schema column list (not the uploaded header)

**Never sent to any provider:** student names or IDs, emails, dates of birth, individual marks or grades,
raw CSV content or rows, row-level error messages, physical line numbers, persistence entities, uploaded
filenames, or full import API responses. The prompt consists of trusted backend instructions plus this DTO.
Tests capture the real Gemini/Ollama request bodies and assert that sensitive sentinels cannot occur; the rule
engine and the API response use the same aggregate view.

## 5. Structured response validation

The provider schema requires exactly one root key, `findings`. Each finding contains only `findingId` and
`explanation`. Severity, evidence metric, canonical value, title, explanation baseline, summary, overall
assessment, limitations and recommended actions are **never** model-controlled output fields: they come from
the Java assessment. Providers may supply explanation wording only; the report narrative is Java-authored.

Validation is structural and semantic:

- Exact keys, text types, non-blank content, length bounds, array size and blocked identifier markers.
- Every approved finding must appear exactly once, in any order; unknown, duplicate or missing identifiers are
  rejected.
- The explanation must be plain, readable text that includes the finding's canonical evidence value
  (numerically equivalent decimal forms are accepted), is relevant to that finding, and contains no field
  labels, JSON fragments, prompt echoes or duplicated Java baseline text. No other metric name may be
  referenced. Any provider-supplied `recommendedAction`, `summary`, `overallAssessment` or `limitations`
  field is rejected outright.
- Numeric claims and percentages must be canonical values from the measured context. Every denominator
  expression (`X out of Y`, `X of Y`, `X/Y`, and reliably detectable natural-language forms) must equal
  `rowsRead`; `9 of 25 rows were rejected (36.0%)` is valid, `9 of 16` is not. Invented baselines,
  expected/acceptable ranges, benchmarks and generic filler phrases are rejected.
- Text that contradicts an approved finding (for example "no issues" when findings exist) is rejected.
- Semantic failures are `FALLBACK` quality failures with codes `GEMINI_SEMANTIC_REJECTED` /
  `OLLAMA_SEMANTIC_REJECTED`; the router continues the safe chain. Raw provider text is never returned.

Four sections remain distinct in the API and UI:

1. `measured` — Java-measured facts.
2. Findings — Java-selected and Java-severitised; AI supplies explanation wording only.
3. `summary` / `overallAssessment` / `limitations` — Java-authored. The limitations sentence is built from
   canonical counters, for example `9 of 25 rows could not be imported (36%).`; inconsistent totals use a
   neutral message instead of a misleading ratio.
4. `recommendedActions` — Java-authored advice, unchanged by providers and grounded in the canonical evidence value.

## 6. Deterministic rule engine

The `RULE_BASED` engine is Java-only, offline and reproducible. `assess()` is the authoritative policy: it
applies the existing thresholds (rejected share ≥ 20% → HIGH; more than one problem occurrence per rejected
row → MEDIUM; non-zero counts for each of the five error codes → MEDIUM/LOW) and returns a `QualityAssessment`
with stable finding IDs, exact evidence and specific advice. Operational counters (`ROWS_READ`,
`IMPORTED_COUNT`, `NEW_COUNT`, `CHANGED_COUNT`, `UNCHANGED_COUNT` and their percentages) are **context only**,
never findings. A clean import produces no findings. No opaque quality score is produced. The same context
always produces the same report, and `RULE_BASED` reports are explicitly labelled as Java-generated.

## 7. Cost control, caching and cooldown

- Analysis runs **only** on the explicit **Analyse import** action. Upload/import never invokes a provider.
- Bounded in-memory LRU cache (default 600 s TTL, 64 entries), keyed by batch id + deterministic context hash
  + provider/model version. TTL/entries are configurable; no database migration or insight table exists.
- Concurrent requests for the same batch/context are coalesced into one provider call, including explicit
  refresh requests, so repeated clicks cannot spend extra quota.
- A failed provider call is not cached. `POST /api/imports/{id}/insights?refresh=true` bypasses the read cache
  and is only sent by the explicit **Run new analysis** button; it repopulates the cache on success.
- Optional per-batch cooldown (`CLEARGRADE_AI_COOLDOWN_SECONDS`, default `0`) returns `429 ANALYSIS_COOLDOWN`.
- Reports state `cached: true|false` safely; no internal or sensitive detail is exposed.
- **Limitation:** the cache is in-memory and disappears on backend restart, so reopening the same batch after a
  restart can call a provider again on the first explicit analysis. A privacy-safe persistent per-batch cache
  (counters/fingerprint + report text only, no PII) would avoid this, but it needs a database migration and is
  not introduced until the need is established.

## 8. Disabling AI / no-key behaviour

Set `CLEARGRADE_AI_ENABLED=false` to disable both AI providers; the rule engine still analyses, labelled
`RULE_BASED` with `AI_DISABLED`. Blank `GEMINI_API_KEY` and blank `OLLAMA_BASE_URL`/`OLLAMA_MODEL` are the
normal defaults: the app starts, imports and REST work, and deterministic analysis always works. No Ollama
installation or network is required. Setting `CLEARGRADE_AI_DETERMINISTIC_ENABLED=false` as well makes analysis
return a controlled `503 ANALYSIS_UNAVAILABLE`.

## 9. Security boundary

**Authentication is still absent.** This application remains for local/trusted evaluation only and must not be
publicly exposed without an authentication layer and network controls. The AI feature adds no write path of any
kind: providers cannot modify data or trigger imports/retries. See [SECURITY.md](SECURITY.md).

## 10. Historical local acceptance evidence (Windows, 2026-11-20)

The observations below are **historical records from the development machine**, not current verification
results. They describe what was exercised at that time; current automated coverage and the verified/unverified
distinction are recorded in [README.md](../README.md) §Testing and Quality. Routing was exercised against the
running Compose stack and isolated backend instances using a **local mock Gemini server**; no real Gemini
request was made and no key value was read or displayed.

| Scenario | Observed result |
|---|---|
| No key, Ollama available | `/api/ai/status` → `defaultEngine: OLLAMA`, routing `OLLAMA_THEN_RULE_BASED`; analysis → `engine: OLLAMA`, `llama3.2:1b`, fresh and cached paths both verified |
| No key, Ollama unreachable | isolated backend with unreachable `OLLAMA_BASE_URL` → `engine: RULE_BASED`, `NO_AI_PROVIDERS_AVAILABLE` |
| Mock Gemini 200 with valid structured output | `engine: GEMINI`, model `gemini-3.8-flash`; outbound body had `store:false`, no PII, no `previous_interaction_id` |
| Mock Gemini 429 | one provider call only → `engine: OLLAMA`, `GEMINI:GEMINI_QUOTA` (no cloud retries) |
| Mock Gemini 500 + Ollama down | `engine: RULE_BASED`, `GEMINI:GEMINI_SERVER`, safe fallback reason |
| Mock Gemini 401 | `503 AI_CONFIGURATION_ERROR`, message "Gemini rejected the configured credentials" — **not** classified as quota |
| `CLEARGRADE_AI_ENABLED=false` | status `AI_DISABLED_DETERMINISTIC_ONLY`; analysis `RULE_BASED` / `AI_DISABLED`; upload and directory APIs still work |
| Unusable model JSON / evidence mismatch | provider output rejected by the parser → Ollama or rule-based fallback; no fabricated insight rendered |
| Semantic rejection | wrong denominator, invented baseline, incorrect severity, generic advice or duplicated finding → `GEMINI_SEMANTIC_REJECTED` / `OLLAMA_SEMANTIC_REJECTED`, chain preserved |
| Batch 4 (25/16/9, 9 errors, 16 new, 0 changed) | rejected share HIGH; no multiple-errors finding at 1.00; new/changed only in measured facts; Java fallback grounded |

Browser acceptance was executed in headless Chrome via Playwright against `http://localhost:3000`, including
upload, analysis, directory search/filter/pagination, student report, and desktop/tablet/mobile viewports;
zero console errors and no key/PII in API responses. The curated gallery from that work is committed under
[`docs/screenshots/`](screenshots/) (eight captures: import, import summary, row problems, intelligence report,
intelligence outcome, student directory, student report finder and individual report). No temporary local
screenshot paths are required to review the release.

A later real-key browser attempt produced `Last analysis: OLLAMA · GEMINI:GEMINI_SERVER`: the Gemini
Interactions POST returned an upstream HTTP 5xx and the safe chain continued to Ollama. This is evidence of
**fallback behaviour only**, not successful live Gemini generation; live Gemini output remains unverified. No key
value, authorization header or raw response body was read or displayed. See
[LOCAL_RUN_GUIDE.md](../LOCAL_RUN_GUIDE.md) for the exact one-line `.env` entry.
