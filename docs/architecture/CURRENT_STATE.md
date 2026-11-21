# Desk — Current Architecture State

**Audit date:** 2026-11-21  
**Repository:** `ajaytainwala-dev/desk`  
**Version under audit:** `0.1.0-SNAPSHOT` (released as `v0.1.0`)  
**Method:** Source inspection of Maven modules, Rust TUI, packaging, CI, and website. Documentation claims were verified against code and often found stale.

**Phase 4 update:** TUI LSP now covers rename/format/codeAction/applyEdit; Temurin managed install is real; fixtures under `testdata/fixtures/`; DAP remains design+stub. Prefer `docs/PHASE_4_RESULTS.md` and `docs/audit/PHASE_4_AUDIT.md` for the latest verified matrix (sections below may still describe pre-Phase-4 baselines).

---

## 1. Product shape

Desk is a **hybrid workbench**:

| Layer | Technology | Role |
|-------|------------|------|
| CLI + Java tooling | Maven multi-module, Java | Entry point, format, compile, run, check |
| Interactive editor | Rust (`desk-tui`), Ratatui + Crossterm | Default UX when `desk` has no args |
| Marketing / downloads | Vite + React (`website/`) | Install funnel for GitHub Releases |

There is **no JNI/FFI**. Java and Rust talk only via process spawn (`ProcessBuilder` / `Command`).

---

## 2. Maven module map

Parent: `com.desk:desk:0.1.0-SNAPSHOT` — baseline **`java.version=23`** (pre-modernization).

```
desk-core
  ├── desk-terminal (+ Lanterna)
  │     └── desk-editor (+ desk-lexer, desk-syntax)
  ├── desk-formatter (+ desk-lexer)
  ├── desk-java
  └── desk-cli → desk-core, desk-editor, desk-terminal, desk-formatter, desk-java
                 (shaded uber-JAR, mainClass desk.cli.Main)

desk-lexer → desk-syntax
desk-tui   (Cargo crate; not a Maven module)
```

**No circular Maven dependencies.**

| Module | Purpose | Live product path? |
|--------|---------|--------------------|
| `desk-core` | Config, FS, logging, `Document` | Partially (FS via formatter; config unused by CLI) |
| `desk-cli` | Commands + TUI launch | **Yes** |
| `desk-lexer` | Java tokenizer | **Yes** (`fmt`) |
| `desk-syntax` | Themes / highlighter | Available; TUI uses its own highlighter |
| `desk-formatter` | Token-stream formatter | **Yes** |
| `desk-java` | JDK discovery, compile, run | **Yes** (with caveats) |
| `desk-terminal` | Lanterna terminal abstraction | **Orphaned** |
| `desk-editor` | Gap-buffer editor + renderer | **Orphaned** |
| `desk-tui` | Ratatui workbench | **Yes** (default UX) |

---

## 3. CLI surface (as implemented)

| Command | Behavior |
|---------|----------|
| `(no args)` | Launch Rust TUI via `TuiLauncher` |
| `version` | Print version (`0.1.0` constant; POM is SNAPSHOT) |
| `help` | List commands |
| `edit <file>` | Create if missing; launch TUI |
| `fmt [--check] <file>` / `--stdin` | Format via `desk-formatter` |
| `check [<file>\|--stdin]` | Compile diagnostics in temp dir |
| `compile <file>` | Compile to sibling `desk-out/` |
| `run <class-or-file>` | Run via discovered JDK `java` |

**Missing relative to workbench goals:** `java` toolchain mgmt, `workspace`, `open`, `new`, `build`, `test`, Git, command palette (CLI), layered settings CLI.

Global `--help` / `--version` are advertised in error text but not registered as flags.

---

## 4. Rust TUI capabilities

**Strengths:** Single-file editing, vim-ish modes, menus, explorer tree (lazy expand), bottom job/shell panel, find/replace, autosave, themes, Java compile/run/fmt/check via process spawn, mouse support.

**Gaps:**

| Area | Status |
|------|--------|
| Multi-document tabs | UI shows one tab; no multi-buffer model |
| Shared document model with Java | Duplicated (`Vec<String>` vs GapBuffer/`Document`) |
| Workspace / project model | CWD + explorer root only |
| LSP / semantic Java | Absent (keyword highlighter only) |
| Git UI | Shell-only |
| Command palette | Vim `:` only |
| Real PTY terminal | Line-buffered job runner, not emulator |
| System clipboard | In-process string only |
| Explorer CRUD | No create/rename/delete/watch |
| Viewport highlighting | Full-file highlight every frame |

