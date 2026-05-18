#!/usr/bin/env zsh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"

export OPENAI_USE_MOCK="${OPENAI_USE_MOCK:-true}"
export OPENAI_MOCK_RESPONSE_FILE="${OPENAI_MOCK_RESPONSE_FILE:-$ROOT_DIR/mock/route-instructions.mock.json}"

echo "OPENAI_USE_MOCK=$OPENAI_USE_MOCK"
echo "OPENAI_MOCK_RESPONSE_FILE=$OPENAI_MOCK_RESPONSE_FILE"

exec "$ROOT_DIR/run_https_phone.sh"
