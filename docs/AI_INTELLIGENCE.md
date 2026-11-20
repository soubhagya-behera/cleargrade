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
        ▼
InsightProviderRouter           Gemini → Ollama → Deterministic
        │
        ├── GeminiInteractionsAdapter   (Gemini Interactions REST API)
        ├── OllamaChatAdapter           (Ollama native chat API)
        └── DeterministicInsightService (Java rules, no network)
```

`GET /api/ai/status` reports only safe availability flags, model names, routing state and reason codes —
never a key, endpoint, response body or credential.

| Priority | Engine | Source label | When used |
|---|---|---|---|
| 1 | Gemini Cloud AI | `GEMINI` | API key configured and a request succeeds |
| 2 | Ollama Local AI | `OLLAMA` | Gemini is absent, or Gemini has a fallback-class failure and Ollama is verified available |
| 3 | Java rule engine | `RULE_BASED` | Both AI providers are absent/unavailable, or Ollama fails after Gemini |

Routing details:

- HTTP `429 RESOURCE_EXHAUSTED`, temporary `5xx`, connection failure, timeout, an incomplete interaction or
  unusable structured output → `FALLBACK` → try Ollama, then the rule engine.
- HTTP `401/403`, invalid configuration and other non-quota `4xx` → `CONFIGURATION` → **no fallback**;
  surfaced as a controlled `503 AI_CONFIGURATION_ERROR`. A quota response is never treated as an auth error,
  and there are no automatic Gemini POST retries.
- Ollama reports `OLLAMA` only when `/api/tags` confirms the configured model is actually installed and
  reachable. A configured URL alone never means "available".
- Exactly one cloud provider and one local provider are considered, strictly sequentially — never concurrently.
- `fallbackReason` is a safe category such as `GEMINI_UNAVAILABLE`, `OLLAMA_UNAVAILABLE`,
  `NO_AI_PROVIDERS_AVAILABLE` or `AI_DISABLED`. Raw provider bodies and credentials are never returned.
- Every response labels the engine that actually produced it.

## 2. Gemini configuration and model choice

The existing environment variable name is reused; no new secret store is introduced.

| Variable | Default | Purpose |
|---|---|---|
| `GEMINI_API_KEY` | *(blank)* | Optional: enables Gemini. Never logged, returned by the API, sent to the browser or used in a Docker build argument. |
| `GEMINI_MODEL` | `gemini-3.8-flash` | Supported model; `gemini-2.5-flash` is **not** the default. |
| `GEMINI_ENDPOINT` | `https://generativelanguage.googleapis.com/v1beta/interactions` | Interactions REST endpoint. |
| `GEMINI_TIMEOUT_READ_MS` | `60000` | Response timeout. Connect timeout is fixed at 10 s. |

The request uses the official Interactions API shape (`POST /v1beta/interactions`, `x-goog-api-key`,
`Api-Revision: 2026-05-20`, `model`, `input`, `store: false`, `response_format` with
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
| `OLLAMA_MODEL` | *(blank)* | Optional installed model name, e.g. `llama3.2`. |
| `OLLAMA_TIMEOUT_READ_MS` | `60000` | Response timeout. Connect timeout is 5 s. |

Setup (choose a model that fits your machine; nothing is downloaded by ClearGrade):

```bash
# Install Ollama from https://ollama.com, then pull a model yourself:
ollama pull llama3.2
ollama serve                       # usually already running after installation
curl http://localhost:11434/api/tags
```

Then set `OLLAMA_BASE_URL=http://localhost:11434` and `OLLAMA_MODEL=llama3.2` in the ignored root `.env` (or
the external local profile for IDE runs). If the model has not been pulled, the status endpoint reports
`ollamaAvailable: false` and the router uses the rule engine; nothing is installed or downloaded at startup.
Docker images never download a model.

Docker host access is explicit and opt-in:

- **Windows / macOS:** `OLLAMA_BASE_URL=http://host.docker.internal:11434`
- **Linux:** `OLLAMA_BASE_URL=http://host.docker.internal:11434` with Compose `extra_hosts:
  ["host.docker.internal:host-gateway"]`, or the host gateway address such as `http://172.17.0.1:11434`,
  or run Ollama reachable on your own network. Ollama is **not** a mandatory Compose service.

Binding Ollama to a loopback-only interface keeps it local. Use the documented `OLLAMA_BASE_URL` override rather
than adding a multi-gigabyte model layer to an image.

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
- the `allowedEvidenceValues` map (the same backend-computed strings the validator will accept)
- a static, non-identifying schema column list (not the uploaded header)

**Never sent to any provider:** student names or IDs, emails, dates of birth, individual marks or grades,
raw CSV content or rows, row-level error messages, physical line numbers, persistence entities, uploaded
filenames, or full import API responses. The prompt consists of trusted backend instructions plus this DTO.
Tests capture the real Gemini/Ollama request bodies and assert that sensitive sentinels cannot occur; the rule
engine and the API response use the same aggregate view.

## 5. Structured response validation

The fixed schema requires `summary`, `overallAssessment`, `findings[]`, `recommendedActions[]` and
`limitations`. Each finding requires `title`, `severity` (`INFO|LOW|MEDIUM|HIGH`), `evidenceMetric`,
`evidenceValue`, `explanation` and `recommendedAction`.

Server-side validation rejects malformed JSON, unknown or missing fields, unsupported severity/metric values,
over-long text, more than eight findings or actions, and blocked identifier markers in free text. The
`evidenceValue` is **replaced with the backend-computed value** for the allowlisted metric; a model cannot
alter measured counters. Three sections remain distinct in the API and UI:

1. `measured` — Java-measured facts.
2. `summary` / `overallAssessment` / `findings` — engine interpretation.
3. `recommendedActions` / `limitations` — advisory guidance.

## 6. Deterministic rule engine

The `RULE_BASED` engine is Java-only, offline and reproducible. It applies measured thresholds such as a
rejected share ≥ 20%, more than one problem occurrence per rejected row, and non-zero counts for each of the
five error codes. Every finding carries an evidence metric and the **exact** backend value; recommendations
append that evidence. No opaque quality score is produced. The same context always produces the same report.
It is explicitly labelled as rule-based — never presented as AI-generated.

## 7. Cost control, caching and cooldown

- Analysis runs **only** on the explicit **Analyse import** action. Upload/import never invokes a provider.
- Bounded in-memory LRU cache (default 600 s TTL, 64 entries), keyed by batch id + deterministic context hash
  + provider/model version. TTL/entries are configurable; no database migration or insight table exists.
- Concurrent requests for the same batch/context are coalesced into one provider call.
- A failed provider call is not cached; a genuinely new analysis after expiry requires another explicit action.
- Optional per-batch cooldown (`CLEARGRADE_AI_COOLDOWN_SECONDS`, default `0`) returns `429 ANALYSIS_COOLDOWN`.
- Reports state `cached: true|false` safely; no internal or sensitive detail is exposed.

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
