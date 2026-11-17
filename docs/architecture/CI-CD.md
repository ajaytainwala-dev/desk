# CI/CD Architecture

## Workflows

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `.github/workflows/ci.yml` | push to main/master, pull requests | Compile, test, static analysis, package |
| `.github/workflows/native-build.yml` | tags, releases | Native Image builds per OS/arch |
| `.github/workflows/portable-build.yml` | tags, releases | jlink portable runtime packages |
| `.github/workflows/release.yml` | version tags (`v*`) | Release verification, checksums, publishing |

## CI job structure (ci.yml)

1. **validate** — checkout, validate Maven Wrapper and project structure (ubuntu)
2. **build** (matrix: ubuntu, windows, macOS) — set up JDK, compile, unit tests, package, upload test reports on failure
3. **static-analysis** — compile with strict warnings, dependency vulnerability check (informational)

Jobs use explicit dependencies. One platform's failure does not hide another's. `fail-fast: false` on the build matrix.

## Security practices

- Third-party actions pinned to full commit SHAs
- Minimum permissions (`contents: read` by default, `contents: write` only for release publishing)
- Build and publishing jobs separated
- No release secrets exposed to untrusted pull requests
- No `pull_request_target` for untrusted code
- No credentials or personal access tokens stored in the repository
- GitHub-provided tokens and OIDC preferred

## Release flow

Tag (`v1.0.0`) → checkout exact commit → verify version/tag → run tests → build native artifacts → build portable artifacts → run smoke tests → generate checksums → generate release notes → publish GitHub release → upload artifacts.

Checksums (SHA256) are generated for every release file. Only targets that built and passed verification are published.
