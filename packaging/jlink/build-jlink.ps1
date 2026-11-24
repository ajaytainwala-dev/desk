# Build a jlink portable runtime distribution for Desk.
#
# Usage:
#   pwsh -ExecutionPolicy Bypass -File packaging/jlink/build-jlink.ps1 [output-dir]
#
# This script:
#   1. Builds all Maven modules (skipping tests)
#   2. Locates the Java home (JAVA_HOME or java.home)
#   3. Runs jlink to create a minimal custom JRE
#   4. Copies the uber JAR and dependencies into the distribution
#   5. Creates launch scripts
#
# Output layout (default: dist/desk-portable/):
#   bin/
#     desk            (shell launcher)
#     desk.cmd        (Windows launcher)
#   lib/
#     desk-cli-...-uber.jar
#   runtime/
#     bin/java          (custom jlink JRE)
#     ...               (jlink runtime modules)
#   config/
#   README.txt

param(
  [string]$OutputDir = "dist/desk-portable"
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$RepoRoot = Resolve-Path (Join-Path $ScriptDir "..\..")

Write-Host "=== Desk jlink Portable Build ===" -ForegroundColor Cyan
Write-Host "Repo root: $RepoRoot"
Write-Host "Output dir: $OutputDir"

# Step 1: Determine Java home
$javaHome = $env:JAVA_HOME
if ($javaHome) {
  $javaHome = ($javaHome -replace "^\s+|\s+$", "")
}
if (-not $javaHome) {
  # Try to get java.home from the running JVM
  $javaHome = & java -XshowSettings:properties -version 2>&1 | Select-String "java.home" | ForEach-Object { $_.ToString().Trim().Split('=')[1] }
  if (-not $javaHome) {
    Write-Error "JAVA_HOME is not set and could not be detected from java -XshowSettings:properties"
    exit 1
  }
}
Write-Host "Using JAVA_HOME: $javaHome"

$javaHome = $javaHome.Trim()
$javaExe = Join-Path $javaHome "bin\java.exe"
$jlinkExe = Join-Path $javaHome "bin\jlink.exe"

if (-not (Test-Path $jlinkExe)) {
  Write-Error "jlink not found at $jlinkExe. A JDK is required (not just a JRE)."
  exit 1
}

# Step 2: Build all modules
Write-Host "`n[1/4] Building Maven modules..." -ForegroundColor Cyan
& "$RepoRoot\mvnw.cmd" -B -q -DskipTests package
if ($LASTEXITCODE -ne 0) {
  Write-Error "Maven build failed."
  exit 1
}

# Step 3: Create output directory structure
Write-Host "`n[2/4] Creating distribution layout..." -ForegroundColor Cyan
$DistDir = Resolve-Path (Join-Path $RepoRoot $OutputDir) -ErrorAction SilentlyContinue
if (-not $DistDir) {
  New-Item -ItemType Directory -Path $OutputDir -Force | Out-Null
  $DistDir = Resolve-Path (Join-Path $RepoRoot $OutputDir)
}

$BinDir = Join-Path $DistDir "bin"
$LibDir = Join-Path $DistDir "lib"
$RuntimeDir = Join-Path $DistDir "runtime"
$ConfigDir = Join-Path $DistDir "config"

New-Item -ItemType Directory -Path $BinDir -Force | Out-Null
New-Item -ItemType Directory -Path $LibDir -Force | Out-Null
New-Item -ItemType Directory -Path $ConfigDir -Force | Out-Null

# Step 4: Build jlink runtime
Write-Host "`n[3/4] Building jlink runtime image..." -ForegroundColor Cyan

# Determine which JDK modules are needed.
# For a basic CLI app using SLF4J + Lanterna, we need:
#   java.base, java.logging, java.xml, java.naming, java.security.jgss,
#   java.security.sasl, jdk.unsupported, jdk.crypto.ec
$jlinkModules = @(
  "java.base",
  "java.logging",
  "java.xml",
  "java.naming",
  "java.security.jgss",
  "java.security.sasl",
  "jdk.compiler",
  "jdk.unsupported",
  "jdk.crypto.ec"
)
$moduleList = $jlinkModules -join ","

# Clean previous jlink output if it exists
if (Test-Path $RuntimeDir) {
  Remove-Item -Recurse -Force $RuntimeDir
}

Write-Host "jlink modules: $moduleList"

& $jlinkExe `
  --module-path "$($javaHome)\jmods" `
  --add-modules $moduleList `
  --output $RuntimeDir `
  --compress=2 `
  --no-header-files `
  --no-man-pages `
  --vm=server

if ($LASTEXITCODE -ne 0) {
  Write-Error "jlink failed."
  exit 1
}

# Verify jlink produced a java.exe
$runtimeJava = Join-Path $RuntimeDir "bin\java.exe"
if (-not (Test-Path $runtimeJava)) {
  Write-Error "jlink did not produce runtime/java.exe at $runtimeJava"
  exit 1
}
Write-Host "jlink runtime created at $RuntimeDir" -ForegroundColor Green

# Step 5: Copy application JARs
Write-Host "`n[4/4] Copying application JARs..." -ForegroundColor Cyan

# Find the shaded application JAR (shade plugin replaces the original)
$uberJar = Get-ChildItem -Path "$RepoRoot\modules\desk-cli\target" -Filter "desk-cli-0.1.0-SNAPSHOT.jar" | Where-Object { $_.Name -notlike "*original*" -and $_.Name -notlike "*-uber*" } | Select-Object -First 1
if (-not $uberJar) {
  # Fallback: any non-original desk-cli jar
  $uberJar = Get-ChildItem -Path "$RepoRoot\modules\desk-cli\target" -Filter "desk-cli-*.jar" | Where-Object { $_.Name -notlike "*original*" } | Select-Object -First 1
}
if (-not $uberJar) {
  Write-Error "Application JAR not found in modules/desk-cli/target"
  exit 1
}

# Copy to a predictable name
Copy-Item $uberJar.FullName -Destination (Join-Path $LibDir "desk-cli.jar") -Force
Write-Host "Copied: $($uberJar.Name) -> desk-cli.jar"

# Optional: bundle a prebuilt TUI binary as bin/desk-tui.exe (env DESK_TUI_BINARY).
$tuiBinary = $env:DESK_TUI_BINARY
if ($tuiBinary -and (Test-Path $tuiBinary)) {
  $tuiDest = Join-Path $BinDir "desk-tui.exe"
  Copy-Item $tuiBinary $tuiDest -Force
  Write-Host "Bundled TUI: $tuiDest"
}

# Copy dependency JARs that aren't shaded into the uber JAR
# (shade plugin embeds them, so we only need the uber JAR)
Write-Host "All dependencies are embedded in the uber JAR."

# Step 6: Create launch scripts
Write-Host "`nCreating launch scripts..." -ForegroundColor Cyan

# Optional Rust TUI (desk-tui.exe) — never overwrite the CLI launcher named desk.
if ($env:DESK_TUI_BINARY -and (Test-Path $env:DESK_TUI_BINARY)) {
  Copy-Item $env:DESK_TUI_BINARY (Join-Path $BinDir "desk-tui.exe") -Force
  Write-Host "Bundled TUI: $(Join-Path $BinDir 'desk-tui.exe')"
} elseif (Test-Path "$RepoRoot\modules\desk-tui\target\release\desk.exe") {
  Copy-Item "$RepoRoot\modules\desk-tui\target\release\desk.exe" (Join-Path $BinDir "desk-tui.exe") -Force
  Write-Host "Bundled TUI from cargo release build"
}

# Shell launcher (for Git Bash, WSL, etc.)
$launchShell = @'
#!/bin/sh
# Desk launcher
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
RUNTIME_JAVA="$SCRIPT_DIR/../runtime/bin/java"
APP_JAR="$SCRIPT_DIR/../lib/desk-cli.jar"
if [ -z "${DESK_TUI:-}" ] && [ -x "$SCRIPT_DIR/desk-tui" ]; then
  DESK_TUI="$SCRIPT_DIR/desk-tui"
  export DESK_TUI
elif [ -z "${DESK_TUI:-}" ] && [ -x "$SCRIPT_DIR/desk-tui.exe" ]; then
  DESK_TUI="$SCRIPT_DIR/desk-tui.exe"
  export DESK_TUI
fi

if [ ! -x "$RUNTIME_JAVA" ]; then
  echo "Error: runtime Java not found at $RUNTIME_JAVA" >&2
  exit 1
fi

if [ ! -f "$APP_JAR" ]; then
  echo "Error: application JAR not found at $APP_JAR" >&2
  exit 1
fi

if [ -z "${DESK_TUI:-}" ] && [ -x "$TUI_BIN" ]; then
  export DESK_TUI="$TUI_BIN"
fi

exec "$RUNTIME_JAVA" -jar "$APP_JAR" "$@"
'@
$launchShell | Set-Content -Path (Join-Path $BinDir "desk") -Encoding UTF8
# chmod is not available on Windows; set executable bit when extracting on Unix

# Windows launcher
$launchCmd = @"
@echo off
setlocal EnableExtensions

set "SCRIPT_DIR=%~dp0"
set "RUNTIME_JAVA=%SCRIPT_DIR%..\runtime\bin\java.exe"
set "APP_JAR=%SCRIPT_DIR%..\lib\desk-cli.jar"
if not defined DESK_TUI if exist "%SCRIPT_DIR%desk-tui.exe" set "DESK_TUI=%SCRIPT_DIR%desk-tui.exe"

if not exist "%RUNTIME_JAVA%" (
    echo Error: runtime Java not found at %RUNTIME_JAVA% >&2
    exit /b 1
)

if not exist "%APP_JAR%" (
    echo Error: application JAR not found at %APP_JAR% >&2
    exit /b 1
)

if not defined DESK_TUI if exist "%TUI_BIN%" set "DESK_TUI=%TUI_BIN%"

"%RUNTIME_JAVA%" -jar "%APP_JAR%" %*
"@
$launchCmd | Set-Content -Path (Join-Path $BinDir "desk.cmd") -Encoding ASCII

# README
$readme = @"
Desk Portable Distribution
============================

This distribution contains a custom Java runtime (built with jlink) and the
Desk application. No system Java installation is required.

Directory layout:
  bin/
    desk        - Shell launcher (Git Bash, WSL, Linux, macOS)
    desk.cmd    - Windows command launcher
    desk-tui.exe - Native TUI editor (when bundled)
  lib/
    desk-cli.jar   - Application JAR (all dependencies embedded)
  runtime/
    Custom JRE (jlink image) - contains only the JDK modules needed by Desk
  config/
    Configuration files (created on first run)

Usage:
  Windows:    bin\desk.cmd [command] [args]
  Linux/macOS: bin/desk [command] [args]
  Git Bash:   ./bin/desk [command] [args]

Available commands:
  version  - Print the application version
  help     - Print available commands
  edit     - Open a file in the terminal editor
  fmt      - Format a Java source file (use --check to verify)
  compile  - Compile a Java source file
  run      - Run a compiled Java class

Examples:
  bin\desk.cmd version
  bin\desk.cmd fmt src/Main.java
  bin\desk.cmd compile src/Main.java
  bin\desk.cmd run Main

No installation required: extract this archive anywhere and run the launcher.
"@
$readme | Set-Content -Path (Join-Path $DistDir "README.txt") -Encoding ASCII

Write-Host "`n=== Build complete ===" -ForegroundColor Green
Write-Host "Distribution: $DistDir"
Write-Host "Launcher:     $BinDir\desk.cmd (Windows) / $BinDir/desk (shell)"
