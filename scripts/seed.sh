#!/usr/bin/env bash
set -euo pipefail

API_BASE_URL="${API_BASE_URL:-http://localhost:8080/api}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
FIXTURE_DIR="$SCRIPT_DIR/seed"

upload() {
  local file="$1"
  local fixture="$FIXTURE_DIR/$file"
  if [[ ! -f "$fixture" ]]; then
    echo "Seed fixture not found: $fixture" >&2
    exit 1
  fi
  local body status
  body="$(mktemp)"
  trap 'rm -f "$body"' RETURN
  status="$(curl --silent --show-error --output "$body" --write-out '%{http_code}' \
    --form "file=@$fixture;type=text/csv" "$API_BASE_URL/imports")" || {
      echo "curl failed while uploading $file" >&2
      return 1
    }
  case "$status" in
    201) echo "  $file -> 201 new import" ;;
    200) echo "  $file -> 200 deduplicated (digest already COMPLETED); no rows written" ;;
    409) echo "  $file -> 409 BATCH_PROCESSING (identical digest active); no rows written" ;;
    *) echo "Unexpected HTTP $status for $file: $(cat "$body")" >&2; return 1 ;;
  esac
  if [[ -s "$body" ]]; then
    echo "    $(cat "$body")"
  fi
  echo "$status"
}

echo "Seeding ClearGrade through $API_BASE_URL/imports (real HTTP API)."
first="$(upload initial.csv | tail -n 1)"
second=""
if [[ "${SKIP_UPDATE:-false}" != "true" ]]; then
  second="$(upload update.csv | tail -n 1)"
fi

echo
echo "Seed complete. All rows were written by the application import pipeline."
echo "Re-running is deterministic: identical digests return 200 deduplicated (or 409 while active), never a new import."
echo "To reset: docker compose down -v; then scripts/start.sh and re-run this seed."
if [[ "$first" == "409" || "$second" == "409" ]]; then
  exit 2
fi
