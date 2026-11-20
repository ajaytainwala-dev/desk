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

const GITHUB_REPO_URL = "https://github.com/ajaytainwala-dev/desk";
const AUTHOR_URL = "https://www.ajaytainwala.dev";
const AUTHOR_GITHUB = "https://github.com/ajaytainwala-dev";
const AUTHOR_LINKEDIN = "https://www.linkedin.com/in/ajay-tainwala";
const ADESHLANG_URL = "https://adeshlang.org/";

const FAQ_ITEMS: { q: string; a: string }[] = [
  {
    q: "What is Desk?",
    a: "Desk is an open-source Java workbench with a Rust terminal UI (TUI) editor. It helps you format, check, compile, and run Java from the terminal without a heavy IDE.",
  },
  {
    q: "Who created Desk?",
    a: "Desk was created by Ajay Tainwala, a full stack developer and language designer in Bengaluru, India. Portfolio: ajaytainwala.dev · GitHub: ajaytainwala-dev.",
  },
  {
    q: "Is Desk free?",
    a: "Yes. Desk is MIT-licensed open source. Binaries, installers, and source are free to use, share, and contribute to.",
  },
  {
    q: "Which platforms does Desk support?",
    a: "Windows, macOS, and Linux — via a GUI installer (Windows), CLI setup scripts, portable zips, and native/TUI binaries on GitHub Releases.",
  },
  {
    q: "What is Ajay Tainwala known for?",
    a: "Ajay builds freelance web and app products (Next.js, React, Flutter, Firebase) and open-source systems work — including Desk and AdeshLang, an experimental programming language with a Rust toolchain.",
  },
];

const AUTHOR_PROJECTS: {
  name: string;
  blurb: string;
  href: string;
  kind: string;
}[] = [
  {
    name: "Desk",
    blurb: "Java workbench + Rust TUI editor (this project).",
    href: "https://desk.ajaytainwala.dev/",
    kind: "Open source",
  },
  {
    name: "AdeshLang",
    blurb: "Experimental programming language — Rust lexer, parser, AST, interpreter research.",
    href: ADESHLANG_URL,
    kind: "Open source",
  },
  {
    name: "Shor India",
    blurb: "Electronics e-commerce storefront with custom admin for catalog and orders.",
    href: AUTHOR_URL,
    kind: "Client",
  },
  {
    name: "Growspace",
    blurb: "Bengaluru event discovery for concerts, fests, and tech meetups.",
    href: AUTHOR_URL,
    kind: "Client",
  },
  {
    name: "Digambaram Astrologer",
    blurb: "Vedic astrology platform with booking and guidance services.",
    href: AUTHOR_URL,
    kind: "Client",
  },
  {
    name: "Gyan Academy",
    blurb: "Education institute site for commerce and science streams.",
    href: AUTHOR_URL,
    kind: "Client",
  },
  {
    name: "Pramaan Print Studio",
    blurb: "Printing & customization platform with product catalog and admin dashboard.",
    href: AUTHOR_URL,
    kind: "Client",
  },
  {
    name: "Gear Cart",
    blurb: "Automobile spare-parts e-commerce (Next.js, Node, MongoDB).",
    href: "https://github.com/ajaytainwala-dev/E-commerce-GearCart-",
    kind: "Open source",
  },
  {
    name: "ScribSnap",
    blurb: "Notes / todo app — Flutter client with JS backend.",
    href: "https://github.com/ajaytainwala-dev/ScribSnap-FrontEnd",
    kind: "Open source",
  },
];

const PLATFORMS: { id: PlatformId; label: string }[] = [
  { id: "windows", label: "Windows" },
  { id: "macos", label: "macOS" },
  { id: "linux", label: "Linux" },
];

