# Desk v0.1.0 — Release Readiness

**Status:** **CONDITIONAL GO** — merge PR stack after human review; tag/publish only when release assets exist.  
**Date:** 2026-11-21 (Asia/Calcutta)  
**Do not** begin Phase 7 from this document.

## Decision summary

| Gate | Result |
|------|--------|
| Phase 1–5 on PR #4 | Ready to merge after human ack of manual review |
| Phase 6 on PR #5 | Ready after #4 lands + rebase/CI re-green |
| Critical/high security | Fixed on release-closure commits (see audit) |
| Automated Copilot/Codex approval | **Unavailable** (quota) — **not** counted as review |
| Tag `v0.1.0` / GitHub Release | **NO-GO until** merge + release workflow assets verified |
| Auto-merge | **Disabled / not performed** |

## v0.1.0 scope (what ships)

In scope after both PRs merge to `main`:

- Java **25** primary LTS baseline (Desk build/CI); workspace JDK pins for other installed JDKs.
- Rust Ratatui TUI: multi-tab editor, save safety (`ReloadConflict` / `DiskDeleted`), LSP-driven edits with workspace-root containment, build/test job runner with cancel.
- Workspace management + Maven / Gradle / javac build orchestration.
- Live **Eclipse JDT LS** integration (pinned **1.57.0**) with required CI job; CLI LSP commands; TUI completion/hover/diagnostics/refactor paths when LS is available.
- Managed Temurin JDK provisioner (checksum **fail-closed**, zip/tar slip checks).
- Quality runners (fmt/check) and process-tree cancellation helpers.
- **JDI/JDWP** debugging via `desk debug run` + `JdiDebugger` E2E (not a TUI debug panel).
- DAP **transport + initialize**; external `DESK_JAVA_DAP` launch **not** claimed complete.
- Cross-platform CI: Linux/macOS/Windows Rust; Linux jlink portable packaging in CI; release workflow for tagged builds.

Explicitly **out of** v0.1.0 (do not advertise as done):

- Interactive TUI debug panel (F5 remains **Run**).
- Full external DAP adapter launch E2E.
- Spring Boot HTTP resolve/run/debug E2E.
- True PTY terminal (`docs/terminal/PTY_DECISION.md`).
- `desk test --watch` (honest reject).
- Phase 7+ roadmap items.

## Supported operating systems

| OS | Build / CI | Notes |
|----|------------|--------|
| Linux (ubuntu-latest) | Full Maven + Rust + jdtls-it + jdi-debug-it + portable | Primary verification host |
| macOS (macos-latest) | Rust CI + release portable matrix | Interactive TUI not manually exercised in this closure |
| Windows (windows-latest) | Rust CI + Windows portable/installer in release.yml | `taskkill /T` helper present; dedicated process-tree IT pending |

## Installation (from source — verified)

Requires **Java 25+** and **Rust stable**.

```bash
git clone https://github.com/ajaytainwala-dev/desk.git
cd desk
./mvnw clean verify
cargo build --release --manifest-path modules/desk-tui/Cargo.toml
```

```bash
java -jar modules/desk-cli/target/desk-cli-0.1.0-SNAPSHOT.jar help
```

Optional: set `DESK_JAR` / `DESK_TUI` for the TUI ↔ CLI pairing.

## Installation (release downloads — pending publish)

After `v0.1.0` is tagged and the **Release** workflow finishes with required assets:

| Asset | Purpose |
|-------|---------|
| `Desk-Setup-<version>.exe` | Windows GUI installer |
| `desk-cli-setup-windows.ps1` | Windows CLI setup |
| `desk-cli-setup.sh` | macOS / Linux CLI setup |
| `desk-portable-<os>.zip` | Portable zip |
| `desk-tui-*` | Standalone TUI binaries |

```bash
curl -fsSL https://github.com/ajaytainwala-dev/desk/releases/latest/download/desk-cli-setup.sh | bash
```

**Do not** publish the GitHub release if required portable/setup assets are missing. GraalVM native jobs are `continue-on-error` and must not be treated as required.

