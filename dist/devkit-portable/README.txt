DevKit Portable Distribution
============================

This distribution contains a custom Java runtime (built with jlink) and the
DevKit application. No system Java installation is required.

Directory layout:
  bin/
    devkit        - Shell launcher (Git Bash, WSL, Linux, macOS)
    devkit.cmd    - Windows command launcher
  lib/
    devkit-cli-*.jar   - Application JAR (all dependencies embedded)
  runtime/
    Custom JRE (jlink image) - contains only the JDK modules needed by DevKit
  config/
    Configuration files (created on first run)

Usage:
  Windows:    bin\devkit.cmd [command] [args]
  Linux/macOS: bin/devkit [command] [args]
  Git Bash:   ./bin/devkit [command] [args]

Available commands:
  version  - Print the application version
  help     - Print available commands
  edit     - Open a file in the terminal editor
  fmt      - Format a Java source file (use --check to verify)
  compile  - Compile a Java source file
  run      - Run a compiled Java class

Examples:
  bin\devkit.cmd version
  bin\devkit.cmd fmt src/Main.java
  bin\devkit.cmd compile src/Main.java
  bin\devkit.cmd run Main

No installation required: extract this archive anywhere and run the launcher.
