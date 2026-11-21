# JDK Management Guide

## Goals

- Discover installed JDKs (PATH, SDKMAN, macOS JVMs, vendor trees).
- Pin a workspace JDK without mutating system `JAVA_HOME`.
- Optionally install a **managed** Temurin JDK with explicit consent.

## CLI

```bash
desk java list
desk java detect
desk java current
desk java use 25          # pin for current workspace
desk java info 25
desk java doctor
desk java install 25 --consent   # managed Temurin download
desk java remove 25              # remove managed install (confirm)
desk java policy
```

## Managed install (Phase 4)

- Vendor discovery: Temurin (download implemented), Corretto/Zulu/Liberica (plan metadata).
- User must pass consent; dry-run plans show URL and archive name.
- SHA-256 checksum verification when Adoptium publishes checksums.
- Safe zip extraction (rejects path traversal); atomic activation; rollback on failure.
- Install root under Desk-managed directory — never overwrites system JDK.

## Pinning

Workspace file: `.desk/jdk.json`. Compile/run paths prefer the pin via `ToolchainContext` / `JdkSelector`.
