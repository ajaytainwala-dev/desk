import { motion, useReducedMotion } from "framer-motion";
import { useEffect, useMemo, useState } from "react";
import "./App.css";
import {
  GITHUB_URL,
  availableDownloads,
  detectPlatform,
  fetchLatestRelease,
  formatBytes,
  latestAssetUrl,
  releasesPageUrl,
  resolveDownload,
  type DownloadKind,
  type LatestRelease,
  type PlatformId,
} from "./releases";

const AUTHOR_URL = "https://www.ajaytainwala.dev";

const FAQ_ITEMS: { q: string; a: string }[] = [
  {
    q: "What is Desk?",
    a: "Desk is a lightweight Java workbench with a terminal UI editor. Open a project, edit code, then format, check, compile, and run — without a heavy IDE.",
  },
  {
    q: "Is Desk free?",
    a: "Yes. Desk is MIT-licensed open source. Binaries, installers, and source are free to use, share, and contribute to.",
  },
  {
    q: "Which platforms does Desk support?",
    a: "Windows, macOS, and Linux — via a GUI installer (Windows), CLI setup scripts, portable zips, and TUI binaries on GitHub Releases.",
  },
  {
    q: "How do downloads stay up to date?",
    a: "This site loads the latest GitHub release on every visit. When a new tag is published, the download buttons pick up the new assets automatically.",
  },
];

const PLATFORMS: { id: PlatformId; label: string }[] = [
  { id: "windows", label: "Windows" },
  { id: "macos", label: "macOS" },
  { id: "linux", label: "Linux" },
];

const FEATURES = [
  {
    title: "Edit in the terminal",
    body: "A focused TUI editor for your project — open files, switch tabs, and stay on the keyboard.",
  },
  {
    title: "Format, check, run",
    body: "Catch issues and run Java from the same place you edit — no context switch to a full IDE.",
  },
  {
    title: "Lightweight by design",
    body: "A small toolkit that fits SSH sessions, quick fixes, and everyday Java loops.",
  },
];

const USES = [
  {
    title: "Open a project",
    body: "Run desk in a folder to start the editor on that workspace.",
  },
  {
    title: "Fix and format",
    body: "Use desk check and desk fmt when you want a fast pass over a file.",
  },
  {
    title: "Compile and run",
    body: "Compile and run from the CLI or TUI when you are ready to try the change.",
  },
];

const CLI_COMMANDS: { cmd: string; desc: string }[] = [
  { cmd: "desk", desc: "Launch the Desk TUI editor" },
  { cmd: "desk open <path>", desc: "Open a folder or file" },
  { cmd: "desk edit Main.java", desc: "Open a file in the TUI" },
  { cmd: "desk fmt Main.java", desc: "Format Java in place" },
  { cmd: "desk check Main.java", desc: "Report compile diagnostics" },
  { cmd: "desk compile Main.java", desc: "Compile a source file" },
  { cmd: "desk run Main", desc: "Run a compiled class" },
  { cmd: "desk help", desc: "List CLI commands" },
];

const TUI_COMMANDS: { cmd: string; desc: string }[] = [
  { cmd: "F1", desc: "Toggle in-editor help overlay" },
  { cmd: "Ctrl+N / Ctrl+O / Ctrl+S", desc: "New file, open tree, save" },
  { cmd: "Ctrl+Q", desc: "Quit the editor" },
  { cmd: "i / Esc / v", desc: "Insert, normal, and visual modes" },
  { cmd: "Ctrl+F / Ctrl+H", desc: "Find and replace" },
  { cmd: "Ctrl+J", desc: "Toggle integrated terminal" },
  { cmd: "F3 / F8 / F4", desc: "Explorer, output panel, line numbers" },
  { cmd: "F5 / F6 / F9 / F10", desc: "Run, compile, format, check" },
  { cmd: "F7", desc: "Stop the running Java job" },
  { cmd: ":w / :wq / :q", desc: "Save, save & quit, quit" },
  { cmd: ":fmt / :check / :help", desc: "Format, check, and help" },
];

