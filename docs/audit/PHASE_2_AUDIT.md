# Phase 2 Hardening Audit

**Branch audited:** `cursor/desk-workbench-foundation-22b9` vs `origin/main`  
**Scope:** Java modules changed in this PR (`desk-core`, `desk-cli`, `desk-java`)  
**Date:** 2026-11-21  
**Diff scale:** ~69 files / +5700 lines (includes docs + TUI; this audit focuses on Java)

## Method

- `git diff origin/main...HEAD --stat`
- Inspected key new/changed types: `DocumentStore`, `WorkspaceStore`, `BuildService`, `JdkProvisioner`, `JdkSelector`, `JavaSourceCompiler`, `LanguageClient` / LSP host, CLI commands, and existing unit tests.

## Verdict (focus questions)

| # | Question | Answer |
|---|----------|--------|
| 7 | Does `JavaSourceCompiler` still ignore discovered/project JDK? | **Yes.** It gates on `preferredJdk()` then compiles via Desk JVM `ToolProvider`; never uses workspace pin / `ToolchainContext`. |
| 8 | `DocumentStore` / `WorkspaceStore` edge cases | **Several:** untitled cannot be session-persisted; `saveAll` skips dirty untitled; concurrent recent-file RMW; `atomicWrite` fallback is non-atomic and mislabeled. |
| 9 | `BuildService` process handling | **Partial:** merges stderr OK; **timeout does not cover hung stdout reads**; no cancellation API. |
| 10 | `JdkProvisioner` claiming install but throwing | **Yes.** After `--yes` consent it still throws “not auto-executed”. Vendor≠Temurin still plans Adoptium URLs. |

---

## Issues

### P2-001 — `JavaSourceCompiler` ignores project JDK (and barely uses preferred JDK)

| Field | Value |
|-------|-------|
| **File** | `modules/desk-java/src/main/java/desk/java/JavaSourceCompiler.java:49-54` |
| **Severity** | **critical** |
| **Problem** | Compiles with `ToolProvider.getSystemJavaCompiler()` (Desk runtime JVM). `JdkDiscovery.preferredJdk()` is only a null-check; workspace pin / `ToolchainContext` / `desk.java.project.home` are unused. `desk compile` / `desk check` / javac LSP diagnostics therefore ignore `desk java use`. |
| **Repro** | Pin workspace to JDK 25 while Desk runs on another feature version; `desk compile Foo.java` still uses Desk’s `ToolProvider`, not the pinned home. |
| **Proposed fix** | Accept optional `Path projectJdkHome` (or `ToolchainContext`); invoke that JDK’s `javac` via process (like `BuildService`) or document that in-process compile is always Desk-runtime-bound and stop claiming preferred/project JDK. Wire `CompileCommand`/`CheckCommand` through `JdkSelector.current`. |
| **Test required** | Unit/integration: workspace pin ≠ `java.home` → compile uses pinned toolchain (or explicit failure if in-process-only). |

### P2-002 — `JdkProvisioner.install` claims install then throws after consent

| Field | Value |
|-------|-------|
| **File** | `modules/desk-java/src/main/java/desk/java/JdkProvisioner.java:119-136` |
| **Severity** | **high** |
| **Problem** | Public `install()` after `consent=true` always throws `JavaException` (“planned but not auto-executed”). CLI `desk java install 25 --yes` looks like a real install; doctor suggests it “when available”. |
| **Repro** | `desk java install 25 --yes` → exit 1 with plan text, no download, no managed JDK. |
| **Proposed fix** | Rename to `planOrInstall`, return a structured `InstallResult` with `UNSUPPORTED`/`NOT_IMPLEMENTED`, or implement download+checksum+extract. Make CLI print a clear “not implemented” without implying success path. |
| **Test required** | `install(consent=true, dryRun=false)` asserts non-success status / expected exception message; CLI test for `--yes` without dry-run. |

### P2-003 — Non-Temurin vendors still get Adoptium download URLs

