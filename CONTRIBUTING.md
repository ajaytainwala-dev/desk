# Contributing to Desk

Desk is a cross-platform Java developer toolkit with two parts:

- **Java** — Maven multi-module build (`modules/*`) producing the `desk` CLI.
- **Rust** — the TUI editor (`modules/desk-tui`) producing the `desk` binary.

This guide covers the commands contributors need to build, test, and run the project.

---

## Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| JDK | 25+ (LTS) | Primary build baseline |
| Maven | 3.9.9 | Use the wrapper (`mvnw` / `mvnw.cmd`) |
| Rust | stable | 2024 edition |

On Windows the repo's `java` is on `JAVA_HOME` but may not be on `PATH` for `cmd`/`PowerShell`. Prepend the JDK `bin` directory before running Maven:

```powershell
# PowerShell
$env:PATH = "C:\Program Files\Eclipse Adoptium\jdk-25.0.5.10002-hotspot\bin;$env:PATH"
```

```cmd
:: cmd
set PATH=C:\Program Files\Eclipse Adoptium\jdk-25.0.5.10002-hotspot\bin;%PATH%
```

Rust is expected on `PATH` (`cargo`, `rustc`). Install from https://rustup.rs/ if missing.

---

## Java — build

From the repository root, use the Maven wrapper. All commands run at the repo root unless noted.

### Compile all modules

```powershell
./mvnw -B compile
```

```bash
./mvnw -B compile
```

### Package (creates the CLI uber JAR)

```powershell
./mvnw -B package -DskipTests
```

```bash
./mvnw -B package -DskipTests
```

Output artifact:

```
modules/desk-cli/target/desk-cli-0.1.0-SNAPSHOT.jar
```

### Full build with tests

```powershell
./mvnw -B clean verify
```

```bash
./mvnw -B clean verify
```

This compiles, runs the unit-test suite, and packages every module.

### Run only tests

```powershell
./mvnw -B test
```

```bash
./mvnw -B test
```

### Build a single module (and its dependencies)

```powershell
./mvnw -B -pl modules/desk-java -am package -DskipTests
```

```bash
./mvnw -B -pl modules/desk-java -am package -DskipTests
```

### Strict-warnings compile (CI profile)

```powershell
./mvnw -B -Pstrict-warnings compile
```

```bash
./mvnw -B -Pstrict-warnings compile
```

---

## Rust — build

The TUI lives in `modules/desk-tui`.

### Check formatting

```powershell
cargo fmt --all -- --check
```

```bash
cargo fmt --all -- --check
```

### Run clippy

```powershell
cargo clippy --all-targets -- -D warnings
```

```bash
cargo clippy --all-targets -- -D warnings
```

### Test

```powershell
cargo test --manifest-path modules/desk-tui/Cargo.toml
```

```bash
cargo test --manifest-path modules/desk-tui/Cargo.toml
```

### Release build

```powershell
cargo build --release --manifest-path modules/desk-tui/Cargo.toml
```

```bash
cargo build --release --manifest-path modules/desk-tui/Cargo.toml
```

Output artifact:

```
modules/desk-tui/target/release/desk.exe   # Windows
modules/desk-tui/target/release/desk        # Linux / macOS
```

---

## Usage

### Run the Java CLI

Directly via Maven (no install needed):

```powershell
./mvnw -q exec:java -pl modules/desk-cli -Dexec.mainClass=desk.cli.Main -Dexec.args="help"
```

```bash
./mvnw -q exec:java -pl modules/desk-cli -Dexec.mainClass=desk.cli.Main -Dexec.args="help"
```

Or run the packaged uber JAR:

```powershell
java -jar modules/desk-cli/target/desk-cli-0.1.0-SNAPSHOT.jar help
```

```bash
java -jar modules/desk-cli/target/desk-cli-0.1.0-SNAPSHOT.jar help
```

Common CLI commands:

| Command | Description |
|---------|-------------|
| `desk help` | Show help |
| `desk version` | Show version |
| `desk java doctor` | Aggregate JDK / project / Git / LSP health check |
| `desk build` | Build a project via Maven / javac |
| `desk test` | Run project tests |
| `desk fmt <file>` | Format a Java source file |
| `desk check <file>` | Check a Java source file |
| `desk new <name> --template maven` | Create a new Maven project |
| `desk` / `desk .` | Launch the TUI editor (cwd as workspace) |
| `desk open <path>` | Open a folder workspace or file |

### Run the Rust TUI

```powershell
modules/desk-tui/target/release/desk.exe
```

```bash
./modules/desk-tui/target/release/desk
```

```powershell
modules/desk-tui/target/release/desk.exe .
```

```bash
./modules/desk-tui/target/release/desk .
```

Set `DESK_JAR` to point the TUI at a packaged CLI JAR; set `DESK_TUI` to override the editor binary path.

### JDT LS (Java language server) path

The TUI needs the Eclipse JDT Language Server for Java features (completion, hover, definition, references). If you see `LSP: unavailable` / "No JDT LS found", set `DESK_JAVA_LS` to the `jdtls` launcher:

```powershell
$env:DESK_JAVA_LS="C:\Users\<you>\.desk\jdtls\1.57.0\bin\jdtls.bat"
modules/desk-tui/target/release/desk.exe
```

```bash
export DESK_JAVA_LS="$HOME/.desk/jdtls/1.57.0/bin/jdtls"
./modules/desk-tui/target/release/desk
```

The TUI also auto-discovers JDT LS at:
- `~/.desk/jdtls/1.57.0/bin/jdtls` (provisioned by the Java-side `JdtLsProvisioner`)
- `~/.desk/jdt-language-server/bin/jdtls`
- `~/.local/share/jdtls/bin/jdtls`
- any `jdtls` (or `jdtls.exe` / `jdtls.bat` on Windows) on `PATH`

On Windows, make sure `JAVA_HOME` points to a JDK 25+ and that `jdtls.bat`/`jdtls.exe` is discoverable, since `PATH` is split on `;`.

---

## CI

The repository runs these checks on every push/PR under `.github/workflows/ci.yml`:

1. `validate` — wrapper + project structure
2. `build` — Maven compile, tests, package (Ubuntu / Windows / macOS)
3. `static-analysis` — strict-warnings compile + dependency inventory
4. `rust-build` — `cargo fmt --check`, `cargo clippy -D warnings`, `cargo test`, `cargo build --release`
5. `jdtls-it` / `jdi-debug-it` — live Java integration tests (Ubuntu)
6. `package` — jlink portable distribution (Ubuntu)

Contributors should run the equivalent local commands above before opening a pull request.

---

## Submitting changes

1. Fork the repo and create a feature branch from `main`.
2. Build and test locally (Java + Rust) as above.
3. Commit with a clear message.
4. Open a pull request against `main`.

See the existing docs under `docs/` for architecture, CLI reference, Java version policy, and release readiness.
