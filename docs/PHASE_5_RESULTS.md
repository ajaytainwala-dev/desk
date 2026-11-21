# Phase 5 Results

**Branch:** `cursor/desk-workbench-foundation-22b9`  
**Base:** `1eee8bf` (Phase 4 + clippy fix)  
**Date:** 2026-11-21 (Asia/Calcutta)

## Audit

`docs/audit/PHASE_5_AUDIT.md` — Phase 4 claims verified against code/CI.

## P5-001 CI recovery

| Run | SHA | Result |
|-----|-----|--------|
| [44928345263](https://github.com/ajaytainwala-dev/desk/actions/runs/44928345263) | `82b3b38` | Failed (clippy sort_by) |
| [44928726788](https://github.com/ajaytainwala-dev/desk/actions/runs/44928726788) | `1eee8bf` | **All 18 green** (incl. Windows Rust) |

Fix preserved descending edit order via `sort_by_key(Reverse(...))`.

## Feature matrix

| ID | Feature | Status | Tests | Platforms | Limitations |
|----|---------|--------|-------|-----------|-------------|
| P5-001 | Cross-platform CI green | **Done** | GHA 18/18 | L/W/M | — |
| P5-002 | Mock LSP JSON-RPC IT | **Done** | `MockLanguageServerIntegrationTest` | Linux agent | Live jdtls optional (`DESK_JDTLS_IT=1`) |
| P5-002 | Live JDT LS IT | **Gated** | `LiveJdtLsIntegrationTest` skipped without env | — | Needs `DESK_JAVA_LS` |
| P5-003 | ReloadConflict / DiskDeleted | **Done** | `external_change_prompts_*`, `deleted_file_*` | Linux | — |
| P5-004 | Process-tree cancel | **Done** | `BuildHandle` descendants + Unix process group | Linux | Windows tree kill best-effort |
| P5-005 | DAP transport + initialize | **Done** | `DapTransportTest` mock handshake | Linux | No debuggee/TUI debug UI |
| P5-006 | PTY decision | **Documented** | N/A | — | Keep job runner; no PTY library yet |
| P5-007 | Benchmarks | **Partial** | `bench_*` timings below | Linux debug | Not SLAs; no 5–10MB in CI |
| P5-008 | Zip-slip / JDK safety | **Hardened + tested** | `ZipSafetyTest` | Linux | Non-Temurin vendors still plan-only |

## Bench timings (Linux agent, debug `cargo test`)

| Bench | Time |
|-------|------|
| fuzzy_rank_2000 | 2.30 ms |
| apply_edits | 128 µs |
| find_in_files | 226 µs (50 hits) |
| buffer 64 KiB | 691 µs |
| buffer 256 KiB | 2.60 ms |
| buffer 1 MiB | 10.9 ms |

## Tests executed (this agent)

| Suite | Result |
|-------|--------|
| `./mvnw -B test` | **PASS** — **151** run, 0 fail, **1** skipped (live jdtls) |
| `cargo fmt --check` | **PASS** |
| `cargo clippy --all-targets -- -D warnings` | **PASS** |
| `cargo test` | **PASS** — **34** |
| `bash mvnw -B -DskipTests package` | **PASS** |

## Unverified

- Live jdtls E2E against Spring Boot (set `DESK_JDTLS_IT=1` + `DESK_JAVA_LS`)
- Real Java Debug Server debuggee session
- Interactive Windows/macOS TUI (CI packaging only)
- 5–10 MiB buffer / LSP latency SLAs

## Phase 6 roadmap

1. Provisioned jdtls CI job with pinned archive URL  
2. DAP launch + breakpoints + TUI debug panel (real adapter only)  
3. Optional `portable-pty` prototype behind a flag  
4. BuildHandle live stream protocol into TUI without CLI jar indirection  
5. Published release-profile large-file baselines  
