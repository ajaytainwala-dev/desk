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
    a: "Desk is an open-source Java development workbench: a Rust Ratatui TUI editor plus a Java CLI for workspaces, JDK management, Maven/Gradle builds, live JDT Language Server support, and JDI debugging.",
  },
  {
    q: "What Java versions does Desk target?",
    a: "Java 25 is the primary LTS baseline for Desk itself and new projects. You can pin other installed JDKs per workspace without changing system JAVA_HOME.",
  },
  {
    q: "Does Desk include a full IDE debugger UI?",
    a: "v0.1 ships real JDI/JDWP debugging through desk debug run on the CLI. An interactive TUI debug panel is not included yet — F5 in the editor still runs the program.",
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
    title: "Rust TUI workbench",
    body: "Multi-tab Ratatui editor with save safety, explorer, and job cancel — stay in the keyboard loop.",
  },
  {
    title: "Java 25 + JDK pins",
    body: "Primary LTS baseline is Java 25. Pin Temurin or local JDKs per workspace without mutating system JAVA_HOME.",
  },
  {
    title: "Maven & Gradle builds",
    body: "desk build / test detect Maven or Gradle (or fall back to javac) and can cancel running jobs.",
  },
  {
    title: "Live JDT Language Server",
    body: "Point DESK_JAVA_LS at Eclipse JDT LS for completion, hover, diagnostics, and refactor edits inside the workspace.",
  },
  {
    title: "JDI debugging (CLI)",
    body: "desk debug run launches real JDWP/JDI sessions — breakpoints, stack, and locals — without a fake TUI debug panel.",
  },
  {
    title: "Format, check, doctor",
    body: "desk fmt, check, compile, run, and doctor keep day-to-day Java hygiene close to the terminal.",
  },
];

const USES = [
  {
    title: "Open a project workspace",
    body: "desk . or desk open ./my-app loads the folder as a workspace with explorer, tabs, and JDK pin support.",
  },
  {
    title: "Scaffold a Java 25 app",
    body: "desk new my-app --template maven creates a starter project on the Java 25 baseline.",
  },
  {
    title: "Build and test with cancel",
    body: "desk build and desk test drive Maven/Gradle; Esc/F7 stop hanging processes without orphaning JVMs.",
  },
  {
    title: "Language intelligence",
    body: "With JDT LS installed, use desk lsp complete/hover or the TUI overlays for navigation and edits.",
  },
  {
    title: "Debug on the CLI",
    body: "desk debug run --main Demo --bp 12 --src Demo.java runs a real JDI session for quick breakpoint checks.",
  },
  {
    title: "Health-check the toolchain",
    body: "desk java doctor and desk doctor surface missing JDKs, project layout, Git, and LSP readiness.",
  },
];

const CLI_COMMANDS: { cmd: string; desc: string }[] = [
  { cmd: "desk / desk .", desc: "Launch the TUI on the current workspace" },
  { cmd: "desk open <path>", desc: "Open a folder workspace or file" },
  { cmd: "desk new <name>", desc: "Create a project (default Java 25)" },
  { cmd: "desk java use 25", desc: "Pin JDK 25 for this workspace" },
  { cmd: "desk build / desk test", desc: "Maven, Gradle, or javac build & test" },
  { cmd: "desk lsp diagnose", desc: "Language server / javac diagnostics" },
  { cmd: "desk debug run --main C --bp N", desc: "JDI/JDWP CLI debug session" },
  { cmd: "desk fmt / desk check", desc: "Format or compile-check a file" },
  { cmd: "desk doctor", desc: "Aggregate JDK / project / LSP health" },
  { cmd: "desk git status", desc: "Git helpers (no credential logging)" },
];

const TUI_COMMANDS: { cmd: string; desc: string }[] = [
  { cmd: "F1", desc: "Toggle in-editor help overlay" },
  { cmd: "Ctrl+N / Ctrl+O / Ctrl+S", desc: "New file, open tree, save" },
  { cmd: "Ctrl+Q", desc: "Quit the editor" },
  { cmd: "i / Esc / v", desc: "Insert, normal, and visual modes" },
  { cmd: "Ctrl+F / Ctrl+H", desc: "Find and replace" },
  { cmd: "Ctrl+J", desc: "Toggle integrated terminal / job panel" },
  { cmd: "F3 / F8 / F4", desc: "Explorer, output panel, line numbers" },
  { cmd: "F5 / F6 / F9 / F10", desc: "Run, compile, format, check (F5 is Run, not debug)" },
  { cmd: "F7 / Esc", desc: "Cancel the running build/run job" },
  { cmd: ":build / :w / :q", desc: "Build, save, quit (command mode)" },
  { cmd: ":fmt / :check / :help", desc: "Format, check, and help commands" },
  { cmd: "Alt+F / E / V / R", desc: "Open File, Edit, View, Run menus" },
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
              Java 25 workbench. Terminal-first.
            </p>
            <p className="hero__lede">
              Rust TUI editor, workspace JDK pins, Maven/Gradle builds, live
              JDT LS intelligence, and real JDI debugging from the CLI.
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
                <span className="terminal__title">desk — workspace</span>
              </div>
              <div className="terminal__body">
                <p className="terminal__line">
                  <span className="terminal__prompt">$</span>{" "}
                  <span className="terminal__cmd">desk java use 25</span>
                </p>
                <p className="terminal__line terminal__out">
                  Pinned <span className="terminal__hl">JDK 25</span> for this
                  workspace
                </p>
                <p className="terminal__line">
                  <span className="terminal__prompt">$</span>{" "}
                  <span className="terminal__cmd">desk build</span>
                </p>
                <p className="terminal__line terminal__out">
                  Maven · BUILD SUCCESS
                </p>
                <p className="terminal__line">
                  <span className="terminal__prompt">$</span>{" "}
                  <span className="terminal__cmd">
                    desk debug run --main Demo --bp 12
                  </span>
                </p>
                <p className="terminal__line terminal__out">
                  JDI · breakpoint hit · locals ready
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
          <h2 className="section__title">Workbench features that are real.</h2>
          <p className="section__lede">
            v0.1 covers the terminal loop for Java 25 projects — editor, builds,
            language server, and CLI debugging — without claiming unfinished UI.
          </p>
          <div className="features features--wide">
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
          <h2 className="section__title">How people use Desk day to day.</h2>
          <p className="section__lede">
            Practical workflows for opening projects, building, diagnosing, and
            debugging — all from the same toolkit.
          </p>
          <div className="features features--wide">
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
            CLI workflows and TUI shortcuts.
          </h2>
          <p className="section__lede">
            Run bare <code>desk</code> for the TUI, or use subcommands for
            builds, LSP, and JDI debug. Inside the editor, press <code>F1</code>{" "}
            anytime for help.
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
