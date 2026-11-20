# Desk

A lightweight, cross-platform Java workbench: terminal UI editor (Rust), Java formatter, syntax highlighting, compile/run helpers, and a small CLI — no IDE required.

## Two modes

**Interactive TUI (default)** — run with no arguments:

```bash
desk
```

**CLI commands:**

```bash
desk fmt Main.java
desk check Main.java
desk compile Main.java
desk run Main
desk edit Main.java
```

## Commands

- `desk` — launch the Rust TUI editor
- `desk version` — print version
- `desk help` — list commands
- `desk edit <file>` — open a file in the TUI
- `desk fmt <file>` — format Java in place (`--check`, `--stdin`)
- `desk check <file>` — report Java compile errors (`--stdin`)
- `desk compile <file>` — compile with `javax.tools`
- `desk run <class>` — run a compiled Java class

## Build from source

Requires **Java 23+** and **Rust stable** (2024 edition).

```bash
git clone https://github.com/ajaytainwala-dev/desk.git
cd desk

./mvnw clean verify
cargo build --release --manifest-path modules/desk-tui/Cargo.toml
```

Run the CLI via Maven:

```bash
./mvnw -q exec:java -pl modules/desk-cli -Dexec.mainClass=desk.cli.Main -Dexec.args="version"
```

Or the shaded JAR after `package`:

```bash
java -jar modules/desk-cli/target/desk-cli-0.1.0-SNAPSHOT.jar help
```

TUI binary: `modules/desk-tui/target/release/desk` (or `desk.exe` on Windows).

Set `DESK_JAR` to point the TUI at a packaged CLI JAR; set `DESK_TUI` to override the editor binary path.

## Architecture

| Module | Role |
|--------|------|
| `desk-core` | Config, filesystem, logging, document model |
| `desk-cli` | CLI entry (`desk.cli.Main`), commands |
| `desk-formatter` | Java source formatter |
| `desk-java` | JDK discovery, compile/run via Java APIs |
| `desk-lexer` / `desk-syntax` | Lexer and highlighting |
| `desk-tui` | Rust TUI (Ratatui + Crossterm) |

The TUI calls the Java toolchain for `fmt` and `check` when a JDK and the CLI JAR are available.

## Distribution

Release artifacts (GitHub Actions on version tags) use **stable asset names** so the website and setup scripts always resolve the latest:

| Asset | Purpose |
|-------|---------|
| `Desk-Setup-<version>.exe` | Windows GUI installer |
| `desk-cli-setup-windows.ps1` | Windows CLI setup |
| `desk-cli-setup.sh` | macOS / Linux CLI setup |
| `desk-portable-<os>.zip` | Portable zip (unzip & run) |
| `desk-native-*` / `desk-tui-*` | Standalone binaries |

```bash
curl -fsSL https://github.com/ajaytainwala-dev/desk/releases/latest/download/desk-cli-setup.sh | bash
```

```powershell
irm https://github.com/ajaytainwala-dev/desk/releases/latest/download/desk-cli-setup-windows.ps1 | iex
```

### Website

Marketing site in `website/` — download buttons call the GitHub Releases API and refresh automatically when you publish a new tag. The **Sync release links** workflow also writes `website/public/latest-release.json` after each release as a cached fallback.

```bash
cd website && npm install && npm run dev
```

Installer sources live under `packaging/installers/`.

## Author

Created by [Ajay Tainwala](https://www.ajaytainwala.dev).

## Contributing

Desk is open source. Issues and pull requests are welcome — including work on the Desk TUI editor (`modules/desk-tui`), CLI, formatter, and packaging.

## License

MIT — see `LICENSE`.