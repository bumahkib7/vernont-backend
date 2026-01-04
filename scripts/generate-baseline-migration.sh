#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
MIGRATIONS_DIR="$ROOT_DIR/vernont-api/src/main/resources/db/migration"
BASELINE_FILE="$MIGRATIONS_DIR/V1__baseline.sql"
EXTRAS_FILE="$ROOT_DIR/scripts/migration-extras.sql"

mkdir -p "$MIGRATIONS_DIR"

echo "Generating baseline migration from JPA entities..."
(cd "$ROOT_DIR" && ./gradlew :vernont-domain:generateSchema -PoutputFile="$BASELINE_FILE")

if [ -f "$EXTRAS_FILE" ]; then
  echo "Appending custom extras from $EXTRAS_FILE..."
  {
    echo ""
    echo "-- Custom extras (partials/expressions/JSONB, etc.)"
    cat "$EXTRAS_FILE"
  } >> "$BASELINE_FILE"
fi

echo "Baseline migration generated at $BASELINE_FILE."
