#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Detekt 1.23.8's embedded Kotlin compiler requires a supported host JDK.
java_major=$(java -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')
if [[ -n "${JAVA_HOME:-}" ]]; then
  java_major=$("$JAVA_HOME/bin/java" -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')
fi
if [[ "$java_major" != 21 ]]; then
  echo "Run this pipeline with JAVA_HOME pointing to JDK 21." >&2
  exit 1
fi
./gradlew qualityCheck --no-configuration-cache
mkdir -p build/reports/security
uv tool run --from semgrep==1.156.0 semgrep scan \
  --config config/semgrep/security.yml --metrics off --disable-version-check \
  --error --strict --sarif --output build/reports/security/semgrep.sarif \
  app/src core/*/src feature/*/src

scripts/check-secrets.sh
