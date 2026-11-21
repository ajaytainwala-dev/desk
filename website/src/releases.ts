/** GitHub repo that hosts Desk release artifacts (renamed from javaproject). */
export const GITHUB_REPO = "ajaytainwala-dev/desk";

export const GITHUB_URL = `https://github.com/${GITHUB_REPO}`;

export type PlatformId = "windows" | "macos" | "linux";

export type DownloadKind =
  | "windows-gui"
  | "windows-cli"
  | "portable-zip"
  | "cli-setup"
  | "tui";

export interface ReleaseAsset {
  name: string;
  url: string;
  size: number;
}

export interface LatestRelease {
  tag: string;
  name: string;
  publishedAt: string | null;
  htmlUrl: string;
  assets: ReleaseAsset[];
}

export interface DownloadOption {
  kind: DownloadKind;
  title: string;
  description: string;
  platforms: PlatformId[];
}

/**
 * Product downloads — each kind maps to a stable release asset name from CI.
 * Only cards with a matching asset are shown on the site.
 */
export const DOWNLOAD_OPTIONS: DownloadOption[] = [
  {
    kind: "windows-gui",
    title: "Windows installer",
    description: "Guided GUI setup with Start Menu shortcuts and PATH option.",
    platforms: ["windows"],
  },
  {
    kind: "windows-cli",
    title: "Windows CLI setup",
    description: "PowerShell installer for terminals and scripts.",
    platforms: ["windows"],
  },
  {
    kind: "cli-setup",
    title: "CLI setup script",
    description: "One-liner install for macOS and Linux shells.",
    platforms: ["macos", "linux"],
  },
  {
    kind: "portable-zip",
    title: "Portable zip",
    description: "jlink runtime + Desk — unzip and run, no install required.",
    platforms: ["windows", "linux"],
  },
  {
    kind: "tui",
    title: "TUI editor binary",
    description: "Rust terminal UI binary (desk).",
    platforms: ["windows", "macos", "linux"],
  },
];

function patternsFor(kind: DownloadKind, platform: PlatformId): RegExp[] {
  switch (kind) {
    case "windows-gui":
      return [/^Desk-Setup-.*\.exe$/i];
    case "windows-cli":
      return [/^desk-cli-setup-windows\.ps1$/i];
    case "cli-setup":
      return [/^desk-cli-setup\.sh$/i];
    case "portable-zip":
      return [
        new RegExp(`^desk-portable-${platform}\\.zip$`, "i"),
        // Generic alias is the Linux portable build
        ...(platform === "linux" ? [/^desk-portable\.zip$/i] : []),
      ];
    case "tui":
      return platform === "windows"
        ? [/^desk-tui-windows(\.exe)?$/i]
        : platform === "macos"
          ? [/^desk-tui-macos$/i]
          : [/^desk-tui-linux$/i];
    default:
      return [];
  }
}

export function detectPlatform(): PlatformId {
  const ua = navigator.userAgent.toLowerCase();
  if (ua.includes("win")) return "windows";
  if (ua.includes("mac")) return "macos";
  return "linux";
}

export function formatBytes(bytes: number): string {
  if (!bytes || bytes <= 0) return "";
  const units = ["B", "KB", "MB", "GB"];
  let n = bytes;
  let i = 0;
  while (n >= 1024 && i < units.length - 1) {
    n /= 1024;
    i += 1;
  }
  return `${n.toFixed(i === 0 ? 0 : 1)} ${units[i]}`;
}

export function findAsset(
  assets: ReleaseAsset[],
  patterns: RegExp[],
): ReleaseAsset | null {
  for (const pattern of patterns) {
    const match = assets.find(
      (a) => pattern.test(a.name) && !a.name.endsWith(".sha256"),
    );
    if (match) return match;
  }
  return null;
}

export function resolveDownload(
  release: LatestRelease | null,
  kind: DownloadKind,
  platform: PlatformId,
): ReleaseAsset | null {
  if (!release) return null;
  return findAsset(release.assets, patternsFor(kind, platform));
}

/** Direct latest-download URL for a stable asset name (works before API loads). */
export function latestAssetUrl(assetName: string): string {
  return `${GITHUB_URL}/releases/latest/download/${assetName}`;
}

interface GhAsset {
  name: string;
  browser_download_url: string;
  size: number;
}

interface GhRelease {
  tag_name: string;
  name: string | null;
  published_at: string | null;
  html_url: string;
  assets: GhAsset[];
}

/**
 * Fetch the newest published GitHub release. Each site load picks up new
 * assets after a tag/release — no website redeploy required for link updates.
 */
export async function fetchLatestRelease(
  signal?: AbortSignal,
): Promise<LatestRelease> {
  const url = `https://api.github.com/repos/${GITHUB_REPO}/releases/latest`;
  const res = await fetch(url, {
    signal,
    headers: {
      Accept: "application/vnd.github+json",
    },
  });

  if (res.status === 404) {
    throw new Error("No published release yet");
  }
  if (!res.ok) {
    throw new Error(`GitHub API ${res.status}`);
  }

  const data = (await res.json()) as GhRelease;
  return {
    tag: data.tag_name,
    name: data.name || data.tag_name,
    publishedAt: data.published_at,
    htmlUrl: data.html_url,
    assets: (data.assets ?? [])
      .filter((a) => !a.name.endsWith(".sha256"))
      .map((a) => ({
        name: a.name,
        url: a.browser_download_url,
        size: a.size,
      })),
  };
}

export function releasesPageUrl(): string {
  return `${GITHUB_URL}/releases`;
}

export function latestReleasePageUrl(tag?: string): string {
  if (tag) return `${GITHUB_URL}/releases/tag/${tag}`;
  return releasesPageUrl();
}