| Field | Value |
|-------|-------|
| **File** | `modules/desk-java/src/main/java/desk/java/JdkProvisioner.java:96-97`, `177-180` |
| **Severity** | **high** |
| **Problem** | `plan()` always calls `adoptiumLatestUrl(...)` regardless of `Vendor` (Corretto/Zulu/Liberica). Wrong URL + wrong checksum if install is later implemented. |
| **Repro** | `desk java install 25 --vendor corretto --dry-run` → URL host is `api.adoptium.net`. |
| **Proposed fix** | Per-vendor URL builders, or reject non-Temurin until supported. |
| **Test required** | Dry-run plans for each vendor assert URL/vendor consistency or unsupported error. |

### P2-004 — LSP completion/navigation APIs return empty lists (placeholders as real API)

| Field | Value |
|-------|-------|
| **File** | `modules/desk-java/src/main/java/desk/java/lsp/JavacLanguageClient.java:90-107`; `ExternalProcessLanguageClient.java:74-90`; `LanguageClient.java:22-28` |
| **Severity** | **high** |
| **Problem** | Interface exposes `complete` / `definition` / `references` / `codeActions` as first-class async APIs; both implementations always return `List.of()`. Easy for callers to treat as working intelligence. Inventory marks LSP “DONE” while Partial admits empty completions. |
| **Repro** | Call `new JavacLanguageClient().complete(...)` → always empty, success future. |
| **Proposed fix** | Throw `UnsupportedOperationException`, return failed futures, or split `DiagnosticsOnlyClient` vs full `LanguageClient`. Do not advertise completion as available in feature inventory. |
| **Test required** | Contract test documenting unsupported ops; diagnostics-only positive test. |

### P2-005 — LSP / `DocumentStore` / `ToolchainContext` not wired into CLI or TUI

