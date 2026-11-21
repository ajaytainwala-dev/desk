# Remaining Limitations

1. **In-TUI completion UI** — LSP JSON-RPC client works; expose completions via `desk lsp` today. TUI popup/gutter wiring is next.
2. **Managed JDK download** — `desk java install` plans and requires consent but does not yet download/extract archives (`NOT_IMPLEMENTED` status).
3. **`desk test --watch`** — Flag accepted; continuous watch not implemented.
4. **Terminal** — Integrated panel is a job/shell runner, not a full PTY emulator.
5. **Split editors / fuzzy command palette** — Deferred.
6. **Explorer CRUD** — Browse/open works; create/rename/delete UI actions are incomplete.
7. **JDT LS in CI** — Optional; transport tested without requiring jdtls on runners.
8. **Quality tool integrations** — Checkstyle/PMD/SpotBugs/Error Prone not wired yet.
9. **Benchmarks** — Baseline harness not yet published; do not claim unverified performance wins.
10. **Windows portable jlink** — Release matrix still focuses Linux/macOS portable + Windows installer/TUI.
