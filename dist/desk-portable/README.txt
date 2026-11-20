Desk Portable Distribution
============================

This distribution contains a custom Java runtime (built with jlink) and the
Desk application. No system Java installation is required.

Directory layout:
  bin/
    desk        - Shell launcher (Git Bash, WSL, Linux, macOS)
    desk.cmd    - Windows command launcher
  lib/
    desk-cli-*.jar   - Application JAR (all dependencies embedded)
  runtime/
    Custom JRE (jlink image) - contains only the JDK modules needed by Desk
  config/
    Configuration files (created on first run)

Usage:
  Windows:    bin\desk.cmd [command] [args]
  Linux/macOS: bin/desk [command] [args]
  Git Bash:   ./bin/desk [command] [args]

Available commands:
  version  - Print the application version
  help     - Print available commands
  edit     - Open a file in the terminal editor
  fmt      - Format a Java source file (use --check to verify)
  compile  - Compile a Java source file
  run      - Run a compiled Java class

Examples:
  bin\desk.cmd version
  bin\desk.cmd fmt src/Main.java
  bin\desk.cmd compile src/Main.java
  bin\desk.cmd run Main

No installation required: extract this archive anywhere and run the launcher.
