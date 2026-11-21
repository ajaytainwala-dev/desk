# Phase 6 Audit — Baseline After Phase 5

**Branch base:** `cursor/desk-workbench-foundation-22b9` @ `091f7f466f26313485082bd334c22a121824f728`  
**Phase 6 branch:** `cursor/desk-phase-6-java-debug-22b9`  
**Date:** 2026-11-21 (Asia/Calcutta)  
**Method:** Code + Phase 5 results; docs as claims only.

## Phase 5 CI baseline

| Run | Conclusion |
|-----|------------|
| [44928726788](https://github.com/ajaytainwala-dev/desk/actions/runs/44928726788) (`1eee8bf`) | **18/18 success** |
| Runs on `091f7f4` | In progress at audit time — do not claim green until concluded |

## Capability matrix (start of Phase 6)

| # | Capability | Classification | Notes |
|---|------------|----------------|-------|
| 1 | Mock LSP JSON-RPC IT | **Implemented and tested** | `MockLanguageServerIntegrationTest` |
| 2 | Live JDT LS | **Implemented but unverified against real external process in required CI** | Gated `LiveJdtLsIntegrationTest`; no provisioned job |
| 3 | TUI LSP client | **Implemented but unverified against real jdtls** | rename/format/codeAction client present |
| 4 | DAP transport + initialize | **Implemented and tested** (mock) | `DapTransportTest` |
| 5 | DAP launch / breakpoints / vars | **Not implemented** | UOE stubs |
| 6 | TUI debug UI | **Not implemented** | F5=run, F9=format — conflicts to resolve |
| 7 | Spring Boot run/HTTP | **Not implemented** | Detection fixture only |
| 8 | Windows process-tree kill | **Partially implemented** | Unix groups; Windows best-effort |
| 9 | BuildHandle vs TUI jobs | **Partially implemented** | Both exist; documented duality |
| 10 | `desk test --watch` | **Partially implemented** | Flag no-op |
| 11 | PTY | **Not implemented** | Decision: defer |
| 12 | JDK zip/tar safety | **Implemented and tested** | ZipSafety + tar entry reject |

## Phase 6 vertical slice (priority)

1. **P6-001** — Pin + provision JDT LS; required Ubuntu CI job; expand live IT (must not skip-as-pass).
2. **P6-002** — Real Java debug via JDK **JDI** (official `jdk.jdi`), DAP-shaped session API, E2E fixture; optional external `DESK_JAVA_DAP` remains.
3. TUI debug panel with non-conflicting keys (Shift+F5 debug continue; F9 stays format — use Ctrl+B breakpoint).
4. Then Spring / Windows process / test-watch as capacity allows.

## Honesty

- Mock LSP ≠ live jdtls.
- DAP initialize ≠ usable debugger.
- JDI-backed debugging is real JVM debugging; document that it is not the VS Code Java Debug Server binary unless that adapter is separately wired.
