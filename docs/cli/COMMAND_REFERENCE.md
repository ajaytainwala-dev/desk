# Desk CLI Reference

```
desk                         Launch TUI (opens cwd as workspace)
desk .                       Open current directory as workspace / TUI
desk open <path>             Open folder workspace or file
desk new <name> [options]    Create project from template (default Java 25)
desk workspace list|open|close|status
desk java list|detect|current|use|info|doctor|install|remove|policy
desk fmt [--check] <file>    Format Java source
desk check <file>            Compile diagnostics
desk compile <file>          Compile single file
desk build [compile|clean|test|package]
desk test [--watch]          Run tests via detected build system
desk run <class>             Run compiled class
desk git <status|branch|…>   Git helpers (never logs credentials)
desk edit <file>             Open file in TUI
desk version | --version
desk help | --help
```

## Java toolchain

```
desk java use 25             Pin JDK 25 for this workspace only
desk java use 27             Pin JDK 27 for this workspace only
desk java use --user 25      User default pin (not system JAVA_HOME)
desk java install 25 --dry-run
```

Changing one workspace pin never mutates another project's `.desk/jdk.json` or the OS `JAVA_HOME`.
