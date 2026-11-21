# Phase 5 Audit — Verify Phase 4 Against Code

**Branch:** `cursor/desk-workbench-foundation-22b9`  
**Verified commit:** `1eee8bfa7faa63c52bd0cf5e2dafb57d01950d34`  
**Date:** 2026-11-21 (Asia/Calcutta)  
**Method:** Source inspection + CI run conclusions. Docs treated as claims.

## CI status (P5-001)

| Run | SHA | Conclusion | Notes |
|-----|-----|------------|-------|
| [44928345263](https://github.com/ajaytainwala-dev/desk/actions/runs/44928345263) | `82b3b38` | **failure** | Clippy `unnecessary_sort_by` at `workspace_edit.rs:202` on ubuntu/macOS (and windows rust job) |
| [44928726788](https://github.com/ajaytainwala-dev/desk/actions/runs/44928726788) | `1eee8bf` | **success** | Clippy fix; all 18 checks green including Windows Rust |
| [44928731119](https://github.com/ajaytainwala-dev/desk/actions/runs/44928731119) | `1eee8bf` | **success** | PR CI re-run |

`sort_by_key` fix is present at `modules/desk-tui/src/workspace_edit.rs:83` and test helper `:202`.

---

## Capability matrix

| # | Capability | Classification | Evidence |
|---|------------|----------------|----------|
| 1 | DocumentStore saveAs/saveAll | **Implemented and tested** | `DocumentStore.java`; `DocumentStoreTest` |
| 2 | Dirty close/quit confirm | **Implemented and tested** | `app.rs` ConfirmAction; `dirty_close_requests_confirmation` |
| 3 | Save overwrite when disk changed | **Implemented and tested** | `SaveOverwriteConflict`; `disk_mtime_conflict_detected` |
| 4 | Proactive ReloadConflict on focus/tick | **Partially implemented** | Handler exists `app.rs:2533-2537`; **never assigned**. No tick/tab disk check |
| 5 | TUI LSP complete/hover/def/refs | **Implemented but not end-to-end tested** | `lsp.rs`; needs live jdtls |
| 6 | TUI rename/format/codeAction/applyEdit | **Implemented but not end-to-end tested** | `lsp.rs`, `workspace_edit.rs` + unit tests; no live LS IT |
| 7 | Java Content-Length transport | **Implemented and tested** | `ContentLengthTransportTest` |
| 8 | Live JDT LS CI | **Not implemented** | No workflow installs jdtls; `DESK_JAVA_LS` optional |
| 9 | BuildHandle cancel API | **Implemented and tested** | `BuildHandle.java`, `BuildHandleTest` (sleep cancel) |
| 10 | Process-tree kill | **Partially implemented** | `destroy`/`destroyForcibly` on parent only |
| 11 | TUI build live output + Esc | **Implemented and tested** (local jobs) | `spawn_job`/`stop_job`; Esc in `input.rs` |
| 12 | TUI ↔ BuildHandle unification | **Not implemented** | TUI uses Rust `Command` jobs, not Java `BuildService.start` |
| 13 | Managed Temurin JDK | **Implemented and tested** (unit/CLI) | `JdkProvisioner`; consent; network download not forced in CI |
| 14 | Other JDK vendors download | **Partially implemented** | Plan metadata; Temurin download path |
| 15 | Quality runners | **Implemented and tested** (skip-when-absent) | Checkstyle/PMD/SpotBugs/ErrorProneHints |
| 16 | DAP debugging | **Not implemented** (honest stub) | `DapSession` throws UOE; `docs/debug/DAP_DESIGN.md` |
| 17 | PTY interactive terminal | **Not implemented** | Job/shell runner only |
| 18 | Fixtures Maven/Gradle/Spring | **Implemented and tested** (detection) | `testdata/fixtures`, `ProjectFixtureTest` (7) |
| 19 | Spring Boot resolve/run E2E | **Not implemented** | POM fixture only |
| 20 | Performance benchmarks | **Partially implemented** | `bench_tests.rs` micro timings; no large-file suite |
| 21 | Windows portable packaging | **Implemented and tested** (release job) | `release.yml` windows portable zip |
| 22 | Cross-platform CI | **Implemented and tested** | `ci.yml` matrix ubuntu/windows/macos Java+Rust |

---

## Reproducible gaps (Phase 5 targets)

1. **P5-001** — Already fixed at `1eee8bf`; keep `--all-targets` clippy green.
2. **P5-002** — No live/mock JSON-RPC LS integration suite beyond framing unit tests.
3. **P5-003** — Wire `ReloadConflict` on tab focus + throttled tick; track prompted mtime; deleted-file handling.
4. **P5-004** — ProcessHandle descendants cancel; TUI path that invokes `desk build` / shared diagnostics; document duality.
5. **P5-005** — Real DAP Content-Length transport + mock adapter tests; no fake debug UI.
6. **P5-006** — Written PTY decision; no library adoption until justified.
7. **P5-007** — Expand benches with sized workloads + methodology doc.
8. **P5-008** — Harden/tests for zip slip, checksum mismatch, interrupt.

---

## Phase 4 claims accuracy

| Claim | Verdict |
|-------|---------|
| Clippy green after Phase 4 | **Was broken** on `82b3b38`; **fixed** on `1eee8bf` |
| LSP rename/format/codeAction in TUI | Accurate for client code; live E2E still unverified |
| Esc cancel | Accurate for TUI jobs; BuildHandle stream UI still missing |
| Managed JDK download | Accurate for Temurin path |
| DAP stub honesty | Accurate |
| ReloadConflict partial | Accurate |

---

## Note on parallel explore audit

An independent explore pass ([Audit Phase 4 gaps in code](bc-a8736e88-5c88-5d66-aba8-1a51f1737ba1)) described pre-implementation gaps (ReloadConflict unwired, no DAP transport, no mock LS IT). Those items were closed in Phase 5 commits on this branch; treat that explore table as historical baseline, not current status. Remaining from that pass addressed in follow-up: tar entry pre-check + bak restore on failed JDK activation.
