# Build execution and cancellation

## Layers

| Layer | Role |
|-------|------|
| `BuildService` / `BuildHandle` (Java) | Maven/Gradle/javac with wrappers, workspace JDK, streamed output, cancel |
| TUI job runner (Rust) | Non-blocking spawn + live panel; Esc/F7 cancel |
| `desk build` CLI | Synchronous `BuildService.execute` for scripts/CI |

`:build` in the TUI prefers `java -jar desk-cli.jar build` when a Desk JAR is found, otherwise falls back to local `javac`.

## Cancellation

- **Java `BuildHandle.cancel()`** destroys child processes via `ProcessHandle.descendants()` then the parent.
- **TUI `stop_job`** (Esc / F7): on Unix, signals the process group (`setpgid` / `process_group(0)` at spawn + `kill(-pid, SIGTERM)`), then kills the direct child.

Partial output is retained in the output panel.

## Diagnostics

`BuildService.parseDiagnostics` maps javac/Maven error lines for navigation. TUI problems list is primarily LSP-driven; build panel shows raw output.