**Performance risks:** Full-buffer rehighlight per frame; `format`/`check` block UI via `wait_with_output`; undo stores full buffer snapshots (cap 200).

---

## 5. Java tooling reality

### JDK discovery (`JdkDiscovery`)

- Checks `JAVA_HOME` + a few Windows/Unix directories.
- Validates `bin/javac` only.
- **No** version/vendor/arch metadata, PATH scan, SDKMAN, macOS JVMs folder depth, registry, or workspace-scoped selection.
- **No** CLI (`desk java …`).

### Compile / run

- Compiles with **`ToolProvider.getSystemJavaCompiler()`** (current JVM), not the discovered JDK toolchain.
- Single-file, empty classpath by default.
- `run` does not resolve packages properly from source layout.

### Formatter

- Usable for common Java.
- Config knobs (`maxLineLength`, brace spacing, blank lines) partly unused.
- No project-level formatter profile; no JDT backend.

---

## 6. Configuration

Java: layered design exists (`Defaults` → user `~/.config/desk/config.json` → project `desk.json` → env) but **`ConfigSources.load()` is never called** from CLI.

Rust: separate `~/.config/desk/editor.toml`.

Env prefix inconsistency: some code/docs mention `DEVKIT_`; product uses `DESK_*` (`DESK_JAR`, `DESK_TUI`).

---

## 7. Tests

| Area | Coverage |
|------|----------|
| Lexer | Strongest |
| Formatter / GapBuffer / Editor model | Moderate unit |
| Config JSON | Light |
| JDK discovery | Smoke only |
| CLI commands | Registry/version helpers only |
| Compile/run integration | Missing |
| Terminal (Java) | None |
| Rust TUI | ~19 unit tests; no JAR/javac integration |
| E2E / benchmarks | Missing |

---

## 8. CI / distribution

**Working:** Multi-OS Java CI (Temurin 23), Rust fmt/clippy/test, jlink portable, release workflow, Windows NSIS, TUI binaries, Pages site, release-link sync.

**Gaps / risks:**

- macOS portable zip not built; setup script can fall back to **Linux** portable.
- GraalVM natives advertised but often missing from release assets.
- Website not linted/built on PRs.
- Dependency “scan” is a placeholder echo.
- Actions not SHA-pinned (docs claim they are).
- ARM64 not in CI matrix.
- Architecture docs under `docs/architecture/` largely **stale**.

---

## 9. Technical debt summary

1. **Dual editor stacks** — Rust ships; Java Lanterna path still shaded into CLI.
2. **Dual highlighters & configs** — Rust keywords vs Java lexer/themes; two config homes.
3. **Discovery vs compile mismatch** — discovered JDK unused for `javax.tools` compile.
4. **Version drift** — POM SNAPSHOT vs `AppVersion` vs packaging JAR name hardcoding.
5. **Monolithic `app.rs`** (~2.3k LOC) — hard to extend safely.
6. **Orphaned document model** — `desk-core` `Document` unused by product path.
7. **Docs drift** — `TECHNICAL-DECISIONS.md` still lists TUI/lexer/formatter as unfinished.
8. **Security** — process spawn uses arg arrays (good); install scripts lack checksum verification; no workspace trust model for tasks.

---

## 10. Security snapshot

| Topic | Assessment |
|-------|------------|
| Shell injection via string `exec` | Not observed; `ProcessBuilder` / arg lists used |
| Untrusted workspace scripts | No trust gate |
| Installer integrity | HTTPS fetch; weak/no checksum enforce in setup scripts |
| Credential logging | No Git integration yet; low current risk |
| Path traversal | Local-dev tool; no jail — acceptable for CLI, needs care for future remote/plugin paths |

---

## 11. What “works today” for a developer

1. Install via setup script / portable zip (Linux/Windows better than macOS).
2. Run `desk` → edit a file in the TUI.
3. Format / check / compile / run a **single** Java file via CLI or TUI menus.
4. Use bottom shell panel for ad-hoc `git` / `mvn` / `gradle`.

What does **not** work as a cohesive workbench yet: multi-file sessions, project-aware JDK selection, Maven/Gradle orchestration, semantic diagnostics, Git UI, restoreable workspaces, or extensible language services.
