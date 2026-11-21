# Security Review — Desk v0.1.0 release closure

**Date:** 2026-11-21 (Asia/Calcutta)  
**Scope:** PR #4 (Phases 1–5) + PR #5 (Phase 6) changed code, focusing on exploitable risk and data loss.  
**Automated review:** Copilot/Codex quota failures — **not** treated as approvals.

## Method

1. Inventory process spawn, archive extract, path joins, network download, LSP/DAP I/O, and document save paths.
2. Prefer concrete exploitability over speculative style nits.
3. Fix critical/high before release readiness GO for merge.

## Findings

### H1 — Unverified JDT LS fetch script (High) — **Fixed**

- **Was:** `.github/scripts/fetch-jdtls.sh` downloaded arbitrary `DESK_JDTLS_URL` and extracted with `tar` without checksum or path containment.
- **Fix:** Script disabled (`exit 2`); docs point to `provision-jdtls.sh` / `JdtLsProvisioner` (pinned URL + SHA-256 + slip checks).

### H2 — Managed JDK checksum fail-open (High) — **Fixed**

- **Was:** `JdkProvisioner.fetchChecksum` returned `null` on failure; `downloadFile` skipped verify when checksum null → MITM/corrupt archive risk.
- **Fix:** Refuse download without SHA-256; checksum fetch failures throw; `downloadFile` requires non-blank expected hash.

### H3 — LSP workspace edits outside workspace (High / data integrity) — **Fixed**

- **Was:** `apply_workspace_edit_plan` / confirm prompt applied any `file://` URI without containing paths under workspace root; confirm omitted path list.
- **Fix:** `filter_plan_to_workspace` + `path_is_inside_workspace`; reject outsiders; confirm lists up to five paths; unit test added.

### M1 — JDWP bind address (Medium) — **Hardened**

- **Was:** Rely on connector defaults (typically loopback, but not explicit).
- **Fix:** Set `localAddress=127.0.0.1` when argument present on SocketListen connector.

## Reviewed areas — no critical issue found (accepted residual risk)

| Area | Notes |
|------|--------|
| Shell injection | Process builders use argv lists; GitService rejects flag-like paths |
| Zip/tar slip (provisioners) | `ZipSafety` / path checks in JDK + JDT LS extractors |
| Process ownership / orphans | `BuildHandle` + `ProcessTreeKiller`; Windows IT thin |
| Unbounded buffers | LSP framing uses Content-Length; large-file SLA not a security gate |
| Save / overwrite | Atomic save-as paths; ReloadConflict/DiskDeleted for external changes |
| LSP/DAP hangs | Timeouts present on key paths; residual hang risk accepted for v0.1.0 |
| Credentials in logs | Git helpers documented not to log secrets; no new secret sinks found |
| Env exposure | No intentional dumping of process env to untrusted sinks in reviewed paths |

## Remaining accepted risks

1. Compromised language server process can still propose many in-workspace edits (user confirm mitigates; containment stops escape).
2. JDWP is a powerful debug protocol by design — loopback bind reduces remote exposure; local malware can still attach.
3. Release download scripts (`curl | bash`) inherit industry trust-on-first-use risk — publish only from GitHub Releases after asset verification.
4. Windows process-tree kill coverage lacks a dedicated IT.

## Sign-off

| Role | Status |
|------|--------|
| Agent manual security pass | Complete — highs fixed |
| Human maintainer | **Required** before merge/tag |
