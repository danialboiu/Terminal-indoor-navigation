#!/usr/bin/env zsh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"

FROM_NODE="${1:-F0_ENT_A}"
TO_NODE="${2:-B1_GATES_30_31}"
PROFILE="${3:-ELDERLY}"
OUT_FILE="${4:-$ROOT_DIR/mock/route-instructions.prompt.json}"
TMP_FILE="${OUT_FILE}.tmp"

URL="https://localhost:8443/api/route-instructions?from=${FROM_NODE}&to=${TO_NODE}&profile=${PROFILE}"

mkdir -p "$(dirname "$OUT_FILE")"
curl -kfsS "$URL" --output "$TMP_FILE"

if [[ ! -s "$TMP_FILE" ]]; then
  rm -f "$TMP_FILE"
  echo "Prompt export failed: empty response for $URL" >&2
  exit 1
fi

rm -f "$TMP_FILE"

if [[ ! -s "$OUT_FILE" ]]; then
  echo "Prompt export failed: $OUT_FILE was not created by the backend." >&2
  exit 1
fi

echo "Saved AI prompt payload to: $OUT_FILE"
echo "Route: $FROM_NODE -> $TO_NODE ($PROFILE)"
