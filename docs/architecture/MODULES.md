# Module Architecture

## Current modules (Milestone 5)

### devkit-core

**Responsibility:** Foundation services used by every other module. Zero dependencies on other devkit modules.

**Packages:**
- `devkit.core.logging` — `Logger` interface, `Slf4jLogger` implementation, `LoggerFactory` access point. Backed by SLF4J + slf4j-simple.
- `devkit.core.config` — `Config` interface, `DefaultConfig` immutable map-backed implementation, `JsonConfigLoader` (small hand-written JSON parser), `Defaults` (built-in defaults), `ConfigSources` (precedence merge), `EnvironmentConfig` (DEVKIT_ prefix overrides), `ConfigException`.
- `devkit.core.filesystem` — `FileSystem` (atomic safe-write, read/readable/writable checks), `FilesystemException`.
- `devkit.core.document` — `Document` interface and `DefaultDocument` (content, path, encoding, CRLF/LF, dirty tracking, cursor position).

**Dependencies:** slf4j-api, slf4j-simple (runtime); junit-jupiter (test).

### devkit-cli

**Responsibility:** Command-line interface. Argument parsing, command dispatch, and the application entry point. No business logic lives here — it only routes to commands.

**Packages:**
- `devkit.cli` — `Main` (entry point), `CommandRegistry` (command lookup), `Command` (interface), `CommandResult` (exit code + message), `AppVersion` (central version constant).

**Commands implemented:**
- `version` — print version (`--short` for just the number)
- `help` — print usage and command list
- `edit` — open a file in the terminal editor
- `fmt` — format a Java source file in place, or `--check` to verify formatting

**Dependencies:** devkit-core.

### devkit-lexer

**Responsibility:** Reusable Java source lexer. Converts Java source text into a stream of `Token`s. Handles keywords, contextual keywords, primitive types, identifiers (with Unicode support), operators, separators, delimiters, numeric literals (decimal, hex, binary, float), string literals (with escapes), character literals, text blocks, line and block comments, annotations, whitespace, and newlines. Never crashes on malformed or incomplete input.

**Packages:**
- `devkit.lexer` — `JavaLexer` (main lexer), `LexerException`.
- `devkit.lexer.tokens` — `Token`, `TokenType`, `TokenStream`.
- `devkit.lexer.diagnostics` — `LexerException`.

**Dependencies:** devkit-core (transitively via slf4j-api); slf4j-api.

### devkit-syntax

**Responsibility:** Syntax-highlighting engine consuming lexer output. Provides configurable themes (dark, light, monokai, dracula, nord, high contrast) and a theme interface designed to support future languages.

**Packages:**
- `devkit.syntax.highlighter` — `Theme` (interface), `TokenStyle`, `Highlighter`, `HighlightedToken`.
- `devkit.syntax.themes` — `AbstractTheme`, `DarkTheme`, `LightTheme`, `MonokaiTheme`, `DraculaTheme`, `NordTheme`, `HighContrastTheme`.
- `devkit.syntax.token.mapping` — `TokenMapping` (interface for future language support).

**Dependencies:** devkit-lexer; slf4j-api.

### devkit-formatter

**Responsibility:** Independent Java source code formatter. Works on the lexer token stream, decoupled from the editor. Provides formatting rules, configuration, diff reporting, and in-place or stdout writing.

**Packages:**
- `devkit.formatter` — `Formatter` (main entry point), `FormattingConfig` (immutable configuration), `FormatResult` (formatted text + change metadata), `FormatterException`.

**Dependencies:** devkit-lexer, devkit-core; slf4j-api.

**CLI command:** `fmt` — formats a Java source file in place, or checks formatting with `--check`.

### devkit-java

**Responsibility:** Java compilation and execution integration. Discovers installed JDKs,
compiles Java source using {@code javax.tools.JavaCompiler}, and runs compiled classes.
Optional JDK dependency — works without a globally installed JVM when bundled with
jlink or Native Image.

**Packages:**
- `devkit.java` — `JdkDiscovery` (JDK discovery), `JavaSourceCompiler` (compilation),
  `JavaRunner` (execution), `CompileResult`, `RunResult`, `JavaException`.

**Dependencies:** devkit-core; slf4j-api.

**CLI commands:**
- `compile` — compiles a Java source file to class files.
- `run` — runs a compiled Java class.

## Future module direction (planned, not yet implemented)

| Module | Responsibility | Depends on |
|--------|---------------|------------|
| devkit-services | Navigation, file search, workspace | devkit-core, devkit-editor |
| devkit-app | Bootstrap, application lifecycle | devkit-cli, devkit-editor, devkit-services |

## Dependency rules

- No cycles between modules.
- Business logic never lives in terminal rendering classes.
- Formatter is decoupled from the editor.
- Compiler integration is a separate capability (optional JDK).
- CLI only routes to commands; it does not contain business logic.
