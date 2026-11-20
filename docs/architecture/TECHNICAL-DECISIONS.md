# Technical Decisions

## Language and build

- **Java 23** baseline (environment available). Project targets Java 25 LTS once available. Version-specific language features are avoided so the code compiles on both.
- **Maven** with **Maven Wrapper** (`mvnw` / `mvnw.cmd`) so no local Maven installation is required.
- Parent POM defines version, dependency management, and compiler plugin config in one place for consistency.

## Logging

- **SLF4J API** + **slf4j-simple** as the default backend.
- Application codes to a `Logger` interface so the backend can be swapped (e.g. for Native Image compatibility) without touching the rest of the codebase.
- Small runtime footprint, no external configuration file required.

## Configuration

- JSON config files with a small hand-written parser (no heavy JSON dependency).
- Precedence: defaults → user config → project config → environment variables → command-line overrides.
- Missing config file is never an error; built-in defaults guarantee sensible behavior.
- Environment variables use the `DESK_` prefix, mapped to dot-separated keys.

## Filesystem

- UTF-8 default encoding; CRLF and LF preservation.
- Atomic safe writes: write to a temp file in the same directory, then move over the target (avoids partially written files on crash).
- Best-effort file permission preservation.

## JSON parser

- Index-based string parser (not Reader-based lookahead) to avoid `mark`/`reset` pitfalls.
- Supports objects, arrays, strings (with escapes and unicode), numbers, booleans, null.
- Duplicate keys keep the last value.

## CLI

- Dedicated `CommandRegistry` for command lookup; argument parsing is decoupled from the entry point.
- `Main` only routes to commands; no business logic in the entry point.
- Each command is a separate class implementing the `Command` interface.
- Commands return `CommandResult` (exit code + optional message).

## Lexer

- Custom hand-written Java lexer (no regex-based highlighting).
- Token model: `TokenType`, `Token`, `TokenStream` in `desk.lexer.tokens`.
- Handles keywords, contextual keywords, primitive types, identifiers (Unicode via `Character.isUnicodeIdentifierStart/Part`), operators, separators, delimiters, numeric literals (decimal, hex `0x`, binary `0b`, floats with exponents and suffixes), string literals (with escapes), character literals, text blocks (`"""`), line comments (`//`), block comments (`/* */`), annotations (`@`), whitespace, newlines, and unknown characters.
- Keywords inside strings and comments are not misclassified — strings and comments are scanned as atomic tokens.
- Unterminated strings/comments are emitted as their respective token types rather than throwing.
- Unrecognized characters become `UNKNOWN` tokens.
- The lexer never crashes on malformed or incomplete input.

## Syntax highlighting

- Separate `Highlighter` consumes `TokenStream` and applies a `Theme`.
- `Theme` interface maps `TokenType` → `TokenStyle` (foreground, background, bold, italic).
- Six themes: dark, light, monokai, dracula, nord, high-contrast.
- Colors are hex `#RRGGBB` strings, renderer-independent.
- `TokenMapping` interface defined for future language support (JSON, XML, YAML, etc.) without modifying the lexer or highlighter.

## Text buffer (planned)

- Underlying representation to be evaluated: gap buffer, piece table, or rope.
- Editing logic independent from UI rendering.
- Gap buffer likely chosen for simplicity and good performance on typical edit workloads; trade-offs to be documented.

## Native Image limitations (known)

- Java Compiler API functionality is not automatically available in a Native Image executable.
- Compiler integration treated as a separate capability; explicit capability fallback implemented if native compilation has limitations.
- Editor, lexer, formatter, and CLI are independent from the external Java compiler.

## Versioning

- Semantic versioning (MAJOR.MINOR.PATCH).
- Version defined centrally in `desk.cli.AppVersion`; consistent across CLI, JAR manifest, and docs.
- Current version: 0.1.0 (SNAPSHOT during development).

## What is deliberately NOT done yet (future milestones)

- Terminal UI editor (Lanterna integration)
- Java lexer and syntax highlighter
- Java source formatter
- Compiler/execution integration
- Native Image and jlink packaging
- GitHub native/portable/release workflows
