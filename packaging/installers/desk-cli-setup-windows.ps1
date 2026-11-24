# Desk CLI setup for Windows (PowerShell).
# Downloads the latest release and installs desk onto the user PATH.
#
# Usage:
#   irm https://github.com/ajaytainwala-dev/desk/releases/latest/download/desk-cli-setup-windows.ps1 | iex
#   .\desk-cli-setup-windows.ps1
[CmdletBinding()]
param(
  [string]$InstallDir = (Join-Path $env:LOCALAPPDATA "Desk\bin"),
  [string]$Repo = "ajaytainwala-dev/desk"
)

$ErrorActionPreference = "Stop"

Write-Host "=== Desk CLI setup (Windows) ===" -ForegroundColor Cyan

$api = "https://api.github.com/repos/$Repo/releases/latest"
Write-Host "Resolving latest release…"
$release = Invoke-RestMethod -Headers @{ Accept = "application/vnd.github+json" } -Uri $api
$tag = $release.tag_name
Write-Host "Latest release: $tag"

New-Item -ItemType Directory -Force -Path $InstallDir | Out-Null
$tmp = Join-Path $env:TEMP ("desk-setup-" + [guid]::NewGuid().ToString("n"))
New-Item -ItemType Directory -Force -Path $tmp | Out-Null

function Try-Download([string]$name) {
  $url = "https://github.com/$Repo/releases/download/$tag/$name"
  try {
    $out = Join-Path $tmp $name
    Invoke-WebRequest -Uri $url -OutFile $out -UseBasicParsing
    return $out
  } catch {
    return $null
  }
}

$assetPath = $null
foreach ($name in @(
  "desk-portable-windows.zip",
  "desk-portable.zip",
  "Desk-Setup-$($tag.TrimStart('v')).exe",
  "desk.exe",
  "desk-native-windows-latest",
  "desk-native-windows-latest.exe"
)) {
  Write-Host "Trying $name…"
  $assetPath = Try-Download $name
  if ($assetPath) { break }
}

if (-not $assetPath) {
  throw "No matching release asset found for Windows. See https://github.com/$Repo/releases/tag/$tag"
}

$destExe = Join-Path $InstallDir "desk.exe"
$portableRoot = Join-Path $env:LOCALAPPDATA "Desk\portable"

if ($assetPath -like "*.zip") {
  $extract = Join-Path $tmp "extract"
  Expand-Archive -Path $assetPath -DestinationPath $extract -Force
  $launcher = Get-ChildItem -Path $extract -Recurse -Filter "desk.cmd" | Select-Object -First 1
  $exe = Get-ChildItem -Path $extract -Recurse -Filter "desk.exe" | Where-Object {
    $_.Name -eq "desk.exe"
  } | Select-Object -First 1
  if ($exe -and (Test-Path (Join-Path $exe.Directory.FullName "..\runtime\bin\java.exe"))) {
    # Native-looking layout that still has a portable runtime sibling — treat as portable.
    $srcRoot = Split-Path $exe.Directory.FullName -Parent
    if (Test-Path $portableRoot) { Remove-Item $portableRoot -Recurse -Force }
    Copy-Item $srcRoot $portableRoot -Recurse -Force
  } elseif ($launcher) {
    # Portable jlink layout — keep under LocalAppData\Desk\portable
    if (Test-Path $portableRoot) { Remove-Item $portableRoot -Recurse -Force }
    $srcRoot = Split-Path (Split-Path $launcher.FullName -Parent) -Parent
    Copy-Item $srcRoot $portableRoot -Recurse -Force
  } elseif ($exe) {
    Copy-Item $exe.FullName $destExe -Force
  } else {
    throw "Zip did not contain desk.exe or desk.cmd"
  }

  if (Test-Path $portableRoot) {
    # Ensure a TUI binary is present next to the portable launcher.
    $bundledTui = Get-ChildItem -Path (Join-Path $portableRoot "bin") -Filter "desk-tui*" -ErrorAction SilentlyContinue |
      Select-Object -First 1
    if (-not $bundledTui) {
      $tuiAsset = Try-Download "desk-tui-windows.exe"
      if ($tuiAsset) {
        Copy-Item $tuiAsset (Join-Path $portableRoot "bin\desk-tui.exe") -Force
      } else {
        Write-Warning "desk-tui-windows.exe not available — TUI launch may fail until it is installed."
      }
    }
    $tuiPath = Join-Path $portableRoot "bin\desk-tui.exe"
    @"
@echo off
setlocal
if not defined DESK_TUI if exist "$tuiPath" set "DESK_TUI=$tuiPath"
"%~dp0..\portable\bin\desk.cmd" %*
"@ | Set-Content -Encoding ASCII (Join-Path $InstallDir "desk.cmd")
    $destExe = Join-Path $InstallDir "desk.cmd"
  }
  if ($bundledTui) {
    New-Item -ItemType Directory -Force -Path (Split-Path $tuiDest -Parent) | Out-Null
    Copy-Item $bundledTui.FullName $tuiDest -Force
    if (Test-Path (Join-Path $shareRoot "portable\bin")) {
      Copy-Item $bundledTui.FullName (Join-Path $shareRoot "portable\bin\desk-tui.exe") -Force
    }
    Write-Host "Installed TUI: $tuiDest"
  }
} elseif ($assetPath -like "*.exe" -and (Split-Path $assetPath -Leaf) -like "Desk-Setup*") {
  Write-Host "Launching GUI installer…"
  Start-Process -FilePath $assetPath -Wait
  Write-Host "GUI installer finished."
  exit 0
} else {
  Copy-Item $assetPath $destExe -Force
}

# Fetch standalone TUI when the portable zip did not already include it.
if (-not (Test-Path $tuiDest)) {
  Write-Host "Trying desk-tui-windows.exe…"
  $tuiPath = Try-Download "desk-tui-windows.exe"
  if ($tuiPath) {
    New-Item -ItemType Directory -Force -Path (Split-Path $tuiDest -Parent) | Out-Null
    Copy-Item $tuiPath $tuiDest -Force
    if (Test-Path (Join-Path $shareRoot "portable\bin")) {
      Copy-Item $tuiPath (Join-Path $shareRoot "portable\bin\desk-tui.exe") -Force
    }
    Write-Host "Installed TUI: $tuiDest"
  } else {
    Write-Host "Warning: desk-tui-windows.exe not found — interactive 'desk' needs DESK_TUI." -ForegroundColor Yellow
  }
}

# User PATH
$userPath = [Environment]::GetEnvironmentVariable("Path", "User")
if (-not $userPath) { $userPath = "" }
$parts = $userPath -split ";" | Where-Object { $_ -and $_.Trim() -ne "" }
if ($parts -notcontains $InstallDir) {
  $newPath = ($parts + $InstallDir) -join ";"
  [Environment]::SetEnvironmentVariable("Path", $newPath, "User")
  $env:Path = "$InstallDir;$env:Path"
  Write-Host "Added to user PATH: $InstallDir"
}

Write-Host ""
Write-Host "Installed: $destExe" -ForegroundColor Green
Write-Host "Open a new terminal, then run: desk version"
Write-Host "Done."
