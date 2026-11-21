# Phase 4 Audit — Verify Phase 3 Claims Against Code

**Branch audited:** `cursor/desk-workbench-foundation-22b9`  
**Verified commit:** `ccf38b1474dcdfcf8c2d9ff58bb261b219ca2936`  
**Date:** 2026-11-21  
**Method:** Code inspection of `desk-core`, `desk-java`, `desk-tui`, `.github/workflows` against `docs/PHASE_3_RESULTS.md` / `docs/audit/PHASE_3_AUDIT.md` (claims not trusted blindly).  
**Sources:** `docs/PHASE_3_RESULTS.md`, `docs/audit/PHASE_3_AUDIT.md`, `docs/REMAINING_LIMITATIONS.md`

## Verdict summary

| # | Claim / gap | Status | Phase 4 priority |
|---|-------------|--------|------------------|
| 1 | DocumentStore `saveAs` / `saveAll` untitled reporting | **FIXED** | — |
| 2 | TUI dirty close/quit confirm | **FIXED** | — |
| 3 | `disk_mtime` conflict detection | **PARTIAL** | P4 medium |
| 4 | TUI `lsp.rs` Content-Length client | **PARTIAL** | P4 high |
| 5 | `BuildHandle` cancel + TUI Esc | **PARTIAL** | P4 high |
| 6 | Checkstyle / PMD / SpotBugs | **PARTIAL** | P4 low–medium |
| 7 | `JdkProvisioner` download | **OPEN** | P4 medium |
| 8 | Windows portable in `release.yml` | **FIXED** | — (docs stale) |
| 9 | DAP / debug | **OPEN** | P4 low (deferred) |
| 10 | Spring Boot / multi-module fixtures | **OPEN** | P4 medium |

---

## 1. DocumentStore.saveAs / saveAll untitled reporting — **FIXED**

**Phase 3 claim:** `saveAs` with path reindexing; `saveAll` returns untitled/errors (never silent drop).

**Evidence:**
- `saveAs` reindexes `pathIndex` and writes atomically via `FileSystem.writeString`:

```94:117:modules/desk-core/src/main/java/desk/core/document/DocumentStore.java
  public synchronized Document saveAs(String id, Path newPath) {
    // ...
    pathIndex.put(normalized, id);
    return dd;
  }
```

- `saveAll` collects `unsavedUntitled` + `errors`; never silently drops dirty untitled:

```124:154:modules/desk-core/src/main/java/desk/core/document/DocumentStore.java
  public synchronized SaveAllResult saveAll() {
    // ...
    if (doc.path() == null) {
      unsavedUntitled.add(doc.id());
      continue;
    }
```

- Tests: `DocumentStoreTest.untitledSaveAsPersistsAndIndexes`, `saveAllReportsUntitled`, `saveAsReindexesRename` (`modules/desk-core/src/test/java/desk/core/document/DocumentStoreTest.java:28-71`).
- TUI mirror: `save_all_tabs` warns on untitled (`modules/desk-tui/src/app.rs:908-948`, test `save_all_reports_untitled` at `:3067`).

**Phase 4:** No work required for this item.

---

## 2. TUI dirty close/quit confirm — **FIXED**

**Phase 3 claim:** Confirm overlays for dirty close/quit; force paths for discard.

**Evidence:**
- Close prompts when modified:

```836:847:modules/desk-tui/src/app.rs
    pub fn request_close_current_file(&mut self) {
        self.sync_active_tab();
        if self.buffer.modified {
            self.overlay = Overlay::Confirm {
                title: "Unsaved changes".into(),
                message: "Close tab and discard unsaved edits?".into(),
                action: ConfirmAction::CloseTab,
            };
```

- Quit prompts across any dirty tab (`request_quit` at `app.rs:2475-2488`).
- Confirm Yes/No + Esc (`confirm_yes` / `confirm_no` at `app.rs:2490-2537`; `input.rs:994-995`).
- Test: `dirty_close_requests_confirmation` (`app.rs:3054-3064`).

**Phase 4:** No work required for this item.

---

## 3. disk_mtime conflict — **PARTIAL**

**Phase 3 claim:** Store mtime on load/save; warn on save/reload conflict.

**What works:**
- Field + detection on buffer (`buffer.rs:149-150`, `disk_changed_externally` at `:209-221`, refresh on `save_as` at `:257-263`).
- Save path prompts overwrite confirm (`app.rs:969-977`, `save_all_tabs` conflict at `:913-918`).
- Test: `disk_mtime_conflict_detected` (`app.rs:3077-3088`).

**Gap:**
- `ConfirmAction::ReloadConflict` is defined (`overlay.rs:10`) and handled in `confirm_yes` (`app.rs:2510-2514`) but **never assigned** anywhere — no focus/tick/reload prompt when disk changes under an open buffer.
- No periodic/on-focus mtime check that surfaces reload UI.

