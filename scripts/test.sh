#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

java_version="$(java -version 2>&1 | head -n 1 || true)"
case "$java_version" in
  *'"21.'* ) ;;
  * ) echo "JDK 21 is required for the backend build; found: $java_version. Set JAVA_HOME." >&2; exit 1 ;;
esac

echo '== Backend: Maven verify =='
(cd backend && bash ./mvnw -B verify)

echo '== Frontend: tests =='
(
  cd frontend
  npm test
  npm run typecheck
  npm run build
)

echo '== Docker Compose configuration =='
docker compose config --quiet

echo '== Whitespace check =='
git diff --check

echo 'All release checks passed.'
