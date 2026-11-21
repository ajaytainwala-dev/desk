# Implemented Feature Inventory

Updated: 2026-11-21

## Completed in this initiative

| Feature | What changed | Why | Modules | Tests |
|---------|--------------|-----|---------|-------|
| Architecture audit | Current/proposed architecture + roadmap | Ground truth before coding | `docs/architecture/*`, `docs/roadmap/*` | N/A |
| Java 25 LTS baseline | `pom.xml` + CI → 25; version policy API | Modern LTS primary; 26/27 not called LTS | parent POM, workflows, `JavaVersionPolicy` | `JdkVersionTest` |
| JDK discovery | Multi-source detect with metadata | Reliable toolchain inventory | `desk-java` `JdkDiscovery` | `JdkDiscoveryTest` |
| Workspace JDK pin | `.desk/jdk.json`, user pin | Per-project JDK without touching `JAVA_HOME` | `JdkSelector` | `JdkSelectorTest` |
| `desk java *` CLI | list/detect/current/use/info/doctor/install/remove/policy | User-facing toolchain control | `JavaCommand` | `JavaCommandTest` |
| Consent JDK provisioning | Plan + license note; no silent download | Safe optional managed JDKs | `JdkProvisioner` | dry-run via CLI test |
| Workspace store | open/list/close/session/recent | Cohesive project context | `desk-core` workspace | `WorkspaceStoreTest` |
| DocumentStore | Single identity per path | Avoid divergent document copies | `desk-core` document | `WorkspaceStoreTest` |
| Project detection | Maven/Gradle/plain/multi-module | Don't force Desk layouts | `ProjectDetector` | `ProjectDetectorTest` |
| Project templates | `desk new` with Java 25 default | Fast project bootstrap | `ProjectTemplates` | `ProjectDetectorTest` |
| Build orchestration | `desk build` / `desk test` | Orchestrate mvn/gradle/javac | `BuildService` | `BuildServiceTest` |
| Diagnostics mapping | Parse javac/maven errors | Jump-friendly problem list | `BuildDiagnostic` | `BuildServiceTest` |
| Git facade | `desk git …` with credential redaction | Useful SCM without reimplementing Git | `GitService` | `GitServiceTest` |
| LSP architecture | Client host + javac fallback + external adapter | Extensible intelligence without fake LS | `desk.java.lsp.*` | covered via compile path |
| TUI multi-tabs | Open multiple files, switch/close/save-all | Real multi-document editing | `desk-tui` | existing + open/reload tests |
| Docs | CLI/workspace/Java matrix | Accurate operator docs | `docs/**` | N/A |

## Partial

| Feature | Status | Limitation |
|---------|--------|------------|
| Managed JDK download | Plan/consent only | Does not auto-download archives yet (explicit) |
| `desk test --watch` | Flag accepted | Continuous watch deferred |
| External JDT LS | Process host + env `DESK_JAVA_LS` | Full JSON-RPC LSP framing not wired |
| Completion / navigation / refactor | API present | Empty until LS protocol complete |
| Terminal PTY | Job runner remains | Not a full emulator |
| Split editor views | Deferred | After multi-tab hardening |
| Command palette (fuzzy) | Deferred | Vim `:` and menus remain |
| macOS portable zip | Known pre-existing gap | Documented; fix tracked |
| Viewport-only highlighting | Not yet | Still full-buffer highlight per frame |

## Deferred (intentional)

- Public plugin marketplace
- Debugger Adapter Protocol
- JavaFX template
- ARM64 CI matrix
- Notarized macOS DMG
- Checkstyle/PMD/SpotBugs integrations (optional quality tools)

## Preserved existing behavior

- `fmt`, `check`, `compile`, `run`, `edit`, `version`, `help`
- Rust TUI editor, explorer, terminal panel, themes
- Release/website pipelines (Java version bumped only)