**Phase 4 priority:** **medium** — wire reload conflict on tab focus / `:e` / tick; cover with test that sets overlay to `ReloadConflict`.

---

## 4. TUI lsp.rs Content-Length client — **PARTIAL**

**Phase 3 claim:** Async Rust Content-Length client with completions, goto def/refs, hover, outline, diagnostics.

**What exists:**
- Module header + Content-Length framing (`modules/desk-tui/src/lsp.rs:1-5`, `write_message`/`read_message` at `:657-683`).
- Implemented request surface: `did_open` / `did_change` / `request_complete` / `request_hover` / `request_definition` / `request_references` / `request_symbols` (`lsp.rs:190-276`); methods `textDocument/completion|hover|definition|references|documentSymbol` (+ didOpen/Change/Save/Close).

**Missing in TUI `lsp.rs` (no symbols / no method strings):**
| Method | In TUI `lsp.rs`? | In Java `LspSession`? |
|--------|------------------|------------------------|
| `textDocument/rename` | **No** | Yes (`LspSession.java:184-190`) |
| `textDocument/codeAction` | **No** | Yes (`:192-216`) |
| `textDocument/formatting` | **No** | Yes (`:163-182`) |
| `workspace/applyEdit` | **No** | **No** (nowhere in tree) |
| `textDocument/signatureHelp` | **No** | **No** (nowhere in tree) |

**Phase 4 priority:** **high** — add rename / codeAction / formatting + workspace-edit apply path in TUI; signatureHelp secondary. Align with `docs/PHASE_3_RESULTS.md` roadmap item 2 and `docs/REMAINING_LIMITATIONS.md` §2.

---

## 5. BuildHandle cancel — TUI Esc — **PARTIAL**

**Phase 3 claim:** `BuildHandle` with cancel + live output; TUI Esc cancel (P3-005).

**What exists:**
- `BuildHandle.cancel()` destroys process (`modules/desk-java/src/main/java/desk/java/build/BuildHandle.java:68-82`).
- `BuildService.start` returns handle (`BuildService.java:54-78`).
- Unit test cancels hung `sleep` (`BuildHandleTest.java:17-24`).

**Gaps:**
- TUI does **not** use `BuildHandle` / `BuildService.start`. It runs local `Command` jobs (`app.rs:1506-1594`) and cancels via `stop_job` → `child.kill()` (`app.rs:1696-1705`, `Job::stop` ~`:205-209`).
- Cancel keybinding is **F7** (and Ctrl+C in terminal focus), **not Esc**:
  - `input.rs:282-284` → `stop_job`
  - Esc in terminal only unfocuses (`input.rs:437-440`); Esc elsewhere closes overlays / leaves insert.
- Matches honest note in `docs/REMAINING_LIMITATIONS.md` §7.

**Phase 4 priority:** **high** — bridge Maven/Gradle via cancellable handle (or CLI), stream live output into TUI panel, bind Esc (when job running / build panel focused) to cancel.

---

## 6. CheckstyleRunner / PMD / SpotBugs — **PARTIAL**

**Phase 3 claim:** `CheckstyleRunner` discovery — skips when tool absent; Phase 4 for PMD/SpotBugs.

**Evidence:**
- `CheckstyleRunner` implements `QualityTool`, `available()` / empty findings when missing (`modules/desk-java/src/main/java/desk/java/quality/CheckstyleRunner.java:19-74`).
- Quality package contains only: `CheckstyleRunner.java`, `QualityTool.java`, `QualityFinding.java` — **no** `PmdRunner` / SpotBugs / Error Prone classes.

**Phase 4 priority:** **low–medium** — optional PMD / SpotBugs / Error Prone runners mirroring Checkstyle skip-when-absent pattern.

---

## 7. JdkProvisioner download — **OPEN** (still NOT_IMPLEMENTED)

**Claim / expected:** Managed download still not implemented after consent.

**Evidence:**

```131:148:modules/desk-java/src/main/java/desk/java/JdkProvisioner.java
  public static InstallResult install(InstallRequest request) {
    // ...
    return new InstallResult(InstallStatus.NOT_IMPLEMENTED, planned,
        "Managed JDK download is not implemented yet (consent recorded, nothing downloaded).\n"
```

`InstallStatus.NOT_IMPLEMENTED` enum at `:117-121`.

**Phase 4 priority:** **medium** — implement consented Temurin download + checksum verify, or keep explicit NOT_IMPLEMENTED but document as intentional until then (`REMAINING_LIMITATIONS` §3).

---

## 8. Windows portable in release.yml — **FIXED**

