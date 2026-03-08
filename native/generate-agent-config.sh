#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NATIVE_POM="$ROOT_DIR/native/pom_native.xml"
TARGET_DIR="$ROOT_DIR/native/target"
JAR_PATH="$TARGET_DIR/iso-native-0.7.jar"
CLASSPATH_FILE="$TARGET_DIR/runtime.classpath"
CONFIG_DIR="$ROOT_DIR/native/src/main/resources/META-INF/native-image/t2linux/iso-native"

mkdir -p "$CONFIG_DIR"

echo "[1/3] Building native module jar (without native-image)..."
if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME}/bin/java" ]]; then
  echo "JAVA_HOME is not set to a valid JDK." >&2
  echo "Example:" >&2
  echo "  export JAVA_HOME=/usr/lib/jvm/java-25-graalvm" >&2
  echo "  export PATH=\$JAVA_HOME/bin:\$PATH" >&2
  exit 1
fi

if ! java -version 2>&1 | grep -qi "GraalVM"; then
  echo "JAVA_HOME must point to a GraalVM JDK for native-image-agent." >&2
  echo "Current java:" >&2
  java -version >&2
  echo "Example:" >&2
  echo "  export JAVA_HOME=/usr/lib/jvm/java-25-graalvm" >&2
  echo "  export PATH=\$JAVA_HOME/bin:\$PATH" >&2
  exit 1
fi

mvn -q -f "$NATIVE_POM" -DskipTests clean compile org.apache.maven.plugins:maven-jar-plugin:3.4.2:jar

echo "[2/3] Resolving runtime classpath..."
mvn -q -f "$NATIVE_POM" -DincludeScope=runtime -Dmdep.outputFile="$CLASSPATH_FILE" dependency:build-classpath

if [[ ! -f "$JAR_PATH" ]]; then
  echo "Jar not found: $JAR_PATH" >&2
  exit 1
fi

RUNTIME_CP="$(cat "$CLASSPATH_FILE"):$JAR_PATH"

echo "[3/3] Starting app with native-image-agent (merge mode)..."
echo "Interact with the app, then close it to flush metadata."

java \
  -Djava.home="$JAVA_HOME" \
  -agentlib:native-image-agent=config-merge-dir="$CONFIG_DIR" \
  -cp "$RUNTIME_CP" \
  iso.T2ISO

echo "Metadata merged into: $CONFIG_DIR"
echo "Now rebuild native image with:"
echo "  mvn -q -f native/pom_native.xml -DskipTests package"
