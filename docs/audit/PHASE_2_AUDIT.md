# Phase 2 Audit — Desk TUI Multi-Tab (`modules/desk-tui`)

**Scope:** `git diff origin/main...HEAD -- modules/desk-tui` (EditorTab / sync_active_tab / open_file / close / activate_tab, tab bar, tab shortcuts)  
**Date:** 2026-11-21  
**Verdict:** Multi-tab scaffolding works for Ctrl+Tab switch when `sync_active_tab` runs, but several paths bypass sync or discard disk-backed state. Session restore and Java LSP/workspace features are not wired into the TUI. Tests do not cover unsaved-edit preservation across tabs.

---

## Issues

### 1. Unsaved changes lost on tab switch / open (clone sync timing)

| | |
|---|---|
| **Severity** | **Critical** |
| **Status** | Confirmed |
| **Files** | `src/app.rs` (`open_file`, `sync_active_tab`, `activate_tab`) |

**Finding:** Live edits live in `App::buffer`; tab slots hold clones updated only via `sync_active_tab`. `activate_tab` / `next_tab` / `prev_tab` sync first and preserve edits. `open_file` does **not** when reopening an already-open path:

```672:681:modules/desk-tui/src/app.rs
        if let Some(idx) = existing {
            // Reload from disk into the existing tab. Do not sync_active_tab first —
            // that would overwrite the on-disk content with unsaved buffer edits.
            self.tabs[idx] = EditorTab {
                path: Some(canonical.clone()),
                buffer: buffer.clone(),
                language,
            };
            self.active_tab = idx;
```

That comment is wrong when `existing != active_tab`: `sync_active_tab` writes the **current** live buffer into `tabs[active_tab]`, not into `tabs[existing]`. Skipping sync drops the previous tab’s unsaved buffer. Setting `active_tab = idx` bypasses `activate_tab`, so no sync occurs.

Additionally, the branch always reloads from disk into the existing tab, discarding that tab’s unsaved edits without the guard used by `reload_current_file`. Re-clicking the active file in the explorer has the same silent discard.

**Ctrl+Tab path:** OK (sync then clone).  
**Explorer / `:e` reopen-existing path:** data loss.

**Fix direction:** Always `sync_active_tab()` before changing `active_tab`. For an existing tab, call `activate_tab(idx)` (or equivalent) instead of reloading unless the user explicitly reloads; if reloading, keep the modified check from `reload_current_file`.

---

### 2. Unnecessary full buffer clones

| | |
|---|---|
| **Severity** | **High** (perf / memory) |
| **Status** | Confirmed |
| **Files** | `src/app.rs`, `src/ui.rs`, `src/buffer.rs` |

**Finding:** `Buffer` is `Clone` and deep-copies `lines`, `undo_stack`, and `redo_stack` (`BufferState` also holds full line vectors). Clones happen on:

- Every `sync_active_tab` call
- Every frame: `render_tab_bar` → `sync_active_tab()` (`ui.rs`)
- Every `activate_tab` (sync clone + load clone)
- `open_file` / `new_file` / `close_current_file` / `close_other_tabs` / `save_all_tabs`

Dual ownership (`App::buffer` mirrored into `tabs[active_tab]`) forces this pattern.

**Fix direction:** Single source of truth (e.g. `tabs` only, or `Rc`/`Arc`/`swap` without full copy); stop syncing on every paint; sync only on tab change / save / close.

---

### 3. Session restore missing (TUI does not load `.desk/session.json`)

| | |
|---|---|
| **Severity** | **High** (feature gap vs docs) |
| **Status** | Confirmed |
| **Files** | `src/main.rs`, `src/app.rs`; Java: `desk.core.workspace.WorkspaceStore` |

**Finding:** Java writes/reads `<root>/.desk/session.json` (`WorkspaceStore.saveSession` / `loadSession`) with open documents, cursors, and active id. Docs (`WORKSPACE_GUIDE.md`, `REMAINING_LIMITATIONS.md` §7) describe session restore.

