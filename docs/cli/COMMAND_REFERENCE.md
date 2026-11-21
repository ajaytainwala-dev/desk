# Desk CLI Reference

```
desk                         Launch TUI (opens cwd as workspace)
desk .                       Open current directory as workspace / TUI
desk open <path>             Open folder workspace or file
desk new <name> [options]    Create project from template (default Java 25)
desk workspace list|open|close|status
desk java list|detect|current|use|info|doctor|install|remove|policy
desk lsp status|diagnose|complete|hover|definition|references|symbols
desk fmt [--check] <file>    Format Java source
desk check <file>            Compile diagnostics
desk compile <file>          Compile single file (uses workspace JDK)
desk build [compile|clean|test|package]
desk clean                   Clean build outputs
desk test [--watch]          Run tests via detected build system
                         (--watch rejected until implemented)
desk debug run …             JDI/JDWP CLI debug (see below)
desk run <class>             Run compiled class
desk doctor                  Aggregate environment health check
desk git <status|branch|…>   Git helpers (never logs credentials)
desk edit <file>             Open file in TUI
desk version | --version
desk help | --help
```

Set `DESK_JAVA_LS` to a JDT Language Server launcher for full completion/navigation.
`desk lsp diagnose` works with the javac fallback when JDT LS is absent.

### Debug (CLI / JDI)

```
desk debug run --main <Class> --bp <line> [--src File.java] [--classpath dir]
```

Uses the JDK JDI/JDWP backend. This is **not** an interactive TUI debug panel (F5 in the editor remains Run).
External `DESK_JAVA_DAP` is initialize-only — see `docs/debug/DAP_IMPLEMENTATION.md`.

## Java toolchain

```
desk java use 25             Pin JDK 25 for this workspace only
desk java use 27             Pin JDK 27 for this workspace only
desk java use --user 25      User default pin (not system JAVA_HOME)
desk java install 25 --dry-run
```

Changing one workspace pin never mutates another project's `.desk/jdk.json` or the OS `JAVA_HOME`.
