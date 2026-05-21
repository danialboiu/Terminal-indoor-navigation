#!/usr/bin/env zsh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"

mkdir -p target/classes/static

cp -f src/main/resources/application.properties target/classes/application.properties
cp -Rf src/main/resources/static/. target/classes/static/

CP="target/classes:$(find target/_cp_extract/BOOT-INF/lib -name '*.jar' | tr '\n' ':')"

exec java \
  -Dserver.address=0.0.0.0 \
  -Dserver.port=8080 \
  -cp "$CP" \
  com.terminal.navigation.app.Main
