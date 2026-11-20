# Build a GraalVM Native Image for Desk.
#
# Prerequisites:
#   - GraalVM JDK installed with native-image component
#   - GRAALVM_HOME set (or JAVA_HOME pointing to GraalVM)
#
# Usage:
#   pwsh -ExecutionPolicy Bypass -File packaging/native-image/build-native.ps1 [output-name]
#
# Output:
#   dist/native/desk.exe (Windows) or dist/native/desk (Linux/macOS)

param(
  [string]$OutputName = "desk"
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Definition
$RepoRoot = Resolve-Path (Join-Path $ScriptDir "..\..")

# Determine GraalVM home
$graalvmHome = $env:GRAALVM_HOME
if (-not $graalvmHome) {
  $graalvmHome = $env:JAVA_HOME
}
if (-not $graalvmHome) {
  Write-Error "GRAALVM_HOME or JAVA_HOME must be set to a GraalVM JDK with native-image."
  exit 1
}

$nativeImageExe = Join-Path $graalvmHome "bin\native-image.exe"
if (-not (Test-Path $nativeImageExe)) {
  # Try without .exe (Linux/macOS)
  $nativeImageExe = Join-Path $graalvmHome "bin\native-image"
}
if (-not (Test-Path $nativeImageExe)) {
  Write-Error "native-image not found in $graalvmHome/bin. Install the native-image component."
  exit 1
}

Write-Host "=== Desk Native Image Build ===" -ForegroundColor Cyan
Write-Host "GraalVM: $graalvmHome"
Write-Host "Output:  $OutputName"

# Build all modules
Write-Host "`nBuilding Maven modules..." -ForegroundColor Cyan
& "$RepoRoot\mvnw.cmd" -B -q -DskipTests package
if ($LASTEXITCODE -ne 0) {
  Write-Error "Maven build failed."
  exit 1
}

# Find the shaded application JAR
$uberJar = Get-ChildItem -Path "$RepoRoot\modules\desk-cli\target" -Filter "desk-cli-0.1.0-SNAPSHOT.jar" | Where-Object { $_.Name -notlike "*original*" -and $_.Name -notlike "*-uber*" } | Select-Object -First 1
if (-not $uberJar) {
  $uberJar = Get-ChildItem -Path "$RepoRoot\modules\desk-cli\target" -Filter "desk-cli-*.jar" | Where-Object { $_.Name -notlike "*original*" } | Select-Object -First 1
}
if (-not $uberJar) {
  Write-Error "Application JAR not found in modules/desk-cli/target"
  exit 1
}

# Find dependency JARs (for classpath, though they're in the uber JAR)
# The uber JAR should contain all dependencies, so we only need it.

# Create output directory
$DistDir = Join-Path $RepoRoot "dist\native"
New-Item -ItemType Directory -Path $DistDir -Force | Out-Null

# Build native image
$outputExe = Join-Path $DistDir $OutputName
if ([environment]::OSVersion.Platform -eq [platformid]::Win32NT) {
  $outputExe = $outputExe + ".exe"
}

Write-Host "`nRunning native-image..." -ForegroundColor Cyan
Write-Host "Output: $outputExe"

& $nativeImageExe `
  --no-fallback `
  --class-path $uberJar.FullName `
  -H:Name=$OutputName `
  -H:Path=$DistDir `
  -H:ReflectionConfigurationFiles="$RepoRoot\packaging\native-image\reflect-config.json" `
  -H:ResourceConfigurationFiles="$RepoRoot\packaging\native-image\resource-config.json" `
  -H:JNIConfigurationFiles="$RepoRoot\packaging\native-image\jni-config.json" `
  -H:ProxyConfigurationFiles="$RepoRoot\packaging\native-image\proxy-config.json" `
  -H:+ReportExceptionStackTraces `
  -H:+ReportUnsupportedElementsAtRuntime `
  desk.cli.Main

if ($LASTEXITCODE -ne 0) {
  Write-Error "native-image failed."
  exit 1
}

if (-not (Test-Path $outputExe)) {
  Write-Error "Native image not produced at $outputExe"
  exit 1
}

Write-Host "`n=== Build complete ===" -ForegroundColor Green
Write-Host "Native executable: $outputExe"
Write-Host "Size: $([math]::Round((Get-Item $outputExe).Length / 1MB, 2)) MB"
