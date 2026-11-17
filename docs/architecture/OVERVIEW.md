# Architecture Overview

**Project:** devkit (generic placeholder name, designed to be renamed easily)  
**Status:** Milestone 1 — Foundation (working CLI)  
**Baseline Java:** 23 (project targets Java 25 LTS once available)

## Product vision

A lightweight, cross-platform, terminal-native Java developer toolkit combining:

1. A terminal-based source code editor (TUI)
2. An independent Java source code formatter
3. A reusable syntax-highlighting engine (custom lexer)
4. Java source code analysis and diagnostics
5. Java compilation and execution integration
6. A command-line interface
7. A configurable developer environment
8. A portable and native distribution system

The application works independently of any IDE and has two modes:

- **Standalone CLI mode** — `devkit fmt`, `devkit check`, `devkit compile`, `devkit run`, `devkit edit`
- **Interactive TUI mode** — `devkit` (no arguments) launches the terminal editor

## Guiding principles

- **Java-first:** Java is the primary implementation language. No Kotlin, Scala, Python, Node.js, or Rust required to run.
- **No external Java installation requirement:** Released binaries must run without a globally installed JVM (native executable or jlink portable runtime).
- **Modular:** Editor, lexer, formatter, and CLI are independent modules with clear dependency direction.
- **Minimal dependencies:** Only stable, maintained, license-compatible dependencies are adopted.
- **No faked functionality:** Commands report "not yet implemented" rather than pretending to work.

## Repository layout (Milestone 2)

```
devkit/
├── pom.xml                     # Parent POM (version, dependency management)
├── mvnw / mvnw.cmd             # Maven Wrapper
├── .mvn/                       # Maven Wrapper configuration
├── modules/
│   ├── devkit-core/            # Logging, config, filesystem, document model
│   ├── devkit-cli/             # CLI entry point, command registry, commands
│   ├── devkit-lexer/           # Java lexer and token model
│   └── devkit-syntax/          # Syntax highlighter and themes
├── packaging/                  # Native, portable, OS-specific packaging (future)
├── docs/architecture/          # Architecture decision documents
├── .github/workflows/          # CI/CD pipelines
├── LICENSE
├── README.md
└── ...
```

## Module dependency direction

```
devkit-syntax  -->  devkit-lexer
devkit-cli     -->  devkit-core
```

`devkit-cli` and `devkit-syntax` both depend on `devkit-core` (directly or transitively). `devkit-core` has no module dependencies (only SLF4J and JUnit for tests). No cycles between modules.

## Distribution strategies (future)

| Strategy | Artifact | Requires external JVM? |
|----------|----------|------------------------|
| Native executable | `project.exe` / `./project` | No (GraalVM Native Image) |
| Portable runtime | `project-portable/` with jlink runtime | No (bundled JRE) |
| Standalone JAR | `project.jar` | Yes (user-supplied JVM) |

Native Image and jlink are separate strategies. A successful JAR build does not guarantee a successful native build.
