#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NATIVE_POM="$ROOT_DIR/native/pom_native.xml"
TARGET_DIR="$ROOT_DIR/native/target"
JAR_PATH="$TARGET_DIR/iso-native-0.7.jar"
CLASSPATH_FILE="$TARGET_DIR/runtime.classpath"
CONFIG_DIR="$ROOT_DIR/native/src/main/resources/META-INF/native-image/t2linux/iso-native"
BACKUP_DIR="$ROOT_DIR/native/src/main/resources/META-INF/native-image/t2linux/iso-native.backup.$(date +%Y%m%d-%H%M%S)"
TEMP_CONFIG_DIR="$TARGET_DIR/agent-config-fresh"

if [[ -z "${JAVA_HOME:-}" || ! -x "${JAVA_HOME}/bin/java" ]]; then
  echo "JAVA_HOME must point to a GraalVM JDK." >&2
  exit 1
fi

if ! java -version 2>&1 | grep -qi "GraalVM"; then
  echo "Current JAVA_HOME is not GraalVM." >&2
  java -version >&2
  exit 1
fi

echo "[1/6] Building native module jar..."
mvn -q -f "$NATIVE_POM" -DskipTests clean compile org.apache.maven.plugins:maven-jar-plugin:3.4.2:jar

echo "[2/6] Resolving runtime classpath..."
mvn -q -f "$NATIVE_POM" -DincludeScope=runtime -Dmdep.outputFile="$CLASSPATH_FILE" dependency:build-classpath

if [[ ! -f "$JAR_PATH" ]]; then
  echo "Jar not found: $JAR_PATH" >&2
  exit 1
fi

RUNTIME_CP="$(cat "$CLASSPATH_FILE"):$JAR_PATH"

rm -rf "$TEMP_CONFIG_DIR"
mkdir -p "$TEMP_CONFIG_DIR"

echo "[3/6] Running app with native-image-agent (fresh output)..."
echo "Use the app normally, cover key paths, then close it."
java \
  -Djava.home="$JAVA_HOME" \
  -agentlib:native-image-agent=config-output-dir="$TEMP_CONFIG_DIR" \
  -cp "$RUNTIME_CP" \
  iso.T2ISO

echo "[4/6] Backing up current config to:"
echo "       $BACKUP_DIR"
if [[ -d "$CONFIG_DIR" ]]; then
  mkdir -p "$(dirname "$BACKUP_DIR")"
  cp -a "$CONFIG_DIR" "$BACKUP_DIR"
fi

echo "[5/6] Replacing config with fresh minimal set..."
rm -rf "$CONFIG_DIR"
mkdir -p "$CONFIG_DIR"
cp -a "$TEMP_CONFIG_DIR"/. "$CONFIG_DIR"/

echo "[6/6] Done."
echo "Rebuild native image now:"
echo "  mvn -q -f native/pom_native.xml -DskipTests package"
