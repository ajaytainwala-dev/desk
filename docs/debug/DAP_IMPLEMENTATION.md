# DAP / Java Debugging Implementation (Phase 6)

## Backend

Desk’s **primary** debugger is JDK **JDI** (`jdk.jdi`) over JDWP — a real JVM debug connection.

| Piece | Role |
|-------|------|
| `JdiDebugger` | Listen connector, launch with `-agentlib:jdwp`, breakpoints, step, stack, locals |
| `DapSession` | Session facade used by CLI/tests; `initializeJdi()` + `launch` |
| `DapTransport` | Content-Length DAP framing (external adapter / unit tests) |
| `desk debug run` | CLI vertical slice |

External `DESK_JAVA_DAP` adapters can still be discovered and `initialize`’d; **launch** of third-party adapters is not claimed E2E yet.

## E2E test

`JdiDebuggerE2ETest`:

1. Compile `DebugDemo` with `-g`
2. Launch under JDI
3. Breakpoint on `result = first + second`
4. Assert stack + locals `first=10`, `second=20`
5. Continue

CI job: **JDI debug E2E**.

## CLI

```bash
desk debug run --main DebugDemo --bp 5 --src DebugDemo.java --classpath .
```

## TUI

F5 remains **Run** (not debug continue) to avoid breaking muscle memory. Use `desk debug run` from the terminal panel / CLI until a dedicated debug panel lands in a follow-up slice. No fake debug buttons.

## Fixture

`testdata/fixtures/debug-demo/DebugDemo.java`
