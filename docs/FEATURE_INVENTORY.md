# Implemented Feature Inventory

Updated: 2026-11-21 (Phase 4)

## Completed in this initiative

| Feature | What changed | Why | Modules | Tests |
|---------|--------------|-----|---------|-------|
| Architecture audit | Current/proposed architecture + roadmap | Ground truth before coding | `docs/architecture/*`, `docs/roadmap/*` | N/A |
| Java 25 LTS baseline | `pom.xml` + CI → 25; version policy API | Modern LTS primary; 26/27 not called LTS | parent POM, workflows, `JavaVersionPolicy` | `JdkVersionTest` |
| JDK discovery | Multi-source detect with metadata | Reliable toolchain inventory | `desk-java` `JdkDiscovery` | `JdkDiscoveryTest` |
| Workspace JDK pin | `.desk/jdk.json`, user pin | Per-project JDK without touching `JAVA_HOME` | `JdkSelector` | `JdkSelectorTest` |
| `desk java *` CLI | list/detect/current/use/info/doctor/install/remove/policy | User-facing toolchain control | `JavaCommand` | `JavaCommandTest` |
| Managed Temurin install | Consent + checksum + safe extract | Optional managed JDKs | `JdkProvisioner` | CLI + provisioner paths |
| Workspace store | open/list/close/session/recent | Cohesive project context | `desk-core` workspace | `WorkspaceStoreTest` |
| DocumentStore | Single identity per path; saveAs/saveAll | Avoid divergent copies / silent loss | `desk-core` document | `DocumentStoreTest` |
| Project detection | Maven/Gradle/plain/multi-module | Don't force Desk layouts | `ProjectDetector` | `ProjectDetectorTest`, `ProjectFixtureTest` |
| Real-world fixtures | Maven/Gradle/Spring/multi | Compatibility matrix | `testdata/fixtures` | `ProjectFixtureTest` |
| Project templates | `desk new` with Java 25 default | Fast project bootstrap | `ProjectTemplates` | `ProjectDetectorTest` |
| Build orchestration | `desk build` / `desk test` + BuildHandle | Orchestrate mvn/gradle/javac | `BuildService` | `BuildServiceTest`, `BuildHandleTest` |
| TUI build + Esc cancel | Live output panel; Esc/F7 stop | Non-blocking builds | `desk-tui` | cargo app/input |
| Diagnostics mapping | Parse javac/maven errors | Jump-friendly problem list | `BuildDiagnostic` | `BuildServiceTest` |
| Git facade | `desk git …` with credential redaction | Useful SCM without reimplementing Git | `GitService` | `GitServiceTest` |
| LSP Java + TUI client | Content-Length; complete/nav/hover/rename/format/codeAction | Real editor intelligence | `desk.java.lsp.*`, `desk-tui/lsp.rs` | transport + cargo |
| WorkspaceEdit apply | Multi-file bottom-up + confirm | Safe refactor | `workspace_edit.rs` | cargo |
| Quality runners | Checkstyle/PMD/SpotBugs/ErrorProne hints | Optional lint | `desk.java.quality` | graceful skip |
| Productivity overlays | Fuzzy files, palette, find-in-files | Faster navigation | `overlay.rs` | cargo |
| Docs | CLI/workspace/Java/LSP/JDK/Phase results | Accurate operator docs | `docs/**` | N/A |

## Partial

| Feature | Status | Limitation |
|---------|--------|------------|
| Managed JDK (non-Temurin) | Plan/consent | Download implemented for Temurin primarily |
| `desk test --watch` | Flag accepted | Continuous watch deferred |
| External JDT LS live E2E | Client ready | CI may lack jdtls |
| DAP debugging | Stub + design | Throws until adapter wired |
| Terminal PTY | Job runner remains | Not a full emulator |
| Split editor views | Deferred | After further hardening |
| Numeric perf SLAs | Micro-benches | Not published as gate SLAs |
| disk_mtime proactive reload | Save conflict works | On-focus ReloadConflict overlay partial |

## Deferred (intentional)

- Public plugin marketplace
- Full DAP end-to-end
- JavaFX template
- ARM64 CI matrix
- Notarized macOS DMG
- Full PTY emulator

## Preserved existing behavior

- `fmt`, `check`, `compile`, `run`, `edit`, `version`, `help`
- Rust TUI editor, explorer, terminal panel, themes
- Release/website pipelines (Java version bumped only)
