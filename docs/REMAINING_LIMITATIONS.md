# Remaining Limitations

Honest inventory after Phase 3 (editor integration).

1. **Live JDT LS in CI** — TUI LSP client is implemented; runners may not have `jdtls`. Set `DESK_JAVA_LS` locally for full intelligence.
2. **LSP rename / code actions / format in TUI** — Protocol support exists in Java session; TUI UI not fully wired for workspace edits.
3. **Managed JDK download** — Still `NOT_IMPLEMENTED` after consent (plan only).
4. **`desk test --watch`** — Flag accepted; continuous watch not implemented.
5. **Terminal** — Job/shell runner, not a full PTY.
6. **Split editors** — Deferred until buffer/layout architecture is further hardened.
7. **Build Esc-cancel in TUI** — `BuildHandle` exists; TUI still primarily uses local compile jobs — wire Maven/Gradle cancel next.
8. **Quality tools** — Checkstyle optional runner present; PMD/SpotBugs/Error Prone not yet.
9. **Windows portable jlink** — Release still focuses Linux/macOS portable + Windows installer/TUI.
10. **Benchmarks** — No published numeric baselines yet; do not invent timings.
