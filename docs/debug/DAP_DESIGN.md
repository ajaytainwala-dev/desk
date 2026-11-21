# Debug Adapter Protocol — Design & Phase 5 Status

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

- **Transport:** `DapTransport` — Content-Length framing (implemented).
- **Session:** `DapSession` — discovery (`DESK_JAVA_DAP`), `initialize` / `initialized` handshake (implemented against mock adapter tests).
- **Debuggee ops:** launch, breakpoints, step, stack, variables — **throw until E2E validated**.
- **TUI:** No debug keybindings exposed until ops are real.

## Capabilities

| Capability | Status |
|------------|--------|
| Content-Length transport | **Implemented + tested** (`DapTransportTest`) |
| Adapter discover (`DESK_JAVA_DAP`) | **Implemented** |
| `initialize` handshake | **Implemented + mock-tested** |
| Launch / breakpoints / step / stack / variables | **Not implemented** (honest UOE) |
| Debug console / TUI UI | **Not started** |

## Adapter choice

Prefer Microsoft/RedHat **Java Debug Server** when redistributable. Desk discovers via env — never hardcodes machine paths or ships proprietary binaries without license review.

## Honesty rule

No debugger buttons that pretend to work. Transport/session foundation may ship; debuggee control waits for a real adapter E2E fixture.

## Phase 6

1. Wire Java Debug Server launch + `configurationDone`.
2. TUI: F9 breakpoint, F5 continue, F10/F11 step (avoid conflicting with existing F5 run / F9 format — remap carefully).
3. Fixture: break in `testdata/fixtures/maven-app`.
