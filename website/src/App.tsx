import { motion, useReducedMotion } from "framer-motion";
import { useEffect, useMemo, useState } from "react";
import "./App.css";
import {
  DOWNLOAD_OPTIONS,
  detectPlatform,
  fetchLatestRelease,
  formatBytes,
  latestReleasePageUrl,
  releasesPageUrl,
  resolveDownload,
  type DownloadKind,
  type LatestRelease,
  type PlatformId,
} from "./releases";

const PLATFORMS: { id: PlatformId; label: string }[] = [
  { id: "windows", label: "Windows" },
  { id: "macos", label: "macOS" },
  { id: "linux", label: "Linux" },
];

const FEATURES = [
  {
    title: "Terminal editor",
    body: "A focused Rust TUI — open files fast, format and check Java without leaving the keyboard.",
  },
  {
    title: "Format & check",
    body: "desk fmt and desk check catch style and compile errors before you push.",
  },
  {
    title: "Compile & run",
    body: "Lightweight compile and run helpers via the JDK — no heavy IDE required.",
  },
];

const COMMANDS: { cmd: string; desc: string }[] = [
  { cmd: "desk", desc: "Launch the TUI editor" },
  { cmd: "desk fmt Main.java", desc: "Format Java in place" },
  { cmd: "desk check Main.java", desc: "Report compile diagnostics" },
  { cmd: "desk compile Main.java", desc: "Compile with javax.tools" },
  { cmd: "desk run Main", desc: "Run a compiled class" },
];

function recommendedKind(platform: PlatformId): DownloadKind {
  if (platform === "windows") return "windows-gui";
  return "cli-setup";
}

function installSnippet(
  platform: PlatformId,
  release: LatestRelease | null,
): string {
  const tag = release?.tag ?? "latest";
  if (platform === "windows") {
    return `# PowerShell — CLI setup from latest release
irm https://github.com/ajaytainwala-dev/javaproject/releases/latest/download/desk-cli-setup-windows.ps1 | iex

# Or download Desk-Setup-${tag.replace(/^v/, "")}.exe from the release page`;
  }
  return `# curl | bash — installs Desk CLI for your architecture
curl -fsSL https://github.com/ajaytainwala-dev/javaproject/releases/latest/download/desk-cli-setup.sh | bash`;
}

