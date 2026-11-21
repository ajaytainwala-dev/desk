# Debug Adapter Protocol — Design (Phase 4)

## Goal

Add a real Java debugging experience to Desk without fake UI.

## Architecture

```
TUI (Rust)  --stdio JSON-RPC-->  desk dap bridge (Java)
                                      |
                                      v
                              Java Debug Server / JDWP adapter
                                      |
                                      v
                              target JVM (launch / attach)
```

- **Transport:** DAP over Content-Length framing (same lessons as LSP).
- **Discovery:** `DESK_JAVA_DAP` env or known install paths — never hardcoded machine paths.
- **TUI:** Non-blocking; events polled in `App::tick` like LSP.
- **CLI:** `desk dap status|launch|…` once the adapter is wired.

## Capabilities (planned)

| Capability | Status |
|------------|--------|
| Launch config | Designed (`DapSession.LaunchConfig`) |
| Line breakpoints | Stub — throws until adapter wired |
| Step over/in/out | Stub |
| Continue / pause | Stub |
| Stack / variables | Stub |
| Debug console | Not started |

## Adapter choice

Prefer Microsoft/RedHat **Java Debug Server** (used by vscode-java) when redistributable, or a JDWP-speaking DAP adapter. Desk must ship discovery only — not bundle proprietary binaries without license review.

## Honesty rule

`DapSession` methods throw `UnsupportedOperationException` until end-to-end tested against a real adapter. No debugger buttons that pretend to work.

## Next (Phase 5)

1. Spawn adapter process with Content-Length.
2. `initialize` / `launch` / `setBreakpoints` / `configurationDone`.
3. TUI breakpoints gutter + stack panel.
4. Fixture: break in `testdata/fixtures/maven-app`.
