#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NO_BUILD="${1:-}"

command -v docker >/dev/null 2>&1 || { echo "Docker is required but was not found on PATH." >&2; exit 1; }
docker compose version >/dev/null 2>&1 || { echo "Docker Compose v2 is required." >&2; exit 1; }

cd "$ROOT"

if [[ ! -f .env ]]; then
  [[ -f .env.example ]] || { echo "Missing .env.example; cannot initialise .env." >&2; exit 1; }
  cp .env.example .env
  password="$(openssl rand -hex 24 2>/dev/null || head -c 48 /dev/urandom | base64 | tr -d '\n')"
  sed -i.bak "s|^POSTGRES_PASSWORD=.*|POSTGRES_PASSWORD=$password|" .env
  rm -f .env.bak
  echo "Created git-ignored .env with a generated local PostgreSQL password."
fi

APP_PORT="$(grep -E '^APP_PORT=' .env | cut -d= -f2 | tr -d '\r' || true)"
APP_PORT="${APP_PORT:-8080}"
FRONTEND_PORT="$(grep -E '^FRONTEND_PORT=' .env | cut -d= -f2 | tr -d '\r' || true)"
FRONTEND_PORT="${FRONTEND_PORT:-3000}"

if [[ "$NO_BUILD" == "--no-build" ]]; then
  docker compose up -d
else
  docker compose up -d --build
fi

health="http://localhost:${APP_PORT}/actuator/health"
ready=false
for _ in $(seq 1 60); do
  status="$(curl -s -o /dev/null -w '%{http_code}' "$health" || true)"
  if [[ "$status" == "200" ]]; then ready=true; break; fi
  sleep 3
done
if [[ "$ready" != "true" ]]; then
  docker compose logs --tail=80 >&2
  echo "Backend did not become healthy at $health within 180 seconds." >&2
  exit 1
fi

echo
echo "ClearGrade is ready:"
echo "  Frontend: http://localhost:${FRONTEND_PORT}"
echo "  Backend health: $health"
echo "  API: http://localhost:${APP_PORT}/api"
echo "Seed sample data (real HTTP API): bash scripts/seed.sh"
echo "Stop: docker compose down   |   Reset data: docker compose down -v"
