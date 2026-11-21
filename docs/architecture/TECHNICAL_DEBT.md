# Technical Debt Report

| ID | Debt | Severity | Mitigation |
|----|------|----------|------------|
| TD-1 | Dual editor stacks (Rust TUI vs Lanterna `desk-editor`) | Medium | Stop expanding Lanterna path; consider eventual removal |
| TD-2 | Dual highlighters (Rust keywords vs Java lexer) | Medium | Prefer LSP/semantic tokens long-term |
| TD-3 | Dual config systems (JSON + TOML) | Medium | Unify under layered Desk settings |
| TD-4 | `App` god-object in Rust (~2.3k LOC) | Medium | Split jobs/explorer/terminal modules |
| TD-5 | Full-buffer highlight every frame | High (perf) | Viewport-based highlighting |
| TD-6 | `ToolProvider` compile uses Desk JVM, not always project JDK | Medium | Prefer external `javac` from pinned JDK |
| TD-7 | Version string drift (POM SNAPSHOT vs AppVersion) | Low | Single source of truth |
| TD-8 | Hand-rolled JSON helpers for pins/sessions | Low | Reuse `JsonConfigLoader` more widely |
| TD-9 | macOS portable release asset historically missing | High (UX) | Add macOS jlink job; setup script fixed to avoid Linux fallback |
| TD-10 | LSP JSON-RPC not fully implemented | Medium | Next phase after host lifecycle |
| TD-11 | No project-wide find-in-files in TUI | Medium | Background search worker |
| TD-12 | Website not gated on PR CI | Low | Add path-filtered website job |

See also `CURRENT_STATE.md` and `IMPLEMENTATION_ROADMAP.md`.
