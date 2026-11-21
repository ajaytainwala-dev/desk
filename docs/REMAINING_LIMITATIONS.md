# Remaining Limitations

Honest inventory after Phase 5 (stabilization & validation).

1. **Live JDT LS in CI** — Mock JSON-RPC IT passes; live test is gated (`DESK_JDTLS_IT=1` + `DESK_JAVA_LS`). No pinned jdtls archive in required CI yet.
2. **DAP debuggee control** — Transport + `initialize` work; launch/breakpoints/step/stack/variables still throw until a real adapter E2E exists. No fake TUI debug UI.
3. **PTY terminal** — Job/shell runner only; see `docs/terminal/PTY_DECISION.md` (defer library adoption).
4. **BuildHandle ↔ TUI protocol** — TUI cancels via process-group kill of `desk build` / jobs; dedicated streaming BuildHandle IPC not yet.
5. **Spring Boot resolve/run E2E** — Fixture POM + detection only.
6. **`desk test --watch`** — Flag accepted; continuous watch not implemented.
7. **Split editors** — Deferred.
8. **Managed JDK vendors** — Temurin download path; others plan-only.
9. **Large-file / LSP latency SLAs** — Micro-benches recorded; 5–10 MiB and jdtls latency not gated.
10. **Error Prone** — Hints only (compiler plugin).
11. **Windows process-tree kill** — Unix process groups handled; Windows best-effort via child.kill().
