# Remaining Limitations

1. **Language intelligence** — Completion, go-to-definition, and refactor require a full JDT LS JSON-RPC client; javac diagnostics work today as a fallback.
2. **Managed JDK download** — `desk java install` plans and requires consent but does not yet download/extract archives automatically.
3. **`desk test --watch`** — Flag accepted; continuous watch not implemented.
4. **Terminal** — Integrated panel is a job/shell runner, not a full PTY emulator.
5. **Split editors / fuzzy command palette** — Deferred after multi-tab hardening.
6. **Explorer CRUD** — Browse/open works; create/rename/delete UI actions are incomplete.
7. **Session restore in TUI** — Session JSON is written from CLI; TUI does not yet fully restore tabs on launch.
8. **Distribution** — macOS portable zip may still be absent from releases; install script no longer falls back to Linux zip.
9. **Quality tool integrations** — Checkstyle/PMD/SpotBugs/Error Prone not wired yet.
10. **Benchmarks** — Baseline harness not yet published; do not claim unverified performance wins.
