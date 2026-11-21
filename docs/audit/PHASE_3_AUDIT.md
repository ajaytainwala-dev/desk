# Phase 3 Audit — Editor Integration, Data Safety & Developer Workflow

**Branch:** `cursor/desk-workbench-foundation-22b9`  
**Verified commit:** `0d20a2c068cd1e439f8440b0f5b9de380681b558`  
**Date:** 2026-11-21  
**Method:** Code inspection of `desk-core`, `desk-java`, `desk-cli`, `desk-tui` against Phase 2 docs (not trusted blindly).

## Phase 2 verification summary

| Finding | Status |
|---------|--------|
| P2-001 Compiler uses workspace JDK | **FIXED** |
| P2-002 Honest install status | **FIXED** |
| P2-003 Temurin-only download URLs | **FIXED** |
| P2-004 javac fails unsupported futures | **FIXED** |
| P2-006 Process timeout + drain | **FIXED** |
| P2-007 Project path escape | **FIXED** |
| P2-011 LSP temp cleanup | **FIXED** |
| P2-012 JavaRunner ProcessIo | **FIXED** |
| P2-013 Git `--` / flag reject | **FIXED** |
| CLI LSP transport / session | **FIXED** |
| TUI unsaved tab switch | **FIXED** |
| TUI viewport highlight | **FIXED** |
| TUI session path restore | **PARTIAL** (paths only) |
| P2-008 DocumentStore untitled / saveAll | **OPEN** — high |
| P2-009 WorkspaceStore atomic fallback | **OPEN** — medium |
| P2-018 ExternalProcessLanguageClient stub | **OPEN** — medium |
| TUI LSP UI | **OPEN** — high |
| Dirty close / quit confirm | **OPEN** — medium |
| External file modification | **OPEN** — medium |
| Palette / fuzzy / find-in-files | **OPEN** — medium |
| Build cancellation API | **OPEN** — high |
| Quality tools | **OPEN** — low |

---

## Open issues (Phase 3)

### P3-001 — DocumentStore cannot save untitled / Save All drops data

| Field | Value |
|-------|-------|
| **Files** | `DocumentStore.java`, `DefaultDocument.java` |
| **Severity** | **critical** |
| **Problem** | Dirty untitled docs skipped by `saveAll`; no `saveAs`; `setPath` can desync `pathIndex`. |
| **Repro** | Create untitled dirty doc → `saveAll` → content never written; no error. |
| **Fix** | `saveAs(Document, Path)`; reject/report untitled in `saveAll`; reindex on path change. |
| **Test** | Untitled saveAs; saveAll reports unsaved untitled; rename reindex. |

### P3-002 — TUI closes/quits without dirty confirmation

| Field | Value |
|-------|-------|
| **Files** | `app.rs` `close_current_file`, `:q`, `input.rs` Ctrl+Q |
| **Severity** | **high** |
| **Problem** | Unsaved buffers discarded silently. |
| **Repro** | Edit file → close tab / quit → edits gone. |
| **Fix** | Confirm overlay; `:q!` / force close for discard. |
| **Test** | Dirty close blocked; force close discards; Save then close. |

### P3-003 — No external modification / conflict detection

| Field | Value |
|-------|-------|
| **Files** | `buffer.rs`, `app.rs` |
| **Severity** | **high** |
| **Problem** | Disk mtime never tracked; overwrite/reload race. |
| **Repro** | Edit in Desk + external editor → save overwrites silently. |
| **Fix** | Store mtime on load/save; warn on save/reload conflict. |
| **Test** | Change mtime externally → save prompts / refuses blindly. |

### P3-004 — LSP not wired into TUI (blocks workbench claim)

| Field | Value |
|-------|-------|
| **Files** | `modules/desk-tui` (no LSP); Java `LspSession` exists |
| **Severity** | **high** |
| **Problem** | Intelligence only via CLI; editor has no completions/diagnostics/nav. |
| **Repro** | Open Java file in TUI → no problems panel / Ctrl+Space. |
| **Fix** | Async LSP bridge (stdio protocol to `desk lsp` helper or embedded); completion popup; problems panel; goto def/refs; hover. |
| **Test** | Mock/fake server or javac diagnose path; popup renders; problems navigate. |

### P3-005 — Build has timeout but no cancellation

| Field | Value |
|-------|-------|
| **Files** | `BuildService.java` |
| **Severity** | **high** |
| **Problem** | Hung builds wait until deadline; TUI cannot cancel. |
| **Fix** | `BuildHandle` / cancel token; destroy process; TUI Esc cancel. |
| **Test** | `sleep` fake build cancelled before timeout. |

### P3-006 — Missing productivity surfaces

| Field | Value |
|-------|-------|
| **Files** | TUI |
| **Severity** | **medium** |
| **Problem** | No fuzzy finder, find-in-files, command palette, outline, explorer CRUD, rich status bar. |
| **Fix** | Implement keyboard-first overlays without per-frame buffer clones. |
| **Test** | Unit tests for search/fuzzy ranking; palette command dispatch. |

### P3-007 — Dead ExternalProcessLanguageClient stub

| Field | Value |
|-------|-------|
| **Severity** | **medium** |
| **Problem** | Returns empty success lists; unused by host. |
| **Fix** | Remove or mark deprecated throwing UnsupportedOperationException. |

### P3-008 — Session restore is path-only / fragile JSON

| Field | Value |
|-------|-------|
| **Severity** | **medium** |
| **Problem** | String-split JSON; no cursor; malformed session may open wrong paths. |
| **Fix** | Robust parse; ignore bad entries; write session on quit from TUI. |

### P3-009 — Quality tool integrations absent

| Field | Value |
|-------|-------|
| **Severity** | **low** |
| **Fix** | Optional discovery runners; skip when missing. |

---

## Implementation order

1. Document safety (P3-001–003, 008)  
2. TUI LSP bridge + problems/completion (P3-004)  
3. Productivity overlays (P3-006)  
4. Build cancel + streaming (P3-005)  
5. Cleanup stub + quality hooks + docs/CI (P3-007, 009)
