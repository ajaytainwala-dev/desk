# Desk

A lightweight, cross-platform Java development workbench: terminal UI editor (Rust), intelligent Java tooling, workspace management, formatter, build orchestration, Git helpers, and a small CLI.

Website: [https://desk.ajaytainwala.dev](https://desk.ajaytainwala.dev)

## Two modes

**Interactive TUI (default)** — run with no arguments:

```bash
desk
desk .
desk open /path/to/project
```

**CLI commands:**

```bash
desk java list
desk java use 25
desk new my-app --template maven
desk build
desk test
desk fmt Main.java
desk check Main.java
desk git status
desk debug run --main Demo --bp 12
```

## Commands

| Command | Description |
|---------|-------------|
| `desk` / `desk .` | Launch TUI; open cwd as workspace |
| `desk open <path>` | Open folder workspace or file |
| `desk new <name>` | Create project (default **Java 25 LTS**) |
| `desk workspace …` | list / open / close / status |
| `desk java …` | list / detect / current / use / info / doctor |
| `desk lsp …` | diagnose / complete / hover / definition / references / symbols |
| `desk build` / `desk clean` | Build or clean via Maven, Gradle, or javac |
| `desk test` | Run tests (`--watch` not implemented — rejected) |
| `desk debug run` | JDI/JDWP CLI debug (breakpoint / stack / locals) — **not** a TUI debug panel |
| `desk doctor` | Aggregate JDK / project / Git / LSP health check |
| `desk fmt` / `check` / `compile` / `run` | Existing single-file tooling |
| `desk git …` | Status, branch, diff, commit, push, pull |
| `desk version` / `help` | Version and help (`--version`, `--help` also work) |

Full reference: [`docs/cli/COMMAND_REFERENCE.md`](docs/cli/COMMAND_REFERENCE.md)  
Release readiness (v0.1.0): [`docs/RELEASE_READINESS.md`](docs/RELEASE_READINESS.md)  
Java matrix: [`docs/java/VERSION_SUPPORT.md`](docs/java/VERSION_SUPPORT.md)  
Project compatibility: [`docs/java/PROJECT_COMPATIBILITY.md`](docs/java/PROJECT_COMPATIBILITY.md)  
JDK management: [`docs/java/JDK_MANAGEMENT.md`](docs/java/JDK_MANAGEMENT.md)  
LSP setup: [`docs/java/LSP_SETUP.md`](docs/java/LSP_SETUP.md)  
Debugging: [`docs/debug/DAP_IMPLEMENTATION.md`](docs/debug/DAP_IMPLEMENTATION.md) (JDI CLI; DAP transport limits)  
Workspaces: [`docs/workspace/WORKSPACE_GUIDE.md`](docs/workspace/WORKSPACE_GUIDE.md)

## Java version policy

- **Java 25** — primary LTS baseline (Desk build & CI)
- **Java 26 / 27** — compatibility targets (**not** LTS)
- **Java 29** — future LTS migration target
- Projects may pin different JDKs per workspace without changing system `JAVA_HOME`

## Build from source

Requires **Java 25+** and **Rust stable** (2024 edition).

```bash
git clone https://github.com/ajaytainwala-dev/desk.git
cd desk

./mvnw clean verify
cargo build --release --manifest-path modules/desk-tui/Cargo.toml
```

```bash
./mvnw -q exec:java -pl modules/desk-cli -Dexec.mainClass=desk.cli.Main -Dexec.args="java doctor"
java -jar modules/desk-cli/target/desk-cli-0.1.0-SNAPSHOT.jar help
```

Set `DESK_JAR` to point the TUI at a packaged CLI JAR; set `DESK_TUI` to override the editor binary path.

## Architecture

| Module | Role |
|--------|------|
| `desk-core` | Config, filesystem, logging, document store, workspace |
| `desk-cli` | CLI entry (`desk.cli.Main`), commands |
| `desk-formatter` | Java source formatter |
| `desk-java` | JDK discovery/selection, build, Git, LSP host |
| `desk-lexer` / `desk-syntax` | Lexer and highlighting |
| `desk-tui` | Rust TUI (Ratatui + Crossterm), multi-tab editor |

See [`docs/architecture/CURRENT_STATE.md`](docs/architecture/CURRENT_STATE.md) and [`PROPOSED_ARCHITECTURE.md`](docs/architecture/PROPOSED_ARCHITECTURE.md).

## Distribution

| Asset | Purpose |
|-------|---------|
| `Desk-Setup-<version>.exe` | Windows GUI installer |
| `desk-cli-setup-windows.ps1` | Windows CLI setup |
| `desk-cli-setup.sh` | macOS / Linux CLI setup |
| `desk-portable-<os>.zip` | Portable zip (unzip & run) |
| `desk-tui-*` | Standalone TUI binaries |

```bash
curl -fsSL https://github.com/ajaytainwala-dev/desk/releases/latest/download/desk-cli-setup.sh | bash
```

```powershell
irm https://github.com/ajaytainwala-dev/desk/releases/latest/download/desk-cli-setup-windows.ps1 | iex
```

## Author

Created by [Ajay Tainwala](https://www.ajaytainwala.dev).

## Contributing

Issues and pull requests are welcome — including work on the Desk TUI (`modules/desk-tui`), CLI, formatter, language intelligence, and packaging. See [`docs/roadmap/IMPLEMENTATION_ROADMAP.md`](docs/roadmap/IMPLEMENTATION_ROADMAP.md).

## License

MIT — see `LICENSE`.
