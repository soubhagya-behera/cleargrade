# ClearGrade — everyday local development (Windows PowerShell)

## Start in two terminals

Prerequisite: native PostgreSQL 18 service `postgresql-x64-18` is running (it is set to Automatic). If it is
stopped after a reboot, start it from `services.msc` or an elevated PowerShell:

```powershell
Start-Service postgresql-x64-18
```

**Terminal 1 — backend**

```powershell
cd backend
mvn spring-boot:run
```

**Terminal 2 — frontend**

```powershell
cd frontend
npm run dev
```

Open <http://localhost:5173>. Health: <http://localhost:8080/actuator/health>. API: <http://localhost:8080/api>.

Set `JAVA_HOME=C:\Program Files\Java\jdk-21.0.10` for the backend terminal if your default `java` is version 17.
Both processes bind to `127.0.0.1` only.

## Database and pgAdmin

| Item | Value |
|---|---|
| Server | Native PostgreSQL 18, `localhost:5432`, Windows service `postgresql-x64-18` (Automatic) |
| Database | `cleargrade` (created by you; never dropped or recreated by setup) |
| Application role | `cleargrade_local` — least privilege: CONNECT on the database plus USAGE/CREATE on `public`, CRUD on application tables/sequences and default privileges for future objects |
| Passwords | The application password is held in the git-ignored root `.env` as `CLEARGRADE_LOCAL_DB_PASSWORD` and, for native Spring runs, in the git-ignored `backend/config/application-local.properties`; the temporary provisioning superuser password is no longer needed and was removed. Never displayed or committed. |
| Schema | Managed by Flyway; migration version `1` (`flyway_schema_history`, `student`, `import_batch`, `import_row_error`) |
| pgAdmin | Locally installed pgAdmin 4 (under `C:\Program Files\PostgreSQL\18\pgAdmin 4`) |

A pgAdmin server entry **ClearGrade Local (cleargrade)** was added for you:
host `127.0.0.1`, port `5432`, maintenance database `cleargrade`, username `cleargrade_local`.
No password is saved in pgAdmin — enter the application password (from `.env`) when prompted, or use your
`postgres` login to inspect everything. You can browse Databases → cleargrade → Schemas → public → Tables and
query records with the Query Tool.

## Configuration responsibilities

| File | Purpose |
|---|---|
| `application.properties` (committed) | All non-secret defaults: app name, port `8080`, loopback bind, native JDBC URL `jdbc:postgresql://localhost:5432/cleargrade`, username `cleargrade_local`, Flyway/Hibernate settings, Gemini model/endpoint/timeouts, native Ollama defaults (`CLEARGRADE_AI_OLLAMA_*`), cache limits. |
| Root `.env` (git-ignored) | Secrets and overrides: `CLEARGRADE_LOCAL_DB_PASSWORD`, optional `GEMINI_API_KEY`, native `CLEARGRADE_AI_OLLAMA_*` overrides, plus Docker-only `POSTGRES_PASSWORD`, port overrides and `OLLAMA_BASE_URL` / `OLLAMA_MODEL`. |
| `.env.example` (committed) | Documented placeholder template for new clones. No real values. |

| Ollama setting | Used by | Notes |
|---|---|---|
| `CLEARGRADE_AI_OLLAMA_BASE_URL` | Native backend | Default `http://127.0.0.1:11434`; can be set in `.env` |
| `CLEARGRADE_AI_OLLAMA_MODEL` | Native backend | Default `llama3.2:1b`; can be set in `.env` |
| `OLLAMA_BASE_URL` | Docker Compose `.env` only | Compose maps it into `CLEARGRADE_AI_OLLAMA_BASE_URL` |
| `OLLAMA_MODEL` | Docker Compose `.env` only | Compose maps it into `CLEARGRADE_AI_OLLAMA_MODEL` |

Spring Boot imports the root `.env` via `spring.config.import=optional:file:../.env[.properties]` relative to the
`backend` directory, so no environment variables need to be exported manually. Every `${...}` placeholder in
`application.properties` can resolve from it — including `CLEARGRADE_LOCAL_DB_PASSWORD`, `GEMINI_API_KEY` and the
optional model, timeout, Ollama, cache and upload-limit overrides.