| Field | Value |
|-------|-------|
| **File** | `modules/desk-java/src/main/java/desk/java/lsp/LanguageServerHost.java` (entire); `DocumentStore.java`; TUI has no Java references |
| **Severity** | **high** |
| **Problem** | Dead integration surface: `LanguageServerHost`, `DocumentStore`, and most of `ToolchainContext` are unused by `desk-cli` / `desk-tui`. Session JSON written by CLI is not restored by TUI (`docs/REMAINING_LIMITATIONS.md` #7). Features look shipped in Java but have no editor path. |
| **Repro** | Grep TUI/CLI for `LanguageServerHost` / `DocumentStore` → no production call sites (only a tiny store identity test). |
| **Proposed fix** | Wire or mark package as experimental/internal; restore session tabs in TUI; use `DocumentStore` as single text authority. |
| **Test required** | Smoke: open workspace → TUI restores paths from session; optional LSP diagnose from editor command. |

### P2-006 — `BuildService` timeout does not cover hung process I/O; no cancellation

| Field | Value |
|-------|-------|
| **File** | `modules/desk-java/src/main/java/desk/java/build/BuildService.java:164-177` |
| **Severity** | **high** |
| **Problem** | Reads stdout to EOF **before** `waitFor(30, MINUTES)`. A hung child that never closes stdout blocks forever; 30‑minute timeout never starts. No cancel/interrupt API for `BuildRequest.watch` / UI stop. InterruptedException is swallowed into “Build failed to start”. |
| **Repro** | Point build at a wrapper script that prints nothing and sleeps indefinitely → `desk build` hangs. |
| **Proposed fix** | Drain streams on a helper thread; apply deadline around the whole process; `destroyForcibly` on timeout/interrupt; optional `Future`/`CancellationToken`. |
| **Test required** | Fake process that never exits → times out with non-zero result; interrupt mid-build clears interrupt status correctly. |

### P2-007 — `ProjectTemplates` allows `..` / `.` project names (path escape)

| Field | Value |
|-------|-------|
| **File** | `modules/desk-java/src/main/java/desk/java/project/ProjectTemplates.java:71-72`, `236-244` |
| **Severity** | **high** |
| **Problem** | `sanitizeName` keeps `.` `_` `-`; name `..` resolves to parent of `parentDir` after normalize → create project outside intended directory. |
| **Repro** | `desk new .. --template basic` (or API `CreateRequest` with name `".."`) under a writable parent. |
| **Proposed fix** | Reject names that are `.`/`..`, contain `..`, or whose normalized root is not strictly under `parentDir`. |
| **Test required** | Assert `IOException`/`IllegalArgumentException` for `..`, `../x`, `.`. |

### P2-008 — Untitled documents: no save-as; `saveAll` silently drops dirty untitled

| Field | Value |
|-------|-------|
| **File** | `modules/desk-core/src/main/java/desk/core/document/DocumentStore.java:42-46`, `73-92`; `OpenDocumentState.java:17-19` |
| **Severity** | **high** |
| **Problem** | `createUntitled` exists; `save` throws if `path == null`; `saveAll` skips dirty untitled without error → **data loss**. `OpenDocumentState` requires non-null path → untitled cannot be session-persisted. No `saveAs` / path-assign that updates `pathIndex`. |
| **Repro** | `createUntitled("x"); setContent(...); saveAll();` → dirty content never written, no exception. |
| **Proposed fix** | Add `saveAs(id, path)` updating indexes; `saveAll` should fail or return unsaved untitled ids; allow nullable path in session or skip with explicit warning list. |
| **Test required** | Untitled dirty `saveAll` behavior; `saveAs` identity + pathIndex; session round-trip policy. |

### P2-009 — `WorkspaceStore.atomicWrite` fallback is non-atomic / misleading

| Field | Value |
|-------|-------|
| **File** | `modules/desk-core/src/main/java/desk/core/workspace/WorkspaceStore.java:258-276` |
| **Severity** | **medium** |
| **Problem** | Comment claims “Fallback for cross-device ATOMIC_MOVE”; body catches **any** failure from `FileSystem.writeString` (which already falls back from `ATOMIC_MOVE`) and writes with `Files.writeString` (non-atomic, partial-write risk). Concurrent `rememberRecent` RMW has no file lock → lost updates. |
| **Repro** | Force `FileSystem.writeString` failure (e.g. mock/readonly race) → fallback non-atomic write; two processes calling `open` concurrently can clobber `recent-workspaces.json`. |
| **Proposed fix** | Remove redundant fallback or only catch atomic-move-specific cases; use same atomic helper; file lock or write-to-temp+replace for recent list. |
| **Test required** | Crash-safe write test; concurrent rememberRecent stress (at least documenting last-writer-wins). |

### P2-010 — Hand-rolled JSON parser fragile for paths/names

| Field | Value |
|-------|-------|
| **File** | `modules/desk-core/src/main/java/desk/core/workspace/WorkspaceStore.java:162-190`, `279-313`; also `JdkSelector.extractJsonString` |
| **Severity** | **medium** |
| **Problem** | Session/recent parsing splits on `},{` and uses first-key `indexOf`; paths containing `}` or unusual escapes can truncate/misparse. `JdkSelector.extractJsonString` does not handle escaped quotes inside values. |
| **Repro** | Save session with a path containing `}` (possible on some systems) or pin home with `\"` → load may drop docs / misread home. |
| **Proposed fix** | Use a minimal real JSON library already on classpath, or stricter escaping + tests for edge paths. |
| **Test required** | Round-trip paths with spaces, backslashes, quotes; corrupt JSON → empty optional, no throw. |

### P2-011 — `JavacLanguageClient` temp directory leak + unused global property

| Field | Value |
|-------|-------|
| **File** | `modules/desk-java/src/main/java/desk/java/lsp/JavacLanguageClient.java:52-63` |
| **Severity** | **medium** |
| **Problem** | `Files.createTempDirectory("desk-lsp-")` never deleted. Sets `System.setProperty("desk.java.project.home", ...)` which nothing reads (false sense that project JDK is used; see P2-001). |
| **Repro** | Repeated `diagnose` → `/tmp/desk-lsp-*` accumulates. |
| **Proposed fix** | try/finally delete recursively; stop setting unused system property; pass JDK into compiler API. |
| **Test required** | diagnose cleans temp; no leftover dirs. |

### P2-012 — `JavaRunner` stdout/stderr sequential drain deadlock risk

| Field | Value |
|-------|-------|
| **File** | `modules/desk-java/src/main/java/desk/java/JavaRunner.java:60-67` |
| **Severity** | **medium** |
| **Problem** | Pre-existing but still used by `desk run` after Phase 2 toolchain work: `redirectErrorStream(false)` then read stdout fully, then stderr → classic pipe deadlock if child fills stderr while stdout still open. Also ignores project JDK (`preferredJdk` only). |
| **Repro** | Run a class that writes large stderr before closing stdout. |
| **Proposed fix** | `redirectErrorStream(true)` or parallel drains; honor `JdkSelector` / `ToolchainContext`. |
| **Test required** | Child flooding stderr+stdout completes; workspace pin selects launcher. |

### P2-013 — `GitService.add` allows leading-dash paths (flag injection)

| Field | Value |
|-------|-------|
| **File** | `modules/desk-java/src/main/java/desk/java/git/GitService.java:69-82` (contrast `diff` at 54-58) |
| **Severity** | **medium** |
| **Problem** | `diff` rejects args starting with `-`; `add` does not → `desk git add --all` / crafted args can become git flags. ProcessBuilder avoids shell injection, but flag injection remains. |
| **Repro** | `desk git add --help` or `--output=/tmp/x` style args depending on git version. |
| **Proposed fix** | Reject `-` prefixes or insert `--` before path args. |
| **Test required** | `add(new String[]{"-n"})` rejected or treated as path after `--`. |

### P2-014 — Bare `desk` now mutates cwd (creates `.desk/`)

| Field | Value |
|-------|-------|
| **File** | `modules/desk-cli/src/main/java/desk/cli/Main.java:50-56` |
| **Severity** | **medium** |
| **Problem** | No-arg `desk` previously only launched TUI; now `WorkspaceStore.open(cwd)` creates `.desk/` and updates recent/current pointers. Backward-compat / surprise side effect in arbitrary directories. |
| **Repro** | `cd /tmp && desk` (with TUI binary) → `.desk` appears; `~/.config/desk/current-workspace.json` updated. |
| **Proposed fix** | Open workspace lazily on explicit `desk open` / `desk workspace open`, or gate behind flag; document the change. |
| **Test required** | Main no-arg does not create `.desk` unless intended; regression for `desk edit`/`compile`/`run`/`fmt`/`check`. |

### P2-015 — `desk test --watch` accepted but no-op beyond one run

| Field | Value |
|-------|-------|
| **File** | `modules/desk-cli/src/main/java/desk/cli/TestCommand.java:48-53`; `BuildRequest` carries `watch` unused by `BuildService` |
| **Severity** | **low** |
| **Problem** | Flag presented in usage; prints deferral message. Incomplete API surface. |
| **Repro** | `desk test --watch` → single test run. |
| **Proposed fix** | Hide flag until implemented, or exit non-zero with “unsupported”. |
| **Test required** | CLI asserts message / exit policy. |

### P2-016 — Windows path filters in `findJavaSources` use Unix separators

| Field | Value |
|-------|-------|
| **File** | `modules/desk-java/src/main/java/desk/java/build/BuildService.java:220-222` |
| **Severity** | **medium** |
| **Problem** | Filters `"/desk-out/"`, `"/target/"`, `"/.desk/"` — on Windows `Path.toString()` uses `\`, so exclusions may fail → compiling generated/output sources. |
| **Repro** | Windows workspace with `desk-out\Foo.java` under scan root. |
| **Proposed fix** | Compare path segments / use `Path` relativize, not substring with `/`. |
| **Test required** | Path with `desk-out` segment excluded independent of separator. |

### P2-017 — Missing tests for new public APIs

| Field | Value |
|-------|-------|
| **File** | Multiple (see gaps) |
| **Severity** | **high** |
| **Problem** | New public surface lacks meaningful tests: |
| | • `DocumentStore` save/untitled/saveAs/pathIndex — only identity open tested |
| | • `WorkspaceStore` concurrent/atomic/corrupt JSON/untitled — thin open/recent only |
| | • `JdkProvisioner.install` consent / vendor URLs — only CLI dry-run |
| | • `LanguageClient` / `LanguageServerHost` — **no tests** |
| | • `GitService` beyond `sanitize` — no process/repo tests |
| | • `ToolchainContext` — **no tests** |
| | • `BuildService` timeout/cancel — only parse + happy javac compile |
| | • `ProjectTemplates` path traversal — only happy maven create |
| **Repro** | Inspect `modules/*/src/test/java` coverage vs public methods. |
| **Proposed fix** | Add focused unit tests listed per issue above before calling Phase 2 “hardened”. |
| **Test required** | (meta) CI gate on the new tests. |

### P2-018 — `ExternalProcessLanguageClient` is lifecycle-only stub

| Field | Value |
|-------|-------|
| **File** | `modules/desk-java/src/main/java/desk/java/lsp/ExternalProcessLanguageClient.java:11-45`, `66-71` |
| **Severity** | **medium** |
| **Problem** | Starts process with `[serverCommand, workspaceRoot]`, discards stdout, no JSON-RPC; `diagnose` spins a **new** `JavacLanguageClient` each call (temp leaks compound). Documented as placeholder but selected whenever `DESK_JAVA_LS` is set — looks “more real” while ignoring the external server for intelligence. |
| **Repro** | `DESK_JAVA_LS=/usr/bin/true` → process started; completions still empty; diagnose uses javac temp dirs. |
| **Proposed fix** | Don’t select external client until protocol exists; or require explicit “diagnostics fallback” mode. |
| **Test required** | Host selection + no zombie processes on shutdown. |

### P2-019 — `DefaultDocument.setPath` desyncs `DocumentStore` pathIndex

| Field | Value |
|-------|-------|
| **File** | `modules/desk-core/src/main/java/desk/core/document/DefaultDocument.java:60-62`; `DocumentStore.java:22-39` |
| **Severity** | **medium** |
| **Problem** | Path is mutable on document; store index not updated → duplicate opens / wrong `getByPath` after rename/save-as. |
| **Repro** | openFile → cast → setPath(newPath) → openFile(newPath) creates second document. |
| **Proposed fix** | Remove public `setPath` or route renames through `DocumentStore.attachPath(id, path)`. |
| **Test required** | Rename updates index; old path absent. |

### P2-020 — Backward-compat: new top-level commands + path heuristic

| Field | Value |
|-------|-------|
| **File** | `modules/desk-cli/src/main/java/desk/cli/Main.java:68-71`, `107-121` |
| **Severity** | **low** |
| **Problem** | Existing commands (`edit`, `fmt`, `check`, `compile`, `run`, `version`, `help`) remain registered. Risk: `looksLikePath` + `Files.exists` can treat a non-command token that happens to be an existing file/dir as `open` (usually desirable). No-arg side effect is the main compat break (P2-014). `desk test` is new and fine. |
| **Repro** | `desk compile X.java` still works; `desk .` now opens workspace (new). |
| **Proposed fix** | Keep registry precedence (already `!registry.has`); document `desk .` / path sugar; soft-launch no-arg workspace open. |
| **Test required** | `MainTest` matrix: legacy commands + path sugar + unknown command. |

---

## Severity summary

| Severity | Count | IDs |
|----------|-------|-----|
| critical | 1 | P2-001 |
| high | 8 | P2-002, P2-003, P2-004, P2-005, P2-006, P2-007, P2-008, P2-017 |
| medium | 8 | P2-009, P2-010, P2-011, P2-012, P2-013, P2-016, P2-018, P2-019 |
| low | 2 | P2-015, P2-020 |

## Suggested hardening order

1. **P2-001 / P2-012** — toolchain truth for compile/run/check  
2. **P2-006** — build process deadline + cancel  
3. **P2-002 / P2-003** — honest install API  
4. **P2-007 / P2-008** — path escape + untitled data loss  
5. **P2-004 / P2-005 / P2-018** — stop shipping empty LSP as product surface; wire or quarantine  
6. **P2-009 / P2-010 / P2-017** — store durability + tests  

## Out of scope notes

- TUI multi-tab Rust changes were not line-audited here beyond confirming no Java `DocumentStore`/LSP wiring.  
- Docs already acknowledge some gaps in `docs/REMAINING_LIMITATIONS.md`; several inventory “DONE” rows overstate readiness relative to this audit.