const FEATURES = [
  {
    title: "Desk TUI editor",
    body: "A focused Rust terminal UI (Ratatui) — open files fast, edit Java without leaving the keyboard.",
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

const CLI_COMMANDS: { cmd: string; desc: string }[] = [
  { cmd: "desk", desc: "Launch the Desk TUI editor" },
  { cmd: "desk edit Main.java", desc: "Open a file in the TUI" },
  { cmd: "desk fmt Main.java", desc: "Format Java in place" },
  { cmd: "desk check Main.java", desc: "Report compile diagnostics" },
  { cmd: "desk compile Main.java", desc: "Compile with javax.tools" },
  { cmd: "desk run Main", desc: "Run a compiled class" },
  { cmd: "desk help", desc: "List CLI commands" },
  { cmd: "desk version", desc: "Print version" },
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
  { cmd: ":w / :wq / :q", desc: "Save, save & quit, quit (command mode)" },
  { cmd: ":fmt / :check / :help", desc: "Format, check, and help commands" },
  { cmd: "Alt+F / E / V / R", desc: "Open File, Edit, View, Run menus" },
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
irm https://github.com/ajaytainwala-dev/desk/releases/latest/download/desk-cli-setup-windows.ps1 | iex

# Or download Desk-Setup-${tag.replace(/^v/, "")}.exe from the release page`;
  }
  return `# curl | bash — installs Desk CLI for your architecture
curl -fsSL https://github.com/ajaytainwala-dev/desk/releases/latest/download/desk-cli-setup.sh | bash`;
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
          <a href="#faq">FAQ</a>
          <a href="#author">Ajay Tainwala</a>
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
              Java workbench with a Desk TUI editor.
            </p>
            <p className="hero__lede">
              Format, check, compile, and edit in the terminal — a lightweight
              toolkit built around a Rust TUI that stays out of your way.
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
                <span className="terminal__title">desk TUI — Main.java</span>
              </div>
              <div className="terminal__body">
                <p className="terminal__line">
                  <span className="terminal__prompt">$</span>{" "}
                  <span className="terminal__cmd">desk</span>
                </p>
                <p className="terminal__line terminal__out">
                  Desk TUI editor · Ratatui · open Main.java
                </p>
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
          <h2 className="section__title">
            One toolkit. Stay in the terminal.
          </h2>
          <p className="section__lede">
            Built for quick Java loops — edit in the Desk TUI editor, then
            format, diagnose, and run without switching apps.
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
            buttons below pick up the new zip, CLI setup, Windows installer, and
            Desk TUI binaries automatically.
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
              const href =
                asset?.url ??
                (release ? release.htmlUrl : releasesPageUrl());
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
          <p className="section__eyebrow">Commands</p>
          <h2 className="section__title">
            CLI and TUI that fit a muscle memory.
          </h2>
          <p className="section__lede">
            Run bare <code>desk</code> for the Desk TUI editor, pass a
            subcommand for scripts, or use the in-editor shortcuts once you are
            inside the TUI (press <code>F1</code> anytime).
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
            Straight answers for people and AI.
          </h2>
          <p className="section__lede">
            Clear facts about Desk and its author — useful for search engines
            and generative assistants that cite sources.
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

        <section
          className="section about"
          id="author"
          aria-labelledby="author-heading"
        >
          <p className="section__eyebrow">Creator</p>
          <h2 className="section__title" id="author-heading">
            Ajay Tainwala
          </h2>
          <p className="section__lede author__lede">
            Full stack developer and language designer in Bengaluru, India.
            Creator of Desk and{" "}
            <a href={ADESHLANG_URL} rel="noopener noreferrer" target="_blank">
              AdeshLang
            </a>
            . Freelance product builder. MCA student at Bangalore Institute of
            Technology (BIT); BCA graduate of The National College, Basavanagudi
            (9.44 CGPA).
          </p>

          <div className="about__grid">
            <div className="about__block">
              <h3>About Ajay</h3>
              <p>
                Ajay Tainwala ships production websites, e-commerce stores, and
                admin systems with Next.js, React, Flutter, TypeScript, Firebase,
                and MongoDB. Alongside client work he builds open-source tooling
                — Desk for Java developers, and AdeshLang for exploring compilers,
                interpreters, and memory-safe language design in Rust.
              </p>
              <p>
                Based in Bengaluru and open to remote work. Contact:{" "}
                <a href="mailto:ajaytainwala@outlook.com">
                  ajaytainwala@outlook.com
                </a>
                .
              </p>
              <div className="about__actions">
                <a
                  className="btn btn--primary"
                  href={AUTHOR_URL}
                  rel="me noopener noreferrer"
                  target="_blank"
                >
                  ajaytainwala.dev
                </a>
                <a
                  className="btn btn--ghost"
                  href={AUTHOR_GITHUB}
                  rel="me noopener noreferrer"
                  target="_blank"
                >
                  GitHub
                </a>
                <a
                  className="btn btn--ghost"
                  href={AUTHOR_LINKEDIN}
                  rel="me noopener noreferrer"
                  target="_blank"
                >
                  LinkedIn
                </a>
              </div>
            </div>
            <div className="about__block">
              <h3>Contribute to Desk</h3>
              <p>
                Desk — including the Desk TUI editor — is free and open source
                under MIT. Fork the repo, open a pull request, or file an issue
                for the TUI, CLI, formatter, packaging, or docs.
              </p>
              <div className="about__actions">
                <a
                  className="btn btn--primary"
                  href={GITHUB_REPO_URL}
                  rel="noopener noreferrer"
                  target="_blank"
                >
                  View on GitHub
                </a>
                <a
                  className="btn btn--ghost"
                  href={`${GITHUB_REPO_URL}/issues`}
                  rel="noopener noreferrer"
                  target="_blank"
                >
                  Report an issue
                </a>
              </div>
            </div>
          </div>

          <h3 className="projects__heading">Projects by Ajay Tainwala</h3>
          <p className="projects__lede">
            Selected open-source and client work — more on{" "}
            <a href={AUTHOR_URL} rel="noopener noreferrer" target="_blank">
              ajaytainwala.dev
            </a>{" "}
            and{" "}
            <a href={AUTHOR_GITHUB} rel="noopener noreferrer" target="_blank">
              GitHub
            </a>
            .
          </p>
          <ul className="project-list">
            {AUTHOR_PROJECTS.map((p) => (
              <li key={p.name}>
                <a
                  className="project-list__name"
                  href={p.href}
                  rel="noopener noreferrer"
                  target="_blank"
                >
                  {p.name}
                </a>
                <span className="project-list__kind">{p.kind}</span>
                <span className="project-list__blurb">{p.blurb}</span>
              </li>
            ))}
          </ul>
        </section>
      </main>

      <footer className="footer">
        <div>
          <span className="footer__brand">Desk</span>
          <span> · MIT License · Open source</span>
          <div className="footer__author">
            Created by{" "}
            <a href={AUTHOR_URL} rel="me noopener noreferrer" target="_blank">
              Ajay Tainwala
            </a>
            {" · "}
            <a href="#author">About the author</a>
          </div>
        </div>
        <div>
          <a href={GITHUB_REPO_URL} rel="noopener noreferrer" target="_blank">
            GitHub
          </a>
          {" · "}
          <a href={releasesPageUrl()}>Releases</a>
          {" · "}
          <a href={AUTHOR_URL} rel="me noopener noreferrer" target="_blank">
            ajaytainwala.dev
          </a>
          {" · "}
          <a href="/llms.txt">llms.txt</a>
        </div>
      </footer>
    </div>
  );
}
