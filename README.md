# DevKit

A lightweight, cross-platform, terminal-native Java developer toolkit.

DevKit combines a terminal source-code editor, an independent Java source
formatter, a reusable syntax-highlighting engine, source analysis, Java
compilation/execution integration, and a command-line interface — all in a
single Java application that works without any particular IDE.

## Two modes

**Standalone CLI mode** — individual tools work without opening the editor:

```
devkit fmt Main.java
devkit check Main.java
devkit compile Main.java
devkit run Main.java
devkit edit Main.java
```

**Interactive TUI mode** — running without arguments launches the integrated
terminal development environment:

```
devkit
```

## Status

Milestone 5 — Compiler/execution integration. All modules built and tested:

- `devkit version` — print the application version
- `devkit help` — print available commands
- `devkit edit <file>` — open a file in the terminal editor
- `devkit fmt <file>` — format a Java source file in place
- `devkit fmt --check <file>` — verify formatting without modifying
- `devkit compile <file>` — compile a Java source file
- `devkit run <class> [args...]` — run a compiled Java class

## Installation (source)

Requires Java 23 or later. No Maven installation is required — the Maven
Wrapper is included.

```bash
# Clone (replace with the actual repository URL)
git clone https://github.com/example/devkit.git
cd devkit

# Build and run tests
./mvnw clean verify

# Run the CLI
./mvnw -q exec:java -Dexec.mainClass=devkit.cli.Main -Dexec.args="version"
# or run the built jar directly
java -cp modules/devkit-cli/target/devkit-cli-0.1.0-SNAPSHOT.jar:modules/devkit-core/target/devkit-core-0.1.0-SNAPSHOT.jar <slf4j-jars> devkit.cli.Main version
```

On Windows, use `mvnw.cmd` instead of `./mvnw`.

## Architecture

The repository is a modular Maven multi-module project:

| Module | Responsibility |
|--------|----------------|
| `devkit-core` | Logging, configuration, filesystem, document model |
| `devkit-cli` | CLI entry point, command registry, commands |

See `docs/architecture/` for detailed design documents:
- `OVERVIEW.md` — product vision and layout
- `MODULES.md` — module responsibilities and dependencies
- `PORTABILITY.md` — distribution and portability strategy
- `CI-CD.md` — GitHub Actions pipelines
- `TECHNICAL-DECISIONS.md` — recorded technical decisions

## Technology stack

| Component | Technology |
|-----------|------------|
| Language | Java 23 (baseline), targeting Java 25 LTS |
| Build system | Maven + Maven Wrapper |
| Logging | SLF4J + slf4j-simple |
| Testing | JUnit 5 |
| Configuration | JSON (hand-written parser) |
| CI/CD | GitHub Actions |

## Dependencies

Only SLF4J and JUnit 5 are used. Both are stable, maintained, and
license-compatible (MIT for the project; SLF4J is MIT, JUnit is EPL-2.0).

## Distribution goals

Released binaries must run without a globally installed Java runtime:

- **Native executable** (GraalVM Native Image) — no JVM required
- **Portable runtime** (jlink) — bundled JRE in the archive
- **Standalone JAR** — for users who already have a JVM

## Contributing

See `CONTRIBUTING.md` (to be added) for development setup, branch and commit
conventions, and pull request requirements.

## License

MIT License. See `LICENSE`.

## Limitations

The following are not yet implemented:

- Native executable distribution (GraalVM Native Image) — packaging scripts created, CI workflow pending
- Portable runtime distribution (jlink) — packaging scripts created, CI workflow pending
- Release automation (GitHub releases) — workflow created

These are tracked as future milestones and will be implemented incrementally.
