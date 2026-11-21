# Workspace Guide

## Opening a workspace

```bash
desk
desk .
desk open /path/to/project
desk workspace open /path/to/project
```

Desk creates `<root>/.desk/` for:

| File | Purpose |
|------|---------|
| `jdk.json` | Workspace-scoped JDK pin |
| `settings.json` | Workspace settings overrides |
| `session.json` | Open tabs, cursors, scroll positions |

Recent workspaces are stored under `~/.config/desk/recent-workspaces.json`.

## Project detection

On open, Desk detects:

- Plain Java (`src/` or `.java` files)
- Maven (`pom.xml`, including multi-module)
- Gradle (`build.gradle` / `.kts`, including multi-module)

## New projects

```bash
desk new my-app --template maven --java 25
desk new lib --template library --group com.acme
```

Templates: `basic`, `console`, `library`, `maven`, `gradle`, `testing`.

## Editor tabs (TUI)

- Open files from the explorer — each file becomes a tab
- Click a tab to activate; click ✕ to close
- `Ctrl+Tab` / `Ctrl+PageDown` — next tab
- `Ctrl+Shift+Tab` / `Ctrl+PageUp` — previous tab
- `Ctrl+Shift+S` — save all tabs
