# LSP Setup and Troubleshooting

## Primary server

Eclipse **JDT Language Server** (jdtls). Point Desk at a launchable command:

```bash
export DESK_JAVA_LS="/path/to/jdtls"   # or a wrapper script that starts jdtls on stdio
```

Without this, Desk falls back to limited local intelligence (javac / local format) and shows a clear toast that the language server is unavailable.

## TUI capabilities (Phase 4)

| Feature | Command / key | Notes |
|---------|---------------|-------|
| Completion | Ctrl+Space | Async; needs jdtls |
| Definition / references | F12 / Shift+F12 | |
| Hover | `:hover` | |
| Outline / symbols | `:outline` | |
| Format | `:format` | LSP or local formatter |
| Rename | `:rename <name>` | Confirm before applying multi-file edits |
| Code actions | `:fix` then `:codeaction N` | Confirm before apply |
| Signature help | requested with LSP | Toast when available |
| Cancel in-flight | document switch/close | `$/cancelRequest` |

## Workspace edits

Incoming `workspace/applyEdit` is acknowledged; application goes through a confirmation overlay so destructive multi-file edits are never silent. Document versions are checked before apply where the client tracks them. Unsaved buffers are preserved.

## Troubleshooting

| Symptom | Check |
|---------|-------|
| "Language server unavailable" | `DESK_JAVA_LS`, PATH, executable bit |
| No completions | Server started? Java project opened? `didOpen` after save |
| Stale diagnostics | Close/reopen file; restart Desk; check classpath after dependency change |
| Rename empty | Server capability; cursor on symbol; writable workspace |
| Crash of LS | Desk keeps running; restart LS / Desk; check LS stderr log |

## Tests

| Suite | When it runs |
|-------|----------------|
| `ContentLengthTransportTest` | Always (`./mvnw test`) |
| `MockLanguageServerIntegrationTest` | Always — in-process mock JSON-RPC (complete/hover/rename/format/shutdown) |
| `LiveJdtLsIntegrationTest` | Only if `DESK_JDTLS_IT=1` and `DESK_JAVA_LS` set |

Helper: `.github/scripts/provision-jdtls.sh` (pinned URL + SHA-256). Do **not** use the deprecated `fetch-jdtls.sh`.

## Honest limits

Live jdtls E2E against a real Spring Boot classpath is **not** in required CI. Mock protocol IT + TUI client unit tests cover framing and WorkspaceEdit apply logic.
