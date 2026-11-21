# Phase 2 Results

**Branch:** `cursor/desk-workbench-foundation-22b9`  
**Date:** 2026-11-21

## Bugs fixed

| ID | Fix |
|----|-----|
| P2-001 | `JavaSourceCompiler` / `JavaRunner` use workspace JDK via external `javac`/`java` (`ProcessIo`) |
| P2-002 | `JdkProvisioner.install` returns `InstallStatus.NOT_IMPLEMENTED` (honest, no fake success) |
| P2-003 | Non-Temurin vendors rejected until URL builders exist |
| P2-004 | javac fallback fails futures for completion/navigation (no empty-list lie) |
| P2-006 | `BuildService` / `ProcessIo` concurrent stream drain + deadline timeout |
| P2-007 | `ProjectTemplates` rejects `..` / path escape |
| P2-012 | `JavaRunner` no longer sequential stdout/stderr deadlock |
| P2-013 | `GitService.add` uses `--` and rejects leading `-` paths |
| P2-016 | Source walk filters normalize `\` → `/` |
| TUI-1 | Opening existing tab preserves unsaved edits; `:e` refuses dirty; `:e!` force |
| TUI-2 | Tab bar no longer full-buffer sync/clone every frame |
| TUI-3 | Session restore from `.desk/session.json` |
| TUI-4 | Viewport-based syntax highlighting |
| TUI-8 | Test for multi-tab unsaved preservation |
| Dist | macOS portable jlink job; setup script no Linux zip fallback |

## Features implemented

- Real **JSON-RPC 2.0 + Content-Length** LSP transport (`ContentLengthTransport`, `JsonRpcConnection`, `LspSession`)
- JDT LS discovery via `DESK_JAVA_LS` / PATH / common dirs (no machine hardcoding)
- CLI: `desk lsp status|diagnose|complete|hover|definition|references|symbols`
- `desk doctor`, `desk clean`
- Compile/check honor workspace JDK pin

## Tests executed

| Suite | Result |
|-------|--------|
| `./mvnw test` (JDK 25) | Pass |
| `cargo test` | 20 pass |
| `cargo clippy -D warnings` | Pass |
| `cargo fmt` | Applied |

## Remaining limitations

- Full JDT LS end-to-end requires installing `jdtls` (CI may not have it); transport + session are tested; live JDT optional
- Managed JDK download still not implemented (consent + plan only)
- TUI does not yet host in-editor completion popup (use `desk lsp` CLI / next phase UI)
- Find-in-files / fuzzy command palette still deferred
- Split views deferred
- Benchmarks not yet established numerically

## Security notes

- Process execution uses argument arrays + `ProcessIo`
- Git path args guarded
- Project name path escape blocked
- Temp LSP dirs cleaned in javac fallback
- Credentials still redacted in Git output
