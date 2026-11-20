# Portability Strategy

## Distribution models

The product must run without requiring a user to install Java. Three distribution strategies are supported:

### A. Native executable (GraalVM Native Image)

Compiles the Java application into a platform-specific native binary.

- Windows: `project.exe` (x64, ARM64 when supported)
- Linux: `./project` (x64, ARM64)
- macOS: `./project` (x64, ARM64)

No external JVM required. Native Image and jlink are separate strategies — a successful JAR build does not guarantee a native build.

### B. Portable runtime distribution (jlink)

An archive containing:

```
project-portable/
├── bin/
│   ├── project
│   └── project.cmd
├── lib/
│   └── application.jar
├── runtime/
│   └── bin/
│       └── java          (custom jlink runtime)
├── config/
└── README.txt
```

Runs without modifying system Java or requiring administrator privileges.

### C. Standalone JAR

Optional executable JAR for developers who already have a compatible JVM.

## Portable configuration and data

The application does not assume a fixed install location. Supported locations:

1. **Portable mode** — configuration and data beside the executable (`--portable` flag or a marker file).
2. **User-level** — OS-standard application configuration directory (e.g. `~/.config/desk/`).
3. **Project-level** — `desk.json` in the project directory.

Configuration precedence (low to high):
1. Built-in defaults
2. User config file
3. Project config file
4. Environment variables (`DESK_*` prefix)
5. Command-line overrides

The application never requires a configuration file to exist.

## Platform abstraction

Operating-system checks are not scattered through the application. Platform interfaces are provided for:

- Terminal capabilities
- Process execution (`ProcessBuilder` with explicit arguments, no shell concatenation)
- Filesystem operations
- Environment variables
- Home-directory discovery
- Configuration locations
- Clipboard integration
- Terminal dimensions
- Platform-specific launch behavior

Java standard APIs are preferred; platform-specific implementations are used only when necessary.

## Supported platforms

| OS | Architectures |
|----|---------------|
| Windows | x64, ARM64 (when toolchain supports) |
| Linux | x64, ARM64 |
| macOS | x64, ARM64 |

Native builds use a pinned GraalVM distribution and version. Linux native builds target an older glibc baseline where practical and are built in a containerized environment when required.
