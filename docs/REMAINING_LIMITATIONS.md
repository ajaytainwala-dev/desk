# Remaining Limitations

Honest inventory after Phase 4 (real-world readiness increment).

1. **Live JDT LS E2E in CI** — Rename/format/code-action clients are implemented; CI may lack `jdtls`. Set `DESK_JAVA_LS` for full intelligence.
2. **DAP debugging** — Architecture stub only (`DapSession`); no working debugger until an adapter is wired (`docs/debug/DAP_DESIGN.md`).
3. **PTY terminal** — Still a job/shell runner, not a full PTY.
4. **BuildHandle streaming in TUI** — Esc cancels TUI jobs (`desk build` / compile); direct `BuildHandle` live stream UI is next.
5. **Spring Boot resolve** — Fixture POM exists; full dependency download/run not asserted offline.
6. **`desk test --watch`** — Flag accepted; continuous watch not implemented.
7. **Split editors** — Deferred.
8. **Managed JDK vendors** — Temurin download implemented with checksum when available; other vendors still plan-only.
9. **Numeric performance SLAs** — Micro-benches print timings; large-file/LSP latency numbers need local profiling.
10. **Error Prone** — Detection/hints only (compiler plugin), not a standalone runner.
11. **disk_mtime ReloadConflict overlay** — Detection on save works; proactive on-focus reload prompt still partial.
