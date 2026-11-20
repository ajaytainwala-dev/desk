/** GitHub repo that hosts Desk release artifacts. */
export const GITHUB_REPO = "ajaytainwala-dev/javaproject";

export type PlatformId = "windows" | "macos" | "linux";

export type DownloadKind =
  | "windows-gui"
  | "windows-cli"
  | "portable-zip"
  | "cli-setup"
  | "native"
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
  /** Prefer these asset name patterns (first match wins). */
  patterns: RegExp[];
  platforms: PlatformId[];
  /** Shown when no matching asset exists yet. */
  comingSoonHint?: string;
}

/** Stable product downloads — patterns match release asset names from CI. */
export const DOWNLOAD_OPTIONS: DownloadOption[] = [
  {
    kind: "windows-gui",
    title: "Windows installer",
    description: "Guided GUI setup with Start Menu shortcuts and PATH option.",
    patterns: [
      /^Desk-Setup-.*\.exe$/i,
      /^desk-setup-.*windows.*\.exe$/i,
      /^desk-.*-setup\.exe$/i,
    ],
    platforms: ["windows"],
  },
  {
    kind: "windows-cli",
    title: "Windows CLI setup",
    description: "PowerShell installer for terminals and scripts.",
    patterns: [
      /^desk-cli-setup-windows\.ps1$/i,
      /^install-cli\.ps1$/i,
      /^desk-install-windows\.ps1$/i,
    ],
    platforms: ["windows"],
  },
  {
    kind: "portable-zip",
    title: "Portable zip",
    description: "jlink runtime + Desk — unzip and run, no install required.",
    patterns: [
      /^desk-portable-windows\.zip$/i,
      /^desk-portable-linux\.zip$/i,
      /^desk-portable-macos\.zip$/i,
      /^desk-portable\.zip$/i,
      /^desk-portable-.*\.zip$/i,
    ],
    platforms: ["windows", "macos", "linux"],
  },
  {
    kind: "cli-setup",
    title: "CLI setup script",
    description: "One-liner install for macOS and Linux shells.",
    patterns: [
      /^desk-cli-setup\.sh$/i,
      /^install-cli\.sh$/i,
      /^desk-install\.sh$/i,
    ],
    platforms: ["macos", "linux"],
  },
  {
    kind: "native",
    title: "Native binary",
    description: "Standalone GraalVM native image for your OS.",
    patterns: [], // filled per-platform below via resolveDownload
    platforms: ["windows", "macos", "linux"],
  },
  {
    kind: "tui",
    title: "TUI editor binary",
    description: "Rust terminal UI binary (desk).",
    patterns: [],
    platforms: ["windows", "macos", "linux"],
  },
];

const NATIVE_PATTERNS: Record<PlatformId, RegExp[]> = {
  windows: [/^desk-native-windows(\.exe)?$/i, /^desk\.exe$/i],
  macos: [/^desk-native-macos$/i, /^desk$/i],
  linux: [/^desk-native-linux$/i, /^desk$/i],
};

const TUI_PATTERNS: Record<PlatformId, RegExp[]> = {
  windows: [/^desk-tui-windows(\.exe)?$/i, /^desk\.exe$/i],
  macos: [/^desk-tui-macos$/i, /^desk$/i],
  linux: [/^desk-tui-linux$/i, /^desk$/i],
};

function patternsFor(kind: DownloadKind, platform: PlatformId): RegExp[] {
  if (kind === "native") return NATIVE_PATTERNS[platform];
  if (kind === "tui") return TUI_PATTERNS[platform];
  const option = DOWNLOAD_OPTIONS.find((o) => o.kind === kind);
  return option?.patterns ?? [];
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
    const match = assets.find((a) => pattern.test(a.name));
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
  if (kind === "portable-zip") {
    const preferred = [
      new RegExp(`^desk-portable-${platform}\\.zip$`, "i"),
      /^desk-portable\.zip$/i,
      /^desk-portable-.*\.zip$/i,
    ];
    return findAsset(release.assets, preferred);
  }
  return findAsset(release.assets, patternsFor(kind, platform));
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
 * assets after a tag/release — no website redeploy required for link updates
 * when asset names match the patterns above.
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
    assets: (data.assets ?? []).map((a) => ({
      name: a.name,
      url: a.browser_download_url,
      size: a.size,
    })),
  };
}

/** Releases page fallback when a specific asset is missing. */
export function releasesPageUrl(): string {
  return `https://github.com/${GITHUB_REPO}/releases`;
}

export function latestReleasePageUrl(tag?: string): string {
  if (tag) return `https://github.com/${GITHUB_REPO}/releases/tag/${tag}`;
  return releasesPageUrl();
}