The native database URL and username are **not** duplicated in `.env` — they are defaults in
`application.properties`. Native Ollama overrides use `CLEARGRADE_AI_OLLAMA_BASE_URL` /
`CLEARGRADE_AI_OLLAMA_MODEL`; the shorter `OLLAMA_*` names are Docker Compose `.env` variables that Compose maps
into those backend settings. To change the Gemini key, edit only `GEMINI_API_KEY` in `.env` and restart the backend.
Do not put the key in Vite, `VITE_*`, Java source, committed properties, Docker build arguments or image layers.
AI is optional; with no key the app still starts and uses Ollama or the rule engine.

## Provider behaviour

- No key + Ollama running → **Ollama**; the report says "Explanation provided by Ollama" (or "Gemini was unavailable, so Ollama provided the explanation." on Gemini fallback).
- Ollama unavailable → **Rule-based**; the report says "ClearGrade built-in analysis" or "AI explanations were unavailable. ClearGrade's built-in analysis is shown instead."
- Valid key → **Gemini** (`gemini-3.8-flash`) primary; quota/timeout/5xx falls to Ollama, then rules.
- Invalid key → controlled `503 AI_CONFIGURATION_ERROR`; never treated as quota.
- Set `CLEARGRADE_AI_ENABLED=false` in `.env` (or environment) + backend restart → both AI providers off; rule-based still available.

Check with `Invoke-RestMethod http://localhost:5173/api/ai/status`.

## Ollama

Ollama is optional and local. A historical development setup used Ollama 0.40.16 with model `llama3.2:1b`,
served on `127.0.0.1:11434` independently of Docker; versions and resource needs are environment-specific.
After a reboot (adjust the path to your installation):

```powershell
& "$env:LOCALAPPDATA\Programs\Ollama\ollama.exe" serve
Invoke-RestMethod http://127.0.0.1:11434/api/tags
```

## Optional Docker Compose environment (release/demo)

Docker Compose is preserved as an alternative. It uses its **own** PostgreSQL on the host port set by
`POSTGRES_PORT` in `.env` (default 5432; this machine uses a non-default port) with a separate named volume; it
does not touch the native database. The Compose backend receives `SERVER_ADDRESS=0.0.0.0` so nginx can proxy to it
inside the Compose network.

```powershell
# stop native processes first (Ctrl+C in both terminals), then:
docker compose up -d --build
# frontend http://localhost:3000  health http://localhost:8080/actuator/health
docker compose stop backend frontend   # keep PostgreSQL volume intact
```

Never run `docker compose down -v` unless you intentionally want to delete Docker database data.

## Optional host hardening (requires administrator rights)

For a trusted local deployment, configure the native PostgreSQL service to listen on loopback only
(`listen_addresses = 'localhost'` in its `postgresql.conf`) and restart the service. ClearGrade's backend binds
to `127.0.0.1` by default, and every published Compose host port is loopback-bound; the database listener is
under the operator's control and is not changed by this application's setup. No other documented setup step
requires administrator rights.

## Troubleshooting

| Symptom | Fix |
|---|---|
| Backend fails to connect | Confirm `postgresql-x64-18` is running and `.env` has the correct `CLEARGRADE_LOCAL_DB_PASSWORD`. The URL/username are defaults in `application.properties`. |
| Port 8080/5173 busy | Stop the other workflow (Docker containers or native terminals) before starting this one. |
| `ollamaReachable: false` or `ollamaModelPresent: false` | Start `ollama serve`, confirm `/api/tags` lists `llama3.2:1b`, restart backend. |
| Rule-based despite Ollama available | Small model returned output that failed strict validation; safe fallback. Retry or configure a larger installed model. |
| `AI_CONFIGURATION_ERROR` | Check `GEMINI_API_KEY` in `.env`; the model default is in `application.properties` (override with `GEMINI_MODEL` if needed). Restart backend. |
| pgAdmin connection prompt | Enter the `cleargrade_local` password from `.env`, or connect with your `postgres` login. |

**Security note:** authentication is still absent. Keep everything on loopback/local/trusted networks only.
