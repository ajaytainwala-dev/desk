# CI/CD Architecture

## Workflows

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `.github/workflows/ci.yml` | push / pull requests | Compile, test, static analysis, package |
| `.github/workflows/native-build.yml` | tags, releases | Native Image builds per OS/arch |
| `.github/workflows/release.yml` | version tags (`v*`) or manual dispatch | Build installers/binaries and **publish a GitHub Release** with downloadable assets |
| `.github/workflows/sync-release-links.yml` | release published / after Release | Updates `website/public/latest-release.json` and commits to default branch |
| `.github/workflows/pages.yml` | push to `main` (`website/**`) or manual | Build marketing site and deploy to **GitHub Pages** |

Build jobs cache Maven dependencies (`setup-java` `cache: maven`) and Rust compile artifacts (`Swatinem/rust-cache`) to cut compile time on repeat runs.

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

Tag (`v1.0.0`) **or** Actions → Release → Run workflow with version input → validate version → build portable / Rust TUI / Windows installer / CLI setup (required) → build GraalVM native images (optional, `continue-on-error`) → generate checksums → publish GitHub Release with assets.

Checksums (SHA256) are generated for every release file. GraalVM native failures do not block publishing; core installers and Desk TUI binaries still ship.

### Trigger a release

```bash
git tag v0.1.0
git push origin v0.1.0
```

Or: GitHub → Actions → **Release** → Run workflow → enter `0.1.0`.

### Website release links

After a release publishes, **Sync release links** writes resolved asset URLs into `website/public/latest-release.json` and commits to the default branch. The marketing site prefers the live GitHub Releases API and falls back to that file when the API is unavailable.

### GitHub Pages

**Deploy website (GitHub Pages)** builds `website/` with `BASE_PATH=/<repo>/` and deploys via Actions.

One-time repo setup: **Settings → Pages → Build and deployment → Source: GitHub Actions**.

Site URL: `https://<owner>.github.io/<repo>/` (for this project: https://ajaytainwala-dev.github.io/desk/).
