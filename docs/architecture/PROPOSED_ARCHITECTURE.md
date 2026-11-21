# Desk — Proposed Architecture

**Status:** Target architecture for the Java Development Workbench initiative  
**Constraints:** Preserve working CLI/TUI behavior; incremental migration; no unnecessary rewrites

---

## 1. Design principles

1. **One product, two runtimes** — Java owns toolchain/project/intelligence; Rust owns interactive TUI. Process IPC, not FFI (unless a future measured need appears).
2. **Single document truth** — One buffer identity per open path; UI views observe it; no divergent copies.
3. **Never block the UI** — Indexing, compile, format, search, and LSP run off the UI thread with cancellation.
4. **Orchestrate, don’t reinvent** — Maven/Gradle/`javac`/Git CLI remain sources of truth.
5. **Project-scoped JDK** — Workspace JDK ≠ system `JAVA_HOME`; changing one project never mutates another.
6. **Consent for downloads** — Managed JDK provisioning is opt-in only.
7. **Extensible, not marketplace-first** — Versioned internal extension API before any public plugin store.

---

## 2. Logical architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        Desk Product                         │
├──────────────────────────┬──────────────────────────────────┤
│  desk-tui (Rust)         │  desk-cli + Java modules         │
│  ─────────────────       │  ───────────────────────────     │
│  • Layout / focus        │  • Command routing               │
│  • Viewport render       │  • JDK discovery & selection     │
│  • Input / command       │  • Workspace & project model     │
│    palette               │  • Document store (canonical)    │
│  • Explorer / tabs       │  • Build orchestration           │
│  • Terminal panel        │  • Formatter / quality tools     │
│  • Problems / status     │  • LSP client host (Java)        │
│  • Session persistence   │  • Git facade                    │
│                          │  • Tasks / trust                 │
└────────────┬─────────────┴───────────────┬──────────────────┘
             │  JSON-RPC / stdin protocol  │
             │  (desk serve / desk doctor) │
             └──────────────┬──────────────┘
                            │
         ┌──────────────────┼──────────────────┐
         ▼                  ▼                  ▼
   Eclipse JDT LS     Maven/Gradle/javac     git CLI
   (optional proc)    (orchestrated)         (orchestrated)
```

### Target module responsibilities

| Module / crate | Owns |
|----------------|------|
| `desk-core` | Config layers, paths, document API, trust, atomic IO |
| `desk-java` | JDK inventory, version policy, provisioning (consent), toolchain selection |
| `desk-project` *(new)* | Workspace, project detection (plain/Maven/Gradle), templates |
| `desk-build` *(new or inside desk-java)* | Build/run/test orchestration + diagnostic parse |
| `desk-lsp` *(new)* | Language client; pluggable server adapters |
| `desk-formatter` | Formatting backends (native + optional JDT) |
| `desk-git` *(new, thin)* | Git CLI facade |
| `desk-cli` | User-facing commands; thin |
| `desk-tui` | Presentation + local edit buffer synced to document events |
| `desk-editor` / `desk-terminal` | Deprecate or reduce to shared algorithms; do not grow Lanterna path |

---

## 3. Java version strategy

| Version | Role | LTS? |
|---------|------|------|
| **25** | Primary development & CI baseline | **Yes** |
| **26** | Compatibility target | No |
| **27** | Latest feature-release compatibility target | No |
| **29** | Future LTS migration target | Expected LTS (track OpenJDK) |
| 21 / 23 | Supported as *project* JDKs via discovery | 21 LTS; 23 not LTS |

**Rules:**

- Desk itself builds on **JDK 25**.
- Do not advertise 26/27 as LTS.
- Workspace may pin any discovered JDK (e.g. 21) without changing Desk’s runtime.
- Separate concepts: Desk runtime JVM, project JDK, `--source`/`--target`/`--release`, compiler vs launcher.

---

## 4. JDK discovery & selection

### Discovery sources

1. `JAVA_HOME`
2. `PATH` (`javac` / `java` realpath)
3. Windows: registry + Program Files vendors
4. macOS: `/Library/Java/JavaVirtualMachines`
5. Linux: `/usr/lib/jvm`, `/usr/java`, `/opt`
6. SDKMAN: `~/.sdkman/candidates/java`
7. Common vendors: Temurin, Corretto, Zulu, Oracle, Microsoft, Liberica, GraalVM
8. Desk-managed install root: `~/.desk/jdks/`

### Metadata per installation

`home`, `version`, `vendor`, `architecture`, `kind` (JDK vs JRE), `source`, `valid`, `errors[]`

### Selection precedence (workspace)

1. Explicit `desk java use <ver>` → `.desk/jdk.json` (or workspace settings)
2. Project build file (`maven.compiler.release`, Gradle toolchain)
3. User default (`~/.config/desk/config.json` → `java.defaultVersion`)
4. Preferred valid discovered JDK matching Desk baseline when sensible
5. Current process JVM (last resort; warn)

### CLI

```
desk java list | detect | current | use <version|path> | info | doctor
desk java install <version> [--vendor temurin]   # consent required
desk java remove <id>                            # managed only
```

---

## 5. Workspace & document model

### Workspace

A workspace is a root directory with:

- `.desk/workspace.json` — open tabs, cursors, layout, recent files
- `.desk/settings.json` — workspace overrides
- `.desk/jdk.json` — selected JDK
- Detected project descriptor (`pom.xml`, `build.gradle*`, or plain `src/`)

### Document store

```
DocumentId → { path?, text rope/gap, version, dirty, readonly, language }
View      → { documentId, cursor, selection, scroll }
```

Rust TUI may keep a local mirror for latency but must treat Java (or a shared protocol) as authority when both sides edit — for v1, **Rust owns live edit**; Java owns file IO helpers, format, and LSP buffer sync via didOpen/didChange.

---

## 6. Language intelligence

Prefer **Eclipse JDT Language Server** (or equivalent maintained LS) behind a thin client:

```
Editor ──► LanguageClient ──► JavaLanguageServerAdapter ──► jdt.ls process
                │
                ├── DiagnosticsService → Problems panel
                ├── CompletionService
                ├── NavigationService
                └── CodeActionService
