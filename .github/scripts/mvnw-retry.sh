#!/usr/bin/env bash
# Run Maven Wrapper with exponential backoff (transient Central / network flakes).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
MAX="${MVNW_RETRY_MAX:-5}"

attempt=1
while true; do
  if bash "$ROOT/mvnw" "$@"; then
    exit 0
  fi
  if [ "$attempt" -ge "$MAX" ]; then
    echo "mvnw failed after $MAX attempt(s)" >&2
    exit 1
  fi
  delay=$((attempt * 5))
  echo "mvnw failed (attempt $attempt/$MAX); retrying in ${delay}s..." >&2
  sleep "$delay"
  attempt=$((attempt + 1))
done