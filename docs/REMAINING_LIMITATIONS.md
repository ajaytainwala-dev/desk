# Remaining Limitations

After Phase 6 vertical slice (live JDT LS CI + JDI debugging).

1. **TUI debug panel** — Real debugger is CLI/`JdiDebugger`; Ratatui debug UI deferred (F5 still Run).
2. **External DAP adapters** — `DESK_JAVA_DAP` initialize only; launch not E2E.
3. **Live jdtls coverage** — CI runs initialize/completion/hover/symbols/format; rename/codeAction/diagnostics-clear still thin.
4. **Spring Boot HTTP E2E** — Detection fixture only; resolve/run/debug next.
5. **PTY** — Job runner only (`docs/terminal/PTY_DECISION.md`).
6. **`desk test --watch`** — Explicitly rejected until implemented.
7. **Windows process-tree** — `taskkill /T` helper added; dedicated IT pending.
8. **BuildHandle streaming IPC** — TUI still primarily job-runner based.
9. **Large-file / LSP latency SLAs** — Micro-benches only.
10. **Non-Temurin managed JDKs** — Plan-only.