**Phase 3 roadmap / limitations claimed:** “Windows portable jlink artifact” remaining; `REMAINING_LIMITATIONS` §9 said release still focuses Linux/macOS portable.

**Code reality — Windows portable is present:**
- Job `build-windows-installer` on `windows-latest` runs `packaging/jlink/build-jlink.ps1`, zips `dist/desk-portable-windows.zip`, uploads with installers (`.github/workflows/release.yml:209-261`).
- Linux/macOS portable matrix remains separate (`release.yml:123-161`).

**Phase 4:** Update `docs/REMAINING_LIMITATIONS.md` §9 (stale). Optional: harden Windows portable CI (job is `continue-on-error: true` at `:213`).

---

## 9. DAP / debug — **OPEN**

**Evidence:** No DAP client, debug adapter, JDWP, or breakpoint runtime in Java/Rust sources. Mentions are roadmap/docs only (`docs/roadmap/IMPLEMENTATION_ROADMAP.md`, `PROPOSED_ARCHITECTURE.md`) plus highlighter keyword `"debugger"` (`modules/desk-tui/src/highlighter.rs:314`).

**Phase 4 priority:** **low / deferred** — explore DAP after LSP rename/format and build cancel land (per roadmap “split editor + debugger adapter protocol exploration”).

---

## 10. Spring Boot / multi-module fixtures — **OPEN**

**What exists:**
- Detector supports multi-module types (`ProjectType.MAVEN_MULTI_MODULE` / `GRADLE_MULTI_MODULE`; `ProjectDetector.java:49`, `:63`, module parsers `:93-125`).
- Tests only create single-module Maven/Gradle/temp trees (`ProjectDetectorTest.java:17-55`) — **no** multi-module `<modules>` / `include` assertion, **no** Spring Boot fixtures, **no** `fixtures/` tree in repo.

**Gaps:**
- Zero `spring` / `spring-boot` matches in `*.java` / `*.xml` / project docs content searched.
- No checked-in sample multi-module or Spring Boot workspace for E2E/CI.

**Phase 4 priority:** **medium** — add temp-dir or `testdata/` multi-module fixtures + detector tests; optional Spring Boot sample for project-kind / classpath / LSP E2E later.

---

## Phase 3 claim accuracy (meta)

| Phase 3 Results statement | Audit |
|---------------------------|-------|
| Document safety (saveAs, saveAll, dirty confirm, mtime, session) | Mostly accurate; mtime **reload** UI incomplete |
| TUI LSP Content-Length client + core nav/complete | Accurate for shipped methods; rename/actions/format correctly deferred |
| `BuildHandle` + cancel test | Accurate for Java; TUI Esc wiring overstated vs F7 local jobs |
| CheckstyleRunner only | Accurate |
| Windows portable as remaining | **Stale** — Windows portable zip already in `release.yml` |

---

## Recommended Phase 4 implementation order

1. **P4-001** — TUI LSP rename / codeAction / formatting + applyEdit handling (**high**)
2. **P4-002** — Wire Maven/Gradle cancellable builds into TUI; Esc (or documented) cancel + streaming panel (**high**)
3. **P4-003** — External-change reload prompt (use dead `ReloadConflict`) (**medium**)
4. **P4-004** — Multi-module (+ optional Spring) fixtures/tests (**medium**)
5. **P4-005** — Managed JDK download or keep NOT_IMPLEMENTED with clearer UX (**medium**)
6. **P4-006** — PMD / SpotBugs / Error Prone optional runners (**low–medium**)
7. **P4-007** — Doc fix: Windows portable already shipped; DAP exploration last (**low**)

See also: `docs/REMAINING_LIMITATIONS.md` (update §7–§9 after this audit).

---

## Post-implementation status (Phase 4 work on this branch)

After closing the gaps above:

| # | Item | Outcome |
|---|------|---------|
| 3 | disk_mtime ReloadConflict | Still **partial** (save conflict works; proactive overlay not fully wired) |
| 4 | TUI LSP rename/format/codeAction/applyEdit/signatureHelp | **Done** in `modules/desk-tui/src/lsp.rs` + `workspace_edit.rs` |
| 5 | Esc cancel | **Done** for TUI jobs (Esc in normal/insert when job running); BuildHandle stream UI still next |
| 6 | PMD / SpotBugs / Error Prone | **Done** (optional runners / hints) |
| 7 | JdkProvisioner download | **Done** for Temurin with consent + checksum when available |
| 8 | Windows portable | Confirmed present in `release.yml` |
| 9 | DAP | Design + honest stub (`DapSession`) |
| 10 | Fixtures | plain / maven / gradle groovy+kotlin / multi-maven / multi-gradle / spring-boot / lib |

See `docs/PHASE_4_RESULTS.md` for executed test results.
