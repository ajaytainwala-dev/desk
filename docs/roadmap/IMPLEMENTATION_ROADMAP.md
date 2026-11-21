# Desk — Implementation Roadmap

**Initiative:** Complete Java Development Workbench & Workspace Engineering  
**Strategy:** Incremental stages with tested vertical slices. Features are “done” only when implemented **and** tested.

---

## Status legend

| Status | Meaning |
|--------|---------|
| DONE | Implemented and tested in this initiative |
| PARTIAL | Foundation landed; gaps remain |
| NEXT | Immediate follow-up |
| DEFERRED | Intentionally postponed with rationale |
| BLOCKED | Waiting on external/product decision |

---

## Stage A — Audit and planning

| Item | Status |
|------|--------|
| Full repository audit | DONE |
| `docs/architecture/CURRENT_STATE.md` | DONE |
| `docs/architecture/PROPOSED_ARCHITECTURE.md` | DONE |
| `docs/roadmap/IMPLEMENTATION_ROADMAP.md` | DONE |

---

## Stage B — Java modernization

| Item | Status | Notes |
|------|--------|-------|
| Java 25 LTS primary baseline | DONE | `pom.xml` + CI on 25; policy API |
| Multi-source JDK discovery | DONE | PATH, SDKMAN, macOS JVMs, vendors, metadata |
| Runtime vs compiler vs project JDK separation | DONE | `ToolchainContext` + workspace pin |
| CLI: `desk java list/detect/current/use/info/doctor` | DONE | + install/remove/policy |
| Optional JDK provisioning (consent) | PARTIAL | Plan + consent gate; download not auto-executed |
| Java 26/27 compatibility smoke | PARTIAL | Policy + detect; needs host JDKs for runtime smoke |

**Dependencies:** Stage A  
**Risks:** CI image must provide JDK 25; local agents may need Temurin download.

---

## Stage C — Workspace foundation

| Item | Status |
|------|--------|
| Workspace open/list/close/new | DONE |
| `.desk/` settings + JDK pin + session | DONE |
| Recent workspaces | DONE |
| Document model wiring | DONE (`DocumentStore`) |
| Project explorer enhancements | PARTIAL (exists in TUI; CRUD still limited) |
| Persistent tabs/cursors | PARTIAL (session JSON; TUI restore next) |

**Dependencies:** Stage B (JDK pin)  
**Risks:** TUI session format changes — migrate gracefully.

---

## Stage D — Editor improvements

| Item | Status |
|------|--------|
| Multi-tab buffers | DONE |
| Split views | DEFERRED (after multi-tab stable) |
| Viewport-based highlighting | NEXT |
| Search in files | PARTIAL (in-file search exists; project-wide next) |
| Cancel obsolete background work | NEXT |
| Large-file safeguards | NEXT |

---

## Stage E — Java intelligence

| Item | Status |
|------|--------|
| LSP client architecture | DONE (interfaces + process host) |
| jdt.ls adapter (optional) | PARTIAL (`DESK_JAVA_LS` + fallback) |
| Diagnostics → Problems panel | PARTIAL (CLI diagnostics; TUI panel next) |
| Completion / navigation / code actions | DEFERRED until LS stable |
| Fallback `javac` diagnostics | DONE (`JavacLanguageClient`) |

**Risk:** jdt.ls distribution size — keep optional.

---

## Stage F — Build and execution

| Item | Status |
|------|--------|
| Project type detection | DONE |
| Project templates (`desk new`) | DONE |
| `desk build|test|run` orchestration | DONE (build/test; run via existing + build) |
| Maven / Gradle / javac backends | DONE |
| Diagnostic mapping | DONE |
| `desk test --watch` | PARTIAL (flag only) |

---

## Stage G — Terminal and Git

| Item | Status |
|------|--------|
| Multi-session terminal | PARTIAL |
| Task runner + trust | NEXT |
| Git status/diff/stage/commit facade | DONE (`desk git`) |
| PTY emulator | DEFERRED (job runner first) |

---

## Stage H — Reliability and distribution

| Item | Status |
|------|--------|
| Autosave / crash recovery | PARTIAL (TUI autosave exists) |
| Atomic config writes | NEXT |
| Security pass (process/path/download) | NEXT |
| macOS portable fix | NEXT |
| CI: website build, real dependency audit | NEXT |
| Installer checksum verification | NEXT |

---

## Stage I — Final validation

| Item | Status |
|------|--------|
| Full test suite | NEXT |
| Benchmarks + baselines | NEXT |
| Feature inventory + CLI reference | NEXT |
| Remaining debt / next roadmap | NEXT |

---

## Implementation dependencies

```
A → B → C → D
         ↘ E
    B → F → G
         ↘ H → I
```

E can proceed in parallel with D once document IDs exist.  
F needs B (JDK) and C (workspace root).

---

## Migration risks

| Risk | Impact | Mitigation |
|------|--------|------------|
| Raising compile release to 25 | Breaks agents/CI on older JDKs | Document; update CI; toolchain fallback notes |
| New CLI commands | Low | Additive; keep old commands |
| `.desk/` directory | Low | Gitignore-friendly; optional |
| TUI multi-buffer | Medium | Feature-flag session restore |
| Deprecating Lanterna editor | Low if unused | Document; stop expanding |
| Setup script Linux fallback on macOS | High UX bug | Fix in H |

---

## Prioritized next-phase (post this PR train)

1. Full jdt.ls-backed completion/navigation/refactor  
2. True PTY terminal  
3. Split editor + debugger adapter protocol exploration  
4. Internal extension host v1  
5. ARM64 CI matrix + notarized macOS DMG  

---

## Progress log

| Date | Milestone |
|------|-----------|
| 2026-11-21 | Audit complete; architecture docs created; implementation begun |
| 2026-11-21 | Stages B–G foundations landed: JDK 25, `desk java`, workspaces, build/git, multi-tabs, LSP host |
