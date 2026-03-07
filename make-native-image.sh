#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NATIVE_POM="$ROOT_DIR/native/pom_native.xml"
OUTPUT_BIN="$ROOT_DIR/native/target/T2ISO-native"

cd "$ROOT_DIR"

if ! command -v mvn >/dev/null 2>&1; then
  echo "mvn not found in PATH." >&2
  exit 1
fi

if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME}/bin/java" ]]; then
  echo "JAVA_HOME must point to a GraalVM JDK." >&2
  echo "Example:" >&2
  echo "  export JAVA_HOME=/usr/lib/jvm/java-25-graalvm" >&2
  echo "  export PATH=\$JAVA_HOME/bin:\$PATH" >&2
  exit 1
fi

if ! java -version 2>&1 | grep -qi "GraalVM"; then
  echo "Current java is not GraalVM. Please switch JAVA_HOME." >&2
  java -version >&2
  exit 1
fi

echo "[1/1] Building native image..."
mvn -q -f "$NATIVE_POM" -DskipTests package

if [[ ! -f "$OUTPUT_BIN" ]]; then
  echo "Build finished, but native binary was not found: $OUTPUT_BIN" >&2
  exit 1
fi

echo "Native image created: $OUTPUT_BIN"