export default function App() {
  const reduceMotion = useReducedMotion();
  const [platform, setPlatform] = useState<PlatformId>("linux");
  const [release, setRelease] = useState<LatestRelease | null>(null);
  const [releaseError, setReleaseError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setPlatform(detectPlatform());
  }, []);

  useEffect(() => {
    const ac = new AbortController();
    setLoading(true);
    fetchLatestRelease(ac.signal)
      .then((r) => {
        setRelease(r);
        setReleaseError(null);
      })
      .catch((err: unknown) => {
        if (ac.signal.aborted) return;
        setRelease(null);
        setReleaseError(
          err instanceof Error ? err.message : "Could not load releases",
        );
      })
      .finally(() => {
        if (!ac.signal.aborted) setLoading(false);
      });
    return () => ac.abort();
  }, []);

  const options = useMemo(
    () => DOWNLOAD_OPTIONS.filter((o) => o.platforms.includes(platform)),
    [platform],
  );

  const primaryHref = useMemo(() => {
    const kind = recommendedKind(platform);
    const asset = resolveDownload(release, kind, platform);
    if (asset) return asset.url;
    const zip = resolveDownload(release, "portable-zip", platform);
    if (zip) return zip.url;
    return release ? latestReleasePageUrl(release.tag) : releasesPageUrl();
  }, [platform, release]);

  const fade = reduceMotion
    ? {}
    : {
        initial: { opacity: 0, y: 18 },
        animate: { opacity: 1, y: 0 },
        transition: { duration: 0.7, ease: [0.22, 1, 0.36, 1] as const },
      };

  return (
    <div className="site">
      <div className="atmosphere" aria-hidden="true">
        <div className="atmosphere__grid" />
        <div className="atmosphere__orb" />
      </div>

      <header className="nav">
        <a className="nav__mark" href="#top">
          Desk
        </a>
        <nav className="nav__links" aria-label="Primary">
          <a href="#features">Features</a>
          <a href="#downloads">Downloads</a>
          <a href="#commands">Commands</a>
          <a className="nav__cta" href="#downloads">
            Get Desk
          </a>
        </nav>
      </header>

      <main id="top">
        <section className="hero">
          <motion.div className="hero__copy" {...fade}>
            <h1 className="hero__brand">Desk</h1>
            <p className="hero__headline">Java workbench for the terminal.</p>
            <p className="hero__lede">
              Format, check, compile, and edit — a lightweight toolkit that
              stays out of your way.
            </p>
            <div className="hero__actions">
              <a className="btn btn--primary" href={primaryHref}>
                Download for{" "}
                {PLATFORMS.find((p) => p.id === platform)?.label ?? "your OS"}
              </a>
              <a className="btn btn--ghost" href="#downloads">
                All installers
              </a>
            </div>
            <p className="hero__meta">
              {loading && "Checking latest release…"}
              {!loading && release && (
                <>
                  Latest{" "}
                  <strong>{release.tag}</strong>
                  {release.publishedAt
                    ? ` · ${new Date(release.publishedAt).toLocaleDateString(undefined, {
                        year: "numeric",
                        month: "short",
                        day: "numeric",
                      })}`
                    : null}
                  {" · "}
                  <a href={release.htmlUrl}>Release notes</a>
                  {" · links refresh on every new release"}
                </>
              )}
              {!loading && releaseError && (
                <>
                  {releaseError}.{" "}
                  <a href={releasesPageUrl()}>Browse releases</a>
                </>
              )}
            </p>
          </motion.div>

          <div className="hero__visual" aria-hidden="true">
            <div className="terminal">
              <div className="terminal__chrome">
                <span className="terminal__dot" />
                <span className="terminal__dot" />
                <span className="terminal__dot" />
                <span className="terminal__title">desk — Main.java</span>
              </div>
              <div className="terminal__body">
                <p className="terminal__line">
                  <span className="terminal__prompt">$</span>{" "}
                  <span className="terminal__cmd">desk check Main.java</span>
                </p>
                <p className="terminal__line terminal__out">
                  Main.java:12: error: cannot find symbol
                </p>
                <p className="terminal__line terminal__err">
                  {"  "}symbol: variable cout
                </p>
                <p className="terminal__line">
                  <span className="terminal__prompt">$</span>{" "}
                  <span className="terminal__cmd">desk fmt Main.java</span>
                </p>
                <p className="terminal__line terminal__out">
                  Formatted <span className="terminal__hl">Main.java</span>
                </p>
                <p className="terminal__line">
                  <span className="terminal__prompt">$</span>{" "}
                  <span className="terminal__cmd">desk</span>
                  <span className="terminal__cursor" />
                </p>
              </div>
            </div>
          </div>
        </section>

        <section className="section" id="features">
          <p className="section__eyebrow">Why Desk</p>
          <h2 className="section__title">One toolkit. Stay in the terminal.</h2>
          <p className="section__lede">
            Built for quick Java loops — edit in a Ratatui TUI, then format,
            diagnose, and run without switching apps.
          </p>
          <div className="features">
            {FEATURES.map((f) => (
              <article className="feature" key={f.title}>
                <hr className="feature__rule" />
                <h3>{f.title}</h3>
                <p>{f.body}</p>
              </article>
            ))}
          </div>
        </section>

        <section className="section downloads" id="downloads">
          <p className="section__eyebrow">Downloads</p>
          <h2 className="section__title">Installers that stay current.</h2>
          <p className="section__lede">
            Every visit loads the newest GitHub release. Ship a tag — the
            buttons below pick up the new zip, CLI setup, and Windows installer
            automatically.
          </p>

          <div
            className="platform-tabs"
            role="tablist"
            aria-label="Choose platform"
          >
            {PLATFORMS.map((p) => (
              <button
                key={p.id}
                type="button"
                role="tab"
                aria-selected={platform === p.id}
                className={`platform-tab${platform === p.id ? " is-active" : ""}`}
                onClick={() => setPlatform(p.id)}
              >
                {p.label}
              </button>
            ))}
          </div>

          <div className="release-banner">
            {loading && <span>Fetching latest release assets…</span>}
            {!loading && release && (
              <>
                <span>
                  Serving assets from <strong>{release.tag}</strong>
                </span>
                <a href={release.htmlUrl}>View on GitHub</a>
              </>
            )}
            {!loading && releaseError && (
              <span className="release-banner--error">
                {releaseError}. Downloads will open the releases page until a
                release is published.
              </span>
            )}
          </div>

          <div className="download-grid">
            {options.map((opt) => {
              const asset = resolveDownload(release, opt.kind, platform);
              const recommended = opt.kind === recommendedKind(platform);
              const href = asset?.url ?? (release ? release.htmlUrl : releasesPageUrl());
              const label = asset
                ? `Download ${opt.title}`
                : release
                  ? "Open release page"
                  : "Browse releases";

              return (
                <article
                  key={opt.kind}
                  className={`download-card${recommended ? " is-recommended" : ""}`}
                >
                  <div className="download-card__head">
                    <h3>{opt.title}</h3>
                    {recommended ? (
                      <span className="download-card__badge">Recommended</span>
                    ) : null}
                  </div>
                  <p>{opt.description}</p>
                  <div className="download-card__meta">
                    {asset
                      ? `${asset.name}${asset.size ? ` · ${formatBytes(asset.size)}` : ""}`
                      : "Matching asset appears when CI uploads it to the latest release"}
                  </div>
                  <a className="btn btn--primary btn--block" href={href}>
                    {label}
                  </a>
                </article>
              );
            })}
          </div>

          <div className="install-hint">
            <h3>Quick CLI setup</h3>
            <pre>
              <code>{installSnippet(platform, release)}</code>
            </pre>
          </div>
        </section>

        <section className="section" id="commands">
          <p className="section__eyebrow">CLI</p>
          <h2 className="section__title">Commands that fit a muscle memory.</h2>
          <p className="section__lede">
            Run bare <code>desk</code> for the TUI, or pass a subcommand for
            scripted workflows.
          </p>
          <div className="commands">
            {COMMANDS.map((c) => (
              <div className="command-row" key={c.cmd}>
                <code>{c.cmd}</code>
                <span>{c.desc}</span>
              </div>
            ))}
          </div>
        </section>
      </main>

      <footer className="footer">
        <div>
          <span className="footer__brand">Desk</span>
          <span> · MIT License</span>
        </div>
        <div>
          <a href="https://github.com/ajaytainwala-dev/javaproject">GitHub</a>
          {" · "}
          <a href={releasesPageUrl()}>Releases</a>
        </div>
      </footer>
    </div>
  );
}
