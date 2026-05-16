#!/usr/bin/env zsh
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$ROOT_DIR"

mkdir -p target/classes/static target/classes/certs

cp -f src/main/resources/application.properties target/classes/application.properties
cp -f src/main/resources/certs/terminal-nav.p12 target/classes/certs/terminal-nav.p12
cp -Rf src/main/resources/static/. target/classes/static/

exec java \
  -Dloader.path=target/classes \
  -Dserver.address=0.0.0.0 \
  -Dserver.port=8443 \
  -Dserver.ssl.enabled=true \
  -Dserver.ssl.key-store=file:$ROOT_DIR/target/classes/certs/terminal-nav.p12 \
  -Dserver.ssl.key-store-type=PKCS12 \
  -Dserver.ssl.key-store-password=changeit \
  -Dserver.ssl.key-password=changeit \
  -Dserver.ssl.key-alias=terminal-nav \
  -Djavax.net.ssl.keyStore=$ROOT_DIR/target/classes/certs/terminal-nav.p12 \
  -Djavax.net.ssl.keyStorePassword=changeit \
  -Djavax.net.ssl.keyStoreType=PKCS12 \
  -cp target/terminal-indoor-navigation-1.0.0.jar \
  org.springframework.boot.loader.launch.PropertiesLauncher