function recommendedKind(platform: PlatformId): DownloadKind {
  if (platform === "windows") return "windows-gui";
  return "cli-setup";
}

function installSnippet(platform: PlatformId): string {
  if (platform === "windows") {
    return `# PowerShell — CLI setup from latest release
irm ${latestAssetUrl("desk-cli-setup-windows.ps1")} | iex`;
  }
  return `# curl | bash — installs Desk CLI for your architecture
curl -fsSL ${latestAssetUrl("desk-cli-setup.sh")} | bash`;
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
    () => availableDownloads(release, platform),
    [platform, release],
  );

  const primaryHref = useMemo(() => {
    const kind = recommendedKind(platform);
    const asset = resolveDownload(release, kind, platform);
    if (asset) return asset.url;
    const zip = resolveDownload(release, "portable-zip", platform);
    if (zip) return zip.url;
    const tui = resolveDownload(release, "tui", platform);
    if (tui) return tui.url;
    if (platform === "windows") {
      return latestAssetUrl("desk-cli-setup-windows.ps1");
    }
    return latestAssetUrl("desk-cli-setup.sh");
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
          <a href="#uses">Uses</a>
          <a href="#downloads">Downloads</a>
          <a href="#faq">FAQ</a>
          <a className="nav__cta" href="#downloads">
            Get Desk
          </a>
        </nav>
      </header>

      <main id="top">
        <section className="hero">
          <motion.div className="hero__copy" {...fade}>
            <h1 className="hero__brand">Desk</h1>
            <p className="hero__headline">
              Edit Java in the terminal.
            </p>
            <p className="hero__lede">
              A lightweight workbench with a TUI editor — format, check,
              compile, and run without opening a heavy IDE.
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
                  Latest <strong>{release.tag}</strong>
                  {release.publishedAt
                    ? ` · ${new Date(release.publishedAt).toLocaleDateString(
                        undefined,
                        {
                          year: "numeric",
                          month: "short",
                          day: "numeric",
                        },
                      )}`
                    : null}
                  {" · "}
                  <a href={release.htmlUrl}>Release notes</a>
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
                <span className="terminal__title">desk TUI — Main.java</span>
              </div>
              <div className="terminal__body">
                <p className="terminal__line">
                  <span className="terminal__prompt">$</span>{" "}
                  <span className="terminal__cmd">desk</span>
                </p>
                <p className="terminal__line terminal__out">
                  Open project · edit <span className="terminal__hl">Main.java</span>
                </p>
                <p className="terminal__line">
                  <span className="terminal__prompt">$</span>{" "}
                  <span className="terminal__cmd">desk check Main.java</span>
                </p>
                <p className="terminal__line terminal__out">
                  Main.java:12: error: cannot find symbol
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
            Built for the everyday Java loop — open a project, edit, then fix
            and run without switching apps.
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

        <section className="section" id="uses">
          <p className="section__eyebrow">Uses</p>
          <h2 className="section__title">The main loop.</h2>
          <p className="section__lede">
            Open a project, fix the code, run it — that is the Desk workflow.
          </p>
          <div className="features">
            {USES.map((u) => (
              <article className="feature" key={u.title}>
                <hr className="feature__rule" />
                <h3>{u.title}</h3>
                <p>{u.body}</p>
              </article>
            ))}
          </div>
        </section>

        <section className="section downloads" id="downloads">
          <p className="section__eyebrow">Downloads</p>
          <h2 className="section__title">Installers that stay current.</h2>
          <p className="section__lede">
            Each button is a direct link to a file on the latest GitHub release.
            Publish a new tag and the links update on the next visit.
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
                <a href={release.htmlUrl}>View release</a>
              </>
            )}
            {!loading && releaseError && (
              <span className="release-banner--error">
                {releaseError}. <a href={releasesPageUrl()}>Open releases</a>
              </span>
            )}
          </div>

          <div className="download-grid">
            {options.map((opt) => {
              const asset = resolveDownload(release, opt.kind, platform);
              const recommended = opt.kind === recommendedKind(platform);
              if (!asset) return null;

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
                    {asset.name}
                    {asset.size ? ` · ${formatBytes(asset.size)}` : ""}
                  </div>
                  <a
                    className="btn btn--primary btn--block"
                    href={asset.url}
                    download={asset.name}
                  >
                    Download {opt.title}
                  </a>
                </article>
              );
            })}
          </div>

          {!loading && release && options.length === 0 && (
            <p className="release-banner">
              No matching assets for this platform in {release.tag}.{" "}
              <a href={release.htmlUrl}>See all files</a>
            </p>
          )}

          <div className="install-hint">
            <h3>Quick CLI setup</h3>
            <pre>
              <code>{installSnippet(platform)}</code>
            </pre>
          </div>
        </section>

        <section className="section" id="commands">
          <p className="section__eyebrow">Commands</p>
          <h2 className="section__title">
            CLI and TUI that fit a muscle memory.
          </h2>
          <p className="section__lede">
            Run bare <code>desk</code> for the editor, or pass a subcommand for
            scripts. Inside the TUI, press <code>F1</code> anytime for help.
          </p>

          <h3 className="commands__heading">CLI</h3>
          <div className="commands">
            {CLI_COMMANDS.map((c) => (
              <div className="command-row" key={c.cmd}>
                <code>{c.cmd}</code>
                <span>{c.desc}</span>
              </div>
            ))}
          </div>

          <h3 className="commands__heading">TUI shortcuts</h3>
          <div className="commands">
            {TUI_COMMANDS.map((c) => (
              <div className="command-row" key={c.cmd}>
                <code>{c.cmd}</code>
                <span>{c.desc}</span>
              </div>
            ))}
          </div>
        </section>

        <section className="section" id="faq" aria-labelledby="faq-heading">
          <p className="section__eyebrow">FAQ</p>
          <h2 className="section__title" id="faq-heading">
            Straight answers.
          </h2>
          <p className="section__lede">
            Clear facts about Desk for users and search engines.
          </p>
          <dl className="faq">
            {FAQ_ITEMS.map((item) => (
              <div className="faq__item" key={item.q}>
                <dt>{item.q}</dt>
                <dd>{item.a}</dd>
              </div>
            ))}
          </dl>
        </section>

        <section className="section" id="contribute">
          <p className="section__eyebrow">Open source</p>
          <h2 className="section__title">Contribute to Desk</h2>
          <p className="section__lede">
            Desk is free under MIT. Fork the repo, open a pull request, or file
            an issue for the TUI, CLI, formatter, packaging, or docs.
          </p>
          <div className="about__actions" style={{ marginTop: "1.25rem" }}>
            <a
              className="btn btn--primary"
              href={GITHUB_URL}
              rel="noopener noreferrer"
              target="_blank"
            >
              View on GitHub
            </a>
            <a
              className="btn btn--ghost"
              href={`${GITHUB_URL}/issues`}
              rel="noopener noreferrer"
              target="_blank"
            >
              Report an issue
            </a>
          </div>
        </section>
      </main>

      <footer className="footer">
        <div>
          <span className="footer__brand">Desk</span>
          <span> · MIT License</span>
          <div className="footer__author">
            Created by{" "}
            <a href={AUTHOR_URL} rel="me noopener noreferrer" target="_blank">
              Ajay Tainwala
            </a>
          </div>
        </div>
        <div>
          <a href={GITHUB_URL} rel="noopener noreferrer" target="_blank">
            GitHub
          </a>
          {" · "}
          <a href={releasesPageUrl()}>Releases</a>
          {" · "}
          <a href="/llms.txt">llms.txt</a>
        </div>
      </footer>
    </div>
  );
}