```

- Client must not hard-depend on one server binary layout.
- Fallback: existing lexer + `javac` diagnostics when LS unavailable.
- Never invent a full Java semantic engine from scratch.

---

## 7. Project & build orchestration

```
ProjectDetector → { PLAIN, MAVEN, GRADLE, MULTI_MODULE_* }
BuildService    → compile | test | package | clean | run
DiagnosticMapper → editor locations
TaskRunner      → cancellable jobs with trust checks
```

Desk invokes `mvn` / `gradle` / `javac` with argument arrays; parses output into unified diagnostics.

---

## 8. Terminal, Git, tasks

| Subsystem | Approach |
|-----------|----------|
| Terminal | Evolve toward PTY where feasible; isolate failures; multi-session |
| Git | Thin CLI wrapper; never log credentials |
| Tasks | `.desk/tasks.json`; require workspace trust for custom scripts |

---

## 9. Settings layers

1. Defaults  
2. User (`~/.config/desk/config.json` + `editor.toml` migration path)  
3. Workspace (`.desk/settings.json`)  
4. Project (`desk.json` at project root)

Validate on load; atomic writes; recover from corruption via `.bak`.

---

## 10. Extension API (internal v0)

```
Extension {
  id, version, permissions[], activate(host), deactivate()
}
Host APIs: commands, diagnostics, settings, FS (scoped), tasks
```

No marketplace. Unsigned extensions only in trusted local dirs.

---

## 11. Migration plan (non-breaking)

| Step | Action |
|------|--------|
| M1 | Add JDK/workspace/build commands; keep existing `fmt/check/compile/run` |
| M2 | Introduce `.desk/` workspace files; TUI reads them |
| M3 | Multi-tab document model in TUI |
| M4 | LSP optional path |
| M5 | Deprecate Lanterna editor path (document; stop growing) |
| M6 | Unify config (`DESK_` only; migrate `DEVKIT_`) |

---

## 12. Architecture diagrams (summary)

### Runtime processes

```
[desk CLI JVM] ──spawns──► [desk-tui]
      │                        │
      ├──javac/java/mvn/gradle │──jobs──► shell / javac
      └──optional jdt.ls       │
```

### Data flow for diagnostics

```
Build/LSP → Diagnostic[] → Problems model → TUI Problems panel → jump-to-line
```

---

## 13. Risks

| Risk | Mitigation |
|------|------------|
| jdt.ls weight | Optional; lazy start; fallback diagnostics |
| Dual buffers diverge | Single-writer rule; version stamps |
| macOS portable wrong arch | Dedicated macOS jlink job; stop Linux fallback |
| JDK download supply chain | Checksums, consent, pinned vendors |
| Scope explosion | Ship vertical slices with tests; defer marketplace/debugger |