Website: [https://desk.ajaytainwala.dev](https://desk.ajaytainwala.dev) — download mapping must match assets that actually exist on the release.

## CLI workflows (implemented)

```text
desk / desk . / desk open <path>
desk new <name>                 # default Java 25 templates
desk workspace …
desk java list|detect|current|use|info|doctor|install|remove|policy
desk lsp …                      # diagnose / complete / hover / …
desk build | clean | test       # Maven / Gradle / javac
desk fmt | check | compile | run
desk debug run --main <Class> --bp <line> [--src …] [--classpath …]
desk git …
desk doctor | version | help
```

## Workspace and project support

- Folder workspaces with `.desk/` pins (JDK).
- Maven multi-module and Gradle detection for build/test.
- Document store with dirty-buffer awareness; external mtime/delete conflict prompts in the TUI.

## Live JDT LS

- Pin: **1.57.0** (`jdt-language-server-1.57.0-202602261110.tar.gz`).
- Provision: `bash .github/scripts/provision-jdtls.sh` or `JdtLsProvisioner` (SHA-256 verified).
- Deprecated: `.github/scripts/fetch-jdtls.sh` (exits 2 — unsafe unverified download).
- Env: `DESK_JAVA_LS` launcher; CI sets `DESK_JDTLS_CI=1` so skip = fail.
- Covered in live IT: initialize, didOpen, completion, hover, symbols, format, shutdown.
- Thin / not fully proven live: rename, codeAction, diagnostics-clear (mock IT + TUI paths exist).

## JDI debugging

```bash
desk debug run --main DebugDemo --bp 5 --src DebugDemo.java --classpath .
```

- Backend: JDK `jdk.jdi` / JDWP; listener bound to **127.0.0.1** when connector supports `localAddress`.
- E2E: `JdiDebuggerE2ETest` (compile with `-g`, breakpoint, stack, locals, continue).
- **Not** an interactive TUI debugger.

## DAP transport limitations

- Framing + `initialize` handshake implemented/tested.
- External adapter via `DESK_JAVA_DAP`: initialize-only — **no** claimed launch/continue UI.
- See `docs/debug/DAP_IMPLEMENTATION.md`.

## Packaging

- CI job packages Linux jlink portable artifact on PR/push.
- Tag/`workflow_dispatch` **Release** workflow builds multi-OS portable + Windows installer + optional natives.
- **v0.1.0 tag not created** as part of this readiness pass.

## Known limitations

See `docs/REMAINING_LIMITATIONS.md`. Highlights: no TUI debug panel; no Spring Boot HTTP E2E; no PTY; watch tests rejected; macOS portable historically weaker than Linux/Windows — verify assets on publish.

## Upgrade guidance

- From pre-workbench snapshots: expect Java **25** CI/toolchain; set `DESK_JAVA_LS` for full LSP.
- Managed JDK downloads now **require** Adoptium checksum availability (fail closed).
- LSP workspace edits outside the workspace root are rejected.
- After merge, prefer released portable/setup scripts over ad-hoc SNAPSHOT jars when available.

## Stacked merge plan (human-operated)

**Do not auto-merge.** Preserve stack: PR #5 bases on PR #4 head until #4 merges.

### 1) Merge PR #4 → `main`

- Branch: `cursor/desk-workbench-foundation-22b9`
- Scope: Phases 1–5 (+ release-closure security fixes for JDK checksum + workspace-edit containment)
- Preconditions: required CI green on **current head**; manual review recorded; no force-push unless necessary
- After merge: confirm `main` contains Phase 1–5 commits

### 2) Update PR #5 → rebase/merge onto new `main`

- Branch: `cursor/desk-phase-6-java-debug-22b9`
- Ensure diff vs `main` is **Phase 6 only** (+ Phase 6 security/docs)
- Resolve conflicts carefully; re-run full CI on new head
- Merge only when checks green + review ack

### 3) Post-merge verification

- Both PRs show **merged**
- Latest `main` CI green
- Intended commits present; no accidental Phase 7
- Run Release workflow / tag `v0.1.0` only when ready to attach assets
- Confirm website download links ↔ existing assets

## Manual review record

**Automated reviews:** Copilot / Codex previously failed on quota — **not approvals**. Fresh automated review was unavailable; this closure uses a **documented manual review**.

| Item | Detail |
|------|--------|
| Scope | Full PR #4 changeset (Phases 1–5) + PR #5 (Phase 6) + security hotspots |
| Files inspected (security focus) | `JdkProvisioner.java`, `JdtLsProvisioner.java`, `ZipSafety` / tar extract paths, `fetch-jdtls.sh`, `provision-jdtls.sh`, `workspace_edit.rs`, `app.rs` (apply/prompt edit), `JdiDebugger.java`, `DapSession.java`, `ProcessTreeKiller.java`, `BuildHandle.java`, `GitService.java`, CI scripts |
| Findings fixed | See `docs/audit/SECURITY_REVIEW_v0.1.0.md` |
| Remaining accepted risks | JDWP still process-local trust model; Windows process-tree IT thin; release asset set unverified until tag; live jdtls rename/codeAction coverage thin |
| Reviewer | Cloud agent manual pass (release closure) — **human maintainer sign-off still required before merge** |

## Exact verification evidence

### Repository heads (fetched 2026-11-21)

| Ref | SHA |
|-----|-----|
| `origin/main` | `4c69dbb77187f6f8c16dd1bc5e643c2fce821b5d` |
| PR #4 head (`cursor/desk-workbench-foundation-22b9`) | `75a47fa` (security: JDK checksum + workspace-edit clamp) |
| PR #5 head (`cursor/desk-phase-6-java-debug-22b9`) | `3faae67bf03d4de8e3d5b42d954bae76d151e238` (release-closure + security) |

### CI (must match **current** head after security commits)

| PR | Known green run (pre-closure head) | Checks |
|----|--------------------------------------|--------|
| #4 | [44930685844](https://github.com/ajaytainwala-dev/desk/actions/runs/44930685844) on `091f7f4` | 18/18 success |
| #5 | [44932291763](https://github.com/ajaytainwala-dev/desk/actions/runs/44932291763) on `d6824e9` | 22/22 success |

**After security/docs commits land, re-verify CI on the new SHAs — do not treat the runs above as evidence for newer commits.**

### Local (Linux agent, release-closure)

| Check | Result |
|-------|--------|
| `./mvnw -DskipITs test package` | SUCCESS |
| `./mvnw -pl modules/desk-java -am -Dtest=JdiDebuggerE2ETest,DapTransportTest -Dsurefire.failIfNoSpecifiedTests=false test` | SUCCESS |
| `cargo fmt` / `cargo clippy --all-targets -- -D warnings` | PASS |
| `cargo test` (desk-tui) | 35 passed |
| `cargo build --release` | SUCCESS |
| Live `LiveJdtLsIntegrationTest` this closure pass | **Not re-run** in final doc write — last proven on Phase 6 CI `jdtls-it` / prior agent run; disclose as CI-dependent |
| Windows/macOS interactive TUI | **Not manually tested** here — rely on CI Rust jobs |
| GitHub Release `v0.1.0` assets | **Not published** |

### Mergeability (API, pre-push)

- PR #4: `mergeable=true`, `mergeable_state=clean`, open, **not merged**
- PR #5: `mergeable=true`, `mergeable_state=clean`, base = foundation branch, open, **not merged**

## GO / NO-GO

| Action | Decision |
|--------|----------|
| Merge PR #4 after human review + CI green on security head | **GO (human)** |
| Merge PR #5 after rebase onto merged main + CI green | **GO (human)** |
| Tag and publish `v0.1.0` now | **NO-GO** (main not updated; assets not built/verified) |
| Claim full TUI debugger / DAP launch / Spring Boot E2E | **NO-GO** (not implemented) |
| Begin Phase 7 | **NO-GO** |
