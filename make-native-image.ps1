param()

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$nativePom = Join-Path $root "native\pom_native.xml"
$outputBin = Join-Path $root "native\target\T2ISO-native.exe"

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    throw "mvn not found in PATH."
}

if (-not $env:JAVA_HOME) {
    throw "JAVA_HOME must point to a GraalVM JDK."
}

$javaExe = Join-Path $env:JAVA_HOME "bin\java.exe"
if (-not (Test-Path $javaExe)) {
    throw "Invalid JAVA_HOME: $env:JAVA_HOME"
}

$ver = & $javaExe -version 2>&1
if (($ver -join "`n") -notmatch "GraalVM") {
    throw "JAVA_HOME is not a GraalVM JDK. Current java: `n$($ver -join "`n")"
}

Write-Host "[1/1] Building native image..."
& mvn -q -f $nativePom -DskipTests package

if (-not (Test-Path $outputBin)) {
    throw "Build finished, but native binary was not found: $outputBin"
}

Write-Host "Native image created: $outputBin"
