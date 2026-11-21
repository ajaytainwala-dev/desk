# PTY Investigation Decision (Phase 5)

## Current state

Desk’s TUI terminal panel is a **job/shell runner**:

- Spawns processes with piped stdout/stderr
- Line-oriented streaming into the output panel
- Esc/F7 cancellation (Unix process-group kill for build trees)
- Not a full interactive PTY (no authentic TTY, no curses apps, limited resize semantics)

This is intentional and separate from build execution (`BuildHandle` / `desk build`).

## Requirements for a real PTY

Interactive shell, resize, I/O streaming, ANSI handling, multi-session, clean teardown on Linux/macOS/Windows.

## Library options (Rust)

| Option | Pros | Cons |
|--------|------|------|
| `portable-pty` (wezterm) | Cross-platform; mature | Extra dependency weight; Windows ConPTY nuances |
| `ptyprocess` / `nix` + manual | Fine-grained Unix control | Weak/no Windows story |
| Keep job runner | Stable; already tested; enough for builds | Not a full terminal emulator |

## Decision (Phase 5)

**Do not adopt a PTY library in Phase 5.**

Rationale:

1. Editor stability and LSP/build/debug foundations take priority.
2. Job runner already covers build/test/run cancellation paths.
3. A PTY subsystem needs dedicated resize, scrollback, ANSI, and Windows ConPTY tests before UI exposure.

## Phase 6 plan

1. Prototype `portable-pty` behind a feature flag in an isolated module.
2. Single-session interactive shell only; keep job runner for builds.
3. Cross-platform CI smoke (bash/zsh on Unix; `cmd`/`pwsh` on Windows).
4. Promote only after no regressions in editor event loop latency.
