# Stage inputs and build Desk-Setup-<version>.exe with NSIS (makensis).
#
# Usage (from repo root, on Windows with NSIS installed):
#   pwsh -File packaging/installers/build-windows-installer.ps1 -Version 0.1.0
param(
  [Parameter(Mandatory = $true)][string]$Version,
  [string]$PortableDir = "",
  [string]$NativeExe = "",
  [string]$OutDir = "dist"
)

$ErrorActionPreference = "Stop"
$RepoRoot = Resolve-Path (Join-Path $PSScriptRoot "..\..")
Set-Location $RepoRoot

$inputDir = Join-Path $RepoRoot "dist\installer-input"
if (Test-Path $inputDir) { Remove-Item $inputDir -Recurse -Force }
New-Item -ItemType Directory -Force -Path $inputDir | Out-Null

Copy-Item (Join-Path $RepoRoot "LICENSE") (Join-Path $inputDir "LICENSE")
@"
Desk ${Version}
===========

Thank you for installing Desk.

After setup, open a new terminal and run:

  desk version
  desk help

Portable files (if included) live under %LOCALAPPDATA%\Desk\portable
"@ | Set-Content -Encoding UTF8 (Join-Path $inputDir "README.txt")

if ($PortableDir -and (Test-Path $PortableDir)) {
  $dest = Join-Path $inputDir "portable"
  Copy-Item $PortableDir $dest -Recurse -Force
  Write-Host "Staged portable from $PortableDir"
}

if ($NativeExe -and (Test-Path $NativeExe)) {
  Copy-Item $NativeExe (Join-Path $inputDir "desk.exe") -Force
  Write-Host "Staged native exe from $NativeExe"
} else {
  # Prefer TUI binary named desk.exe if present in cwd artifacts
  $candidates = @(
    "modules\desk-tui\target\release\desk.exe",
    "dist\native\desk.exe"
  )
  foreach ($c in $candidates) {
    $full = Join-Path $RepoRoot $c
    if (Test-Path $full) {
      Copy-Item $full (Join-Path $inputDir "desk.exe") -Force
      Write-Host "Staged $full"
      break
    }
  }
}

if (-not (Test-Path (Join-Path $inputDir "desk.exe")) -and -not (Test-Path (Join-Path $inputDir "portable"))) {
  throw "Nothing to install: provide -PortableDir and/or -NativeExe"
}

New-Item -ItemType Directory -Force -Path (Join-Path $RepoRoot $OutDir) | Out-Null
$outFile = Join-Path $RepoRoot "$OutDir\Desk-Setup-$Version.exe"

$makensis = Get-Command makensis -ErrorAction SilentlyContinue
if (-not $makensis) {
  $guess = @(
    "${env:ProgramFiles(x86)}\NSIS\makensis.exe",
    "$env:ProgramFiles\NSIS\makensis.exe"
  ) | Where-Object { Test-Path $_ } | Select-Object -First 1
  if ($guess) {
    $makensis = @{ Source = $guess }
  }
}
if (-not $makensis) {
  throw "makensis not found. Install NSIS (https://nsis.sourceforge.io/) or choco install nsis"
}

$nsi = Join-Path $RepoRoot "packaging\installers\desk-setup.nsi"
$args = @(
  "/DDESK_VERSION=$Version",
  "/DDESK_INPUT=$inputDir",
  "/DOUTFILE=$outFile",
  $nsi
)

Write-Host "Running makensis…"
& $makensis.Source @args
if ($LASTEXITCODE -ne 0) { throw "makensis failed with exit $LASTEXITCODE" }

Write-Host "Built $outFile" -ForegroundColor Green
Get-Item $outFile | Format-List FullName, Length