TUI startup:

```45:53:modules/desk-tui/src/main.rs
    let args: Vec<String> = env::args().collect();
    let initial_path = if args.len() > 1 {
        Some(PathBuf::from(&args[1]))
    } else {
        None
    };

    let mut app = App::new(initial_path.as_deref());
```

No read of `.desk/session.json`, no restore of multiple tabs/cursors/scroll. `TuiLauncher` only forwards optional path args; it does not pass session document lists. `App::new` opens at most one file.

**Fix direction:** On launch, parse `.desk/session.json` (or accept structured args from CLI) and open tabs with cursor/scroll; on quit / tab change, persist session (or call into Java workspace store).

---

### 4. Performance: full-file highlight every frame

| | |
|---|---|
| **Severity** | **High** |
| **Status** | Confirmed (pre-existing path still used by multi-tab UI) |
| **Files** | `src/ui.rs` (`render_editor` / workspace editor), `src/highlighter.rs` |

**Finding:**

```421:430:modules/desk-tui/src/ui.rs
    let tokens_all = highlight_lines(&buffer.lines, app.language);
    ...
    for line_idx in scroll_top..line_count.min(scroll_top + inner.height as usize + 1) {
```

`highlight_lines` tokenizes **every** line every draw (~20 FPS poll loop in `main.rs`), then only the visible window is painted. Cost grows with file size, not viewport size. Combined with per-frame buffer clone (#2), large Java files will stutter.

**Fix direction:** Highlight only `scroll_top..scroll_top+visible` (carrying multiline state from a cached prefix), or invalidate a token cache on edit.

---

### 5. Mouse tab click index / hit-testing bugs

| | |
|---|---|
| **Severity** | **Medium** |
| **Status** | Confirmed (partially improved vs pre-diff) |
| **Files** | `src/ui.rs` (`render_tab_bar`), `src/input.rs` (`handle_mouse_event`) |

**Findings:**

1. **Width vs char count:** Hit boxes use `label.chars().count()` / `close.chars().count()`, not Unicode display width (`unicode_width` is imported elsewhere). Wide glyphs in names or `✕` / `●` / `│` misalign mouse columns vs rendered cells.
2. **Overflow:** `x = x.saturating_add(tab_len + close_len + 3)` never clips to `area.width`. Many tabs push `x` toward `u16::MAX`; saturated regions collide so the wrong `idx` can win.
3. **Close flow:** Close does `activate_tab(idx)` then `close_current_file()` — correct for preserving the previous tab via sync, but closes without an unsaved-changes prompt.
4. **Tuple semantics:** Positions are `(tab_start, tab_end, close_end, idx)`; click on label activates, click on ✕ closes. Better than the old “any click in label range closes” behavior, but naming (`tab_close_positions`) still implies close-only.

**Fix direction:** Accumulate with `UnicodeWidthStr`; stop emitting hit regions past `area.x + area.width`; optionally `close_tab(idx)` without activate+close dance; prompt on modified.

---

### 6. Dead / unreachable code from recent tab shortcuts

| | |
|---|---|
| **Severity** | **Low** |
| **Status** | Confirmed |
| **Files** | `src/app.rs`, `src/input.rs` |

**Findings:**

| Symbol / behavior | Notes |
|---|---|
| `close_other_tabs` | Defined and syncs correctly; **never** called from keys, menus, or `:` commands. Dead API. |
| File menu | No “Save All” / “Close Others” despite `save_all_tabs` / `close_other_tabs`. |
| Ctrl+PageUp/PageDown | Replaced former Ctrl+Page* terminal-scroll arms under the global CONTROL handler. Terminal focus still scrolls via non-Ctrl PageUp/Down in `handle_terminal_input` — not unreachable, but Ctrl+Page* no longer scrolls when terminal is unfocused (intentional tab binding). |
| `save_all_tabs` double-save | Saves each `tab.buffer`, then saves `self.buffer` again for the active path — redundant, not dead. |

**Fix direction:** Wire menu/commands to `close_other_tabs` / `save_all_tabs`, or remove unused helpers.

---

### 7. LSP / workspace features from Java not integrated into TUI

| | |
|---|---|
| **Severity** | **High** (architecture gap) |
| **Status** | Confirmed |
| **Files** | TUI: no LSP client; Java: `desk.java.lsp.*`, `desk.core.workspace.*` |

**Finding:** Phase 2 Java side has `LanguageServerHost`, `JavacLanguageClient`, `ExternalProcessLanguageClient`, workspace open/session/recent, JDK pin, etc. The Rust TUI:

- Has no LSP / JSON-RPC / completion / diagnostics UI wired to those types
- Runs local `javac`/`java` as shell jobs only (`compile_current_file` / `run_current_file`)
- Treats “workspace” as explorer + cwd UI (`render_workspace`), not `WorkspaceStore`
- Is launched as a separate process (`TuiLauncher`) with no IPC bridge for session, LSP, or document sync

Aligned with `REMAINING_LIMITATIONS.md` §1 and architecture docs (CLI JVM spawns TUI). Multi-tab work did not close this gap.

**Fix direction:** Define a TUI↔CLI/Java protocol (or embed session + diagnostics IPC) before claiming workbench parity.

---

### 8. Missing tests for multi-tab unsaved-edit preservation

| | |
|---|---|
| **Severity** | **High** (quality gate) |
| **Status** | Confirmed |
| **Files** | `src/app.rs` `mod tests` |

**Finding:** Existing tests cover single-file save/open, search, terminal, render, and `closing_file_clears_tab_render`. There are **no** tests that:

- Edit tab A, switch to tab B via `activate_tab` / `next_tab`, switch back, assert text/modified preserved
- Open a second file while the first has unsaved edits
- Re-open an already-open path without discarding edits (#1)
- `save_all_tabs` across modified tabs
- Mouse hit indices for multiple tabs

`FEATURE_INVENTORY.md` claims “existing + open/reload tests” for multi-tabs; that overstates coverage for unsaved preservation.

**Minimal regression suite to add:**

```text
tab_switch_preserves_unsaved_edits
open_second_file_preserves_first_tab_buffer
reopen_existing_tab_does_not_discard_edits   // currently fails — locks #1
save_all_tabs_writes_inactive_modified
```

---

## Summary table

| # | Issue | Severity |
|---|--------|----------|
| 1 | Unsaved edits lost when `open_file` hits an existing tab (skip sync + disk reload) | Critical |
| 2 | Full `Buffer` clones every sync / every frame | High |
| 3 | TUI never loads `.desk/session.json` | High |
| 4 | Full-file `highlight_lines` every frame | High |
| 5 | Mouse tab hit boxes: char-count vs width, overflow, no dirty prompt | Medium |
| 6 | `close_other_tabs` dead; Save All not in File menu | Low |
| 7 | Java LSP/workspace not integrated into TUI | High |
| 8 | No tests for multi-tab unsaved-edit preservation | High |

## What works

- `EditorTab` + `tabs` / `active_tab` model and tab bar rendering for multiple titles
- `activate_tab` / `next_tab` / `prev_tab` preserve edits when used (Ctrl+Tab / Ctrl+PageDown / BackTab / Ctrl+PageUp)
- `new_file` / `close_current_file` / `save_as` call `sync_active_tab`
- Mouse can activate by label and close via ✕ (improved vs single-tab close-on-any-click)
- `Ctrl+Shift+S` → `save_all_tabs`

## Recommended Phase 2 exit criteria

1. Fix #1 and add the four regression tests in #8 (must pass).
2. Stop per-frame `sync_active_tab` (#2) and viewport-scoped highlight (#4).
3. Either implement TUI session load (#3) or keep the limitation but stop implying restore in TUI docs until done.
4. Treat #7 as a separate Phase (IPC/LSP), not as done by multi-tab UI alone.
